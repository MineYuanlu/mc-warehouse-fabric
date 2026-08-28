package bid.yuanlu.mc.warehouse.core.engine.transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.item.SlotAllocation;
import bid.yuanlu.mc.warehouse.api.item.SlotAllocator;
import bid.yuanlu.mc.warehouse.api.navigation.Goal;
import bid.yuanlu.mc.warehouse.api.navigation.Navigator;
import bid.yuanlu.mc.warehouse.api.navigation.PathStatus;
import bid.yuanlu.mc.warehouse.api.transport.ItemMove;
import bid.yuanlu.mc.warehouse.api.transport.RunGrade;
import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.api.transport.TransportState;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.engine.container.ContainerProtocol;
import bid.yuanlu.mc.warehouse.core.engine.container.ContainerSession;
import bid.yuanlu.mc.warehouse.core.engine.container.MenuSlots;
import bid.yuanlu.mc.warehouse.core.engine.rule.PutFeasibility;
import bid.yuanlu.mc.warehouse.core.engine.rule.RuleApplicator;
import bid.yuanlu.mc.warehouse.core.event.WarehouseEvents;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.impl.allocator.FirstFitAllocator;
import bid.yuanlu.mc.warehouse.impl.navigation.NoOpNavigator;

/**
 * 传输引擎（PDD §5）：状态机 + 基于缓存的访问队列 + 单容器会话滚动执行
 * + 防振荡双机制 + 轮次追踪 + 异常表 → SUSPENDED/DONE(RunReport)。
 * <p>
 * 一切回调在客户端主线程；每 tick 由外部驱动 {@link #tick()}（引擎内部再泵协议层）。
 * <p>
 * 精度口径：itemsMoved 以「会话内规划并逐击对账」的数量计——快速路径（整堆 quickMove）
 * 的实际落位由服务端裁量，settle 重扫刷新缓存自愈（§3.8/§5.4），聚合总量不受影响。
 */
public final class TransportEngineImpl implements bid.yuanlu.mc.warehouse.api.transport.TransportEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/engine");

	/** 一次循环内阶段跳转上限（防意外环） */
	private static final int STAGE_HOP_GUARD = 6;
	/** 放入规划的循环上限 */
	private static final int PLAN_LOOP_GUARD = 128;

	// ---- 动作级进度描述（§5.9 PROGRESS 两级粒度）----

	public static final String MOVING_ACTION = "wh.progress.moving";
	public static final String SCANNING_ACTION = "wh.progress.scanning";
	public static final String PICKING_ACTION = "wh.progress.picking";
	public static final String PUTTING_ACTION = "wh.progress.putting";

	// ---- 单例 ----

	private static volatile TransportEngineImpl instance;

	public static TransportEngineImpl get() {
		TransportEngineImpl i = instance;
		if (i == null) throw new IllegalStateException("TransportEngine not initialized");
		return i;
	}

	public static void setInstance(@Nullable TransportEngineImpl engine) {
		instance = engine;
	}

	// ---- 依赖 ----

	private final WarehouseManagerImpl manager;
	private final ContainerMemoryStore cache;
	private final ModConfig config;

	// ---- 状态机 ----

	private boolean running;
	@Nullable
	private TransportState state;
	@Nullable
	private RunReport lastReport;
	@Nullable
	private String lastDetailKey;

	/** 阶段访问队列（进入阶段时构建一次，滚动消费） */
	private List<ContainerInfo> visitQueue = List.of();
	private int queueIdx;
	@Nullable
	private Navigator activeNavigator;
	private int navRetries;
	private boolean navStarted;

	/** 探索失败计数（跨轮次累计，达 exploreFailMax 触发 SUSPENDED） */
	private final Map<CacheKey, Integer> exploreFails = new HashMap<>();
	/** /wh continue 标记跳过的容器 */
	private final Set<CacheKey> skippedKeys = new HashSet<>();

	// 轮次追踪（§5.3）
	private boolean roundHadAction;
	private boolean roundHadNewExplore;
	private int roundNoProgressStreak;
	private int rounds;
	private int itemsMoved;
	private long startedAtMs;

	/** 阶段级移动记录（事件/日志用，§3.9 TransferPlan 语义的运行时形态） */
	private final List<ItemMove> stageLog = new ArrayList<>();
	private int plannedExpected;

	// ---- 会话桥接目标 ----

	@Nullable
	private WorldDimPos sessionPos;
	@Nullable
	private CacheKey sessionKey;
	@Nullable
	private ContainerInfo sessionInfo;
	private boolean sessionExploredNow;
	private final List<ItemMove> sessionPlanned = new ArrayList<>();

	// ---- 构造 ----

	public TransportEngineImpl(WarehouseManagerImpl manager, ContainerMemoryStore cache, ModConfig config) {
		this.manager = manager;
		this.cache = cache;
		this.config = config;
	}

	// ---- 对外控制 API（命令层经 api/transport/TransportEngine 接口）----

	@Override
	public void start() {
		start(null);
	}

	@Override
	public void start(@Nullable String pathfinderId) {
		if (running) return;
		resetRunState();
		pathfinderOverride = pathfinderId;
		running = true;
		setState(TransportState.ENTRY);
	}

	/** 本次运行的一次性寻路器覆盖（/wh start --pathfinder）；run 结束/重启时清除 */
	@Nullable
	private String pathfinderOverride;

	/** 仅暂停（可 continue 无损恢复，不跳过容器） */
	@Override
	public void stop() {
		if (!isRunning()) return;
		suspend(null, "wh.error.user_stop");
	}

	/** 重头开始：重置状态与轮次标志，重新 ENTRY */
	@Override
	public void restart() {
		if (state == null || state == TransportState.DONE) return;
		LOGGER.info("run restarted");
		discardSessionQuietly();
		cache.endRound();
		resetRunState();
		running = true;
		setState(TransportState.ENTRY);
	}

	/** 断点继续：出错容器标记 skip，继续当前轮次 */
	@Override
	public void continueRun() {
		if (running || state != TransportState.SUSPENDED) return;
		running = true;
		if (sessionKey != null && sessionInfo != null) {
			skippedKeys.add(sessionKey);
			LOGGER.info("skip container at {}", sessionPos);
		}
		sessionPos = null;
		sessionKey = null;
		lastDetailKey = null;
		reenterCurrentStage();
	}

	/** 退出搬运 */
	@Override
	public void abort() {
		if (state == null) return;
		finish(RunGrade.BLOCKED, "wh.report.aborted");
	}

	@Override
	public boolean isRunning() {
		return running && state != null && state != TransportState.DONE;
	}

	@Override
	@Nullable
	public TransportState state() {
		return state;
	}

	@Override
	@Nullable
	public RunReport lastReport() {
		return lastReport;
	}

	@Override
	@Nullable
	public WorldDimPos erroredPos() {
		return sessionPos;
	}

	@Override
	@Nullable
	public String lastDetailKey() {
		return lastDetailKey != null ? lastDetailKey : (lastReport != null ? lastReport.detailKey() : null);
	}

	// ---- 每 tick 驱动 ----

	@Override
	public void tick() {
		if (!isRunning()) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || Minecraft.getInstance().level == null) {
			finish(RunGrade.BLOCKED, "wh.report.player_gone"); // 断线/退出世界终止（§5.5）
			return;
		}
		ContainerProtocol.get().tick(); // 泵协议层（回调里推进队列）
		if (!isRunning()) return;
		switch (state) {
			case ENTRY -> driveEntry();
			case GET_TEMP -> driveTakeStage();
			case GET_INPUT -> driveTakeStage();
			case PUT_OUTPUT -> drivePutStage();
			case PUT_TEMP -> drivePutStage();
			default -> {
			}
		}
	}

	// ---- ENTRY ----

	private void driveEntry() {
		Warehouse wh = manager.active();
		if (wh == null || wh.containers.isEmpty()) {
			finish(RunGrade.BLOCKED, "wh.report.empty_warehouse");
			return;
		}
		PackView pack = PackView.capture();
		if (pack.hasRoom()) setState(TransportState.GET_TEMP);
		else setState(TransportState.PUT_OUTPUT);
	}

	// ---- 取出阶段驱动 ----

	private void driveTakeStage() {
		for (int guard = 0; guard < STAGE_HOP_GUARD; guard++) {
			if (sessionActive()) return; // 会话推进中，等回调
			if (queueIdx >= visitQueue.size()) {
				exitStage();
				return;
			}
			ContainerInfo c = visitQueue.get(queueIdx);
			CacheKey key = keyOf(c);
			if (skippedKeys.contains(key)) {
				queueIdx++;
				continue;
			}
			PathStatus st = navigateStep(c);
			switch (st) {
				case MOVING -> {
					return;
				}
				case CANCELLED -> {
					continue;
				}
				case FAILED -> {
					if (++navRetries <= config.navRetryMax) {
						startNavigation(c);
						continue;
					}
					suspend(c, "wh.error.nav_failed");
					return;
				}
				case ARRIVED -> {
					beginOpen(c);
					return;
				}
			}
		}
	}

	// ---- 放入阶段驱动 ----

	private void drivePutStage() {
		driveTakeStage(); // 驱动逻辑同构：开箱后的方向差异全在 planForCurrentStage
	}

	// ---- 访问队列与导航 ----

	private void rebuildQueueIfFresh(IOType io) {
		Warehouse wh = manager.active();
		if (wh == null) {
			visitQueue = List.of();
			queueIdx = 0;
			return;
		}
		List<ContainerInfo> q = new ArrayList<>();
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != io) continue;
			if (!sameDimHere(c.canonicalPos())) continue;
			q.add(c);
		}
		// 缓存预筛（§5.3 访问队列）：未探索一律进队；已探索按缓存口径剔除
		q.removeIf(c -> cacheJudgesNothingToDo(io, c));
		// hard 降序、soft 次级降序
		q.sort((a, b) -> {
			int cmp = Integer.compare(b.priority.hard(), a.priority.hard());
			return cmp != 0 ? cmp : Integer.compare(b.priority.soft(), a.priority.soft());
		});
		this.visitQueue = q;
		this.queueIdx = 0;
		this.navRetries = 0;
		this.navStarted = false;
		LOGGER.debug("{} queue: {} container(s)", io, q.size());
	}

	/**
	 * 缓存判定「无事可做」→ 出队（§5.4：缓存只决定要不要去，不作为操作依据）。
	 * 未探索容器永远有事可做（探索即收益，§5.3）。
	 * <p>
	 * 防振荡①（仅 INPUT 未探索容器）：背包无空间且 OUTPUT/TEMP 均无可确认空间 → 不去空取。
	 */
	private boolean cacheJudgesNothingToDo(IOType io, ContainerInfo c) {
		var mem = cache.getValid(keyOf(c), c.cacheType);
		boolean takeDirection = io == IOType.INPUT || io == IOType.TEMP;
		if (mem == null) {
			// 未探索
			if (io == IOType.INPUT) {
				PackView pack = PackView.capture();
				if (!pack.hasRoom() && !anyExploredHasSpace(IOType.OUTPUT) && !anyExploredHasSpace(IOType.TEMP)) {
					return true; // 取了也放不下——跳过该未探索 INPUT 本轮
				}
			}
			if (!takeDirection) {
				// 防振荡③（F3）：放入方向未探索——背包空、或规则上无可放物品则不去（避免空跑开箱）。
				// 注意：规则不可行的未探索 OUTPUT 将保持未探索，storage_full 出口（要求 OUTPUT 全探索）
				// 可能永不触发；input_empty 出口不受影响，运行仍正常终止。
				PackView pack = PackView.capture();
				if (pack.isEmpty()) return true;
				if (!PutFeasibility.anyFeasible(c.effectiveRuleMode(), resolveRules(manager.active(), c),
						pack.aggregate())) return true;
			}
			return false;
		}
		ContainerSnapshot snap = mem.snapshot();
		if (takeDirection) return !hasAnyNonEmpty(snap);
		// 防振荡③（F3）：已探索放入方向——无空间、背包空、或背包无「规则可放 + Detector 收」的物品则不去
		if (!hasAnyFreeSpace(snap)) return true;
		PackView pack = PackView.capture();
		if (pack.isEmpty()) return true;
		Map<ItemStack, Integer> agg = pack.aggregate();
		if (!PutFeasibility.anyFeasible(c.effectiveRuleMode(), resolveRules(manager.active(), c), agg)) return true;
		ContainerDetector detector = detectAt(absPos(c));
		for (ItemStack sample : agg.keySet()) {
			if (detector != null && !detector.acceptsPutAnywhere(snap, sample)) continue;
			return false; // 存在规则与检测器双过审的可放物品
		}
		return true;
	}

	/** 该类目是否存在未探索容器（§5.2：未知不得当作满足） */
	private boolean hasUnexplored(IOType io) {
		Warehouse wh = manager.active();
		if (wh == null) return false;
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != io) continue;
			if (skippedKeys.contains(keyOf(c))) continue; // 已跳过不算未知
			if (!cache.isExplored(keyOf(c), c.cacheType)) return true;
		}
		return false;
	}

	private boolean anyExploredHasSpace(IOType io) {
		Warehouse wh = manager.active();
		if (wh == null) return false;
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != io) continue;
			var mem = cache.getValid(keyOf(c), c.cacheType);
			if (mem != null && hasAnyFreeSpace(mem.snapshot())) return true;
		}
		return false;
	}

	private static boolean hasAnyNonEmpty(ContainerSnapshot snap) {
		for (var e : snap.slots().entrySet()) {
			if (!e.getValue().isEmpty() && snap.slotInfo(e.getKey()).canTakeFrom()) return true;
		}
		return false;
	}

	private static boolean hasAnyFreeSpace(ContainerSnapshot snap) {
		for (int i = 0; i < snap.slotCount(); i++) {
			if (!snap.slotInfo(i).canPutTo()) continue;
			ItemStack s = snap.slots().get(i);
			if (s == null || s.getCount() < s.getMaxStackSize()) return true;
		}
		return false;
	}

	private void startNavigation(ContainerInfo c) {
		activeNavigator = resolveNavigator();
		Goal goal = new Goal(absPos(c), Math.max(2.5, config.reachLimit - 1), null);
		activeNavigator.start(goal);
		navStarted = true;
		fireProgress(MOVING_ACTION);
	}

	private PathStatus navigateStep(ContainerInfo c) {
		if (!navStarted) {
			startNavigation(c);
			return PathStatus.MOVING; // NoOp 下一个 tick 即 ARRIVED
		}
		Navigator nav = activeNavigator;
		if (nav == null) return PathStatus.ARRIVED;
		return nav.tick();
	}

	private Navigator resolveNavigator() {
		LocalPlayer p = Minecraft.getInstance().player;
		String worldId = p == null ? null : bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker.get().currentWorldId();
		String dimId = currentDimId();
		String wanted = pathfinderOverride != null ? pathfinderOverride : config.pathfinder(worldId, dimId);
		Navigator nav = WarehouseRegistryImpl.navigator(wanted);
		return nav != null ? nav : new NoOpNavigator();
	}

	private static String currentDimId() {
		var level = Minecraft.getInstance().level;
		return level == null ? "unknown" : level.dimension().identifier().toString();
	}

	private static boolean sameDimHere(WorldDimPos pos) {
		return currentDimId().equals(pos.dim());
	}

	// ---- 开箱与会话 ----

	/** 默认交互实现：无显式配置时取首个注册项（原版 GUI 实现最先注册） */
	@Nullable
	private static ContainerInteraction defaultInteraction() {
		var all = WarehouseRegistryImpl.interactions();
		return all.isEmpty() ? null : all.getFirst();
	}

	@Nullable
	private static ContainerDetector detectAt(WorldDimPos pos) {
		return bid.yuanlu.mc.warehouse.core.cache.DetectorResolver.at(pos);
	}

	private void beginOpen(ContainerInfo c) {
		WorldDimPos abs = absPos(c);
		ContainerDetector detector = detectAt(abs);
		if (detector == null) {
			recordExploreFailure(c, "wh.error.container_gone");
			queueIdx++;
			return;
		}
		ContainerInteraction it = defaultInteraction();
		if (it == null) {
			suspend(c, "wh.error.no_interaction");
			return;
		}
		sessionPos = abs;
		sessionKey = keyOf(c, detector); // 复用已解析 Detector，避免重复扫描
		sessionInfo = c;
		sessionExploredNow = !cache.isExplored(sessionKey, c.cacheType);
		sessionPlanned.clear();
		plannedExpected = 0;
		fireProgress(SCANNING_ACTION);
		LOGGER.debug("opening {} ({})", c.canonicalPos(), c.ioType);
		ContainerDetector det = detector;
		ContainerProtocol.get().open(abs, detector, it, config, new SessionBridge(),
				snap -> cache.remember(keyOf(c, det), c.cacheType, snap)); // §5.4 关屏回写
	}

	private boolean sessionActive() {
		return ContainerProtocol.get().active() != null;
	}

	private void recordExploreFailure(ContainerInfo c, String errorKey) {
		CacheKey key = keyOf(c);
		int fails = exploreFails.merge(key, 1, Integer::sum);
		cache.invalidate(key); // 自愈失效
		if (fails >= config.exploreFailMax) {
			suspend(c, "wh.error.explore_fail_max");
			return;
		}
		LOGGER.debug("explore fail {} ({} attempts)", key, fails);
		WarehouseEvents.ERROR.invoker().onError(posString(c), errorKey);
	}

	// ---- 会话回调桥 ----

	private class SessionBridge implements ContainerSession.Listener {

		@Override
		public void onReady(ContainerSession session) {
			ContainerSnapshot snap = session.scanNow();
			if (snap == null || sessionKey == null || sessionInfo == null) {
				suspend(sessionInfo, "wh.error.scan_failed");
				return;
			}
			// §6.3：VERIFY 通过后即为对账态，写入缓存
			cache.remember(sessionKey, sessionInfo.cacheType, snap);
			exploreFails.remove(sessionKey);
			if (sessionExploredNow) {
				roundHadNewExplore = true;
				sessionExploredNow = false;
			}
			PlanResult plan = planForCurrentStage(session, snap);
			if (plan.steps.isEmpty()) {
				closeSessionAndAdvance("nothing-to-do", 0);
				return;
			}
			LOGGER.debug("plan built: {} step(s), expect {}", plan.steps.size(), plan.expectedMoved);
			session.execute(plan.steps);
			plannedExpected = plan.expectedMoved;
			stageLog.addAll(plan.moves);
			fireProgress(plan.picking ? PICKING_ACTION : PUTTING_ACTION);
		}

		@Override
		public void onStepsCompleted(ContainerSession session) {
			ContainerSnapshot after = session.scanNow();
			if (after != null && sessionKey != null && sessionInfo != null) {
				cache.remember(sessionKey, sessionInfo.cacheType, after); // settle 重扫回写（§5.3 步骤 5）
			}
			int moved = plannedExpected;
			itemsMoved += moved;
			if (moved > 0) roundHadAction = true;
			for (ItemMove m : sessionPlanned) {
				WarehouseEvents.ITEM_MOVED.invoker().onItemMoved(posString(m.targetPos()), m.amount());
			}
			closeSessionAndAdvance("completed", moved);
		}

		@Override
		public void onFailed(ContainerSession session, ContainerSession.Failure failure) {
			handleSessionFailure(failure);
		}
	}

	private void closeSessionAndAdvance(String why, int moved) {
		LOGGER.info("container done [{}] @{} state={} (+{} moved, total {})",
				why, sessionPos != null ? posString(sessionPos) : "?", state, moved, itemsMoved);
		ContainerProtocol.get().cancel(why);
		sessionPos = null;
		sessionKey = null;
		sessionInfo = null;
		sessionPlanned.clear();
		plannedExpected = 0;
		queueIdx++;
	}

	/** 异常映射（§5.5 异常表） */
	private void handleSessionFailure(ContainerSession.Failure f) {
		ContainerInfo c = sessionInfo;
		switch (f) {
			case CONTAINER_GONE, NOT_OPENED, UI_MISMATCH, REACH_FAILED -> {
				// 探索类失败：计数 + 自愈失效 + 达上限暂停；否则跳过本容器
				if (c == null) {
					suspend(null, "wh.error." + f.name().toLowerCase(java.util.Locale.ROOT));
					return;
				}
				recordExploreFailure(c, "wh.error." + f.name().toLowerCase(java.util.Locale.ROOT));
				if (state == TransportState.SUSPENDED) return; // recordExploreFailure 已 suspend
				closeSessionAndAdvance("explore-failed:" + f, 0);
			}
			case CLICK_TIMEOUT, CLICK_CORRECTED -> {
				if (c != null) cache.invalidate(keyOf(c)); // 操作超时：失效缓存 + 暂停
				suspend(c, "wh.error." + f.name().toLowerCase(java.util.Locale.ROOT));
			}
			case UI_CLOSED_EXTERNAL -> suspend(c, "wh.error.ui_closed_external"); // 绝不视为成功
			case PLAYER_GONE -> finish(RunGrade.BLOCKED, "wh.report.player_gone");
		}
	}

	// ---- 规划 ----

	private record PlanResult(List<ContainerSession.Step> steps, List<ItemMove> moves, int expectedMoved,
			boolean picking) {

		static PlanResult empty(boolean picking) {
			return new PlanResult(List.of(), List.of(), 0, picking);
		}
	}

	private PlanResult planForCurrentStage(ContainerSession session, ContainerSnapshot snap) {
		return switch (state) {
			case GET_TEMP -> planTakeFrom(session, snap, true);
			case GET_INPUT -> planTakeFrom(session, snap, false);
			case PUT_OUTPUT -> planPutInto(session, snap);
			case PUT_TEMP -> planPutInto(session, snap);
			default -> PlanResult.empty(true);
		};
	}

	/**
	 * 取出方向。INPUT=规则语义；TEMP=双策略（防振荡② §5.3）：
	 * 有未探索 OUTPUT → 保守全取；全部 OUTPUT 已探索 → 仅取「任一 OUTPUT 当前要」的物品。
	 */
	private PlanResult planTakeFrom(ContainerSession session, ContainerSnapshot snap, boolean tempStage) {
		Warehouse wh = manager.active();
		ContainerInfo info = sessionInfo;
		if (wh == null || info == null) return PlanResult.empty(true);
		List<ContainerRule> rules = resolveRules(wh, info);

		RuleApplicator.TakeMode mode = RuleApplicator.TakeMode.RULES;
		Predicate<ItemStack> filter = null;
		if (tempStage) {
			if (hasUnexploredOutput(wh)) {
				mode = RuleApplicator.TakeMode.ALL; // 保守策略
			} else {
				mode = RuleApplicator.TakeMode.ALL;
				filter = this::anyOutputWants; // 精确策略
			}
		}
		List<bid.yuanlu.mc.warehouse.core.engine.rule.TakePlan> plans =
				RuleApplicator.planTake(info, rules, snap, mode, filter);
		return buildTakeSteps(session, plans, snap);
	}

	private List<ContainerRule> resolveRules(Warehouse wh, ContainerInfo info) {
		List<ContainerRule> out = new ArrayList<>();
		for (String id : info.rules) {
			var r = wh.rules.get(id);
			if (r != null) out.add(r);
		}
		return out;
	}

	private boolean hasUnexploredOutput(Warehouse wh) {
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != IOType.OUTPUT) continue;
			if (!cache.isExplored(keyOf(c), c.cacheType)) return true;
		}
		return false;
	}

	/** TEMP 精确模式谓词：存在某已探索 OUTPUT 此刻仍能收下该物品（复用 planPut 判定） */
	private boolean anyOutputWants(ItemStack sample) {
		Warehouse wh = manager.active();
		if (wh == null) return false;
		Map<ItemStack, Integer> one = new LinkedHashMap<>();
		one.put(sample.copyWithCount(1), sample.getMaxStackSize());
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != IOType.OUTPUT) continue;
			var mem = cache.getValid(keyOf(c), c.cacheType);
			if (mem == null) continue;
			List<bid.yuanlu.mc.warehouse.core.engine.rule.PutDemand> demands =
					RuleApplicator.planPut(c, resolveRules(wh, c), mem.snapshot(), one);
			if (!demands.isEmpty()) return true;
		}
		return false;
	}

	/**
	 * 取出步骤构建：整堆走 quickMove 快速路径；部分量走 ExactAmount.withdraw。
	 * 滚动模拟：packSim（背包镜像）/contSim（容器镜像）同步演进，防跨堆总量超限。
	 */
	private PlanResult buildTakeSteps(ContainerSession session,
			List<bid.yuanlu.mc.warehouse.core.engine.rule.TakePlan> plans, ContainerSnapshot snap) {
		AbstractMenuAdapter menu = AbstractMenuAdapter.capture(session.handle());
		if (menu == null) return PlanResult.empty(true);

		List<ContainerSession.Step> steps = new ArrayList<>();
		List<ItemMove> moves = new ArrayList<>();
		int expected = 0;
		var contSim = WorkingModel.ofSnapshot(snap);

		for (var plan : plans) {
			ItemStack cur = contSim.get(plan.slot());
			int nowCount = cur.isEmpty() ? 0 : cur.getCount();
			if (nowCount <= 0) continue;
			int amount = Math.min(plan.amount(), nowCount);
			if (amount <= 0) continue;

			int menuSlot = menu.containerToMenu(plan.slot());
			if (menuSlot < 0) return PlanResult.empty(true); // 结构异常，放弃本容器

			if (amount >= nowCount) {
				int absorbed = Math.min(nowCount, menu.pack.freeUnits(cur));
				if (absorbed <= 0) break; // 背包完全装不下
				// 整堆：quickMove 全走（吸收多少由服务端裁量）
				steps.add(new ContainerSession.Step("整堆移出 s" + plan.slot(),
						() -> menu.it.quickMoveToPlayer(menu.handle, menuSlot), menuSlot));
				menu.pack.applyQuickMoveIn(cur, absorbed);
				contSim.removeUpTo(plan.slot(), nowCount);
				expected += absorbed;
				moves.add(new ItemMove(sessionPos, ItemMove.AUTO_SLOT, cur.copyWithCount(1), absorbed, 0));
			} else {
				// 部分量：找一个背包容纳位（同类部分堆优先，空槽次之）；target 为 invIdx
				int target = menu.pack.bestAbsorbSlotFor(cur, amount);
				if (target < 0) break; // 背包彻底装不下更多
				int fit = menu.pack.capacityAt(target, cur);
				int take = Math.min(amount, Math.max(fit, 0));
				if (take <= 0) break;
				int targetMenu = menu.packToMenu(target);
				if (targetMenu < 0) return PlanResult.empty(true);
				steps.addAll(bid.yuanlu.mc.warehouse.core.engine.container.ExactAmount.withdraw(
						menu.it, menu.handle, menuSlot, nowCount, take, new int[]{targetMenu}));
				menu.pack.applyWithdrawPlace(cur, target, take);
				contSim.removeUpTo(plan.slot(), take);
				expected += take;
				moves.add(new ItemMove(sessionPos, ItemMove.AUTO_SLOT, cur.copyWithCount(1), take, 0));
			}
			if (!menu.pack.hasAnyRoomFor(cur)) break;
		}
		if (steps.size() > PLAN_LOOP_GUARD) LOGGER.warn("take steps large: {}", steps.size());
		return steps.isEmpty() ? PlanResult.empty(true)
				: new PlanResult(steps, moves, expected, true);
	}

	/**
	 * 放入步骤构建：planPut 得需求 → SlotAllocator 分配到槽位（容量门控）→
	 * 整堆 quickMove / 部分量 ExactAmount.deposit。滚动模拟同取出侧。
	 */
	private PlanResult planPutInto(ContainerSession session, ContainerSnapshot snap) {
		Warehouse wh = manager.active();
		ContainerInfo info = sessionInfo;
		if (wh == null || info == null) return PlanResult.empty(true);

		AbstractMenuAdapter menu = AbstractMenuAdapter.capture(session.handle());
		if (menu == null) return PlanResult.empty(true);
		var packAgg = menu.pack.aggregate();

		// §8.2 第 3 条：槽位级放入过滤（熔炉系燃料/输入路由）经 Detector 判定
		ContainerDetector detector = session.detector();
		List<bid.yuanlu.mc.warehouse.core.engine.rule.PutDemand> demands =
				RuleApplicator.planPut(info, resolveRules(wh, info), snap, packAgg);
		if (detector != null) {
			demands.removeIf(d -> !detector.acceptsPutAnywhere(snap, d.sample())); // 必拒即跳，避免整轮超时
		}
		if (demands.isEmpty()) return PlanResult.empty(true);

		SlotAllocator allocator = resolveAllocator();
		List<ContainerSession.Step> steps = new ArrayList<>();
		List<ItemMove> moves = new ArrayList<>();
		int expected = 0;
		var contSim = WorkingModel.ofSnapshot(snap);

		for (var demand : demands) {
			ItemStack sample = demand.sample();
			int remaining = demand.amount();
			// 该物品被 Detector 允许进入的槽位集合（null = 全部 canPutTo 槽位）
			java.util.function.Predicate<bid.yuanlu.mc.warehouse.api.container.SlotInfo> slotFilter =
					detector == null ? null : si -> detector.acceptsPut(si, sample);
			int guard = 0;
			while (remaining > 0 && guard++ < PLAN_LOOP_GUARD) {
				int capAll = contSim.freeUnitsFor(sample);
				if (capAll <= 0) break;
				List<SlotAllocation> allocs = allocator.allocate(
						contSim.view(snap), sample.copyWithCount(Math.min(remaining, capAll)),
						Math.min(remaining, capAll), true, slotFilter);
				if (allocs.isEmpty()) break;
				for (SlotAllocation alloc : allocs) {
					if (remaining <= 0) break;
					int want = Math.min(alloc.count(), remaining);
					if (want <= 0) continue;

					Integer srcPack = menu.pack.pickSourceSlot(sample, want);
					if (srcPack == null) break; // 背包侧凑不出这批
					int srcMenu = menu.packToMenu(srcPack);
					if (srcMenu < 0) return PlanResult.empty(false);
					int srcCount = menu.pack.countAt(srcPack, sample);
					int give = Math.min(want, srcCount);

					if (give >= srcCount) {
						// 整堆移入
						steps.add(new ContainerSession.Step("整堆移入 p" + srcPack,
								() -> menu.it.quickMoveToContainer(menu.handle, srcMenu), srcMenu));
					} else {
						steps.addAll(bid.yuanlu.mc.warehouse.core.engine.container.ExactAmount.deposit(
								menu.it, menu.handle, srcMenu, srcCount, give));
					}
					menu.pack.takeFrom(srcPack, give);
					contSim.putItems(sample, alloc.slot(), give);
					remaining -= give;
					expected += give;
					moves.add(new ItemMove(sessionPos, ItemMove.AUTO_SLOT, sample.copyWithCount(1), give, 0));
				}
				if (steps.size() > PLAN_LOOP_GUARD * 2) break;
			}
		}
		return steps.isEmpty() ? PlanResult.empty(false)
				: new PlanResult(steps, moves, expected, false);
	}

	private SlotAllocator resolveAllocator() {
		SlotAllocator a = WarehouseRegistryImpl.slotAllocator(config.slotAllocator);
		return a != null ? a : new FirstFitAllocator();
	}

	// ---- 阶段出口与轮次边界 ----

	private void exitStage() {
		switch (state) {
			case GET_TEMP -> setState(TransportState.GET_INPUT);
			case GET_INPUT -> setState(TransportState.PUT_OUTPUT);
			case PUT_OUTPUT -> setState(TransportState.PUT_TEMP);
			case PUT_TEMP -> checkCycleBoundaryAndTransitionAfterPutTemp();
			default -> {
			}
		}
	}

	private void reenterCurrentStage() {
		if (state == null || state == TransportState.DONE || state == TransportState.SUSPENDED) {
			state = state == null ? TransportState.ENTRY : state;
			if (state == TransportState.SUSPENDED) state = TransportState.ENTRY;
		}
		switch (state) {
			case ENTRY -> setState(TransportState.ENTRY); // 重新评估入口
			default -> setState(state); // 重建当前阶段队列（rebuild 在 setState 时触发）
		}
	}

	/**
	 * PUT_TEMP 结束=一轮完成。先判出口条件（§5.2，未探索一律视为不满足），
	 * 再做轮次追踪与无进展双轮终止（§5.3）。
	 */
	private void checkCycleBoundaryAndTransitionAfterPutTemp() {
		PackView pack = PackView.capture();
		rounds++; // 本轮已完成（无论后续结束还是继续）

		// §5.2 v0.2：任何类目存在未探索容器 → 出口条件一律视为不满足（先探索再判定）
		boolean unexploredSources = hasUnexplored(IOType.INPUT) || hasUnexplored(IOType.TEMP);
		// 出口②：INPUT 全部已探索且无可取出
		if (!unexploredSources && allExploredAndNothingToTake(IOType.INPUT)) {
			finish(naturalGrade(pack, true), "wh.report.input_empty");
			return;
		}
		// 出口①：OUTPUT 与 TEMP 全满且背包装不下什么，且不存在未探索的可能货源
		if (!unexploredSources && allFullOrUnwanted(IOType.OUTPUT) && allFullOrUnwanted(IOType.TEMP)
				&& !hasUnexplored(IOType.OUTPUT)) {
			finish(naturalGrade(pack, false), "wh.report.storage_full");
			return;
		}


		boolean noProgress = !roundHadAction && !roundHadNewExplore;
		roundNoProgressStreak = noProgress ? roundNoProgressStreak + 1 : 0;
		roundHadAction = false;
		roundHadNewExplore = false;
		LOGGER.info("round {} done (no-progress streak={})", rounds, roundNoProgressStreak);

		if (roundNoProgressStreak >= 2) {
			finish(RunGrade.ABNORMAL, "wh.report.no_progress");
			return;
		}
		setState(TransportState.GET_TEMP);
	}

	/** 自然结束的五档判定（PDD §5.9 表；含 MVP 式近似） */
	private RunGrade naturalGrade(PackView pack, boolean inputExhausted) {
		boolean packIdle = pack.isEmpty() || !backpackHasAnythingOutputsWouldAccept(pack);
		if (inputExhausted && packIdle) return RunGrade.PERFECT;
		if (allFullOrUnwanted(IOType.OUTPUT) && allFullOrUnwanted(IOType.TEMP)) {
			return pack.isEmpty() ? RunGrade.GOOD : RunGrade.BLOCKED;
		}
		if (allFullOrUnwanted(IOType.OUTPUT)) return RunGrade.ACCEPTABLE; // TEMP 仍有剩余
		return RunGrade.BLOCKED;
	}

	/** 是否所有 {io} 容器已探索且「满或无法合并」（出口①口径的简化实现） */
	private boolean allFullOrUnwanted(IOType io) {
		Warehouse wh = manager.active();
		if (wh == null) return true;
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != io) continue;
			var mem = cache.getValid(keyOf(c), c.cacheType);
			if (mem == null) return false; // 未探索视为不满足
			if (hasAnyFreeSpace(mem.snapshot())) {
				// 有空间但若没有任何物品可填入也视为「满」（合并无源）
				if (mergeableSourceExists(io, mem.snapshot())) return false;
			}
		}
		return true;
	}

	/** 该类容器的剩余空间是否可能有物品可补（INPUT 有货 / 背包有货 或 TEMP 可流动） */
	private boolean mergeableSourceExists(IOType targetIo, ContainerSnapshot snap) {
		PackView pack = PackView.capture();
		for (ItemStack sample : pack.aggregate().keySet()) {
			if (containerWantsSample(snap, sample)) return true;
		}
		Warehouse wh = manager.active();
		if (wh == null) return false;
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != IOType.INPUT && c.ioType != IOType.TEMP) continue;
			var mem = cache.getValid(keyOf(c), c.cacheType);
			if (mem == null) continue;
			for (ItemStack sample : mem.snapshot().slots().values()) {
				if (!sample.isEmpty() && containerWantsSample(snap, sample)) return true;
			}
		}
		return false;
	}

	/** 目标容器是否还有「同类部分堆」可合并（出口①满判定的合并无源口径） */
	private static boolean containerWantsSample(ContainerSnapshot snap, ItemStack sample) {
		for (int i = 0; i < snap.slotCount(); i++) {
			ItemStack s = snap.slots().get(i);
			if (s == null || s.isEmpty()) continue;
			if (s.getCount() < s.getMaxStackSize() && ItemStack.isSameItemSameComponents(s, sample)) return true;
		}
		return false;
	}

	private boolean allExploredAndNothingToTake(IOType io) {
		Warehouse wh = manager.active();
		if (wh == null) return true;
		boolean any = false;
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != io) continue;
			any = true;
			var mem = cache.getValid(keyOf(c), c.cacheType);
			if (mem == null) return false; // 未探索→不满足
			if (hasAnyNonEmpty(mem.snapshot())) return false;
		}
		return any || io == IOType.INPUT; // 无 INPUT 容器视作空源满足
	}

	private boolean backpackHasAnythingOutputsWouldAccept(PackView pack) {
		Warehouse wh = manager.active();
		if (wh == null) return false;
		Map<ItemStack, Integer> maxed = new LinkedHashMap<>();
		pack.aggregate().forEach((k, v) -> maxed.put(k.copyWithCount(k.getMaxStackSize()), v));
		for (ContainerInfo c : wh.containers) {
			if (c.ioType != IOType.OUTPUT && c.ioType != IOType.TEMP) continue;
			var mem = cache.getValid(keyOf(c), c.cacheType);
			if (mem == null) return true; // 未探索还有可能收下
			var demands = RuleApplicator.planPut(c, resolveRules(wh, c), mem.snapshot(), maxed);
			if (!demands.isEmpty()) return true;
		}
		return false;
	}

	// ---- 状态机基础件 ----

	private void setState(TransportState s) {
		TransportState prev = state;
		state = s;
		navRetries = 0;
		navStarted = false;
		switch (s) {
			case GET_TEMP -> prepareQueue(IOType.TEMP);
			case GET_INPUT -> prepareQueue(IOType.INPUT);
			case PUT_OUTPUT -> prepareQueue(IOType.OUTPUT);
			case PUT_TEMP -> prepareQueue(IOType.TEMP);
			default -> {
				visitQueue = List.of();
				queueIdx = 0;
			}
		}
		if (prev != s) {
			WarehouseEvents.TRANSPORT_STATE.invoker().onStateChanged(s, lastDetailKey);
			fireProgress(MOVING_ACTION);
		}
	}

	private void prepareQueue(IOType io) {
		rebuildQueueIfFresh(io);
	}

	private void fireProgress(String action) {
		WarehouseEvents.PROGRESS.invoker().onProgress(state, action);
	}

	private void suspend(@Nullable ContainerInfo errored, String detailKey) {
		LOGGER.warn("SUSPENDED: {} ({})", errored != null ? errored.canonicalPos() : "?", detailKey);
		discardSessionQuietly();
		if (errored != null) {
			sessionPos = errored.canonicalPos();
			sessionKey = keyOf(errored);
			sessionInfo = errored; // 完整记录：continueRun 靠它进 skippedKeys
		}
		lastDetailKey = detailKey;
		running = false;
		setState(TransportState.SUSPENDED);
		WarehouseEvents.ERROR.invoker().onError(errored != null ? posString(errored) : null, detailKey);
	}

	private void discardSessionQuietly() {
		ContainerProtocol.get().cancel("discard");
		sessionPlanned.clear();
		plannedExpected = 0;
	}

	private void resetRunState() {
		exploreFails.clear();
		skippedKeys.clear();
		roundHadAction = false;
		roundHadNewExplore = false;
		roundNoProgressStreak = 0;
		rounds = 0;
		itemsMoved = 0;
		startedAtMs = System.currentTimeMillis();
		lastReport = null;
		lastDetailKey = null;
		stageLog.clear();
		visitQueue = List.of();
		queueIdx = 0;
		sessionPos = null;
		sessionKey = null;
		sessionInfo = null;
		sessionExploredNow = false;
		navStarted = false;
		navRetries = 0;
		pathfinderOverride = null;
		cache.beginRound();
	}

	private void finish(RunGrade grade, String detailKey) {
		if (state == TransportState.DONE) return;
		discardSessionQuietly();
		long duration = System.currentTimeMillis() - startedAtMs;
		RunReport report = new RunReport(grade, itemsMoved, rounds, duration, detailKey);
		lastReport = report;
		lastDetailKey = detailKey;
		running = false;
		state = TransportState.DONE;
		cache.endRound();
		WarehouseEvents.TRANSPORT_STATE.invoker().onStateChanged(TransportState.DONE, detailKey);
		WarehouseEvents.RUN_FINISHED.invoker().onRunFinished(report);
		LOGGER.info("run finished: grade={} moved={} rounds={} {}ms", report.grade(), report.itemsMoved(),
				report.rounds(), report.durationMs());
	}

	// ---- 杂项 ----

	private CacheKey keyOf(ContainerInfo c) {
		return keyOf(c, bid.yuanlu.mc.warehouse.core.cache.DetectorResolver.at(absPos(c)));
	}

	private static CacheKey keyOf(ContainerInfo c, @Nullable ContainerDetector d) {
		return CacheKey.of(c.canonicalPos(),
				bid.yuanlu.mc.warehouse.core.cache.DetectorResolver.playerUuidIfScoped(d));
	}

	/** 相对坐标→绝对坐标（有 anchor 时）；无解析能力时原样返回 */
	private WorldDimPos absPos(ContainerInfo c) {
		Warehouse wh = manager.active();
		if (wh == null) return c.canonicalPos();
		var abs = wh.resolveAbsolute(c.canonicalPos());
		return abs != null ? abs : c.canonicalPos();
	}

	private static String posString(WorldDimPos p) {
		return p.x() + "," + p.y() + "," + p.z() + "@" + p.dim();
	}

	private static String posString(ContainerInfo c) {
		return posString(c.canonicalPos());
	}

	// ---- 内联数据结构 ----

	/** 会话菜单的索引适配器：容器槽 k ↔ menu 索引、背包 invIdx ↔ menu 索引（归属判定，禁算术） */
	private static final class AbstractMenuAdapter {

		final ContainerInteraction it;
		final bid.yuanlu.mc.warehouse.api.interaction.ContainerHandle handle;
		final List<Integer> containerMenuIndexes;
		/** invIdx(0-35) → menu 槽位索引 */
		final List<Integer> packBridge;
		final PackSim pack;
		final int containerSlots;

		private AbstractMenuAdapter(ContainerInteraction it,
				bid.yuanlu.mc.warehouse.api.interaction.ContainerHandle handle,
				net.minecraft.world.inventory.AbstractContainerMenu menu) {
			this.it = it;
			this.handle = handle;
			this.containerMenuIndexes = MenuSlots.containerSlotIndexes(menu);
			this.containerSlots = containerMenuIndexes.size();
			this.packBridge = MenuSlots.packSlotIndexes(menu);
			this.pack = PackSim.capture(Minecraft.getInstance().player);
		}

		/** 背包内索引(0-35) → menu 槽位索引；越界返回 -1 */
		int packToMenu(int invIdx) {
			return invIdx >= 0 && invIdx < packBridge.size() ? packBridge.get(invIdx) : -1;
		}

		static AbstractMenuAdapter capture(bid.yuanlu.mc.warehouse.api.interaction.ContainerHandle h) {
			var menu = h.menu();
			var screen = h.screen();
			if (menu == null || screen == null || screen != Minecraft.getInstance().screen) return null;
			var it = defaultInteraction();
			if (it == null) return null;
			return new AbstractMenuAdapter(it, h, menu);
		}

		int containerToMenu(int k) {
			return k >= 0 && k < containerMenuIndexes.size() ? containerMenuIndexes.get(k) : -1;
		}
	}

	/** 主背包 36 格工作镜像（menu 索引 → 物品副本） */
	private static final class PackSim {

		private final Map<Integer, ItemStack> slots;

		private PackSim(Map<Integer, ItemStack> slots) {
			this.slots = slots;
		}

		/** 主背包 36 格镜像；key=Inventory 内部槽位号（0-35），经 packBridge 才映射到 menu */
		static PackSim capture(LocalPlayer player) {
			Map<Integer, ItemStack> m = new LinkedHashMap<>();
			if (player != null) {
				Inventory inv = player.getInventory();
				int n = Math.min(inv.getContainerSize(), 36);
				for (int i = 0; i < n; i++) m.put(i, inv.getItem(i).copy());
			}
			return new PackSim(m);
		}

		boolean hasRoom() {
			for (ItemStack s : slots.values())
				if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
			return false;
		}

		boolean hasAnyRoomFor(ItemStack sample) {
			for (ItemStack s : slots.values()) {
				if (s.isEmpty()) return true;
				if (s.getCount() < s.getMaxStackSize() && ItemStack.isSameItemSameComponents(s, sample)) return true;
			}
			return false;
		}

		/** 同类余量+空槽容量之和 */
		int freeUnits(ItemStack sample) {
			int sum = 0;
			for (ItemStack s : slots.values()) {
				if (s.isEmpty()) sum += sample.getMaxStackSize();
				else if (s.getCount() < s.getMaxStackSize() && ItemStack.isSameItemSameComponents(s, sample))
					sum += s.getMaxStackSize() - s.getCount();
			}
			return sum;
		}

		/** 最优容纳槽：同类可容纳 ≥ need 的部分堆优先，其次空槽；返回 menu 索引或 -1 */
		int bestAbsorbSlotFor(ItemStack sample, int need) {
			int emptyCandidate = -1;
			for (var e : slots.entrySet()) {
				ItemStack s = e.getValue();
				if (s.isEmpty()) {
					if (emptyCandidate < 0) emptyCandidate = e.getKey();
				} else if (s.getCount() < s.getMaxStackSize() && ItemStack.isSameItemSameComponents(s, sample)) {
					if (s.getMaxStackSize() - s.getCount() >= need) return e.getKey();
				}
			}
			return emptyCandidate;
		}

		int capacityAt(int menuIdx, ItemStack sample) {
			ItemStack s = slots.get(menuIdx);
			if (s == null || s.isEmpty()) return sample.getMaxStackSize();
			if (ItemStack.isSameItemSameComponents(s, sample))
				return s.getMaxStackSize() - s.getCount();
			return 0;
		}

		void applyQuickMoveIn(ItemStack sample, int units) {
			// moveItemStackTo 近似：同类部分堆填满 → 空槽新堆
			int left = units;
			for (var e : slots.entrySet()) {
				ItemStack s = e.getValue();
				if (left <= 0) return;
				if (!s.isEmpty() && s.getCount() < s.getMaxStackSize()
						&& ItemStack.isSameItemSameComponents(s, sample)) {
					int add = Math.min(left, s.getMaxStackSize() - s.getCount());
					s.setCount(s.getCount() + add);
					left -= add;
				}
			}
			for (var e : slots.entrySet()) {
				if (left <= 0) return;
				if (e.getValue().isEmpty()) {
					int put = Math.min(left, sample.getMaxStackSize());
					e.setValue(sample.copyWithCount(put));
					left -= put;
				}
			}
		}

		void applyWithdrawPlace(ItemStack sample, int menuIdx, int units) {
			ItemStack s = slots.get(menuIdx);
			if (s == null || s.isEmpty()) slots.put(menuIdx, sample.copyWithCount(units));
			else s.setCount(s.getCount() + units);
		}

		@Nullable
		Integer pickSourceSlot(ItemStack sample, int want) {
			Integer partialFit = null;
			Integer anySame = null;
			for (var e : slots.entrySet()) {
				ItemStack s = e.getValue();
				if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, sample)) continue;
				if (anySame == null) anySame = e.getKey();
				if (partialFit == null && s.getCount() >= want) partialFit = e.getKey(); // 尽量整付
			}
			return partialFit != null ? partialFit : anySame;
		}

		int countAt(int menuIdx, ItemStack sample) {
			ItemStack s = slots.get(menuIdx);
			return s == null || s.isEmpty() || !ItemStack.isSameItemSameComponents(s, sample) ? 0 : s.getCount();
		}

		void takeFrom(int menuIdx, int units) {
			ItemStack s = slots.get(menuIdx);
			if (s == null) return;
			int left = s.getCount() - units;
			slots.put(menuIdx, left > 0 ? s.copyWithCount(left) : ItemStack.EMPTY);
		}

		Map<ItemStack, Integer> aggregate() {
			Map<ItemStack, Integer> out = new LinkedHashMap<>();
			for (ItemStack s : slots.values()) {
				if (s.isEmpty()) continue;
				boolean merged = false;
				for (Map.Entry<ItemStack, Integer> e : out.entrySet()) {
					if (ItemStack.isSameItemSameComponents(e.getKey(), s)) {
						e.setValue(e.getValue() + s.getCount());
						merged = true;
						break;
					}
				}
				if (!merged) out.put(s.copyWithCount(1), s.getCount());
			}
			return out;
		}
	}

	/** 容器工作镜像（快照模拟器）：供滚动规划与分配器查询 */
	private static final class WorkingModel {

		private final Map<Integer, ItemStack> slots;
		private final int slotCount;

		private WorkingModel(Map<Integer, ItemStack> slots, int slotCount) {
			this.slots = slots;
			this.slotCount = slotCount;
		}

		static WorkingModel ofSnapshot(ContainerSnapshot snap) {
			Map<Integer, ItemStack> m = new LinkedHashMap<>();
			snap.slots().forEach((k, v) -> m.put(k, v.copy()));
			return new WorkingModel(m, snap.slotCount());
		}

		ItemStack get(int slot) {
			ItemStack s = slots.get(slot);
			return s == null ? ItemStack.EMPTY : s;
		}

		void removeUpTo(int slot, int units) {
			ItemStack s = get(slot);
			if (s.isEmpty()) return;
			int left = s.getCount() - units;
			if (left <= 0) slots.remove(slot);
			else slots.put(slot, s.copyWithCount(left));
		}

		void putItems(ItemStack sample, int slot, int units) {
			ItemStack s = get(slot);
			if (s.isEmpty()) {
				slots.put(slot, sample.copyWithCount(units));
			} else if (ItemStack.isSameItemSameComponents(s, sample)) {
				slots.put(slot, s.copyWithCount(s.getCount() + units));
			} else {
				throw new IllegalStateException("slot " + slot + " occupied by different item");
			}
		}

	/** 按槽位计算的该物品可放入总余量（不含能力过滤——分配器侧同样只看容量） */
		int freeUnitsFor(ItemStack sample) {
			int sum = 0;
			for (int i = 0; i < slotCount; i++) {
				ItemStack s = get(i);
				if (s.isEmpty()) sum += sample.getMaxStackSize();
				else if (ItemStack.isSameItemSameComponents(s, sample))
					sum += s.getMaxStackSize() - s.getCount();
			}
			return sum;
		}

		ContainerSnapshot view(ContainerSnapshot origin) {
			return new ContainerSnapshot(Map.copyOf(slots), origin.slotInfos(), origin.title(), origin.slotCount());
		}
	}

	/** 主背包视图快照（媒介口径 §5.3 统一；独立于开启中的 Screen） */
	record PackView(Map<Integer, ItemStack> slots) {

		static PackView capture() {
			Minecraft mc = Minecraft.getInstance();
			var inv = mc.player == null ? null : mc.player.getInventory();
			Map<Integer, ItemStack> slots = new LinkedHashMap<>();
			if (inv != null) {
				int n = Math.min(inv.getContainerSize(), 36);
				for (int i = 0; i < n; i++) slots.put(i, inv.getItem(i).copy());
			}
			return new PackView(slots);
		}

		boolean hasRoom() {
			for (ItemStack s : slots.values())
				if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
			return false;
		}

		boolean isEmpty() {
			return slots.values().stream().allMatch(ItemStack::isEmpty);
		}

		Map<ItemStack, Integer> aggregate() {
			Map<ItemStack, Integer> out = new LinkedHashMap<>();
			for (ItemStack s : slots.values()) {
				if (s.isEmpty()) continue;
				boolean merged = false;
				for (Map.Entry<ItemStack, Integer> e : out.entrySet()) {
					if (ItemStack.isSameItemSameComponents(e.getKey(), s)) {
						e.setValue(e.getValue() + s.getCount());
						merged = true;
						break;
					}
				}
				if (!merged) out.put(s.copyWithCount(1), s.getCount());
			}
			return out;
		}
	}
}

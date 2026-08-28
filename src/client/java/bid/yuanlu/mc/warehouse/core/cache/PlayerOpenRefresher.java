package bid.yuanlu.mc.warehouse.core.cache;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.WarehouseServices;
import bid.yuanlu.mc.warehouse.core.engine.container.ContainerProtocol;
import bid.yuanlu.mc.warehouse.core.engine.container.OpenScreenCapture;
import bid.yuanlu.mc.warehouse.core.engine.container.ScreenHooks;
import bid.yuanlu.mc.warehouse.core.engine.container.ScreenScanner;
import bid.yuanlu.mc.warehouse.core.mark.MarkMode;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.util.McScreens;

/**
 * 玩家手动开箱缓存刷新（PDD §5.4/§8.7，F2）：玩家自行打开激活仓库内已注册容器时，
 * 关屏后以真实扫描回写缓存，覆盖「标记后手动改箱」造成的缓存过期。
 * <p>
 * 绑定用三层原版信号收敛（F2 决策，2026-08-28）：
 * <ol>
 *   <li><b>点击捕获</b>：{@code MultiPlayerGameMode.useItemOn} 返回 consumed 的瞬间记录坐标
 *       （Mixin {@code MultiPlayerGameModeMixin}）——坐标在点击发包时定格，网络延时/移开视线无影响；
 *       潜行+手持物品（放置分支）与界面上点击被排除。</li>
 *   <li><b>FIFO 配对</b>：客户端收到 OpenScreen 的顺序 == 点击顺序，队头出队即配对。</li>
 *   <li><b>开箱信号验证</b>：木桶看方块状态 OPEN；箱子/末影箱/潜影盒看开合块事件
 *       （{@code ClientboundBlockEventPacket}）；无开合动画的类型（熔炉/漏斗等）直接放行。</li>
 * </ol>
 * 终审：关屏时 {@link ScreenScanner} 做 Detector 身份校验（BE 类型+标题+槽位数），
 * 任何错绑/不匹配绝不写缓存——缓存本就只是「要不要去」依据，非操作依据（§5.4）。
 */
public final class PlayerOpenRefresher {

	private static final Logger LOG = LoggerFactory.getLogger("yuanlu-warehouse/refresh");
	private static final PlayerOpenRefresher INSTANCE = new PlayerOpenRefresher();

	/** 点击记录窗口与最大数量 */
	private static final long CLICK_WINDOW_MS = 5000L;
	private static final int MAX_CLICKS = 4;
	/** 开箱信号有效窗口 */
	private static final long OPEN_WINDOW_MS = 5000L;
	/** pending 等待屏幕出现的超时 */
	private static final long PENDING_TIMEOUT_MS = 3000L;

	public static PlayerOpenRefresher get() {
		return INSTANCE;
	}

	private PlayerOpenRefresher() {
	}

	/** 点击记录（FIFO 队头 = 最早未消费点击） */
	private record Click(WorldDimPos abs, long at) {
	}

	/** 已绑定屏幕：其容器绝对坐标 */
	private record Bound(WorldDimPos abs) {
	}

	private final ArrayDeque<Click> clicks = new ArrayDeque<>();
	private final Map<AbstractContainerScreen<?>, Bound> bindings = new HashMap<>();
	/** 近 5s 内收到开箱块事件（箱子/末影箱/潜影盒）的坐标 */
	private final Map<BlockPos, Long> openSignals = new HashMap<>();

	/** 本次开屏的候选坐标（点击 FIFO 快照），绑定 tick 时按开箱信号逐一出选 */
	private final ArrayDeque<WorldDimPos> pairCandidates = new ArrayDeque<>();
	private long pendingExpireAt;
	private int lastOpenContainerId = -1;

	@Nullable
	private String lastWorldKey;
	private boolean initDone;

	/** 客户端初始化时调用：注册开屏/关屏钩子 */
	public synchronized void init() {
		if (initDone) return;
		initDone = true;
		OpenScreenCapture.register(id -> onOpenScreen(id));
		ScreenHooks.registerOnClose(screen -> onClose(screen));
	}

	/** 每 tick：屏幕出现时绑定 + 过期清理 + 会话切换清理 */
	public synchronized void tick() {
		long now = System.currentTimeMillis();
		expire(now);

		String worldKey = currentWorldKey();
		if (worldKey != null && !worldKey.equals(lastWorldKey)) {
			lastWorldKey = worldKey;
			clicks.clear();
			bindings.clear();
			openSignals.clear();
			pairCandidates.clear();
			lastOpenContainerId = -1;
			return;
		}
		if (lastWorldKey == null) lastWorldKey = worldKey;

		if (!pairCandidates.isEmpty() && lastOpenContainerId != -1) {
			Minecraft mc = Minecraft.getInstance();
			if (pendingExpireAt < now) {
				pairCandidates.clear();
				lastOpenContainerId = -1;
			} else if (McScreens.current() instanceof AbstractContainerScreen<?> s
					&& s.getMenu().containerId == lastOpenContainerId
					&& !ContainerProtocol.isEngineBound(s)) {
				// 候选按序选：开箱信号验证放在此处（块事件比 OpenScreen 晚到一拍）
				WorldDimPos picked = null;
				for (WorldDimPos cand : pairCandidates) {
					if (openingEvidenceOk(cand.toBlockPos(), now)) {
						picked = cand;
						break;
					}
				}
				if (picked != null && !engineOwns(picked)) {
					bindings.put(s, new Bound(picked));
					pairCandidates.clear();
					lastOpenContainerId = -1;
					LOG.debug("bind screen {} -> {}", s.getMenu().containerId, picked);
				} else {
					LOG.debug("bind skipped: picked={} engineOwns={} cands={}", picked,
							picked != null && engineOwns(picked), pairCandidates.size());
				}
			}
		}
	}

	/** Mixin：方块 use 分支 consumed 的点击（已排除放置分支/界面上点击） */
	public void onClickConsumed(BlockPos pos) {
		if (MarkMode.get().isActive()) return; // 标记模式自行处理注册/移除
		WorldDim dim = currentDim();
		if (dim == null) return;
		WorldDimPos abs = new WorldDimPos(dim.worldId(), dim.dimId(),
				pos.getX(), pos.getY(), pos.getZ());
		synchronized (this) {
			if (findContainer(abs) == null) return; // 只关心激活仓库内容器
			if (clicks.size() >= MAX_CLICKS) clicks.poll();
			clicks.add(new Click(abs, System.currentTimeMillis()));
			LOG.debug("click captured {}", abs);
		}
	}

	/** Mixin：开箱块事件（箱子/末影箱/潜影盒开合动画） */
	public void onBlockEvent(BlockPos pos, int eventId, int data) {
		if (eventId != 1) return;
		synchronized (this) {
			if (data > 0) {
				openSignals.put(pos, System.currentTimeMillis());
			} else {
				openSignals.remove(pos);
			}
		}
	}

	/** Mixin：服务端关屏包（新开屏替换旧屏/按 ESC）——放弃未出现的候选 */
	public synchronized void onServerCloseContainer() {
		pairCandidates.clear();
		lastOpenContainerId = -1;
	}

	/** 该坐标是否正被引擎会话占用（引擎 open 也走 useItemOn，需避免双绑双写） */
	private static boolean engineOwns(WorldDimPos pos) {
		var active = ContainerProtocol.get().active();
		return active != null && active.pos().equals(pos);
	}

	// ---- 内部 ----

	private void onOpenScreen(int containerId) {
		synchronized (this) {
			// handleOpenScreen 因 ensureRunningOnSameThread 会在 Netty 与 Render 线程
			// 各派发一次（同 containerId）：首次已排空点击并置候选，第二次直接忽略，
			// 防止把已排空的候选清掉导致绑定丢失。
			if (containerId == lastOpenContainerId && !pairCandidates.isEmpty()) {
				return;
			}
			long now = System.currentTimeMillis();
			expire(now);
			lastOpenContainerId = containerId;
			// 点击 FIFO 整体转入候选；开箱信号验证延后到绑定 tick（块事件比 OpenScreen 晚一拍）
			pairCandidates.clear();
			Click c = clicks.poll();
			while (c != null) {
				pairCandidates.add(c.abs());
				c = clicks.poll();
			}
			pendingExpireAt = now + PENDING_TIMEOUT_MS;
			LOG.debug("onOpenScreen id={} candidates={}", containerId, pairCandidates.size());
		}
	}

	private void onClose(AbstractContainerScreen<?> screen) {
		Bound bound;
		synchronized (this) {
			bound = bindings.remove(screen);
		}
		if (bound == null) {
			LOG.debug("close: no binding for screen {}", screen.getMenu().containerId);
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			ScreenScanner.ScanResult scan = ScreenScanner.scan(screen,
					new BlockInWorld(mc.level, bound.abs().toBlockPos(), false));
			if (scan == null) {
				LOG.debug("close: scan null for {}", bound.abs());
				return;
			}
			ContainerInfo info = findContainer(bound.abs());
			if (info == null || info.cacheType == CacheType.NONE) {
				LOG.debug("close: no container/cacheType for {}", bound.abs());
				return;
			}
			var store = WarehouseServices.cacheStore();
			if (store == null) return;
			var key = CacheKey.of(info.canonicalPos(),
					DetectorResolver.playerUuidIfScoped(scan.detector()));
			store.remember(key, info.cacheType, scan.snapshot());
			LOG.debug("player-open refreshed cache {}: {} items", bound.abs(),
					scan.snapshot().slots().values().stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum());
		} catch (Exception e) {
			LOG.warn("player-open refresh failed {}: {}", bound.abs(), e.toString());
		}
	}

	/** 开箱信号：动画类容器必须有近期打开证据；无动画类型直接放行 */
	private boolean openingEvidenceOk(BlockPos pos, long now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		BlockState state = mc.level.getBlockState(pos);
		Block block = state.getBlock();
		if (state.hasProperty(BarrelBlock.OPEN)) {
			return state.getValue(BarrelBlock.OPEN);
		}
		if (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock) {
			Long t = openSignals.get(pos);
			return t != null && now - t <= OPEN_WINDOW_MS;
		}
		return true; // 熔炉/漏斗等无开合动画，退回点击捕获口径
	}

	private void expire(long now) {
		clicks.removeIf(c -> now - c.at() > CLICK_WINDOW_MS);
		openSignals.entrySet().removeIf(e -> now - e.getValue() > OPEN_WINDOW_MS);
		if (!pairCandidates.isEmpty() && pendingExpireAt < now) {
			pairCandidates.clear();
			lastOpenContainerId = -1;
		}
	}

	/** 激活仓库内是否存在该绝对坐标容器 */
	@Nullable
	private static ContainerInfo findContainer(WorldDimPos abs) {
		WarehouseManagerImpl mgr = WarehouseManagerImpl.get();
		if (mgr == null || mgr.active() == null) return null;
		var wh = mgr.active();
		WorldDim dim = new WorldDim(abs.world(), abs.dim());
		WorldDimPos rel = wh.toRelative(dim, abs.toBlockPos());
		if (rel == null) return null;
		return wh.containerAt(dim, rel.toBlockPos());
	}

	@Nullable
	private static String currentWorldKey() {
		WorldDim dim = currentDim();
		if (dim == null) return null;
		return dim.worldId() + "|" + dim.dimId();
	}

	@Nullable
	private static WorldDim currentDim() {
		Minecraft mc = Minecraft.getInstance();
		String worldId = WorldSessionTracker.get() == null ? null : WorldSessionTracker.get().currentWorldId();
		String dimId = mc.level == null ? null : mc.level.dimension().identifier().toString();
		if (worldId == null || dimId == null) return null;
		return new WorldDim(worldId, dimId);
	}
}

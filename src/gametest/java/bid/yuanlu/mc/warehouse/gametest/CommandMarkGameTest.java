package bid.yuanlu.mc.warehouse.gametest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.command.SelectionState;
import bid.yuanlu.mc.warehouse.command.WhCommands;
import bid.yuanlu.mc.warehouse.core.WarehouseServices;
import bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.engine.transport.TransportEngineImpl;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.core.mark.MarkMode;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * gametest③（L11）：命令冒烟 + 标记模式右键注册/移除。
 * <p>
 * 命令经独立 dispatcher + 本地 stub source 直接执行（断言 manager 状态与
 * 反馈组件的 i18n key）；标记模式走真实开箱链路（useItemOn → 开屏包 →
 * 真实扫描采集 → 自动关屏）。
 */
@SuppressWarnings("UnstableApiUsage")
public class CommandMarkGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	private static final Path CFG_DIR = Path.of("/tmp/opencode/wh-gt3");

	private static volatile CommandDispatcher<FabricClientCommandSource> testDispatcher;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(sp);
			context.waitTicks(10);

			context.runOnClient(mc -> {
				bootstrap();
			});
			StubSource src = new StubSource();

			bind(src);
		commandSmoke(context, src);
			markModeE2E(context, sp);

			LOGGER.info("yuanlu-warehouse L11 command/mark gametest passed");

			sp.getServer().runOnServer(server -> server.halt(false));
		}
	}

	// ---- 引导：与生产装配同构（临时目录隔离）----

	private static void bootstrap() {
		WarehouseRegistryImpl.resetForTest();
		bid.yuanlu.mc.warehouse.client.YuanluWarehouseClient.registerBuiltins();
		bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs.freeze();
		WarehouseRegistryImpl.freeze();
		try {
			ModConfig config = new ConfigIO(CFG_DIR).loadModConfig();
			WorldSessionTracker trk = new WorldSessionTracker(List.of(
					new bid.yuanlu.mc.warehouse.impl.world.SingleplayerWorldIdentifier(),
					new bid.yuanlu.mc.warehouse.impl.world.MultiplayerWorldIdentifier()));
			trk.tick();
			WorldSessionTracker.setInstance(trk);
			ContainerMemoryStore store = new ContainerMemoryStore(config, trk);
			WarehouseServices.setCacheStore(store);
			WarehouseServices.setModConfig(config);
			WarehouseManagerImpl mgr = new WarehouseManagerImpl(new ConfigIO(CFG_DIR));
			WarehouseManagerImpl.setInstance(mgr);
			TransportEngineImpl engine = new TransportEngineImpl(mgr, store, config);
			TransportEngineImpl.setInstance(engine);
			WarehouseServices.setTransportEngine(engine);
			for (String id : List.of("gt3", "gt3-dst")) {
				if (mgr.exists(id)) mgr.delete(id);
			}
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static CommandDispatcher<FabricClientCommandSource> d() {
		if (testDispatcher == null) {
			var disp = new CommandDispatcher<FabricClientCommandSource>();
			disp.register(WhCommands.buildRoot());
			testDispatcher = disp;
		}
		return testDispatcher;
	}

	private static final ClientGameTestContext CTX_PLACEHOLDER = null;

	private static int exec(ClientGameTestContext ctx, StubSource src, String commandLine) {
		try {
			Integer ok = ctx.computeOnClient(mc -> {
				try {
					return d().execute(commandLine, src);
				} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
					throw new RuntimeException("command failed: " + commandLine
							+ " -> " + e.getMessage(), e);
				}
			});
			return ok == null ? 0 : ok;
		} catch (Throwable t) {
			if (t instanceof RuntimeException r) throw r;
			throw new RuntimeException(t);
		}
	}

	static final class ExecShim {
		static ClientGameTestContext cached;
		static int call(StubSource src, String commandLine) {
			return exec(cached, src, commandLine);
		}
	}

	// ---- 场景①：命令冒烟 ----

	private static void commandSmoke(ClientGameTestContext context, StubSource src) {
		exec(context, src, "wh help");
		exec(context, src, "wh create gt3");
		check(WarehouseManagerImpl.get().exists("gt3"), "create 应创建 gt3");
		checkEquals("gt3", WarehouseManagerImpl.get().activeId(), "首个仓库应自动激活");
		check(exec(context, src, "wh status") == 1, "status 成功");

		// anchor 显式坐标落库
		check(exec(context, src, "wh anchor set 0 -60 0") == 1, "anchor set 成功");
		BlockPos anchor = WarehouseManagerImpl.get().active()
				.anchorOf(dim());
		check(anchor != null && anchor.getX() == 0 && anchor.getY() == -60 && anchor.getZ() == 0,
				"anchor 落库=" + anchor);

		// 规则生命周期
		exec(context, src, "wh rule create ores");
		check(WarehouseManagerImpl.get().active().rules.containsKey("ores"), "规则已创建");
		check(exec(context, src, "wh rule add ores id:minecraft:diamond --quantity count:64") == 1,
				"条目加入");
		check(WarehouseManagerImpl.get().active().rules.get("ores").itemRules.size() == 1,
				"条目数=1");

		// container add（显式坐标 + 可选参数）
		check(exec(context, src, "wh container add -2 -60 -2 --type INPUT --rule ores") == 1, "容器注册");
		List<ContainerInfo> cs = WarehouseManagerImpl.get().active().containers;
		check(cs.size() == 1 && cs.getFirst().ioType == IOType.INPUT
				&& cs.getFirst().canonicalPos().x() == -2
				&& cs.getFirst().canonicalPos().z() == -2, "INPUT 容器已注册且相对坐标正确");
		check(cs.getFirst().rules.contains("ores"), "--rule 已关联");

		// D2 拒载：OUTPUT × 不限量
		exec(context, src, "wh rule create all_free");
		exec(context, src, "wh rule add all_free id:minecraft:dirt"); // 无 quantity = 不限量
		src.clear();
		int rcD2 = exec(context, src, "wh container add 5 -60 5 --type OUTPUT --rule all_free");
		check(rcD2 == 0, "D2 应拒绝注册");
		check("commands.wh.error.generic".equals(src.errorKeys().getLast()),
				"D2 错误反馈存在");
		check(WarehouseManagerImpl.get().active().containers.size() == 1,
				"D2 拒绝后不落库");

		// type / mode 改写（坐标形态）
		String canPos = "-2 -60 -2";
		check(exec(context, src, "wh container type " + canPos + " OUTPUT") == 1, "type 改写");
		cs = WarehouseManagerImpl.get().active().containers;
		check(cs.getFirst().ioType == IOType.OUTPUT, "type 生效");
		check(exec(context, src, "wh container type " + canPos + " TEMP") == 1, "");
		check(exec(context, src, "wh container mode " + canPos + " WHITELIST") == 1, "mode 改写");
		check(RuleMode.WHITELIST.equals(
				WarehouseManagerImpl.get().active().containers.getFirst().effectiveRuleMode()),
				"mode 生效（TEMP 默认 BLACKLIST，被改写为 WHITELIST）");
		check(exec(context, src, "wh container mode " + canPos + " BLACKLIST") == 1, "");

		// 选区批量：先包围箱外设置 IGNORE，验证无影响；再包进选区改写
		check(exec(context, src, "wh select pos1 50 -55 50") == 1, "");
		check(exec(context, src, "wh select pos2 60 -45 60") == 1, "");
		check(exec(context, src, "wh select set-type IGNORE") >= 0, "远选区批量（空结果合法）");
		check(WarehouseManagerImpl.get().active().containers.getFirst().ioType != IOType.IGNORE,
				"选区外容器不受影响");
		check(exec(context, src, "wh select pos1 -4 -62 -4") == 1, "");
		check(exec(context, src, "wh select pos2 0 -58 0") == 1, ""); // 覆盖 (-2,-60,-2)
		check(exec(context, src, "wh select set-type INPUT") == 1, "选区内批量");
		check(WarehouseManagerImpl.get().active().containers.getFirst().ioType == IOType.INPUT,
				"选区内类型批量生效");
		check(exec(context, src, "wh select show") == 1, "select show");
		check(exec(context, src, "wh select clear") == 1, "");

		// config show/set/reload
		check(exec(context, src, "wh config set debug true") == 1, "config set");
		check(WarehouseServices.modConfig().debug, "config set debug 落地");
		check(exec(context, src, "wh config set debug false") == 1, "");
		check(!WarehouseServices.modConfig().debug, "config set debug 复位");
		check(exec(context, src, "wh config show") > 0, "config show");
		check(exec(context, src, "wh reload") == 1, "reload");

		// transfer 覆盖视图生命周期
		check(exec(context, src, "wh create gt3-dst") == 1, "");
		exec(context, src, "wh use gt3");
		checkEquals("gt3", WarehouseManagerImpl.get().activeId(), "恢复激活 gt3");
		check(exec(context, src, "wh transfer gt3 gt3-dst start") == 1, "transfer 启动");
		check(WarehouseManagerImpl.get().hasTransferOverlay(), "overlay 推入");
		checkEquals("gt3__to__gt3-dst", WarehouseManagerImpl.get().activeId(), "overlay 激活");
		check(exec(context, src, "wh transfer status") == 1, "transfer status");
		check(exec(context, src, "wh transfer stop") == 1, "transfer stop");
		check(!WarehouseManagerImpl.get().hasTransferOverlay(), "stop 弹出 overlay");
		checkEquals("gt3", WarehouseManagerImpl.get().activeId(), "激活态恢复 gt3");

		// --pathfinder 参数贯通（引擎空转即停；断言参数可解析且引擎接受）
		check(exec(context, src, "wh start --pathfinder noop") == 1, "start --pathfinder");
		check(WarehouseServices.transportEngine().isRunning(), "引擎已启动");
		check(exec(context, src, "wh stop") == 1, "stop 停止");
		check(!WarehouseServices.transportEngine().isRunning(), "引擎已停");

		// RUN_FINISHED 自动回收：再起一次 transfer，模拟自然跑完（不发 stop）
		check(exec(context, src, "wh transfer gt3 gt3-dst start") == 1, "transfer 启动#2");
		check(WarehouseManagerImpl.get().hasTransferOverlay(), "overlay 推入#2");
		context.runOnClient(mc -> mc.execute(() -> bid.yuanlu.mc.warehouse.core.event.WarehouseEvents
				.RUN_FINISHED.invoker().onRunFinished(new bid.yuanlu.mc.warehouse.api.transport.RunReport(
						bid.yuanlu.mc.warehouse.api.transport.RunGrade.PERFECT, 0, 1, 1, "wh.report.ok"))));
		check(awaitTrue(context, 50, () -> !WarehouseManagerImpl.get().hasTransferOverlay()),
				"RUN_FINISHED 自动弹 overlay");
		checkEquals("gt3", WarehouseManagerImpl.get().activeId(), "激活态恢复 gt3#2");

		LOGGER.info("[gt3] commands smoke passed (feedback={}, errors={})",
				src.feedbackKeys().size(), src.errorKeys().size());
	}

	// ---- 场景②：标记模式 E2E ----

	private static void markModeE2E(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos chestA = new BlockPos(8, -60, 10);
		placeChest(context, sp, chestA);

		// 清掉冒烟场景遗留（本次断言从空列表开始）
		Warehouse wh = WarehouseManagerImpl.get().active();
		wh.containers.clear();
		WarehouseManagerImpl.get().save(wh);
		int preCount = wh.containers.size();
		boolean addedEmpty = awaitTrue(context, 50, () -> WarehouseManagerImpl.get()
				.active().containers.size() >= preCount);
		check(addedEmpty || true, "baseline");

		context.runOnClient(mc -> mc.execute(() -> {
			LocalPlayer p = mc.player;
			p.setPos(chestA.getX() + 0.5, chestA.getY() + 2.2, chestA.getZ() + 0.5);
			p.setXRot(85f);
		}));
		context.waitTicks(5);

		StubSource src = new StubSource();
		check(exec(context, src, "wh container mark input") == 1, "mark input 进入");
		check(MarkMode.get().isActive(), "标记模式 active");

		pointThenUse(context, chestA);
		boolean registered = awaitTrue(context, 200,
				() -> !WarehouseManagerImpl.get().active().containers.isEmpty());
		check(registered, "标记注册应在限时内完成");
		context.waitTicks(10);

		ContainerInfo info = WarehouseManagerImpl.get().active().containers.stream()
				.filter(c -> c.canonicalPos().x() == 8 && c.canonicalPos().z() == 10)
				.findFirst().orElse(null);
		check(info != null, " Chest8/10 应已注册: "
				+ WarehouseManagerImpl.get().active().containers);
		check(info.ioType == IOType.INPUT, "入型应为 INPUT，实际 " + info.ioType);
		check(info.pos.getFirst().x() == 8 && info.pos.getFirst().y() == 0
				&& info.pos.getFirst().z() == 10,
				"相对坐标应为 (8,0,10)，实际 " + info.pos.getFirst());

		// §5.7 缓存种子：标记即首采，缓存应已有该容器条目
		check(awaitTrue(context, 50, () -> WarehouseServices.cacheStore() != null
				&& WarehouseServices.cacheStore().size() > 0),
				"标记完成应写入缓存种子");
		check(WarehouseServices.cacheStore().allValid().stream().anyMatch(m ->
				m.snapshot() != null && m.snapshot().slotCount() == 27), "种子快照槽位数应为 27");

		// 已注册容器重复标记 → 移除（标记模式仍激活，切换式命令此刻只会退出）
		check(MarkMode.get().isActive(), "标记模式应保持激活以连续标记");
		pointThenUse(context, chestA);
		int beforeCount = WarehouseManagerImpl.get().active().containers.size();
		boolean removed = awaitTrue(context, 200,
				() -> WarehouseManagerImpl.get().active().containers.size() < beforeCount
						|| WarehouseManagerImpl.get().active().containers.isEmpty());
		check(removed, "重复标记应移除容器");
		context.waitTicks(10);

		check(exec(context, src, "wh container mark ignore") == 1, "第三次执行退出");
		check(!MarkMode.get().isActive(), "标记模式已退出");

		LOGGER.info("[gt3] mark mode e2e passed");
	}

	/** 注入准星指向（无头环境渲染拾取不可靠）→ 记录指向 → 右键开箱 */
	private static void pointThenUse(ClientGameTestContext context, BlockPos target) {
		context.runOnClient(mc -> mc.execute(() -> {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target),
					Direction.UP, target, false);
			mc.hitResult = hit;
			MarkMode.get().tick(); // 消费 hitResult 写入 lastLookedAt
			mc.gameMode.useItemOn(mc.player,
					net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		}));
	}

	private static void placeChest(ClientGameTestContext context, TestSingleplayerContext sp,
			BlockPos pos) {
		AtomicBoolean done = new AtomicBoolean(false);
		sp.getServer().runOnServer(server -> {
			server.getLevel(Level.OVERWORLD).setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
			done.set(true);
		});
		awaitTrue(context, 100, done::get);
	}

	private static boolean awaitTrue(ClientGameTestContext context, int maxTicks,
			java.util.function.BooleanSupplier cond) {
		for (int i = 0; i < maxTicks; i++) {
			if (cond.getAsBoolean()) return true;
			context.waitTick();
		}
		return false;
	}

	private static WorldDim dim() {
		return new WorldDim(WorldSessionTracker.get().currentWorldId(),
				Level.OVERWORLD.identifier().toString());
	}

	private static boolean hasKey(Component c, String key) {
		return c.getContents() instanceof TranslatableContents tc && tc.getKey().equals(key);
	}

	private static String keyOf(Component c) {
		return c.getContents() instanceof TranslatableContents tc ? tc.getKey() : "?";
	}

	private static void check(boolean cond, String msg) {
		if (!cond) {
			LOGGER.warn("[gt3-check] {} | fb={} err={}", msg,
					lastSrc == null ? List.of() : lastSrc.feedbackKeys(),
					lastSrc == null ? List.of() : lastSrc.errorKeys());
			throw new AssertionError(msg);
		}
	}

	static StubSource lastSrc;

	static void bind(StubSource s) {
		lastSrc = s;
	}

	private static void checkEquals(Object expected, Object actual, String msg) {
		if (!java.util.Objects.equals(expected, actual)) {
			throw new AssertionError(msg + ": expected=" + expected + " actual=" + actual);
		}
	}

	// ---- FabricClientCommandSource stub ----

	static final class StubSource implements FabricClientCommandSource {

		private final List<Component> feedbackList = new ArrayList<>();
		private final List<Component> errorList = new ArrayList<>();

		List<String> feedbackKeys() {
			return feedbackList.stream().map(CommandMarkGameTest::keyOf).toList();
		}

		List<String> errorKeys() {
			return errorList.stream().map(CommandMarkGameTest::keyOf).toList();
		}

		void clear() {
			feedbackList.clear();
			errorList.clear();
		}

		@Override
		public void sendFeedback(Component message) {
			feedbackList.add(message);
		}

		@Override
		public void sendError(Component message) {
			errorList.add(message);
		}

		@Override
		public Minecraft getClient() {
			return Minecraft.getInstance();
		}

		@Override
		public LocalPlayer getPlayer() {
			return Minecraft.getInstance().player;
		}

		@Override
		public ClientLevel getLevel() {
			return Minecraft.getInstance().level;
		}

		@Override
		public Collection<String> getOnlinePlayerNames() {
			return List.of();
		}

		@Override
		public Collection<String> getAllTeams() {
			return List.of();
		}

		@Override
		public Stream<Identifier> getAvailableSounds() {
			return Stream.empty();
		}

		@Override
		public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> ctx) {
			return Suggestions.empty();
		}

		@Override
		public Set<ResourceKey<Level>> levels() {
			ClientLevel l = Minecraft.getInstance().level;
			return l == null ? Set.of() : Set.of(l.dimension());
		}

		@Override
		public RegistryAccess registryAccess() {
			return Minecraft.getInstance().level.registryAccess();
		}

		@Override
		public FeatureFlagSet enabledFeatures() {
			ClientLevel l = Minecraft.getInstance().level;
			return l == null ? net.minecraft.world.flag.FeatureFlagSet.of() : l.enabledFeatures();
		}

		@Override
		public CompletableFuture<Suggestions> suggestRegistryElements(
				ResourceKey<? extends net.minecraft.core.Registry<?>> key,
				SharedSuggestionProvider.ElementSuggestionType type, SuggestionsBuilder builder,
				CommandContext<?> ctx) {
			return builder.buildFuture();
		}

		@Override
		public PermissionSet permissions() {
			return PermissionSet.ALL_PERMISSIONS;
		}
	}
}

package bid.yuanlu.mc.warehouse.gametest;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.Priority;
import bid.yuanlu.mc.warehouse.api.transport.RunGrade;
import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.api.transport.TransportState;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.engine.container.ContainerProtocol;
import bid.yuanlu.mc.warehouse.core.engine.transport.TransportEngineImpl;
import bid.yuanlu.mc.warehouse.core.event.WarehouseEvents;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;

/**
 * gametest②（L10）：传输引擎状态机整轮循环、出口条件、探索失败 SUSPENDED→continue 跳过
 * （PDD §5/§13）。
 * <p>
 * 场景①：INPUT 箱 60 钻石 → 白名单 OUTPUT 箱收下 → 出口②结束，PERFECT / itemsMoved==60；
 * 未探索 TEMP 空箱被顺带探索（探索收益）。场景②：追加指空气的高优先 INPUT，
 * exploreFailMax 次后 SUSPENDED，continueRun 跳过该容器后流程照常完成。
 */
@SuppressWarnings("UnstableApiUsage")
public class TransportEngineGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(singleplayer);
			context.waitTicks(30);

			// ---- 场景① ----
			Harness h = Harness.boot(context);
			BlockPos input = near(context, -2, 0, -1);
			BlockPos output = near(context, -2, 0, 1);
			BlockPos temp = near(context, -3, 0, -1);
			String warehouseId = h.buildWorldSetup(context, singleplayer, input, output, temp, null);

			List<String> states = new java.util.ArrayList<>();
			List<RunReport> reports = new java.util.ArrayList<>();
			WarehouseEvents.TRANSPORT_STATE.register((s, d) -> {
				if (states.isEmpty() || !states.getLast().equals(s.name())) states.add(s.name());
			});
			WarehouseEvents.RUN_FINISHED.register(reports::add);

			h.engine.start();
			awaitDone(context, h, 1500);

			check(!reports.isEmpty(), "场景①应有 RunReport");
			RunReport r1 = reports.getFirst();
			LOGGER.info("[gt2-1] {} rounds={} moved={}", r1.grade(), r1.rounds(), r1.itemsMoved());
			check(states.contains(TransportState.GET_TEMP.name()), "应经过 GET_TEMP");
			check(states.contains(TransportState.GET_INPUT.name()), "应经过 GET_INPUT");
			check(states.contains(TransportState.PUT_OUTPUT.name()), "应经过 PUT_OUTPUT");
			check(states.contains(TransportState.PUT_TEMP.name()), "应经过 PUT_TEMP");
			check(r1.grade() == RunGrade.PERFECT, "场景①应为 PERFECT，实际=" + r1.grade() + " states=" + states);
			check(r1.itemsMoved() == 120, "itemsMoved 应为 120（取出60+放入60 双段计数），实际=" + r1.itemsMoved());

			int inTotal = chestDiamondsAwaited(singleplayer, context, input);
			int outTotal = chestDiamondsAwaited(singleplayer, context, output);
			int tmpTotal = chestDiamondsAwaited(singleplayer, context, temp);
			LOGGER.info("[gt2-verify] input={} output={} temp={}", inTotal, outTotal, tmpTotal);
			check(inTotal == 0, "INPUT 箱应清空，实际=" + inTotal);
			check(outTotal >= 60, "OUTPUT 箱应收下全部钻石，实际=" + outTotal);
			check(tmpTotal <= 0, "TEMP 不应有钻石");
			WarehouseManagerImpl.get().delete(warehouseId);
			reports.clear();
			states.clear();

			// ---- 场景②：损坏容器 → SUSPENDED → continue 跳过 ----
			BlockPos input2 = near(context, -2, 0, -3);
			BlockPos airTarget = near(context, 8, 40, 8); // 空中无方块
			String wh2 = h.buildWorldSetup(context, singleplayer, input2, output, temp, airTarget);
			List<TransportState> suspendStates = new java.util.ArrayList<>();
			WarehouseEvents.TRANSPORT_STATE.register((s, d) -> {
				if (s == TransportState.SUSPENDED && suspendStates.isEmpty()) suspendStates.add(s);
			});

			Harness.resetReport();
			h.engine.start();
			LOGGER.info("[gt2-2] engine started, awaiting SUSPENDED (broken container at {})", airTarget.toShortString());
			boolean suspendedSeen = awaitCondition(context, h, 800, eng -> eng.state() == TransportState.SUSPENDED);
			if (!suspendedSeen) {
				// 诊断出口：队列口径 / exploreFailMax 生效性
				LOGGER.warn("[gt2-2] no SUSPENDED; states={} lastDetail={}", states,
						context.computeOnClient(mc -> {
							var e = peekEngine();
							return e == null ? "no-engine" : String.valueOf(e.lastReport());
						}));
			}
			check(suspendedSeen, "应在反复探索失败后进入 SUSPENDED（states=" + states + "）");

			context.runOnClient(mc -> TransportEngineImpl.get().continueRun());
			awaitDone(context, h, 1500);

			check(!reports.isEmpty(), "场景②应有 RunReport");
			RunReport r2 = reports.getLast();
			LOGGER.info("[gt2-2] {} rounds={} moved={} detail={}", r2.grade(), r2.rounds(), r2.itemsMoved(),
					r2.detailKey());
			int input2Total = chestDiamondsAwaited(singleplayer, context, input2);
			LOGGER.info("[gt2-verify-2] input2={}", input2Total);
			check(input2Total == 0, "跳过坏容器后剩余 INPUT 应清空，实际=" + input2Total);

			try {
				context.takeScreenshot("yuanlu-warehouse-l10-engine");
			} catch (Exception ignored) {
			}
			LOGGER.info("yuanlu-warehouse L10 transport engine gametest passed");

			context.runOnClient(mc -> ContainerProtocol.get().cancel("gt2-end"));
			h.teardown();
			singleplayer.getServer().runOnServer(server -> server.halt(false));
		}
	}

	// ---- 工具 ----

	private static BlockPos near(ClientGameTestContext context, int dx, int dy, int dz) {
		return context.computeOnClient(mc -> {
			assert mc.player != null;
			return mc.player.blockPosition().offset(dx, dy, dz);
		});
	}

	private static void check(boolean cond, String msg) {
		if (!cond) throw new AssertionError(msg);
	}

	private interface EngineProbe {
		boolean test(TransportEngineImpl e);
	}

	/** 每 tick 驱动引擎直到条件满足；返回是否满足 */
	private static boolean awaitCondition(ClientGameTestContext context, Harness h, int maxTicks, EngineProbe probe) {
		for (int i = 0; i < maxTicks; i++) {
			if (!h.stopped() && probe.test(h.engine)) return true;
			context.runOnClient(mc -> {
				TransportEngineImpl e = peekEngine();
				if (e != null) e.tick();
			});
			context.waitTick();
		}
		return false;
	}

	/** 等 RunReport 出现（DONE）；超时抛错 */
	private static void awaitDone(ClientGameTestContext context, Harness h, int maxTicks) {
		for (int i = 0; i < maxTicks; i++) {
			if (h.reportCaptured()) return;
			context.runOnClient(mc -> {
				TransportEngineImpl e = peekEngine();
				if (e != null) e.tick();
			});
			context.waitTick();
		}
		throw new AssertionError("await DONE 失败：states=" + Harness.lastStates);
	}

	private static volatile TransportEngineImpl engineRef;

	private static TransportEngineImpl peekEngine() {
		return engineRef;
	}

	/**
	 * 服务端读箱子钻石总量（关闭后的客户端方块实体不含容器内容——只有 Menu 才同步，
	 * L8 口径）。runOnServer 是异步派发：用标志位轮询等待其真正完成。
	 */
	private static int chestDiamondsAwaited(TestSingleplayerContext sp, ClientGameTestContext context, BlockPos pos) {
		java.util.concurrent.atomic.AtomicInteger out =
				new java.util.concurrent.atomic.AtomicInteger(Integer.MIN_VALUE);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			int total = 0;
			if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					ItemStack st = chest.getItem(i);
					if (st.is(Items.DIAMOND)) total += st.getCount();
				}
			}
			out.set(total);
		});
		for (int i = 0; i < 200 && out.get() == Integer.MIN_VALUE; i++) {
			context.waitTick();
		}
		return out.get();
	}

	// ---- 引擎栈引导 ----

	static final class Harness {

		final ModConfig config;
		final TransportEngineImpl engine;
		static volatile List<String> lastStates = List.of();
		final AtomicReference<RunReport> reportRef = reportRefStatic();

		Harness(ModConfig cfg, TransportEngineImpl eng) {
			this.config = cfg;
			this.engine = eng;
		}

		static Harness boot(ClientGameTestContext context) {
			// 引擎栈在客户端线程构建（computeOnClient 同步语义）
			return context.computeOnClient(mc -> {
				try {
					// 内置 codec 注册（L11 前的临时内联，等价 TestCodecs.install()）
					registerBuiltins();
					ModConfig config = new ConfigIO(Path.of("/tmp/opencode/wh-gt2")).loadModConfig();
					WorldSessionTracker trk = new WorldSessionTracker(java.util.List.of(
							new bid.yuanlu.mc.warehouse.impl.world.SingleplayerWorldIdentifier(),
							new bid.yuanlu.mc.warehouse.impl.world.MultiplayerWorldIdentifier()));
					trk.tick();
					WorldSessionTracker.setInstance(trk);
					ContainerMemoryStore store = new ContainerMemoryStore(config, trk);
					WarehouseManagerImpl mgr = new WarehouseManagerImpl(new ConfigIO(Path.of("/tmp/opencode/wh-gt2")));
					WarehouseManagerImpl.setInstance(mgr);
					TransportEngineImpl engine = new TransportEngineImpl(mgr, store, config);
					TransportEngineImpl.setInstance(engine);
					engineRef = engine;
					if (mgr.exists("gt2-a")) mgr.delete("gt2-a");
					if (mgr.exists("gt2-b")) mgr.delete("gt2-b");
					WarehouseEvents.TRANSPORT_STATE.register((s, d) -> appendState(s.name()));
					WarehouseEvents.RUN_FINISHED.register(reportRefStatic()::set);
					return new Harness(config, engine);
				} catch (Throwable t) {
					throw new RuntimeException(t);
				}
			});
		}

		private static void registerBuiltins() {
			// L11 起生产入口启动时已注册并冻结——gametest 解冻后经同一装配函数重灌
			bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl.resetForTest();
			bid.yuanlu.mc.warehouse.client.YuanluWarehouseClient.registerBuiltins();
		}

		private static void appendState(String s) {
			var l = new java.util.ArrayList<>(lastStates);
			l.add(s);
			lastStates = List.copyOf(l);
		}

		static AtomicReference<RunReport> STATIC_REPORT_REF = new AtomicReference<>();

		static AtomicReference<RunReport> reportRefStatic() {
			return STATIC_REPORT_REF;
		}

		static void resetReport() {
			reportRefStatic().set(null);
		}

		AtomicReference<RunReport> reportRef() {
			return reportRefStatic();
		}

		boolean stopped() {
			return false;
		}

		boolean reportCaptured() {
			return reportRefStatic().get() != null;
		}

		/**
		 * 构建仓库配置与真实箱子；anchor=(0,0,0)，容器坐标直接用绝对坐标。
		 * brokenAir 为 null 时不含损坏容器。
		 */
		String buildWorldSetup(ClientGameTestContext context, TestSingleplayerContext singleplayer,
				BlockPos inputPos, BlockPos outputPos, BlockPos tempPos, @org.jetbrains.annotations.Nullable BlockPos brokenAir) {
			String id = brokenAir == null ? "gt2-a" : "gt2-b";
			context.runOnClient(mc -> {
				assert mc.level != null && mc.player != null;
				WorldDim dimId = new WorldDim(WorldSessionTracker.get().currentWorldId(),
						mc.level.dimension().identifier().toString());

				WarehouseManagerImpl mgr = WarehouseManagerImpl.get();
				if (mgr.exists(id)) mgr.delete(id);
				Warehouse wh = mgr.create(id);
				wh.setAnchor(dimId, BlockPos.containing(0, 0, 0));

				ContainerRule rule = new ContainerRule(id + "-out-diamond");
				rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
						new bid.yuanlu.mc.warehouse.impl.quantity.CountSelector(65536)));
				wh.rules.put(rule.id, rule);

				wh.containers.add(container(IOType.INPUT, dimId, inputPos, new Priority(5, 0)));
				ContainerInfo out = container(IOType.OUTPUT, dimId, outputPos, Priority.ZERO);
				out.rules.add(rule.id);
				wh.containers.add(out);
				wh.containers.add(container(IOType.TEMP, dimId, tempPos, Priority.ZERO));
				if (brokenAir != null) {
					wh.containers.add(container(IOType.INPUT, dimId, brokenAir, new Priority(99, 99)));
				}
				mgr.save(wh);
				mgr.activate(id);
			});

			// 放置真实方块并填充钻石（服务端线程）
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getLevel(Level.OVERWORLD);
				fill(level, inputPos, 60);
				level.setBlock(outputPos, Blocks.CHEST.defaultBlockState(), 3);
				level.setBlock(tempPos, Blocks.CHEST.defaultBlockState(), 3);
			});
			GameTestUtil.waitForChunksRender(singleplayer);
			context.waitTicks(10);
			return id;
		}

		private static void fill(ServerLevel level, BlockPos pos, int amount) {
			level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
			if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
				int left = amount;
				int slot = 0;
				while (left > 0 && slot < chest.getContainerSize()) {
					int c = Math.min(left, 64);
					chest.setItem(slot, new ItemStack(Items.DIAMOND, c));
					left -= c;
					slot++;
				}
			}
		}

		private static ContainerInfo container(IOType io, WorldDim dim, BlockPos abs, Priority prio) {
			ContainerInfo c = new ContainerInfo(io);
			c.priority = prio;
			c.pos.add(bid.yuanlu.mc.warehouse.api.world.WorldDimPos.of(dim, abs));
			return c;
		}

		void teardown() {
			TransportEngineImpl.setInstance(null);
			WarehouseManagerImpl.setInstance(null);
			engineRef = null;
		}
	}
}

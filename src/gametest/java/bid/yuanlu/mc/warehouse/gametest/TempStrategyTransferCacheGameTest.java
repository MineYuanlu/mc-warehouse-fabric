package bid.yuanlu.mc.warehouse.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.Priority;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.client.YuanluWarehouseClient;
import bid.yuanlu.mc.warehouse.core.WarehouseServices;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore;
import bid.yuanlu.mc.warehouse.core.cache.DiskCacheStore;
import bid.yuanlu.mc.warehouse.core.cache.StackCodecs;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.engine.transport.TransportEngineImpl;
import bid.yuanlu.mc.warehouse.core.event.WarehouseEvents;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;
import bid.yuanlu.mc.warehouse.util.McScreens;

/**
 * gametest⑤（L13）：TEMP 双策略防振荡（§5.3 机制②）+ 跨仓库 transfer 真实搬运
 * （§5.6）+ DISK 缓存落盘与跨会话回填（§5.4/§11.1）。
 * <p>
 * 场景①：TEMP(10 dirt) + INPUT(10 钻石) + 未探索白名单 OUTPUT(钻石)——保守模式取走
 * dirt 又因 OUTPUT 拒收回落 TEMP，不产生无限振荡，最终 dirt 留在 TEMP、钻石进 OUTPUT；
 * 场景②：覆盖仓库（src 全 INPUT / dst 全 OUTPUT）真实搬运 40 钻石；
 * 场景③：cacheType=DISK 容器经玩家开箱（F2）落盘，重建 store（模拟重启）后回填。
 */
@SuppressWarnings("UnstableApiUsage")
public class TempStrategyTransferCacheGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	private static final Path CFG_DIR = Path.of("/tmp/opencode/wh-l13");

	private static volatile TransportEngineImpl engineRef;
	private static volatile ModConfig configRef;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(sp);
			context.waitTicks(30);

			boot(context);

			tempDualStrategy(context, sp);
			transferRun(context, sp);
			diskCachePersistence(context, sp);

			LOGGER.info("yuanlu-warehouse L13 temp/transfer/disk-cache gametest passed");

			teardown();
			sp.getServer().runOnServer(server -> server.halt(false));
		}
	}

	// ---- 场景①：TEMP 双策略防振荡 ----

	private static void tempDualStrategy(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos input = near(context, 2, 0, -1);
		BlockPos output = near(context, 2, 0, 1);
		BlockPos temp = near(context, 3, 0, -1);
		fillChests(context, sp,
				Map.of(input, Map.of(Items.DIAMOND, 10), temp, Map.of(Items.DIRT, 10)),
				Map.of(output, Map.of()));
		setupWarehouse(context, "l13-a", wh -> {
			ContainerRule rule = new ContainerRule("l13-out-diamond");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(65536)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context);
		LOGGER.info("[l13-1] {} rounds={} moved={} detail={}", r.grade(), r.rounds(),
				r.itemsMoved(), r.detailKey());

		// 终态收敛：无振荡、dirt 不进 OUTPUT、不丢失
		check(countAwaited(context, sp, input, Items.DIAMOND) == 0, "INPUT 应清空");
		check(countAwaited(context, sp, output, Items.DIAMOND) == 10,
				"OUTPUT 应收 10 钻石，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		check(countAwaited(context, sp, output, Items.DIRT) == 0,
				"OUTPUT 不应收 dirt，实际=" + countAwaited(context, sp, output, Items.DIRT));
		check(countAwaited(context, sp, temp, Items.DIRT) == 10,
				"dirt 应回落并留在 TEMP（防振荡②），实际=" + countAwaited(context, sp, temp, Items.DIRT));
		check(countAwaited(context, sp, temp, Items.DIAMOND) == 0, "TEMP 不应留钻石");
		clearBag(context);
	}

	// ---- 场景②：跨仓库 transfer 真实搬运 ----

	private static void transferRun(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos srcChest = near(context, 2, 0, -3);
		BlockPos dstChest = near(context, 2, 0, 3);
		fillChests(context, sp, Map.of(srcChest, Map.of(Items.DIAMOND, 40)),
				Map.of(dstChest, Map.of()));
		setupWarehouse(context, "l13-src", wh -> {
			wh.containers.add(cont(IOType.INPUT, srcChest, new Priority(5, 0)));
		});
		setupWarehouse(context, "l13-dst", wh -> {
			ContainerRule rule = new ContainerRule("l13-dst-diamond");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(65536)));
			wh.rules.put(rule.id, rule);
			ContainerInfo out = cont(IOType.OUTPUT, dstChest, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
		});

		TransportEngineGameTest.Harness.resetReport();
		context.runOnClient(mc -> {
			WarehouseManagerImpl mgr = WarehouseManagerImpl.get();
			mgr.activate("l13-src"); // 真实使用语义：激活源仓库后发起 transfer
			Warehouse src = mgr.get("l13-src");
			Warehouse dst = mgr.get("l13-dst");
			// TransferOverlay.build 为包私有：等价构建覆盖仓库（src 全 INPUT / dst 全 OUTPUT）
			Warehouse ov = new Warehouse("l13-src__to__l13-dst");
			ov.anchors.putAll(src.anchors);
			ov.anchors.putAll(dst.anchors);
			ov.rules.putAll(src.rules);
			ov.rules.putAll(dst.rules);
			for (ContainerInfo c : src.containers) ov.containers.add(cloneAs(c, IOType.INPUT));
			for (ContainerInfo c : dst.containers) ov.containers.add(cloneAs(c, IOType.OUTPUT));
			mgr.pushTransferOverlay(ov); // 推入即激活（activeId=overlay.id）
			engineRef.start();
		});

		RunReport r = awaitReport(context);
		LOGGER.info("[l13-2] {} rounds={} moved={} detail={}", r.grade(), r.rounds(),
				r.itemsMoved(), r.detailKey());

		check(countAwaited(context, sp, srcChest, Items.DIAMOND) == 0, "源仓库 chest 应清空");
		check(countAwaited(context, sp, dstChest, Items.DIAMOND) == 40,
				"目标仓库 chest 应收 40 钻石，实际=" + countAwaited(context, sp, dstChest, Items.DIAMOND));
		// 生产装配已注册 RUN_FINISHED auto-pop：自然跑完应自动弹 overlay 并恢复激活态
		boolean popped = awaitTrue(context, 100, () ->
				!WarehouseManagerImpl.get().hasTransferOverlay());
		check(popped, "RUN_FINISHED 后 overlay 应被 auto-pop 自动回收");
		checkEquals("l13-src", WarehouseManagerImpl.get().activeId(), "激活态应恢复源仓库");
		clearBag(context);
	}

	private static ContainerInfo cloneAs(ContainerInfo c, IOType io) {
		ContainerInfo n = new ContainerInfo(io);
		n.pos.addAll(c.pos);
		n.cacheType = c.cacheType;
		n.priority = c.priority;
		n.rules.addAll(c.rules);
		return n;
	}

	// ---- 场景③：DISK 缓存落盘 + 跨会话回填 ----

	private static void diskCachePersistence(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos chest = near(context, 2, 0, -2);
		fillChests(context, sp, Map.of(chest, Map.of(Items.DIAMOND, 3)), Map.of());
		setupWarehouse(context, "l13-disk", wh -> {
			ContainerInfo in = cont(IOType.INPUT, chest, Priority.ZERO);
			in.cacheType = CacheType.DISK;
			wh.containers.add(in);
		});
		// 预清理：确保断言不受历史运行残留影响
		deleteRecursively(CFG_DIR.resolve("cache"));

		// F2：玩家手动开箱 → 关屏 → 关屏扫描回写 DISK 缓存
		context.runOnClient(mc -> mc.execute(() -> {
			LocalPlayer p = mc.player;
			p.setPos(chest.getX() + 0.5, chest.getY() + 2.2, chest.getZ() + 0.5);
			p.setXRot(85f);
		}));
		context.waitTicks(5);
		context.runOnClient(mc -> mc.execute(() -> {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false);
			mc.hitResult = hit;
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		}));
		boolean screenSeen = awaitTrue(context, 200, () -> context.computeOnClient(mc ->
				McScreens.current() instanceof AbstractContainerScreen<?>));
		check(screenSeen, "玩家开箱应出现容器界面");
		context.waitTicks(6);
		context.runOnClient(mc -> mc.execute(() -> {
			if (McScreens.current() instanceof AbstractContainerScreen<?> s) s.onClose();
		}));

		ContainerInfo info = context.computeOnClient(mc -> {
			Warehouse wh = WarehouseManagerImpl.get().active();
			WorldDimPos rel = wh.toRelative(dim(), chest);
			return wh.containerAt(dim(), rel.toBlockPos());
		});
		Path expectFile = CFG_DIR.resolve("cache")
				.resolve(StackCodecs.sanitizeWorldDir(dim().worldId()))
				.resolve(StackCodecs.sanitizeWorldDir(dim().dimId()) + "__"
						+ info.canonicalPos().x() + "_" + info.canonicalPos().y() + "_"
						+ info.canonicalPos().z() + ".json");
		boolean fileSeen = awaitTrue(context, 100, () -> Files.isRegularFile(expectFile));
		check(fileSeen, "DISK 缓存应落盘：" + expectFile);

		// 模拟重启：重建 store（新 DiskCacheStore），DISK 条目应从磁盘回填
		int items = context.computeOnClient(mc -> {
			ContainerMemoryStore fresh = new ContainerMemoryStore(configRef,
					WorldSessionTracker.get(), new DiskCacheStore(CFG_DIR));
			CacheKey key = CacheKey.of(info.canonicalPos(), null);
			var mem = fresh.getValid(key, CacheType.DISK);
			if (mem == null || mem.snapshot() == null) return -1;
			return mem.snapshot().slots().values().stream()
					.mapToInt(ItemStack::getCount).sum();
		});
		LOGGER.info("[l13-3] disk backfill items={}", items);
		check(items == 3, "重启后 DISK 回填应含 3 钻石，实际=" + items);
		clearBag(context);
	}

	// ---- 引擎栈引导（L10 Harness.boot 同构，store 注入 DiskCacheStore）----

	private static void boot(ClientGameTestContext context) {
		context.computeOnClient(mc -> {
			try {
				WarehouseRegistryImpl.resetForTest();
				YuanluWarehouseClient.registerBuiltins();
				ModConfig cfg = new ConfigIO(CFG_DIR).loadModConfig();
				WorldSessionTracker trk = new WorldSessionTracker(List.of(
						new bid.yuanlu.mc.warehouse.impl.world.SingleplayerWorldIdentifier(),
						new bid.yuanlu.mc.warehouse.impl.world.MultiplayerWorldIdentifier()));
				trk.tick();
				WorldSessionTracker.setInstance(trk);
				ContainerMemoryStore store = new ContainerMemoryStore(cfg, trk,
						new DiskCacheStore(CFG_DIR));
				WarehouseServices.setCacheStore(store);
				WarehouseServices.setModConfig(cfg);
				WarehouseManagerImpl mgr = new WarehouseManagerImpl(new ConfigIO(CFG_DIR));
				WarehouseManagerImpl.setInstance(mgr);
				TransportEngineImpl eng = new TransportEngineImpl(mgr, store, cfg);
				TransportEngineImpl.setInstance(eng);
				engineRef = eng;
				configRef = cfg;
				WarehouseEvents.RUN_FINISHED.register(
						TransportEngineGameTest.Harness.reportRefStatic()::set);
				for (String id : List.of("l13-a", "l13-src", "l13-dst", "l13-disk")) {
					if (mgr.exists(id)) mgr.delete(id);
				}
				return null;
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		});
	}

	private static void teardown() {
		TransportEngineImpl.setInstance(null);
		WarehouseManagerImpl.setInstance(null);
		engineRef = null;
	}

	// ---- 共享工具 ----

	private static BlockPos near(ClientGameTestContext context, int dx, int dy, int dz) {
		return context.computeOnClient(mc -> {
			assert mc.player != null;
			return mc.player.blockPosition().offset(dx, dy, dz);
		});
	}

	private static WorldDim dim() {
		return new WorldDim(WorldSessionTracker.get().currentWorldId(),
				Level.OVERWORLD.identifier().toString());
	}

	private static ContainerInfo cont(IOType io, BlockPos abs, Priority prio) {
		ContainerInfo c = new ContainerInfo(io);
		c.priority = prio;
		c.pos.add(WorldDimPos.of(dim(), abs));
		return c;
	}

	private static void setupWarehouse(ClientGameTestContext context, String id,
			java.util.function.Consumer<Warehouse> builder) {
		context.runOnClient(mc -> {
			WarehouseManagerImpl mgr = WarehouseManagerImpl.get();
			if (mgr.exists(id)) mgr.delete(id);
			var store = bid.yuanlu.mc.warehouse.core.WarehouseServices.cacheStore();
			if (store != null) store.invalidateAll(); // 场景隔离：清掉前一场景同 pos 的陈旧缓存
			Warehouse wh = mgr.create(id);
			wh.setAnchor(dim(), BlockPos.containing(0, 0, 0));
			builder.accept(wh);
			mgr.save(wh);
			mgr.activate(id);
		});
	}

	private static void fillChests(ClientGameTestContext context, TestSingleplayerContext sp,
			Map<BlockPos, Map<Item, Integer>> filled, Map<BlockPos, Map<Item, Integer>> empty) {
		AtomicBoolean done = new AtomicBoolean(false);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			filled.forEach((pos, items) -> fill(level, pos, items));
			empty.forEach((pos, items) -> fill(level, pos, items));
			done.set(true);
		});
		for (int i = 0; i < 200 && !done.get(); i++) context.waitTick();
		check(done.get(), "放置箱子超时");
	}

	private static void fill(ServerLevel level, BlockPos pos, Map<Item, Integer> items) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
		if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
			int slot = 0;
			for (Map.Entry<Item, Integer> e : items.entrySet()) {
				int left = e.getValue();
				while (left > 0 && slot < chest.getContainerSize()) {
					int n = Math.min(left, new ItemStack(e.getKey()).getMaxStackSize());
					chest.setItem(slot++, new ItemStack(e.getKey(), n));
					left -= n;
				}
			}
		}
	}

	private static int countAwaited(ClientGameTestContext context, TestSingleplayerContext sp,
			BlockPos pos, Item item) {
		java.util.concurrent.atomic.AtomicInteger out =
				new java.util.concurrent.atomic.AtomicInteger(Integer.MIN_VALUE);
		sp.getServer().runOnServer(server -> out.set(count(
				server.getLevel(Level.OVERWORLD), pos, item)));
		for (int i = 0; i < 200 && out.get() == Integer.MIN_VALUE; i++) context.waitTick();
		return out.get();
	}

	private static int count(ServerLevel level, BlockPos pos, Item item) {
		if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) return -1;
		int total = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack st = chest.getItem(i);
			if (st.is(item)) total += st.getCount();
		}
		return total;
	}

	private static void clearBag(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (mc.player != null) mc.player.getInventory().clearContent();
		});
	}

	private static RunReport runAndWait(ClientGameTestContext context) {
		TransportEngineGameTest.Harness.resetReport();
		context.runOnClient(mc -> engineRef.start());
		return awaitReport(context);
	}

	private static RunReport awaitReport(ClientGameTestContext context) {
		for (int i = 0; i < 1500; i++) {
			RunReport r = TransportEngineGameTest.Harness.reportRefStatic().get();
			if (r != null) return r;
			context.runOnClient(mc -> {
				TransportEngineImpl e = engineRef;
				if (e != null && e.isRunning()) e.tick();
			});
			context.waitTick();
		}
		throw new AssertionError("引擎未在限时内结束");
	}

	private static boolean awaitTrue(ClientGameTestContext context, int maxTicks,
			java.util.function.BooleanSupplier cond) {
		for (int i = 0; i < maxTicks; i++) {
			if (cond.getAsBoolean()) return true;
			context.waitTick();
		}
		return false;
	}

	private static void deleteRecursively(Path root) {
		if (!Files.exists(root)) return;
		try (Stream<Path> walk = Files.walk(root)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (Exception ignored) {
				}
			});
		} catch (Exception ignored) {
		}
	}

	private static void check(boolean cond, String msg) {
		if (!cond) throw new AssertionError(msg);
	}

	private static void checkEquals(Object expected, Object actual, String msg) {
		if (!java.util.Objects.equals(expected, actual)) {
			throw new AssertionError(msg + ": expected=" + expected + " actual=" + actual);
		}
	}
}

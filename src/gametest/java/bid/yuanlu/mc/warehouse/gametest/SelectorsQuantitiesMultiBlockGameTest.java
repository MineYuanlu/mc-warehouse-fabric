package bid.yuanlu.mc.warehouse.gametest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.Priority;
import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.core.engine.transport.TransportEngineImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.FillSlotsSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.GroupSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.PercentSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;

/**
 * gametest⑥（L14）：反选规则、group/fill_slots/percent 数量选择器、跨维度容器隔离、
 * 双箱（多格容器）搬运（PDD §3.6/§3.7/§3.2/§4.3）。
 * <p>
 * 场景①：WHITELIST + negative(id:dirt) = 「收一切除 dirt 外」——钻石进 OUTPUT、dirt 落 TEMP；
 * 场景②：group:1 → 目标 1×64=64（100 钻 → OUTPUT 64 / TEMP 36）；
 * 场景③：fill_slots:26 → 目标 (27-26)×64=64；
 * 场景④：percent:5 → 目标 ⌊27×64×5/100⌋=86；
 * 场景⑤：跨维度 INPUT（下界高优先）——出口/队列口径一致，仅在 overworld 范围内收敛
 * （input_empty 结束而非 no_progress 误报）；
 * 场景⑥：双箱 LEFT/RIGHT 相邻构成大箱子，ContainerInfo 含两半坐标 → 引擎经 canonical
 * 半边开 54 格 UI 全量取空。
 */
@SuppressWarnings("UnstableApiUsage")
public class SelectorsQuantitiesMultiBlockGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(sp);
			context.waitTicks(30);

			TransportEngineGameTest.Harness h = TransportEngineGameTest.Harness.boot(context);

			negativeWhitelist(context, sp, h);
			groupQuantity(context, sp, h);
			fillSlotsQuantity(context, sp, h);
			percentQuantity(context, sp, h);
			crossDimSkipped(context, sp, h);
			doubleChest(context, sp, h);

			LOGGER.info("yuanlu-warehouse L14 selectors/quantities/multiblock gametest passed");

			h.teardown();
			sp.getServer().runOnServer(server -> server.halt(false));
		}
	}

	// ---- 场景①：反选（negative）白名单 ----

	private static void negativeWhitelist(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos input = near(context, 2, 0, -1);
		BlockPos output = near(context, 2, 0, 1);
		BlockPos temp = near(context, 3, 0, -1);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 30, Items.DIRT, 10)),
				Map.of(output, Map.of(), temp, Map.of()));
		setupWarehouse(context, "l14-a", wh -> {
			// negative=true：dirt 匹配后取反 → 不命中 → WHITELIST 拒收；其余物品命中 → 全收
			ContainerRule rule = new ContainerRule("l14-not-dirt");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:dirt"), true,
					new CountSelector(65536)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l14-1] {} moved={} detail={}", r.grade(), r.itemsMoved(), r.detailKey());

		check(countAwaited(context, sp, output, Items.DIAMOND) == 30,
				"OUTPUT 应收 30 钻石，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		check(countAwaited(context, sp, output, Items.DIRT) == 0,
				"反选规则下 dirt 不应进 OUTPUT，实际=" + countAwaited(context, sp, output, Items.DIRT));
		check(countAwaited(context, sp, temp, Items.DIRT) == 10,
				"dirt 应落 TEMP，实际=" + countAwaited(context, sp, temp, Items.DIRT));
		check(countAwaited(context, sp, input, Items.DIAMOND) == 0
				&& countAwaited(context, sp, input, Items.DIRT) == 0, "INPUT 应清空");
		clearBag(context);
	}

	// ---- 场景②：group:1 → 64 ----

	private static void groupQuantity(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos input = near(context, 2, 1, -1);
		BlockPos output = near(context, 2, 1, 1);
		BlockPos temp = near(context, 3, 1, -1);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 100)),
				Map.of(output, Map.of(), temp, Map.of()));
		setupWarehouse(context, "l14-b", wh -> {
			ContainerRule rule = new ContainerRule("l14-group1");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new GroupSelector(1)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l14-2] {} moved={} detail={}", r.grade(), r.itemsMoved(), r.detailKey());

		check(countAwaited(context, sp, output, Items.DIAMOND) == 64,
				"group:1 目标 64，OUTPUT 应收 64，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		check(countAwaited(context, sp, temp, Items.DIAMOND) == 36,
				"TEMP 应收溢出 36，实际=" + countAwaited(context, sp, temp, Items.DIAMOND));
		clearBag(context);
	}

	// ---- 场景③：fill_slots:26 → (27-26)×64=64 ----

	private static void fillSlotsQuantity(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos input = near(context, 2, 2, -1);
		BlockPos output = near(context, 2, 2, 1);
		BlockPos temp = near(context, 3, 2, -1);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 100)),
				Map.of(output, Map.of(), temp, Map.of()));
		setupWarehouse(context, "l14-c", wh -> {
			ContainerRule rule = new ContainerRule("l14-fill26");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new FillSlotsSelector(26)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l14-3] {} moved={} detail={}", r.grade(), r.itemsMoved(), r.detailKey());

		check(countAwaited(context, sp, output, Items.DIAMOND) == 64,
				"fill_slots:26 目标 64，OUTPUT 应收 64，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		check(countAwaited(context, sp, temp, Items.DIAMOND) == 36,
				"TEMP 应收溢出 36，实际=" + countAwaited(context, sp, temp, Items.DIAMOND));
		clearBag(context);
	}

	// ---- 场景④：percent:5 → ⌊27×64×5/100⌋=86 ----

	private static void percentQuantity(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos input = near(context, 2, 0, -2);
		BlockPos output = near(context, 2, 0, 2);
		BlockPos temp = near(context, 3, 0, -2);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 100)),
				Map.of(output, Map.of(), temp, Map.of()));
		setupWarehouse(context, "l14-d", wh -> {
			ContainerRule rule = new ContainerRule("l14-percent5");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new PercentSelector(5)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l14-4] {} moved={} detail={}", r.grade(), r.itemsMoved(), r.detailKey());

		check(countAwaited(context, sp, output, Items.DIAMOND) == 86,
				"percent:5 目标 86，OUTPUT 应收 86，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		check(countAwaited(context, sp, temp, Items.DIAMOND) == 14,
				"TEMP 应收溢出 14，实际=" + countAwaited(context, sp, temp, Items.DIAMOND));
		clearBag(context);
	}

	// ---- 场景⑤：跨维度容器隔离 ----

	private static void crossDimSkipped(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos input = near(context, 2, 1, -2);
		BlockPos output = near(context, 2, 1, 2);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 20)),
				Map.of(output, Map.of()));
		setupWarehouse(context, "l14-e", wh -> {
			ContainerRule rule = new ContainerRule("l14-out-free");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(65536)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			// 下界「容器」：最高优先、本维度不可达——不应阻塞出口判定（跨维度搬运属二阶段寻路）
			ContainerInfo nether = new ContainerInfo(IOType.INPUT);
			nether.priority = new Priority(99, 99);
			nether.pos.add(new WorldDimPos(dim().worldId(), "minecraft:the_nether", 0, 99, 0));
			wh.containers.add(nether);
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l14-5] {} moved={} detail={}", r.grade(), r.itemsMoved(), r.detailKey());

		check("wh.report.input_empty".equals(r.detailKey()),
				"跨维度容器不应阻塞出口②（应 input_empty 而非 no_progress），实际=" + r.detailKey());
		check(countAwaited(context, sp, output, Items.DIAMOND) == 20,
				"overworld OUTPUT 应收 20 钻石，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		clearBag(context);
	}

	// ---- 场景⑥：双箱（多格容器） ----

	private static void doubleChest(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos halfA = near(context, 2, 3, -1);
		BlockPos halfB = near(context, 3, 3, -1);
		BlockPos output = near(context, 2, 3, 1);
		BlockPos temp = near(context, 3, 3, 1);
		// LEFT 连接 facing 顺时针（北向的东邻）→ A=LEFT 在西、B=RIGHT 在东构成大箱子
		AtomicBoolean done = new AtomicBoolean(false);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			BlockState base = Blocks.CHEST.defaultBlockState()
					.setValue(ChestBlock.FACING, Direction.NORTH);
			level.setBlock(halfA, base.setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
			level.setBlock(halfB, base.setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
			if (level.getBlockEntity(halfA) instanceof RandomizableContainerBlockEntity a) {
				a.setItem(0, new ItemStack(Items.DIAMOND, 50));
			}
			if (level.getBlockEntity(halfB) instanceof RandomizableContainerBlockEntity b) {
				b.setItem(0, new ItemStack(Items.DIAMOND, 50));
			}
			level.setBlock(output, Blocks.CHEST.defaultBlockState(), 3);
			level.setBlock(temp, Blocks.CHEST.defaultBlockState(), 3);
			done.set(true);
		});
		for (int i = 0; i < 200 && !done.get(); i++) context.waitTick();
		check(done.get(), "放置双箱超时");

		// 等客户端同步到新方块与 BE（否则 PRECHECK/UI 身份校验撞上陈旧区块 → 误判 explore 失败）
		boolean synced = awaitTrue(context, 200, () -> context.computeOnClient(mc -> {
			if (mc.level == null) return false;
			return mc.level.getBlockState(halfA).getBlock() instanceof ChestBlock
					&& mc.level.getBlockState(halfB).getBlock() instanceof ChestBlock
					&& mc.level.getBlockEntity(halfA) != null
					&& mc.level.getBlockEntity(halfB) != null
					&& mc.level.getBlockEntity(output) != null
					&& mc.level.getBlockEntity(temp) != null;
		}));
		check(synced, "客户端未同步到双箱方块实体");

		setupWarehouse(context, "l14-f", wh -> {
			ContainerRule rule = new ContainerRule("l14-out-free2");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(65536)));
			wh.rules.put(rule.id, rule);
			ContainerInfo in = cont(IOType.INPUT, halfA, new Priority(5, 0));
			in.pos.add(WorldDimPos.of(dim(), halfB)); // 多格：两半同属一个逻辑容器
			wh.containers.add(in);
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l14-6] {} moved={} detail={}", r.grade(), r.itemsMoved(), r.detailKey());

		int left = countAwaited(context, sp, halfA, Items.DIAMOND)
				+ countAwaited(context, sp, halfB, Items.DIAMOND);
		check(left == 0, "双箱（两半共 100 钻）应被取空，剩余=" + left);
		check(countAwaited(context, sp, output, Items.DIAMOND) == 100,
				"OUTPUT 应收 100 钻石，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		clearBag(context);
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

	private static RunReport runAndWait(ClientGameTestContext context,
			TransportEngineGameTest.Harness h) {
		TransportEngineGameTest.Harness.resetReport();
		context.runOnClient(mc -> h.engine.start());
		for (int i = 0; i < 1500; i++) {
			if (h.reportCaptured()) break;
			context.runOnClient(mc -> {
				if (h.engine.isRunning()) h.engine.tick();
			});
			context.waitTick();
		}
		RunReport r = TransportEngineGameTest.Harness.reportRefStatic().get();
		check(r != null, "引擎未在限时内结束");
		return r;
	}

	private static boolean awaitTrue(ClientGameTestContext context, int maxTicks,
			java.util.function.BooleanSupplier cond) {
		for (int i = 0; i < maxTicks; i++) {
			if (cond.getAsBoolean()) return true;
			context.waitTick();
		}
		return false;
	}

	private static void check(boolean cond, String msg) {
		if (!cond) throw new AssertionError(msg);
	}
}

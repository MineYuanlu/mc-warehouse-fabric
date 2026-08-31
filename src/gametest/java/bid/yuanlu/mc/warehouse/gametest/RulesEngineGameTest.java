package bid.yuanlu.mc.warehouse.gametest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.Priority;
import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;

/**
 * gametest④（L12）：规则语义经真实引擎轮验证（PDD §3.6/§3.7/§6.2）。
 * <p>
 * 场景①：OUTPUT 白名单 count:32、INPUT 60 钻石 → 精确数量存入（部分堆 §6.2 算法），
 * 恰好 32 进 OUTPUT、28 溢出进 TEMP、INPUT 清空；
 * 场景②：INPUT 规则 count:16（保留下限）→ 只取出多余 44；
 * 场景③：WHITELIST 排除异类——钻石+dirt 的 INPUT，dirt 永不进 OUTPUT，落 TEMP。
 */
@SuppressWarnings("UnstableApiUsage")
public class RulesEngineGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(sp);
			context.waitTicks(30);

			TransportEngineGameTest.Harness h = TransportEngineGameTest.Harness.boot(context);

			countExactPut(context, sp, h);
			inputRetain(context, sp, h);
			whitelistExcludes(context, sp, h);

			LOGGER.info("yuanlu-warehouse L12 rules engine gametest passed");

			h.teardown();
			sp.getServer().runOnServer(server -> server.halt(false));
		}
	}

	// ---- 场景①：count:32 精确填充 + 溢出进 TEMP ----

	private static void countExactPut(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos input = near(context, 2, 0, -1);
		BlockPos output = near(context, 2, 0, 1);
		BlockPos temp = near(context, 3, 0, -1);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 60)),
				Map.of(output, Map.of(), temp, Map.of()));
		setupWarehouse(context, "l12-a", wh -> {
			ContainerRule rule = new ContainerRule("l12-out-32");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(32)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l12-1] {} rounds={} moved={} detail={}", r.grade(), r.rounds(),
				r.itemsMoved(), r.detailKey());
		snap(context, "l12-1-count-32");

		check(countAwaited(context, sp, input, Items.DIAMOND) == 0, "INPUT 应清空");
		check(countAwaited(context, sp, output, Items.DIAMOND) == 32,
				"OUTPUT 应恰好 32 钻石（精确数量算法），实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		check(countAwaited(context, sp, temp, Items.DIAMOND) == 28,
				"TEMP 应收下溢出的 28 钻石，实际=" + countAwaited(context, sp, temp, Items.DIAMOND));
		clearBag(context);
	}

	// ---- 场景②：INPUT 保留下限 count:16 ----

	private static void inputRetain(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		BlockPos input = near(context, 2, 0, -3);
		BlockPos output = near(context, 2, 0, 3);
		BlockPos temp = near(context, 3, 0, -3);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 60)),
				Map.of(output, Map.of(), temp, Map.of()));
		setupWarehouse(context, "l12-b", wh -> {
			ContainerRule keep = new ContainerRule("l12-keep-16");
			keep.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(16)));
			wh.rules.put(keep.id, keep);
			ContainerInfo in = cont(IOType.INPUT, input, new Priority(5, 0));
			in.rules.add(keep.id); // INPUT 侧数量语义 = 保留下限
			wh.containers.add(in);
			ContainerRule outRule = new ContainerRule("l12-out-free");
			outRule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(65536)));
			wh.rules.put(outRule.id, outRule);
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(outRule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l12-2] {} rounds={} moved={} detail={}", r.grade(), r.rounds(),
				r.itemsMoved(), r.detailKey());
		snap(context, "l12-2-retain-16");

		check("wh.report.input_empty".equals(r.detailKey()),
				"保留规则达标后出口②应 input_empty 结束（而非 no_progress），实际=" + r.detailKey());
		check(countAwaited(context, sp, input, Items.DIAMOND) == 16,
				"INPUT 应保留 16 钻石，实际=" + countAwaited(context, sp, input, Items.DIAMOND));
		check(countAwaited(context, sp, output, Items.DIAMOND) == 44,
				"OUTPUT 应收下取出的 44 钻石，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		clearBag(context);
	}

	// ---- 场景③：WHITELIST 排除异类 ----

	private static void whitelistExcludes(ClientGameTestContext context, TestSingleplayerContext sp,
			TransportEngineGameTest.Harness h) {
		// 全新坐标（距离 ≤4.5 且未在前序场景用过——避免同 pos 陈旧缓存）
		BlockPos input = near(context, 2, 0, -2);
		BlockPos output = near(context, 2, 0, 2);
		BlockPos temp = near(context, 3, 0, -2);
		fillChests(context, sp, Map.of(input, Map.of(Items.DIAMOND, 30, Items.DIRT, 10)),
				Map.of(output, Map.of(), temp, Map.of()));
		setupWarehouse(context, "l12-c", wh -> {
			ContainerRule rule = new ContainerRule("l12-out-diamond");
			rule.itemRules.add(new ItemRule(new IdSelector("minecraft:diamond"), false,
					new CountSelector(65536)));
			wh.rules.put(rule.id, rule);
			wh.containers.add(cont(IOType.INPUT, input, new Priority(5, 0)));
			ContainerInfo out = cont(IOType.OUTPUT, output, Priority.ZERO);
			out.rules.add(rule.id);
			wh.containers.add(out);
			wh.containers.add(cont(IOType.TEMP, temp, Priority.ZERO));
		});

		RunReport r = runAndWait(context, h);
		LOGGER.info("[l12-3] {} rounds={} moved={} detail={}", r.grade(), r.rounds(),
				r.itemsMoved(), r.detailKey());
		snap(context, "l12-3-whitelist");

		check(countAwaited(context, sp, input, Items.DIAMOND) == 0
				&& countAwaited(context, sp, input, Items.DIRT) == 0, "INPUT 应清空");
		check(countAwaited(context, sp, output, Items.DIAMOND) == 30,
				"OUTPUT 应收 30 钻石，实际=" + countAwaited(context, sp, output, Items.DIAMOND));
		check(countAwaited(context, sp, output, Items.DIRT) == 0,
				"WHITELIST 不应收 dirt，实际=" + countAwaited(context, sp, output, Items.DIRT));
		check(countAwaited(context, sp, temp, Items.DIRT) == 10,
				"dirt 应落 TEMP，实际=" + countAwaited(context, sp, temp, Items.DIRT));
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
		return new WorldDim(WorldSessionTracker.get().currentServerId(),
				WorldSessionTracker.get().currentWorldName(),
				Level.OVERWORLD.identifier().toString());
	}

	private static ContainerInfo cont(IOType io, BlockPos abs, Priority prio) {
		ContainerInfo c = new ContainerInfo(io);
		c.priority = prio;
		c.pos.add(WorldDimPos.of(dim(), abs));
		return c;
	}

	/** 在客户端线程构建仓库（anchor=原点，绝对坐标即相对坐标）并激活 */
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

	/** 服务端放置空箱 + 注入内容（items: 物品→数量，多箱并发一次派发） */
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

	/** 服务端读箱子内某物品总量（runOnServer 异步派发，轮询等待） */
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

	/** 启动引擎并等 DONE（RunReport 出现）；超时断言失败 */
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


	private static void snap(ClientGameTestContext context, String name) {
		try {
			context.takeScreenshot("yuanlu-warehouse-" + name);
		} catch (Exception e) {
			LOGGER.warn("screenshot {} failed: {}", name, e.toString());
		}
	}
	private static void check(boolean cond, String msg) {
		if (!cond) throw new AssertionError(msg);
	}
}

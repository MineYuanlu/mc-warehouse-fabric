package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import bid.yuanlu.mc.warehouse.impl.allocator.FirstFitAllocator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * FirstFitAllocator 两遍式落位（PDD §3.6/§6.2）+ 槽位能力约束（PDD §8.2）。
 */
public class FirstFitAllocatorTest extends McBootstrap {

	private static ContainerSnapshot snapshot(Map<Integer, ItemStack> slots, Map<Integer, SlotInfo> infos, int slotCount) {
		return new ContainerSnapshot(slots, infos, "test", slotCount);
	}

	private static ItemStack diamonds(int n) {
		return new ItemStack(Items.DIAMOND, n);
	}

	@Test
	void putMergesPartialThenEmpty() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamonds(32));
		slots.put(1, diamonds(64));
		slots.put(2, new ItemStack(Items.STICK, 10));

		var out = new FirstFitAllocator().allocate(snapshot(slots, Map.of(), 27), diamonds(1), 130, true);

		// 期望：并入 slot0（+32 到满）→ 空槽 3、4 依次填
		assertEquals(3, out.size());
		assertEquals(new bid.yuanlu.mc.warehouse.api.item.SlotAllocation(0, 32), out.get(0));
		assertEquals(new bid.yuanlu.mc.warehouse.api.item.SlotAllocation(3, 64), out.get(1));
		assertEquals(new bid.yuanlu.mc.warehouse.api.item.SlotAllocation(4, 34), out.get(2));
		int total = out.stream().mapToInt(bid.yuanlu.mc.warehouse.api.item.SlotAllocation::count).sum();
		assertEquals(130, total);
	}

	@Test
	void putRespectsCanPutToFalse() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamonds(32)); // 熔炉输出槽风格：只可取
		Map<Integer, SlotInfo> infos = Map.of(0, new SlotInfo(bid.yuanlu.mc.warehouse.api.container.SlotRole.MACHINE_OUTPUT, true, false));

		var out = new FirstFitAllocator().allocate(snapshot(slots, infos, 5), diamonds(1), 100, true);

		// slot0 被跳过，从空槽开始
		assertTrue(out.stream().noneMatch(a -> a.slot() == 0));
		assertEquals(new bid.yuanlu.mc.warehouse.api.item.SlotAllocation(1, 64), out.get(0));
	}

	@Test
	void putTruncatesWhenFull() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamonds(60));
		var out = new FirstFitAllocator().allocate(snapshot(slots, Map.of(), 2), diamonds(1), 200, true);
		// 容量：slot0 剩 4 + 空槽1 的 64 = 68 < 200 → 截断不超发
		assertEquals(68, out.stream().mapToInt(a -> a.count()).sum());
	}

	@Test
	void takeDrainsInSlotOrder() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamonds(32));
		slots.put(1, diamonds(64));

		var out = new FirstFitAllocator().allocate(snapshot(slots, Map.of(), 27), diamonds(1), 40, false);

		assertEquals(2, out.size());
		assertEquals(new bid.yuanlu.mc.warehouse.api.item.SlotAllocation(0, 32), out.get(0));
		assertEquals(new bid.yuanlu.mc.warehouse.api.item.SlotAllocation(1, 8), out.get(1));
	}

	@Test
	void takeSkipsCanTakeFromFalse() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamonds(32));
		slots.put(1, diamonds(64));
		Map<Integer, SlotInfo> infos = Map.of(0, new SlotInfo(
				bid.yuanlu.mc.warehouse.api.container.SlotRole.MACHINE_INPUT, false, true));

		var out = new FirstFitAllocator().allocate(snapshot(slots, infos, 27), diamonds(1), 40, false);

		// slot0 不可取，全部来自 slot1
		assertTrue(out.stream().noneMatch(a -> a.slot() == 0));
		assertEquals(40, out.stream().mapToInt(a -> a.count()).sum());
	}

	@Test
	void differentItemDoesNotMerge() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, new ItemStack(Items.EMERALD, 10));

		var out = new FirstFitAllocator().allocate(snapshot(slots, Map.of(), 27), diamonds(1), 70, true);

		// 不与异类堆合并，直接走空槽
		assertTrue(out.stream().noneMatch(a -> a.slot() == 0));
		assertEquals(70, out.stream().mapToInt(a -> a.count()).sum());
	}
}

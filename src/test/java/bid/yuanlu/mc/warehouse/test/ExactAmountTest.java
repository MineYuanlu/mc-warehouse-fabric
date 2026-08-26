package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.api.interaction.ContainerHandle;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.engine.container.ContainerSession;
import bid.yuanlu.mc.warehouse.core.engine.container.ExactAmount;

/**
 * 精确数量算法的步骤序列（PDD §6.2；点击序列以 MVP 实现为准）。
 */
public class ExactAmountTest {

	/** 录制型假交互：记录调用序列，不产生真实点击 */
	private static final class RecordingInteraction implements ContainerInteraction {
		final List<String> log = new ArrayList<>();

		@Override
		public String id() {
			return "recording";
		}

		@Override
		public void requestOpen(ContainerHandle handle) {
			log.add("open");
		}

		@Override
		public void requestClose(ContainerHandle handle) {
			log.add("close");
		}

		@Override
		public boolean quickMoveToPlayer(ContainerHandle handle, int slot) {
			log.add("quickMoveToPlayer:" + slot);
			return true;
		}

		@Override
		public boolean quickMoveToContainer(ContainerHandle handle, int slot) {
			log.add("quickMoveToContainer:" + slot);
			return true;
		}

		@Override
		public boolean supportsExactAmount() {
			return true;
		}

		@Override
		public boolean pickupAll(ContainerHandle handle, int slot) {
			log.add("pickupAll:" + slot);
			return true;
		}

		@Override
		public boolean pickupHalf(ContainerHandle handle, int slot) {
			log.add("pickupHalf:" + slot);
			return true;
		}

		@Override
		public boolean placeOne(ContainerHandle handle, int slot) {
			log.add("placeOne:" + slot);
			return true;
		}

		@Override
		public boolean putBackHeld(ContainerHandle handle, int slot) {
			log.add("putBack:" + slot);
			return true;
		}

		@Override
		public boolean dragDistribute(ContainerHandle handle, int[] slots) {
			log.add("drag:" + java.util.Arrays.toString(slots));
			return true;
		}
	}

	private static void run(List<ContainerSession.Step> steps) {
		steps.forEach(s -> s.action().run());
	}

	@Test
	void depositSingleItemTailPhase() {
		var it = new RecordingInteraction();
		var h = new ContainerHandle(new WorldDimPos("w", "d", 0, 0, 0));

		// total=64, quota=10：t=4；折半段条件 剩余配额>=S/2 → 10>=32 否，直接单件补齐
		List<ContainerSession.Step> steps = ExactAmount.deposit(it, h, 40, 64, 10);
		// pickupAll + placeOne×10 + quickMove + 归还剩余（光标仍持有 54）
		assertEquals(13, steps.size());
		run(steps);
		assertEquals("pickupAll:40", it.log.get(0));
		assertEquals(10, it.log.stream().filter(op -> op.equals("placeOne:40")).count());
		assertEquals("quickMoveToContainer:40", it.log.get(11));
		assertEquals("putBack:40", it.log.get(12));
	}

	@Test
	void depositHalfSplitLoopMatchesMvpSequence() {
		var it = new RecordingInteraction();
		var h = new ContainerHandle(new WorldDimPos("w", "d", 0, 0, 0));

		// total=64, quota=48：t=4
		// 迭代1: S=64>4 且 48>=32 ✓ → pickupHalf+quickMove（光标初始为空）
		//        moved=32, S=32
		// 迭代2: 32>4 且 48>=16 ✓ → putBack+pickupHalf+quickMove
		//        moved=48, S=16
		// 循环退出：R-moved=0 → 补齐段跳过；归还段 putBack
		List<ContainerSession.Step> steps = ExactAmount.deposit(it, h, 5, 64, 48);
		run(steps);

		List<String> expect = List.of(
				"pickupHalf:5",
				"quickMoveToContainer:5",
				"putBack:5",
				"pickupHalf:5",
				"quickMoveToContainer:5",
				"putBack:5");
		assertEquals(expect, it.log);
	}

	@Test
	void withdrawHalfBranchPutsBackRemainderThenDrags() {
		var it = new RecordingInteraction();
		var h = new ContainerHandle(new WorldDimPos("w", "d", 0, 0, 0));

		// nowCount=64, need=10：ceil(32) ≥ 10 → 半组分支，放回 22 个
		List<ContainerSession.Step> steps = ExactAmount.withdraw(it, h, 7, 64, 10, new int[]{60, 61});
		run(steps);

		assertEquals("pickupHalf:7", it.log.get(0));
		assertEquals(22, it.log.stream().filter(op -> op.equals("placeOne:7")).count());
		assertEquals("drag:[60, 61]", it.log.get(it.log.size() - 1));
	}

	@Test
	void withdrawAllBranchWhenNeedExceedsHalf() {
		var it = new RecordingInteraction();
		var h = new ContainerHandle(new WorldDimPos("w", "d", 0, 0, 0));

		// nowCount=20, need=15：ceil(10)=10 < 15 → 全取分支，放回 5 个
		List<ContainerSession.Step> steps = ExactAmount.withdraw(it, h, 3, 20, 15, new int[]{50});
		run(steps);

		assertEquals("pickupAll:3", it.log.get(0));
		assertEquals(5, it.log.stream().filter(op -> op.equals("placeOne:3")).count());
		assertEquals("drag:[50]", it.log.get(it.log.size() - 1));
	}

	@Test
	void withdrawWithoutTargetsIsEmpty() {
		var it = new RecordingInteraction();
		var h = new ContainerHandle(new WorldDimPos("w", "d", 0, 0, 0));
		assertTrue(ExactAmount.withdraw(it, h, 3, 20, 15, null).isEmpty());
	}
}

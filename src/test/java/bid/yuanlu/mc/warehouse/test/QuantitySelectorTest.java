package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.api.item.QuantityContext;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.FillSlotsSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.GroupSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.PercentSelector;

/**
 * 数量选择器公式（PDD §3.6 表格；含旧项目缺陷回归：fill_slots 负值钳制、percent 取整）。
 */
public class QuantitySelectorTest {

	private static QuantityContext ctx(int current, int slots, int free, int maxStack) {
		return new QuantityContext(current, slots, free, maxStack);
	}

	@Test
	void countIsAbsolute() {
		assertEquals(64, new CountSelector(64).computeTargetAmount(ctx(0, 27, 27, 64)));
		assertEquals(64, new CountSelector(64).computeTargetAmount(ctx(10, 9, 0, 16)), "与槽位口径无关");
	}

	@Test
	void groupScalesByMaxStackSize() {
		assertEquals(192, new GroupSelector(3).computeTargetAmount(ctx(0, 27, 27, 64)));
		assertEquals(48, new GroupSelector(3).computeTargetAmount(ctx(0, 27, 27, 16)));
	}

	@Test
	void fillSlotsKeepsEmptySlotsAndClampsNegative() {
		// 27 槽保留 5 空 → 22 × 64
		assertEquals(22 * 64, new FillSlotsSelector(5).computeTargetAmount(ctx(0, 27, 27, 64)));
		// value > slotCount → 钳制为 0（旧项目会产生负目标）
		assertEquals(0, new FillSlotsSelector(30).computeTargetAmount(ctx(0, 27, 27, 64)));
		// value=0 → 占满全部槽位
		assertEquals(64, new FillSlotsSelector(0).computeTargetAmount(ctx(0, 4, 4, 16)));
	}

	@Test
	void percentFloorsToCapacityRatio() {
		assertEquals(1296, new PercentSelector(75).computeTargetAmount(ctx(0, 27, 27, 64)));
		assertEquals(96, new PercentSelector(50).computeTargetAmount(ctx(0, 3, 3, 64)));
		// 截断：1×7×50/100 = 3.5 → 3（按容量比例取整）
		assertEquals(3, new PercentSelector(50).computeTargetAmount(ctx(0, 1, 1, 7)));
		assertEquals(0, new PercentSelector(0).computeTargetAmount(ctx(5, 27, 20, 64)));
	}

	@Test
	void validationRejectsInvalidValues() {
		assertThrows(IllegalArgumentException.class, () -> new CountSelector(-1));
		assertThrows(IllegalArgumentException.class, () -> new GroupSelector(-1));
		assertThrows(IllegalArgumentException.class, () -> new FillSlotsSelector(-1));
		assertThrows(IllegalArgumentException.class, () -> new PercentSelector(-1));
		assertThrows(IllegalArgumentException.class, () -> new PercentSelector(101));

		assertThrows(IllegalArgumentException.class, () -> ctx(0, -1, 0, 64), "slotCount < 0");
		assertThrows(IllegalArgumentException.class, () -> ctx(0, 9, 10, 64), "freeSlots > slotCount");
		assertThrows(IllegalArgumentException.class, () -> ctx(0, 9, 0, 0), "maxStackSize <= 0");
	}
}

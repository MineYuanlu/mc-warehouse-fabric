package bid.yuanlu.mcwarehouse.engine.rule;

import bid.yuanlu.mcwarehouse.model.rule.QuantitySelector;

public class QuantityCalculator {

	public static int computeTarget(QuantitySelector selector, int currentCount, int totalSlots, int maxStackSize) {
		return selector.computeTargetQuantity(currentCount, totalSlots, maxStackSize);
	}

	public static boolean isSatisfied(QuantitySelector selector, int currentCount, int totalSlots, int maxStackSize) {
		return selector.isSatisfied(currentCount, totalSlots, maxStackSize);
	}
}

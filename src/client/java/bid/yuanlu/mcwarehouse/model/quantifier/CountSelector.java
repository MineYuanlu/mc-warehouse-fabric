package bid.yuanlu.mcwarehouse.model.quantifier;

import bid.yuanlu.mcwarehouse.model.rule.QuantitySelector;

public class CountSelector implements QuantitySelector {

	public int value;

	@Override
	public int computeTargetQuantity(int currentCount, int totalSlots, int maxStackSize) {
		return Math.min(value, maxStackSize * 64);
	}

	@Override
	public boolean isSatisfied(int currentCount, int totalSlots, int maxStackSize) {
		return currentCount >= value;
	}
}

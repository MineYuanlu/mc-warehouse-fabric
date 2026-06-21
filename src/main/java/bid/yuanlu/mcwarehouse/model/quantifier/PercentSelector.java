package bid.yuanlu.mcwarehouse.model.quantifier;

import bid.yuanlu.mcwarehouse.model.rule.QuantitySelector;

public class PercentSelector implements QuantitySelector {

	public int value;

	@Override
	public int computeTargetQuantity(int currentCount, int totalSlots, int maxStackSize) {
		return totalSlots * maxStackSize * value / 100;
	}

	@Override
	public boolean isSatisfied(int currentCount, int totalSlots, int maxStackSize) {
		return currentCount >= computeTargetQuantity(currentCount, totalSlots, maxStackSize);
	}
}

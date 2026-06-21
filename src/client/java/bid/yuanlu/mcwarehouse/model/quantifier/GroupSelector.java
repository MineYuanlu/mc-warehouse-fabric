package bid.yuanlu.mcwarehouse.model.quantifier;

import bid.yuanlu.mcwarehouse.model.rule.QuantitySelector;

public class GroupSelector implements QuantitySelector {

	public int value;

	@Override
	public int computeTargetQuantity(int currentCount, int totalSlots, int maxStackSize) {
		return value * maxStackSize;
	}

	@Override
	public boolean isSatisfied(int currentCount, int totalSlots, int maxStackSize) {
		return currentCount >= value * maxStackSize;
	}
}

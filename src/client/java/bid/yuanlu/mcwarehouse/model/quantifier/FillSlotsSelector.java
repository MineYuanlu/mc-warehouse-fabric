package bid.yuanlu.mcwarehouse.model.quantifier;

import bid.yuanlu.mcwarehouse.model.rule.QuantitySelector;

public class FillSlotsSelector implements QuantitySelector {

	public int value;

	@Override
	public int computeTargetQuantity(int currentCount, int totalSlots, int maxStackSize) {
		return (totalSlots - value) * maxStackSize;
	}

	@Override
	public boolean isSatisfied(int currentCount, int totalSlots, int maxStackSize) {
		int filled = (currentCount + maxStackSize - 1) / maxStackSize;
		return filled >= totalSlots - value;
	}
}

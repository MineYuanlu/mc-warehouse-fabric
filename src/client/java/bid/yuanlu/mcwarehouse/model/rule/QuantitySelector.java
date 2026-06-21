package bid.yuanlu.mcwarehouse.model.rule;

public interface QuantitySelector {

	int computeTargetQuantity(int currentCount, int totalSlots, int maxStackSize);

	boolean isSatisfied(int currentCount, int totalSlots, int maxStackSize);
}

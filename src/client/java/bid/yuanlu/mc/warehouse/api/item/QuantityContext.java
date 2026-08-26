package bid.yuanlu.mc.warehouse.api.item;

/**
 * 数量计算的上下文（PDD §3.6）。
 *
 * @param currentTotal  容器中匹配物品的当前总量
 * @param slotCount     参与判定的槽位总数（canTakeFrom || canPutTo 口径，§8.2）
 * @param freeSlots     其中空槽数
 * @param maxStackSize  该物品最大堆叠数
 */
public record QuantityContext(int currentTotal, int slotCount, int freeSlots, int maxStackSize) {

	public QuantityContext {
		if (slotCount < 0) throw new IllegalArgumentException("slotCount < 0: " + slotCount);
		if (freeSlots < 0 || freeSlots > slotCount) throw new IllegalArgumentException("freeSlots out of range: " + freeSlots + "/" + slotCount);
		if (maxStackSize <= 0) throw new IllegalArgumentException("maxStackSize <= 0: " + maxStackSize);
	}
}

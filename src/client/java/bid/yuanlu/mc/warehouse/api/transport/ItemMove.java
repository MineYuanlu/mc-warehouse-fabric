package bid.yuanlu.mc.warehouse.api.transport;

import net.minecraft.world.item.ItemStack;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;

/**
 * 单个物品移动指令（PDD §3.9）。
 *
 * @param targetPos  目标容器坐标（绝对坐标）
 * @param targetSlot 目标槽位；{@link #AUTO_SLOT} 表示自动分配
 * @param item       要移动的物品样本（id + 组件）
 * @param amount     移动数量（支持任意精确数量，PDD §6.2）
 * @param priority   执行优先级（同一 plan 内的排序）
 */
public record ItemMove(WorldDimPos targetPos, int targetSlot, ItemStack item, int amount, int priority) {

	public static final int AUTO_SLOT = -1;

	public ItemMove {
		java.util.Objects.requireNonNull(item, "item");
		if (amount <= 0) throw new IllegalArgumentException("amount <= 0: " + amount);
	}
}

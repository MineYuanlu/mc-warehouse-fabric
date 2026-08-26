package bid.yuanlu.mc.warehouse.core.engine.rule;

import net.minecraft.world.item.ItemStack;

/**
 * 取出计划（引擎滚动模拟的原子输出）：从容器槽位 {@link #slot} 取走
 * {@link #sample}×{@link #amount}。sample 仅承载物品身份与 maxStackSize 信息。
 */
public record TakePlan(int slot, ItemStack sample, int amount) {

	public TakePlan {
		if (slot < 0) throw new IllegalArgumentException("slot < 0");
		if (amount <= 0) throw new IllegalArgumentException("amount <= 0: " + amount);
	}
}

package bid.yuanlu.mc.warehouse.core.engine.rule;

import net.minecraft.world.item.ItemStack;

/**
 * 放入需求：需向容器放入 {@link #sample}×{@link #amount}；具体落在哪些槽位由
 * SlotAllocator 在会话内对活动 Menu 分配（PDD §3.6）。
 */
public record PutDemand(ItemStack sample, int amount) {

	public PutDemand {
		if (amount <= 0) throw new IllegalArgumentException("amount <= 0: " + amount);
		sample = sample.copyWithCount(1);
	}
}

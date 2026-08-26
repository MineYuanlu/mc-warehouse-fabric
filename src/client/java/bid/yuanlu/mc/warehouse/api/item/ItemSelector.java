package bid.yuanlu.mc.warehouse.api.item;

import net.minecraft.world.item.ItemStack;

/**
 * 物品选择器（PDD §3.5）：判断给定的物品堆是否匹配此选择器。
 * <p>
 * 只看物品本身与组件，不看数量。实现必须配套 {@link bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec}
 * 注册后才能参与 JSON 持久化。
 */
@FunctionalInterface
public interface ItemSelector {

	/** 判断给定的物品堆是否匹配此选择器（只看物品本身与组件，不看数量） */
	boolean matches(ItemStack stack);
}

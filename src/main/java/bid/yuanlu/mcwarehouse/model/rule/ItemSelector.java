package bid.yuanlu.mcwarehouse.model.rule;

import net.minecraft.world.item.ItemStack;

public interface ItemSelector {

	boolean matches(ItemStack stack);
}

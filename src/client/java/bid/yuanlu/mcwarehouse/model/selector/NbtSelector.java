package bid.yuanlu.mcwarehouse.model.selector;

import bid.yuanlu.mcwarehouse.model.rule.ItemSelector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class NbtSelector implements ItemSelector {

	public String value;

	@Override
	public boolean matches(ItemStack stack) {
		var tag = stack.getComponentsPatch();
		return tag != null && tag.toString().contains(value);
	}
}

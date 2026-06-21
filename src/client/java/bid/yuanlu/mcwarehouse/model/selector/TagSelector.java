package bid.yuanlu.mcwarehouse.model.selector;

import bid.yuanlu.mcwarehouse.model.rule.ItemSelector;
import net.minecraft.world.item.ItemStack;

public class TagSelector implements ItemSelector {

	public String value;

	@Override
	public boolean matches(ItemStack stack) {
		return stack.getItemHolder().tags()
			.anyMatch(tag -> tag.location().toString().equals(value));
	}
}

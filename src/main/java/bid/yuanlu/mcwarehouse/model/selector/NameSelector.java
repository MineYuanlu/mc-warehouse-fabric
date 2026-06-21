package bid.yuanlu.mcwarehouse.model.selector;

import bid.yuanlu.mcwarehouse.model.rule.ItemSelector;
import net.minecraft.world.item.ItemStack;

public class NameSelector implements ItemSelector {

	public String value;
	public boolean fuzzy;

	@Override
	public boolean matches(ItemStack stack) {
		String name = stack.getHoverName().getString();
		if (fuzzy) {
			return name.toLowerCase().contains(value.toLowerCase());
		}
		return name.equals(value);
	}
}

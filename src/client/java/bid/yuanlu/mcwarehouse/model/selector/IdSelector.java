package bid.yuanlu.mcwarehouse.model.selector;

import bid.yuanlu.mcwarehouse.model.rule.ItemSelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;

public class IdSelector implements ItemSelector {

	public String value;

	@Override
	public boolean matches(ItemStack stack) {
		return Registries.ITEM.getKey(stack.getItem()).toString().equals(value);
	}
}

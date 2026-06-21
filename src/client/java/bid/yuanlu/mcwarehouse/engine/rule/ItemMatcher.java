package bid.yuanlu.mcwarehouse.engine.rule;

import bid.yuanlu.mcwarehouse.model.rule.ItemRule;
import net.minecraft.world.item.ItemStack;

public class ItemMatcher {

	public static boolean matches(ItemRule rule, ItemStack stack) {
		boolean base = rule.selector.matches(stack);
		return rule.negate ? !base : base;
	}
}

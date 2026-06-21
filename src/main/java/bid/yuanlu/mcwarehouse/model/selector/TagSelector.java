package bid.yuanlu.mcwarehouse.model.selector;

import bid.yuanlu.mcwarehouse.model.rule.ItemSelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class TagSelector implements ItemSelector {

	public String value;

	@Override
	public boolean matches(ItemStack stack) {
		try {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(value));
			return stack.is(tag);
		} catch (Exception e) {
			return false;
		}
	}
}

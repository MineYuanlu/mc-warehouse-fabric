package bid.yuanlu.mcwarehouse.model.selector;

import java.util.List;

import bid.yuanlu.mcwarehouse.model.rule.ItemSelector;
import net.minecraft.world.item.ItemStack;

public class CompositeSelector implements ItemSelector {

	public String op;
	public List<ItemSelector> selectors;

	@Override
	public boolean matches(ItemStack stack) {
		return switch (op) {
			case "AND" -> selectors.stream().allMatch(s -> s.matches(stack));
			case "OR" -> selectors.stream().anyMatch(s -> s.matches(stack));
			case "NOT" -> {
				if (selectors.size() != 1) {
					throw new IllegalArgumentException("NOT requires exactly one selector");
				}
				yield !selectors.getFirst().matches(stack);
			}
			default -> throw new IllegalArgumentException("Unknown op: " + op);
		};
	}
}

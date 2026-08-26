package bid.yuanlu.mc.warehouse.core.engine.rule;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;

/**
 * 首条命中判定（PDD §3.7）：按容器 rules 数组顺序遍历关联的 ContainerRule 及其
 * itemRules 列表——首条命中的 ItemRule 生效，其余忽略。negative=true 先求反再命中。
 */
public final class ItemMatcher {

	/** 无命中返回 null（随后按 ruleMode 缺省语义处理） */
	@Nullable
	public static ItemRule firstMatch(Iterable<ContainerRule> rules, ItemStack stack) {
		for (ContainerRule rule : rules) {
			for (ItemRule itemRule : rule.itemRules) {
				if (itemRule.selector.matches(stack) ^ itemRule.negative) {
					return itemRule;
				}
			}
		}
		return null;
	}

	private ItemMatcher() {
	}
}

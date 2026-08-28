package bid.yuanlu.mc.warehouse.core.engine.rule;

import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;

/**
 * 放入可行性纯函数（PDD §5.3 防振荡③，F3）：仅用静态规则判定「背包里是否有
 * 该容器规则可放下的物品」，用于放入方向队列预筛——身上没有可放之物就不去，
 * 避免空跑开箱。
 * <p>
 * 口径：规则层判据（含 negative 求反命中）。数量/容量/检测器级过滤（已满、
 * 燃料等）由执行期 planPutInto 再裁——这里只做「有没有可能」，宁可多去不可漏去。
 */
public final class PutFeasibility {

	/**
	 * 背包是否存在该规则集下可放入的物品。
	 *
	 * @param mode           容器生效规则模式（effectiveRuleMode）
	 * @param rules          容器引用的规则列表（resolveRules 结果）
	 * @param inventoryItems 背包按物品聚合（PackView.aggregate）
	 */
	public static boolean anyFeasible(RuleMode mode, List<ContainerRule> rules,
			Map<ItemStack, Integer> inventoryItems) {
		for (Map.Entry<ItemStack, Integer> e : inventoryItems.entrySet()) {
			ItemStack sample = e.getKey();
			if (sample == null || sample.isEmpty() || e.getValue() <= 0) continue;
			ItemRule matched = ItemMatcher.firstMatch(rules, sample);
			if (mode == RuleMode.WHITELIST) {
				if (matched != null) return true; // 命中白名单即可放入
			} else if (matched == null) {
				return true; // 未命中黑名单 = 不受限
			}
		}
		return false;
	}

	private PutFeasibility() {
	}
}

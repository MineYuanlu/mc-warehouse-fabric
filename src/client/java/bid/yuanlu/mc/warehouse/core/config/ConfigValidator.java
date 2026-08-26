package bid.yuanlu.mc.warehouse.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;

/**
 * 配置加载校验（PDD §3.7/§11.3）：
 * <ul>
 *   <li>规则 id 冲突：仓库内嵌 rules 与全局 rules 同名 → 拒载该仓库并列出冲突</li>
 *   <li>引用完整性：容器引用的规则必须存在（内嵌或全局）</li>
 *   <li>selector×IOType 合法性：不限量语义禁止用于 OUTPUT（D2 严格拒载）；
 *       数量选择器声明的 {@code incompatibleIOTypes} 命中容器类型同样报错</li>
 * </ul>
 */
public final class ConfigValidator {

	/** @return 错误列表；空 = 通过 */
	public static List<String> validate(Warehouse warehouse, Set<String> globalRuleIds) {
		List<String> errors = new ArrayList<>();

		// 规则 id 冲突：内嵌 ∩ 全局
		List<String> conflicts = new ArrayList<>();
		for (String id : warehouse.rules.keySet()) {
			if (globalRuleIds.contains(id)) conflicts.add(id);
		}
		if (!conflicts.isEmpty()) {
			errors.add("rule id conflict with global rules: " + String.join(", ", conflicts));
		}

		// 容器逐项校验
		int index = 0;
		for (ContainerInfo container : warehouse.containers) {
			String where = "containers[" + index++ + "] " + describe(container);

			if (container.pos.isEmpty()) {
				errors.add(where + ": no pos");
			}

			Set<String> available = warehouse.rules.keySet();
			for (String ruleId : container.rules) {
				boolean embedded = available.contains(ruleId);
				if (!embedded && !globalRuleIds.contains(ruleId)) {
					errors.add(where + ": unknown rule \"" + ruleId + "\"");
					continue;
				}
				ContainerRule rule = embedded ? warehouse.rules.get(ruleId) : null;
				if (rule == null) continue; // 全局规则的条目在规则文件自身加载时已校验
				checkQuantityCompatibility(errors, where, container, rule);
			}
		}
		return errors;
	}

	private static void checkQuantityCompatibility(List<String> errors, String where, ContainerInfo container, ContainerRule rule) {
		if (container.ioType != IOType.OUTPUT && !container.ioType.participates()) return;

		int itemIndex = 0;
		for (ItemRule itemRule : rule.itemRules) {
			String at = where + " rule \"" + rule.id + "\" itemRules[" + itemIndex++ + "]";
			if (container.ioType == IOType.OUTPUT) {
				// D2：不限量 × OUTPUT 严格拒载
				if (itemRule.isUnlimited()) {
					errors.add(at + ": unlimited quantity is forbidden on OUTPUT containers");
					continue;
				}
			}
			if (itemRule.quantity != null && itemRule.quantity.incompatibleIOTypes().contains(container.ioType)) {
				errors.add(at + ": quantity selector incompatible with IOType " + container.ioType);
			}
		}
	}

	private static String describe(ContainerInfo c) {
		return c.pos.isEmpty() ? "(no pos)" : c.canonicalPos().toString();
	}

	private ConfigValidator() {
	}
}

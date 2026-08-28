package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.item.QuantityContext;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.core.engine.rule.PutFeasibility;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;

/**
 * 放入可行性预筛纯函数（F3，PDD §5.3 防振荡③）：静态规则判定「背包是否有该容器
 * 可放之物」。只判可能性——数量/容量/检测器过滤在 planPutInto 执行期再裁。
 */
public class PutFeasibilityTest extends McBootstrap {

	static final ItemSelector DIAMONDS = new IdSelector("minecraft:diamond");
	static final ItemSelector DIRT = new IdSelector("minecraft:dirt");

	static ContainerRule ruleOf(String id, ItemRule... rules) {
		var r = new ContainerRule(id);
		r.itemRules.addAll(List.of(rules));
		return r;
	}

	/** 固定目标量选择器 */
	static final class Fixed implements QuantitySelector {
		final int target;

		Fixed(int target) {
			this.target = target;
		}

		@Override
		public int computeTargetAmount(QuantityContext ctx) {
			return target;
		}
	}

	static Map<ItemStack, Integer> inv(ItemStack... stacks) {
		java.util.LinkedHashMap<ItemStack, Integer> out = new java.util.LinkedHashMap<>();
		for (ItemStack s : stacks) {
			if (s.isEmpty()) continue;
			out.put(s.copyWithCount(1), out.getOrDefault(s.copyWithCount(1), 0) + s.getCount());
		}
		return out;
	}

	static ItemStack diamond(int n) {
		return new ItemStack(Items.DIAMOND, n);
	}

	static ItemStack dirt(int n) {
		return new ItemStack(Items.DIRT, n);
	}

	// ---- WHITELIST ----

	@Test
	void whitelistEmptyRulesNeverFeasible() {
		// OUTPUT 默认 WHITELIST + 空规则 → 无任何物品可放（§3.7「必须明确配置」）
		assertFalse(PutFeasibility.anyFeasible(RuleMode.WHITELIST, List.of(), inv(diamond(1))));
	}

	@Test
	void whitelistMatchFeasible() {
		assertTrue(PutFeasibility.anyFeasible(RuleMode.WHITELIST,
				List.of(ruleOf("v", new ItemRule(DIAMONDS, false, new Fixed(10)))),
				inv(diamond(3))));
	}

	@Test
	void whitelistNoMatchNotFeasible() {
		assertFalse(PutFeasibility.anyFeasible(RuleMode.WHITELIST,
				List.of(ruleOf("v", new ItemRule(DIAMONDS, false, new Fixed(10)))),
				inv(dirt(3))));
	}

	// ---- BLACKLIST ----

	@Test
	void blacklistEmptyRulesFeasibleForAnything() {
		// INPUT/TEMP 默认 BLACKLIST + 空规则 → 任意物品可放
		assertTrue(PutFeasibility.anyFeasible(RuleMode.BLACKLIST, List.of(), inv(dirt(1))));
	}

	@Test
	void blacklistAllExcludedNotFeasible() {
		assertFalse(PutFeasibility.anyFeasible(RuleMode.BLACKLIST,
				List.of(ruleOf("no-dirt", new ItemRule(DIRT, false, new Fixed(1)))),
				inv(dirt(3))));
	}

	@Test
	void blacklistPartialFeasible() {
		// 背包同时有被排除与不受限物品 → 可放
		assertTrue(PutFeasibility.anyFeasible(RuleMode.BLACKLIST,
				List.of(ruleOf("no-dirt", new ItemRule(DIRT, false, new Fixed(1)))),
				inv(dirt(3), diamond(2))));
	}

	// ---- negative 求反（经 ItemMatcher，与 RuleApplicator 同语义）----

	@Test
	void negativeRuleFlipsWhitelistMatch() {
		// 负选「非泥土」→ diamond（对 DIRT 不匹配）求反后命中 → WHITELIST 下可放
		assertTrue(PutFeasibility.anyFeasible(RuleMode.WHITELIST,
				List.of(ruleOf("not-dirt", new ItemRule(DIRT, true, new Fixed(10)))),
				inv(diamond(1))));
		// 但 dirt 本身对 DIRT 匹配，求反后不命中 → 不可放
		assertFalse(PutFeasibility.anyFeasible(RuleMode.WHITELIST,
				List.of(ruleOf("not-dirt", new ItemRule(DIRT, true, new Fixed(10)))),
				inv(dirt(1))));
	}

	// ---- 空背包 ----

	@Test
	void emptyInventoryNeverFeasible() {
		assertFalse(PutFeasibility.anyFeasible(RuleMode.BLACKLIST, List.of(), Map.of()));
		assertFalse(PutFeasibility.anyFeasible(RuleMode.WHITELIST,
				List.of(ruleOf("v", new ItemRule(DIAMONDS, false, new Fixed(10)))), Map.of()));
	}
}

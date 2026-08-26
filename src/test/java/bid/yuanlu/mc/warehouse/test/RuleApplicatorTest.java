package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import bid.yuanlu.mc.warehouse.api.container.SlotRole;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.item.QuantityContext;
import bid.yuanlu.mc.warehouse.core.engine.rule.PutDemand;
import bid.yuanlu.mc.warehouse.core.engine.rule.RuleApplicator;
import bid.yuanlu.mc.warehouse.core.engine.rule.TakePlan;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;

/**
 * L9 规则引擎单测（PDD §3.6/§3.7）：首条命中、delta 推导、模式缺省、槽位口径。
 */
public class RuleApplicatorTest extends McBootstrap {

	static final ItemSelector DIAMONDS = new IdSelector("minecraft:diamond");

	// ---- 构造辅助 ----

	static ContainerRule ruleOf(String id, ItemRule... rules) {
		var r = new ContainerRule(id);
		r.itemRules.addAll(List.of(rules));
		return r;
	}

	static ContainerSnapshot snap(Map<Integer, ItemStack> slots) {
		return new ContainerSnapshot(slots, Map.of(), "chest", 27);
	}

	static ItemStack diamond(int n) {
		return new ItemStack(Items.DIAMOND, n);
	}

	static ItemStack dirt(int n) {
		return new ItemStack(Items.DIRT, n);
	}

	/** 固定目标量选择器 */
	static QuantityCtxTarget fixed(int target) {
		return new QuantityCtxTarget(target);
	}

	record QuantityCtxTarget(int target) implements bid.yuanlu.mc.warehouse.api.item.QuantitySelector {
		@Override
		public int computeTargetAmount(QuantityContext ctx) {
			return target;
		}
	}

	// ---- 首条命中（经 planPut/planTake 间接验证）----

	@Test
	void firstRuleWinsOverLater() {
		// 两条规则都匹配 diamond：首条 target=10 生效；容器 20 个 → 取 10
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(12));
		slots.put(1, diamond(8));
		ContainerInfo info = new ContainerInfo(IOType.INPUT);
		List<ContainerRule> rules = List.of(
				ruleOf("r1", new ItemRule(DIAMONDS, false, fixed(10))),
				ruleOf("r2", new ItemRule(DIAMONDS, false, fixed(0))));
		List<TakePlan> plans = RuleApplicator.planTake(info, rules, snap(slots), RuleApplicator.TakeMode.RULES);
		assertEquals(10, plans.stream().mapToInt(TakePlan::amount).sum());
	}

	@Test
	void negativeFlipsMatch() {
		// 负选「非泥土」对 diamond 不命中（matches=false ^ negative=true → true 才命中——
		// 等等：非泥土 = matches(!dirt)。DIAMONDS 对 dirt 返回 false，求反 = true → dirt 被命中）
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, dirt(64));
		ContainerInfo info = new ContainerInfo(IOType.INPUT); // BLACKLIST 默认
		List<ContainerRule> rules = List.of(
				ruleOf("not-dirt", new ItemRule(new IdSelector("minecraft:dirt"), true, fixed(4))));
		// dirt 被「非dirt」命中？不——matches(dirt)=true ^ negative=true = false → 未命中；
		// 但 negative 求反语义是「选择器先求反再参与匹配」→ IdSelector(dirt).matches(dirt)=true,
		// 求反后 false → dirt 不被此条命中 → fallback 到 BLACKLIST 缺省 ∞ → 全取 64
		List<TakePlan> plans = RuleApplicator.planTake(info, rules, snap(slots), RuleApplicator.TakeMode.RULES);
		assertEquals(64, plans.stream().mapToInt(TakePlan::amount).sum());
	}

	@Test
	void negativeMatchControlsQuantity() {
		// 负选规则命中钻石（selector=DIRT 对 diamond 不匹配，求反=true）→ target=5 → 取 7
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(7));
		slots.put(1, dirt(1));
		ContainerInfo info = new ContainerInfo(IOType.INPUT);
		List<ContainerRule> rules = List.of(
				ruleOf("non-dirt-keep5", new ItemRule(new IdSelector("minecraft:dirt"), true, fixed(5))));
		// diamond: matches(false)^true=true → 命中 target=5 → 取 2
		// dirt: matches(true)^true=false → 未命中 → BLACKLIST 缺省 ∞ → 全取 1
		List<TakePlan> plans = RuleApplicator.planTake(info, rules, snap(slots), RuleApplicator.TakeMode.RULES);
		int diamondsTaken = plans.stream().filter(p -> p.sample().is(Items.DIAMOND)).mapToInt(TakePlan::amount).sum();
		int dirtTaken = plans.stream().filter(p -> p.sample().is(Items.DIRT)).mapToInt(TakePlan::amount).sum();
		assertEquals(2, diamondsTaken);
		assertEquals(1, dirtTaken);
	}

	// ---- planTake ----

	@Test
	void takeBlacklistNoRulesTakesAll() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(32));
		slots.put(1, dirt(64));
		ContainerInfo info = new ContainerInfo(IOType.INPUT);
		List<TakePlan> plans = RuleApplicator.planTake(info, List.of(), snap(slots), RuleApplicator.TakeMode.RULES);
		assertEquals(2, plans.size());
		assertEquals(96, plans.stream().mapToInt(TakePlan::amount).sum());
	}

	@Test
	void takeCountRuleDrainsFromLowestSlots() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(32));
		slots.put(1, diamond(32));
		slots.put(2, diamond(36)); // 总 100，目标 64 → 取 36
		ContainerInfo info = new ContainerInfo(IOType.INPUT);
		ContainerRule rule = ruleOf("cnt", new ItemRule(DIAMONDS, false, fixed(64)));
		List<TakePlan> plans = RuleApplicator.planTake(info, List.of(rule), snap(slots), RuleApplicator.TakeMode.RULES);
		assertEquals(2, plans.size());
		assertEquals(0, plans.get(0).slot());
		assertEquals(32, plans.get(0).amount());
		assertEquals(1, plans.get(1).slot());
		assertEquals(4, plans.get(1).amount());
	}

	@Test
	void takeAllModeIgnoresRules() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(32));
		ContainerInfo info = new ContainerInfo(IOType.OUTPUT);
		ContainerRule rule = ruleOf("keep", new ItemRule(DIAMONDS, false, fixed(100)));
		List<TakePlan> plans = RuleApplicator.planTake(info, List.of(rule), snap(slots), RuleApplicator.TakeMode.ALL);
		assertEquals(1, plans.size());
		assertEquals(32, plans.get(0).amount()); // ALL 无视 keep 规则
	}

	@Test
	void takeSkipsNonParticipatingSlots() {
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(32));
		Map<Integer, SlotInfo> infos = Map.of(0, new SlotInfo(SlotRole.MACHINE_OUTPUT, false, false));
		ContainerSnapshot snapshot = new ContainerSnapshot(slots, infos, "furnace", 3);
		ContainerInfo info = new ContainerInfo(IOType.INPUT);
		List<TakePlan> plans = RuleApplicator.planTake(info, List.of(), snapshot, RuleApplicator.TakeMode.RULES);
		assertTrue(plans.isEmpty());
	}

	// ---- planPut ----

	@Test
	void putBlacklistNoRulesAcceptsEverything() {
		ContainerInfo info = new ContainerInfo(IOType.INPUT);
		Map<ItemStack, Integer> inv = new HashMap<>();
		inv.put(diamond(1), 30);
		List<PutDemand> demands = RuleApplicator.planPut(info, List.of(), snap(Map.of()), inv);
		assertEquals(1, demands.size());
		assertEquals(30, demands.get(0).amount());
		assertTrue(demands.get(0).sample().is(Items.DIAMOND));
	}

	@Test
	void putCountRuleComputesDelta() {
		ContainerInfo info = new ContainerInfo(IOType.OUTPUT);
		ContainerRule rule = ruleOf("g", new ItemRule(DIAMONDS, false, fixed(128)));
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(30));
		Map<ItemStack, Integer> inv = new HashMap<>();
		inv.put(diamond(1), 200); // min(128-30, 200)=98
		List<PutDemand> demands = RuleApplicator.planPut(info, List.of(rule), snap(slots), inv);
		assertEquals(1, demands.size());
		assertEquals(98, demands.get(0).amount());
	}

	@Test
	void putWhitelistUnmatchedNoDemand() {
		ContainerInfo info = new ContainerInfo(IOType.OUTPUT);
		Map<ItemStack, Integer> inv = new HashMap<>();
		inv.put(dirt(1), 64); // 白名单未命中 → 无需求
		List<PutDemand> demands = RuleApplicator.planPut(info, List.of(), snap(Map.of()), inv);
		assertTrue(demands.isEmpty());
	}

	@Test
	void putWhitelistMatchedUnlimitedDemandsAll() {
		ContainerInfo info = new ContainerInfo(IOType.OUTPUT);
		ContainerRule rule = ruleOf("any-diamond", new ItemRule(DIAMONDS, false, null)); // 不限量
		Map<ItemStack, Integer> inv = new HashMap<>();
		inv.put(diamond(1), 42);
		List<PutDemand> demands = RuleApplicator.planPut(info, List.of(rule), snap(Map.of()), inv);
		assertEquals(1, demands.size());
		assertEquals(42, demands.get(0).amount());
	}

	@Test
	void putOverstockedNoDemand() {
		ContainerInfo info = new ContainerInfo(IOType.OUTPUT);
		ContainerRule rule = ruleOf("g", new ItemRule(DIAMONDS, false, fixed(64)));
		Map<Integer, ItemStack> slots = new HashMap<>();
		slots.put(0, diamond(70)); // 已超目标
		Map<ItemStack, Integer> inv = new HashMap<>();
		inv.put(diamond(1), 10);
		List<PutDemand> demands = RuleApplicator.planPut(info, List.of(rule), snap(slots), inv);
		assertTrue(demands.isEmpty());
	}
}

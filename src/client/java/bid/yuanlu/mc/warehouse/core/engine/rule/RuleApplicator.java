package bid.yuanlu.mc.warehouse.core.engine.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;

/**
 * 规则引擎（PDD §3.7）：把「容器规则×快照」翻译成取/放计划。纯函数、无副作用，
 * 由传输引擎在滚动模拟中反复调用（§5.3）；每轮会话执行后用重扫结果再算一轮。
 */
public final class RuleApplicator {

	/** 取出模式：RULES=按规则目标量；ALL=忽略规则全取（TEMP 保守策略，D1） */
	public enum TakeMode {
		RULES, ALL
	}

	/**
	 * 取出计划：容器内应被取走的逐槽位数量。
	 * <p>
	 * 只对参与判定槽位（canTakeFrom）派发取出；同物品按槽位升序排空直到
	 * delta 用尽（delta = current - target，§3.7）。
	 */
	public static List<TakePlan> planTake(ContainerInfo info, List<ContainerRule> rules,
			ContainerSnapshot snapshot, TakeMode mode) {
		var ctx = RuleModeContext.fromSnapshot(info.effectiveRuleMode(), snapshot);
		List<TakePlan> plans = new ArrayList<>();
		// 目标量：物品 → target；先用聚合结果计算 delta，再按槽位排水
		Map<ItemStack, Integer> currentByItem = QuantityCalculator.aggregateByItem(snapshot);

		Map<ItemStack, Integer> takeRemainder = new java.util.IdentityHashMap<>();
		for (Map.Entry<ItemStack, Integer> e : currentByItem.entrySet()) {
			ItemStack sample = e.getKey();
			int current = e.getValue();
			int target;
			if (mode == TakeMode.ALL) {
				target = 0; // 全取
			} else {
				// negative 求反后命中即为合法匹配，其数量规则照常生效（§3.7）
				ItemRule matched = ItemMatcher.firstMatch(rules, sample);
				target = QuantityCalculator.targetAmount(ctx, matched, current, sample.getMaxStackSize());
				if (target == QuantityCalculator.UNLIMITED) target = 0; // ∞=取空
			}
			int delta = current - target;
			if (delta > 0) takeRemainder.put(sample, delta);
		}
		if (takeRemainder.isEmpty()) return plans;

		// 槽位升序排空
		for (int slot : new TreeMap<>(snapshot.slots()).keySet()) {
			ItemStack stack = snapshot.slots().get(slot);
			if (stack.isEmpty()) continue;
			SlotInfo slotInfo = snapshot.slotInfo(slot);
			if (!slotInfo.canTakeFrom()) continue;

			for (Map.Entry<ItemStack, Integer> e : takeRemainder.entrySet()) {
				if (!ItemStack.isSameItemSameComponents(e.getKey(), stack)) continue;
				int need = e.getValue();
				if (need <= 0) continue;
				int take = Math.min(need, stack.getCount());
				plans.add(new TakePlan(slot, stack, take));
				e.setValue(need - take);
				break;
			}
		}
		return plans;
	}

	/**
	 * 放入需求：给定背包侧聚合内容（物品→总量），返回应放入该容器的需求列表。
	 * <p>
	 * WHITELIST 未命中物品自然产生 target=0 → 无需求；BLACKLIST 未命中=∞ →
	 * 全部可放入。落位与容量裁剪由 SlotAllocator 完成。
	 */
	public static List<PutDemand> planPut(ContainerInfo info, List<ContainerRule> rules,
			ContainerSnapshot snapshot, Map<ItemStack, Integer> inventoryItems) {
		var ctx = RuleModeContext.fromSnapshot(info.effectiveRuleMode(), snapshot);
		Map<ItemStack, Integer> currentByItem = QuantityCalculator.aggregateByItem(snapshot);
		List<PutDemand> demands = new ArrayList<>();
		for (Map.Entry<ItemStack, Integer> e : inventoryItems.entrySet()) {
			ItemStack sample = e.getKey();
			int available = e.getValue();
			if (available <= 0) continue;
			ItemRule matched = ItemMatcher.firstMatch(rules, sample);
			int current = findCurrent(currentByItem, sample);
			int target = QuantityCalculator.targetAmount(ctx, matched, current, sample.getMaxStackSize());
			if (target == QuantityCalculator.UNLIMITED) target = Integer.MAX_VALUE;
			long delta = (long) target - current;
			if (delta <= 0) continue;
			demands.add(new PutDemand(sample, (int) Math.min(delta, available)));
		}
		return demands;
	}

	private static int findCurrent(Map<ItemStack, Integer> byItem, ItemStack sample) {
		for (Map.Entry<ItemStack, Integer> e : byItem.entrySet()) {
			if (ItemStack.isSameItemSameComponents(e.getKey(), sample)) return e.getValue();
		}
		return 0;
	}

	private RuleApplicator() {
	}
}

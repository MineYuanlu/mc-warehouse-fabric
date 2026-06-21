package bid.yuanlu.mcwarehouse.engine.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerInfo.RuleMode;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.model.rule.ItemRule;
import bid.yuanlu.mcwarehouse.model.rule.ItemRules;
import net.minecraft.world.item.ItemStack;

public class RuleApplicator {

	public static class TransferPlan {

		public final List<ItemMove> moves;
		public final Direction direction;

		public TransferPlan(List<ItemMove> moves, Direction direction) {
			this.moves = moves;
			this.direction = direction;
		}

		public enum Direction {
			TO_CONTAINER,
			TO_PLAYER
		}

		public static class ItemMove {

			public final int slotIndex;
			public final ItemStack item;
			public final int amount;
			public final int targetSlot;

			public ItemMove(int slotIndex, ItemStack item, int amount, int targetSlot) {
				this.slotIndex = slotIndex;
				this.item = item;
				this.amount = amount;
				this.targetSlot = targetSlot;
			}
		}
	}

	public static TransferPlan calculatePlan(ContainerInfo info, ContainerSnapshot snapshot,
			Map<String, ItemRules> rulesMap, ContainerSnapshot playerInventory) {
		if (info.type == ContainerType.IGNORE) return null;

		RuleMode mode = info.ruleMode != null ? info.ruleMode : ContainerInfo.defaultMode(info.type);

		List<ItemRule> allRules = new ArrayList<>();
		if (info.rulesNames != null) {
			for (String name : info.rulesNames) {
				ItemRules group = rulesMap.get(name);
				if (group != null && group.rules != null) {
					allRules.addAll(group.rules);
				}
			}
		}

		return switch (info.type) {
			case INPUT -> planInput(snapshot, allRules, mode);
			case OUTPUT -> planOutput(snapshot, allRules, playerInventory, mode);
			case RELAY -> planRelay(snapshot, allRules, playerInventory, mode);
			default -> null;
		};
	}

	private static TransferPlan planInput(ContainerSnapshot snapshot, List<ItemRule> allRules, RuleMode mode) {
		List<TransferPlan.ItemMove> moves = new ArrayList<>();
		if (snapshot.slots.isEmpty()) return null;

		Map<ItemStack, Integer> typeCounts = new HashMap<>();
		Map<ItemStack, ItemRule> typeRule = new HashMap<>();

		for (ItemStack stack : snapshot.slots.values()) {
			if (stack.isEmpty()) continue;
			typeCounts.merge(stack, stack.getCount(), Integer::sum);
			if (!typeRule.containsKey(stack)) {
				typeRule.put(stack, findFirstMatch(allRules, stack));
			}
		}

		Map<ItemStack, Integer> removalCounts = new HashMap<>();

		for (var entry : snapshot.slots.entrySet()) {
			int slotIndex = entry.getKey();
			ItemStack stack = entry.getValue();
			if (stack.isEmpty()) continue;

			ItemRule rule = typeRule.get(stack);

			if (rule == null) {
				if (mode == RuleMode.BLACKLIST) {
					moves.add(new TransferPlan.ItemMove(slotIndex, stack, stack.getCount(), -1));
				}
			} else {
				int total = typeCounts.get(stack);
				int maxStack = stack.getMaxStackSize();
				int target = QuantityCalculator.computeTarget(rule.quantifier, total, snapshot.containerSize, maxStack);
				if (total > target) {
					int excess = total - target;
					int removed = removalCounts.getOrDefault(stack, 0);
					int toRemove = Math.min(stack.getCount(), excess - removed);
					if (toRemove > 0) {
						moves.add(new TransferPlan.ItemMove(slotIndex, stack, toRemove, -1));
						removalCounts.put(stack, removed + toRemove);
					}
				}
			}
		}

		return moves.isEmpty() ? null : new TransferPlan(moves, TransferPlan.Direction.TO_PLAYER);
	}

	private static TransferPlan planOutput(ContainerSnapshot snapshot, List<ItemRule> allRules,
			ContainerSnapshot playerInventory, RuleMode mode) {
		if (allRules.isEmpty() || playerInventory == null) return null;

		List<TransferPlan.ItemMove> moves = new ArrayList<>();

		Map<ItemStack, Integer> containerCounts = new HashMap<>();
		for (ItemStack stack : snapshot.slots.values()) {
			if (stack.isEmpty()) continue;
			containerCounts.merge(stack, stack.getCount(), Integer::sum);
		}

		for (ItemRule rule : allRules) {
			int currentCount = 0;
			int maxStack = 64;
			boolean foundMatch = false;

			for (var entry : containerCounts.entrySet()) {
				ItemStack stack = entry.getKey();
				if (!ItemMatcher.matches(rule, stack)) continue;
				currentCount += entry.getValue();
				maxStack = stack.getMaxStackSize();
				foundMatch = true;
			}

			if (!foundMatch) {
				for (ItemStack stack : playerInventory.slots.values()) {
					if (stack.isEmpty()) continue;
					if (!ItemMatcher.matches(rule, stack)) continue;
					maxStack = stack.getMaxStackSize();
					break;
				}
			}

			int target = QuantityCalculator.computeTarget(rule.quantifier, currentCount,
					snapshot.containerSize, maxStack);
			int needed = target - currentCount;
			if (needed <= 0) continue;

			for (var entry : playerInventory.slots.entrySet()) {
				int playerSlot = entry.getKey();
				ItemStack playerStack = entry.getValue();
				if (playerStack.isEmpty()) continue;
				if (!ItemMatcher.matches(rule, playerStack)) continue;

				int toTake = Math.min(playerStack.getCount(), needed);
				moves.add(new TransferPlan.ItemMove(playerSlot, playerStack, toTake, -1));
				needed -= toTake;
				if (needed <= 0) break;
			}
		}

		return moves.isEmpty() ? null : new TransferPlan(moves, TransferPlan.Direction.TO_CONTAINER);
	}

	private static TransferPlan planRelay(ContainerSnapshot snapshot, List<ItemRule> allRules,
			ContainerSnapshot playerInventory, RuleMode mode) {
		List<TransferPlan.ItemMove> allMoves = new ArrayList<>();

		TransferPlan remove = planInput(snapshot, allRules, mode);
		if (remove != null) allMoves.addAll(remove.moves);

		TransferPlan add = planOutput(snapshot, allRules, playerInventory, mode);
		if (add != null) allMoves.addAll(add.moves);

		return allMoves.isEmpty() ? null : new TransferPlan(allMoves, TransferPlan.Direction.TO_PLAYER);
	}

	private static ItemRule findFirstMatch(List<ItemRule> rules, ItemStack stack) {
		for (ItemRule rule : rules) {
			if (ItemMatcher.matches(rule, stack)) {
				return rule;
			}
		}
		return null;
	}
}

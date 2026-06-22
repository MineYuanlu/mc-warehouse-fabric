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

		public TransferPlan(List<ItemMove> moves) {
			this.moves = moves;
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
			public final Direction direction;

			public ItemMove(int slotIndex, ItemStack item, int amount, int targetSlot, Direction direction) {
				this.slotIndex = slotIndex;
				this.item = item;
				this.amount = amount;
				this.targetSlot = targetSlot;
				this.direction = direction;
			}
		}
	}

	public static TransferPlan calculatePlan(ContainerInfo info, ContainerSnapshot snapshot,
			Map<String, ItemRules> rulesMap, ContainerSnapshot playerInventory) {
		return calculatePlan(info, snapshot, rulesMap, playerInventory, false, null);
	}

	public static TransferPlan calculatePlan(ContainerInfo info, ContainerSnapshot snapshot,
			Map<String, ItemRules> rulesMap, ContainerSnapshot playerInventory,
			boolean hasUnexploredOutput, List<ItemRule> allOutputRules) {
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
			case TEMP -> planTemp(snapshot, allRules, playerInventory, mode, hasUnexploredOutput, allOutputRules);
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
					moves.add(new TransferPlan.ItemMove(slotIndex, stack, stack.getCount(), -1, TransferPlan.Direction.TO_PLAYER));
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
						moves.add(new TransferPlan.ItemMove(slotIndex, stack, toRemove, -1, TransferPlan.Direction.TO_PLAYER));
						removalCounts.put(stack, removed + toRemove);
					}
				}
			}
		}

		return moves.isEmpty() ? null : new TransferPlan(moves);
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
				moves.add(new TransferPlan.ItemMove(playerSlot, playerStack, toTake, -1, TransferPlan.Direction.TO_CONTAINER));
				needed -= toTake;
				if (needed <= 0) break;
			}
		}

		return moves.isEmpty() ? null : new TransferPlan(moves);
	}

	private static TransferPlan planTemp(ContainerSnapshot snapshot, List<ItemRule> tempRules,
			ContainerSnapshot playerInventory, RuleMode mode,
			boolean hasUnexploredOutput, List<ItemRule> allOutputRules) {
		List<TransferPlan.ItemMove> allMoves = new ArrayList<>();

		if (hasUnexploredOutput) {
			TransferPlan remove = planInput(snapshot, tempRules, mode);
			if (remove != null) allMoves.addAll(remove.moves);
		} else if (allOutputRules != null && !allOutputRules.isEmpty()) {
			List<TransferPlan.ItemMove> selective = calcTempRemoveSelective(snapshot, tempRules, mode, allOutputRules);
			allMoves.addAll(selective);
		}

		if (allOutputRules != null && !allOutputRules.isEmpty()) {
			List<TransferPlan.ItemMove> adds = calcTempAddSelective(snapshot, tempRules, playerInventory, mode, allOutputRules);
			allMoves.addAll(adds);
		}

		return allMoves.isEmpty() ? null : new TransferPlan(allMoves);
	}

	private static List<TransferPlan.ItemMove> calcTempRemoveSelective(
			ContainerSnapshot snapshot, List<ItemRule> tempRules, RuleMode mode,
			List<ItemRule> allOutputRules) {
		List<TransferPlan.ItemMove> moves = new ArrayList<>();

		Map<ItemStack, Integer> typeCounts = new HashMap<>();
		for (ItemStack stack : snapshot.slots.values()) {
			if (stack.isEmpty()) continue;
			typeCounts.merge(stack, stack.getCount(), Integer::sum);
		}

		Map<ItemStack, Integer> removalCounts = new HashMap<>();

		for (var entry : snapshot.slots.entrySet()) {
			int slotIndex = entry.getKey();
			ItemStack stack = entry.getValue();
			if (stack.isEmpty()) continue;

			boolean matchesAnyOutput = false;
			for (ItemRule outputRule : allOutputRules) {
				if (ItemMatcher.matches(outputRule, stack)) {
					matchesAnyOutput = true;
					break;
				}
			}

			if (matchesAnyOutput) {
				ItemRule tempRule = findFirstMatch(tempRules, stack);
				if (tempRule != null) {
					int total = typeCounts.get(stack);
					int maxStack = stack.getMaxStackSize();
					int target = QuantityCalculator.computeTarget(tempRule.quantifier, total, snapshot.containerSize, maxStack);
					if (total > target) {
						int excess = total - target;
						int removed = removalCounts.getOrDefault(stack, 0);
						int toRemove = Math.min(stack.getCount(), excess - removed);
						if (toRemove > 0) {
							moves.add(new TransferPlan.ItemMove(slotIndex, stack, toRemove, -1, TransferPlan.Direction.TO_PLAYER));
							removalCounts.put(stack, removed + toRemove);
						}
					}
				} else if (mode == RuleMode.BLACKLIST) {
					moves.add(new TransferPlan.ItemMove(slotIndex, stack, stack.getCount(), -1, TransferPlan.Direction.TO_PLAYER));
				}
			}
		}

		return moves;
	}

	private static List<TransferPlan.ItemMove> calcTempAddSelective(
			ContainerSnapshot snapshot, List<ItemRule> tempRules, ContainerSnapshot playerInventory,
			RuleMode mode, List<ItemRule> allOutputRules) {
		List<TransferPlan.ItemMove> moves = new ArrayList<>();
		if (playerInventory == null) return moves;

		for (var entry : playerInventory.slots.entrySet()) {
			int playerSlot = entry.getKey();
			ItemStack stack = entry.getValue();
			if (stack.isEmpty()) continue;

			boolean matchesAnyOutput = false;
			for (ItemRule outputRule : allOutputRules) {
				if (ItemMatcher.matches(outputRule, stack)) {
					matchesAnyOutput = true;
					break;
				}
			}

			if (matchesAnyOutput) continue;

			ItemRule tempRule = findFirstMatch(tempRules, stack);
			if (tempRule != null) {
				moves.add(new TransferPlan.ItemMove(playerSlot, stack, stack.getCount(), -1, TransferPlan.Direction.TO_CONTAINER));
			} else if (mode == RuleMode.BLACKLIST) {
				moves.add(new TransferPlan.ItemMove(playerSlot, stack, stack.getCount(), -1, TransferPlan.Direction.TO_CONTAINER));
			}
		}

		return moves;
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

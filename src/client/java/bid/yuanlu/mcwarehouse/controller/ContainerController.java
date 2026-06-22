package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mcwarehouse.engine.container.ContainerInteractor;
import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator;
import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator.TransferPlan;
import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerMemory;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.model.rule.ItemRule;
import bid.yuanlu.mcwarehouse.model.rule.ItemRules;
import bid.yuanlu.mcwarehouse.storage.WarehouseStorage;
import bid.yuanlu.mcwarehouse.storage.WorldConfigStorage;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class ContainerController {

	private static final ContainerController INSTANCE = new ContainerController();

	private final ContainerMemory memory;
	private final WarehouseStorage storage;
	private BlockPos lastInteractionPos;

	public static ContainerController getInstance() {
		return INSTANCE;
	}

	private ContainerController() {
		this.memory = new ContainerMemory();
		this.storage = new WarehouseStorage();
	}

	public ContainerSnapshot getMemory(BlockPos pos) {
		return memory.get(pos);
	}

	public void clearMemory() {
		memory.clear();
	}

	public void clearMemory(BlockPos pos) {
		memory.clear(pos);
	}

	public void snapshotMemory(BlockPos pos, ContainerSnapshot snapshot) {
		memory.snapshot(pos, snapshot);
	}

	public void setLastInteractionPos(BlockPos pos) {
		this.lastInteractionPos = pos;
	}

	public ContainerSnapshot captureCurrentScreen() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof AbstractContainerScreen<?> screen) {
			return new ContainerSnapshot(screen);
		}
		return null;
	}

	public void onScreenClosed() {
		ContainerSnapshot snapshot = captureCurrentScreen();
		if (snapshot == null) return;
		if (lastInteractionPos != null) {
			memory.snapshot(lastInteractionPos, snapshot);
		}
	}

	public boolean executeTransfer(ContainerInfo info, BlockPos absolutePos, String warehouseName) {
		Warehouse warehouse = storage.loadWarehouse(warehouseName);
		if (warehouse == null) return false;

		ContainerSnapshot snapshot = memory.get(absolutePos);
		if (snapshot == null) {
			snapshot = captureCurrentScreen();
			if (snapshot == null) return false;
		}

		List<ItemRules> ruleSets = new ArrayList<>();
		if (info.rulesNames != null && warehouse.rules != null) {
			for (String name : info.rulesNames) {
				ItemRules r = warehouse.rules.get(name);
				if (r != null) ruleSets.add(r);
			}
		}

		int speed = WorldConfigStorage.getInstance().getInteractionSpeed();
		ContainerSnapshot playerInv = new ContainerInteractor(speed).capturePlayerInventory();
		TransferPlan plan = RuleApplicator.calculatePlan(info, snapshot, warehouse.rules, playerInv);

		if (plan != null) {
			new ContainerInteractor(speed).execute(plan);
		}

		boolean changed = false;
		if (info.type == ContainerType.INPUT || info.type == ContainerType.TEMP) {
			changed = processInput(snapshot, info.ruleMode, ruleSets);
		}
		if (info.type == ContainerType.OUTPUT || info.type == ContainerType.TEMP) {
			changed = processOutput(snapshot, info.ruleMode, ruleSets) || changed;
		}

		if (changed || plan != null) {
			memory.snapshot(absolutePos, snapshot);
		}
		return true;
	}

	private boolean processInput(ContainerSnapshot snapshot, ContainerInfo.RuleMode mode, List<ItemRules> ruleSets) {
		boolean changed = false;
		Iterator<Map.Entry<Integer, ItemStack>> iter = snapshot.slots.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Integer, ItemStack> entry = iter.next();
			ItemStack stack = entry.getValue();
			if (stack.isEmpty()) continue;
			if (shouldRemoveFromInput(stack, mode, ruleSets)) {
				iter.remove();
				changed = true;
			}
		}
		return changed;
	}

	private boolean shouldRemoveFromInput(ItemStack stack, ContainerInfo.RuleMode mode, List<ItemRules> ruleSets) {
		boolean anyMatch = false;
		for (ItemRules rules : ruleSets) {
			if (rules.rules == null) continue;
			for (ItemRule rule : rules.rules) {
				if (rule.selector != null && rule.selector.matches(stack)) {
					anyMatch = !rule.negate;
				}
			}
		}
		if (mode == ContainerInfo.RuleMode.WHITELIST) {
			return !anyMatch;
		} else {
			return anyMatch;
		}
	}

	private boolean processOutput(ContainerSnapshot snapshot, ContainerInfo.RuleMode mode, List<ItemRules> ruleSets) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;

		boolean changed = false;
		for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
			ItemStack stack = mc.player.getInventory().getItem(i);
			if (stack.isEmpty()) continue;
			if (shouldAddToOutput(stack, mode, ruleSets)) {
				mc.player.getInventory().setItem(i, ItemStack.EMPTY);
				changed = true;
			}
		}
		return changed;
	}

	private boolean shouldAddToOutput(ItemStack stack, ContainerInfo.RuleMode mode, List<ItemRules> ruleSets) {
		boolean anyMatch = false;
		for (ItemRules rules : ruleSets) {
			if (rules.rules == null) continue;
			for (ItemRule rule : rules.rules) {
				if (rule.selector != null && rule.selector.matches(stack)) {
					anyMatch = !rule.negate;
				}
			}
		}
		if (mode == ContainerInfo.RuleMode.WHITELIST) {
			return anyMatch;
		} else {
			return !anyMatch;
		}
	}

	// === Query helpers for PathfindingController ===

	public boolean hasUnexploredOutput(Warehouse w) {
		if (w == null || w.containers == null) return false;
		for (ContainerInfo c : w.containers) {
			if (c.type == ContainerType.OUTPUT && memory.get(CoordinateUtils.toAbsolute(c.relativePos, w.anchor)) == null) {
				return true;
			}
		}
		return false;
	}

	public List<ItemRule> collectAllOutputRules(Warehouse w) {
		List<ItemRule> allRules = new ArrayList<>();
		if (w == null || w.containers == null || w.rules == null) return allRules;
		for (ContainerInfo c : w.containers) {
			if (c.type != ContainerType.OUTPUT) continue;
			if (c.rulesNames != null) {
				for (String name : c.rulesNames) {
					ItemRules group = w.rules.get(name);
					if (group != null && group.rules != null) {
						allRules.addAll(group.rules);
					}
				}
			}
		}
		return allRules;
	}

	public boolean hasAnyOutputSpace(Warehouse w, ContainerSnapshot playerInv) {
		if (w == null || w.containers == null) return false;
		for (ContainerInfo c : w.containers) {
			if (c.type != ContainerType.OUTPUT) continue;
			BlockPos abs = CoordinateUtils.toAbsolute(c.relativePos, w.anchor);
			ContainerSnapshot mem = getMemory(abs);
			if (mem == null) return true;
			TransferPlan plan = RuleApplicator.calculatePlan(c, mem, w.rules, playerInv);
			if (plan != null && !plan.moves.isEmpty()) return true;
		}
		return false;
	}

	public boolean hasAnyTempSpace(Warehouse w, ContainerSnapshot playerInv) {
		if (w == null || w.containers == null) return false;
		boolean hasUnexplored = hasUnexploredOutput(w);
		List<ItemRule> allOutputRules = collectAllOutputRules(w);
		for (ContainerInfo c : w.containers) {
			if (c.type != ContainerType.TEMP) continue;
			BlockPos abs = CoordinateUtils.toAbsolute(c.relativePos, w.anchor);
			ContainerSnapshot mem = getMemory(abs);
			if (mem == null) return true;
			TransferPlan plan = RuleApplicator.calculatePlan(c, mem, w.rules, playerInv, hasUnexplored, allOutputRules);
			if (plan != null && !plan.moves.isEmpty()) return true;
		}
		return false;
	}

	public boolean isInventoryFull(ContainerSnapshot playerInv) {
		if (playerInv == null) return true;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = playerInv.slots.get(i);
			if (stack == null || stack.isEmpty()) return false;
			if (stack.getCount() < stack.getMaxStackSize()) return false;
		}
		return true;
	}

	public boolean isOutputFullySatisfied(Warehouse w) {
		if (w == null || w.containers == null) return true;
		ContainerSnapshot playerInv = new ContainerInteractor(2).capturePlayerInventory();
		for (ContainerInfo c : w.containers) {
			if (c.type != ContainerType.OUTPUT) continue;
			BlockPos abs = CoordinateUtils.toAbsolute(c.relativePos, w.anchor);
			ContainerSnapshot mem = getMemory(abs);
			if (mem == null) return false;
			TransferPlan plan = RuleApplicator.calculatePlan(c, mem, w.rules, playerInv);
			if (plan != null && !plan.moves.isEmpty()) return false;
		}
		return true;
	}

	public boolean isTempFullyEmpty(Warehouse w) {
		if (w == null || w.containers == null) return true;
		boolean hasUnexplored = hasUnexploredOutput(w);
		List<ItemRule> allOutputRules = collectAllOutputRules(w);
		ContainerSnapshot playerInv = new ContainerInteractor(2).capturePlayerInventory();
		for (ContainerInfo c : w.containers) {
			if (c.type != ContainerType.TEMP) continue;
			BlockPos abs = CoordinateUtils.toAbsolute(c.relativePos, w.anchor);
			ContainerSnapshot mem = getMemory(abs);
			if (mem == null) return false;
			TransferPlan plan = RuleApplicator.calculatePlan(c, mem, w.rules, playerInv, hasUnexplored, allOutputRules);
			if (plan != null && !plan.moves.isEmpty()) return false;
		}
		return true;
	}

	public boolean isInventoryEmpty() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return true;
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (!inv.getItem(i).isEmpty()) return false;
		}
		return true;
	}

	public boolean isInputFullyEmpty(Warehouse w) {
		if (w == null || w.containers == null) return true;
		ContainerSnapshot playerInv = new ContainerInteractor(2).capturePlayerInventory();
		for (ContainerInfo c : w.containers) {
			if (c.type != ContainerType.INPUT) continue;
			BlockPos abs = CoordinateUtils.toAbsolute(c.relativePos, w.anchor);
			ContainerSnapshot mem = getMemory(abs);
			if (mem == null) return false;
			TransferPlan plan = RuleApplicator.calculatePlan(c, mem, w.rules, playerInv);
			if (plan != null && !plan.moves.isEmpty()) return false;
		}
		return true;
	}
}

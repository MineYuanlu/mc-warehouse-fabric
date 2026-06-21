package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerMemory;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.model.rule.ItemRule;
import bid.yuanlu.mcwarehouse.model.rule.ItemRules;
import bid.yuanlu.mcwarehouse.storage.WarehouseStorage;

public class ContainerController {

	private static final ContainerController INSTANCE = new ContainerController();

	private final ContainerMemory memory;
	private final WarehouseStorage storage;

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

	public ContainerSnapshot captureCurrentScreen() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof AbstractContainerScreen<?> screen) {
			return new ContainerSnapshot(screen);
		}
		return null;
	}

	public boolean executeTransfer(ContainerInfo info, BlockPos absolutePos, String warehouseName) {
		Warehouse warehouse = storage.loadWarehouse(warehouseName);
		if (warehouse == null) {
			return false;
		}
		ContainerSnapshot snapshot = memory.get(absolutePos);
		if (snapshot == null) {
			snapshot = captureCurrentScreen();
			if (snapshot == null) {
				return false;
			}
		}
		List<ItemRules> ruleSets = new ArrayList<>();
		if (info.rulesNames != null && warehouse.rules != null) {
			for (String name : info.rulesNames) {
				ItemRules r = warehouse.rules.get(name);
				if (r != null) {
					ruleSets.add(r);
				}
			}
		}
		boolean changed = false;
		if (info.type == ContainerType.INPUT || info.type == ContainerType.RELAY) {
			changed = processInput(snapshot, info.ruleMode, ruleSets);
		}
		if (info.type == ContainerType.OUTPUT || info.type == ContainerType.RELAY) {
			changed = processOutput(snapshot, info.ruleMode, ruleSets) || changed;
		}
		if (changed) {
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
			if (stack.isEmpty()) {
				continue;
			}
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
			if (rules.rules == null) {
				continue;
			}
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
		if (mc.player == null) {
			return false;
		}
		boolean changed = false;
		for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
			ItemStack stack = mc.player.getInventory().getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
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
			if (rules.rules == null) {
				continue;
			}
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
}

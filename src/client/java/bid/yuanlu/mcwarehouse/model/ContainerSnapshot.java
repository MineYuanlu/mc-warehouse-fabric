package bid.yuanlu.mcwarehouse.model;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

public class ContainerSnapshot {

	public final Map<Integer, ItemStack> slots;
	public final int containerSize;

	public ContainerSnapshot(AbstractContainerScreen<?> screen) {
		var menu = screen.getMenu();
		var allSlots = menu.slots;
		this.containerSize = allSlots.size();
		this.slots = new HashMap<>();
		for (int i = 0; i < this.containerSize; i++) {
			ItemStack stack = allSlots.get(i).getItem();
			if (!stack.isEmpty()) {
				this.slots.put(i, stack.copy());
			}
		}
	}

	public ContainerSnapshot(Map<Integer, ItemStack> slots, int containerSize) {
		this.slots = new HashMap<>(slots);
		this.containerSize = containerSize;
	}
}

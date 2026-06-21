package bid.yuanlu.mcwarehouse.engine.container;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator.TransferPlan;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;

public class ContainerInteractor {

	private final int speed;

	public ContainerInteractor(int speed) {
		this.speed = speed;
	}

	public int getSpeed() {
		return this.speed;
	}

	public void execute(TransferPlan plan) {
		// Stub — will be driven by ContainerScreenMixin
	}

	public ContainerSnapshot captureCurrentScreen() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof AbstractContainerScreen<?> screen) {
			return new ContainerSnapshot(screen);
		}
		return null;
	}

	public ContainerSnapshot capturePlayerInventory() {
		var player = Minecraft.getInstance().player;
		if (player == null) return null;

		var inv = player.getInventory();
		Map<Integer, ItemStack> slots = new HashMap<>();
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack stack = inv.items.get(i);
			if (!stack.isEmpty()) {
				slots.put(i, stack.copy());
			}
		}
		return new ContainerSnapshot(slots, inv.items.size());
	}
}

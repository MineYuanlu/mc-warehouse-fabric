package bid.yuanlu.mcwarehouse.engine.container;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator.TransferPlan;
import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator.TransferPlan.ItemMove;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;

public class ContainerInteractor {

	public enum State { IDLE, WORKING, COMPLETED }

	private final int speed;
	private State state;
	private TransferPlan currentPlan;
	private int moveIndex;
	private int tickTimer;

	public ContainerInteractor(int speed) {
		this.speed = speed;
		resetState();
	}

	public int getSpeed() {
		return this.speed;
	}

	public State getState() {
		return state;
	}

	public boolean isCompleted() {
		return state == State.COMPLETED;
	}

	public void startExecution(TransferPlan plan) {
		this.currentPlan = plan;
		this.moveIndex = 0;
		this.tickTimer = 0;
		this.state = State.WORKING;
	}

	public State tick() {
		if (state != State.WORKING) return state;

		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || mc.player == null) {
			state = State.COMPLETED;
			return state;
		}

		tickTimer++;
		if (tickTimer < speed) return state;
		tickTimer = 0;

		if (currentPlan == null || moveIndex >= currentPlan.moves.size()) {
			state = State.COMPLETED;
			return state;
		}

		var menu = screen.getMenu();
		var player = mc.player;
		var playerInv = player.getInventory();

		int containerSlots = 0;
		for (Slot s : menu.slots) {
			if (s.container == playerInv) break;
			containerSlots++;
		}

		ItemMove move = currentPlan.moves.get(moveIndex);
		moveIndex++;

		int menuSlot = mapSlot(move, containerSlots);
		if (menuSlot >= 0 && menuSlot < menu.slots.size() && !menu.slots.get(menuSlot).getItem().isEmpty()) {
			menu.clicked(menuSlot, 0, ContainerInput.QUICK_MOVE, player);
		}

		return state;
	}

	public void execute(TransferPlan plan) {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
		if (mc.player == null) return;

		var menu = screen.getMenu();
		var player = mc.player;
		var playerInv = player.getInventory();

		int containerSlots = 0;
		for (Slot s : menu.slots) {
			if (s.container == playerInv) break;
			containerSlots++;
		}

		for (ItemMove move : plan.moves) {
			int menuSlot = mapSlot(move, containerSlots);
			if (menuSlot < 0 || menuSlot >= menu.slots.size()) continue;
			if (menu.slots.get(menuSlot).getItem().isEmpty()) continue;

			menu.clicked(menuSlot, 0, ContainerInput.QUICK_MOVE, player);
		}
	}

	public void resetState() {
		this.state = State.IDLE;
		this.currentPlan = null;
		this.moveIndex = 0;
		this.tickTimer = 0;
	}

	private static int mapSlot(ItemMove move, int containerSlots) {
		if (move.direction == TransferPlan.Direction.TO_CONTAINER) {
			if (move.slotIndex < 9) {
				return containerSlots + 27 + move.slotIndex;
			} else {
				return containerSlots + (move.slotIndex - 9);
			}
		} else {
			return move.slotIndex;
		}
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
		int size = inv.getContainerSize();
		for (int i = 0; i < size; i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty()) {
				slots.put(i, stack.copy());
			}
		}
		return new ContainerSnapshot(slots, size);
	}

	public static void onScreenClosed() {
		Minecraft mc = Minecraft.getInstance();
		var ctrl = bid.yuanlu.mcwarehouse.controller.ContainerController.getInstance();
		if (mc.player != null) {
			ctrl.onScreenClosed();
		}
	}
}

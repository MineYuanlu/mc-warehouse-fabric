package bid.yuanlu.mc.warehouse.impl.container;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;

import bid.yuanlu.mc.warehouse.api.interaction.ContainerHandle;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;

/**
 * 原版 GUI 交互实现（PDD §8.3）：useItemOn 打开 + menu.clicked 实现全部原语。
 * <p>
 * 所有原语的 slot 参数均为 <b>Menu 槽位索引</b>（含玩家背包区）；
 * 仅「发起」动作，成败由协议层逐击对账判定（§6.2）。每次调用前校验会话仍绑定当前界面。
 */
public final class VanillaGuiInteraction implements ContainerInteraction {

	public static final String ID = "vanilla_gui";

	@Override
	public String id() {
		return ID;
	}

	// ---- 打开 / 关闭 ----

	@Override
	public void requestOpen(ContainerHandle handle) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || mc.level == null) return;
		BlockPos pos = handle.pos().toBlockPos();

		// 命中点取方块中心；服务端按 eye→hit 距离校验触及距离
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 eye = player.getEyePosition();
		Direction face = nearestFace(eye, center);
		BlockHitResult hit = new BlockHitResult(center, face, pos, false);
		mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
	}

	private static Direction nearestFace(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		if (Math.abs(dy) >= Math.abs(dz)) {
			return dy > 0 ? Direction.UP : Direction.DOWN;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	@Override
	public void requestClose(ContainerHandle handle) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.closeContainer();
		}
	}

	// ---- 快速路径（语义封装，§8.3）----

	@Override
	public boolean quickMoveToPlayer(ContainerHandle handle, int slot) {
		return click(handle, slot, 0, ContainerInput.QUICK_MOVE);
	}

	@Override
	public boolean quickMoveToContainer(ContainerHandle handle, int slot) {
		return click(handle, slot, 0, ContainerInput.QUICK_MOVE);
	}

	// ---- 点击原语层（§6.2）----

	@Override
	public boolean supportsExactAmount() {
		return true;
	}

	@Override
	public boolean pickupAll(ContainerHandle handle, int slot) {
		return click(handle, slot, 0, ContainerInput.PICKUP);
	}

	@Override
	public boolean pickupHalf(ContainerHandle handle, int slot) {
		return click(handle, slot, 1, ContainerInput.PICKUP);
	}

	@Override
	public boolean placeOne(ContainerHandle handle, int slot) {
		// 光标持有堆时右键 = 放 1 个；与 pickupHalf 同包但语义不同
		return click(handle, slot, 1, ContainerInput.PICKUP);
	}

	@Override
	public boolean putBackHeld(ContainerHandle handle, int slot) {
		return click(handle, slot, 0, ContainerInput.PICKUP);
	}

	@Override
	public boolean dragDistribute(ContainerHandle handle, int[] slots) {
		if (slots == null || slots.length == 0) return false;
		if (slots.length == 1) {
			// B10：单目标槽走左键放下（1 包）。完整拖拽三连包在单槽下等价——
			// END 包槽位号在 26.1 线协议中无语义（占位值），行为一致但多 2 包
			return putBackHeld(handle, slots[0]);
		}
		AbstractContainerScreen<?> screen = boundScreen(handle);
		if (screen == null) return false;
		var menu = screen.getMenu();
		var player = Minecraft.getInstance().player;
		if (player == null || Minecraft.getInstance().gameMode == null) return false;

		int id = menu.containerId;
		var mode = Minecraft.getInstance().gameMode;
		mode.handleContainerInput(id, slots[0], 0, ContainerInput.QUICK_CRAFT, player); // 开始拖拽（并加入首槽）
		for (int i = 1; i < slots.length; i++) {
			mode.handleContainerInput(id, slots[i], 1, ContainerInput.QUICK_CRAFT, player); // 加入槽位
		}
		mode.handleContainerInput(id, -999, 2, ContainerInput.QUICK_CRAFT, player); // 结束拖拽（-999 为占位值：END 包槽位号无语义）
		return true;
	}

	// ---- 内部 ----

	private boolean click(ContainerHandle handle, int slot, int button, @NotNull ContainerInput input) {
		AbstractContainerScreen<?> screen = boundScreen(handle);
		if (screen == null) return false;
		var player = Minecraft.getInstance().player;
		var mode = Minecraft.getInstance().gameMode;
		if (player == null || mode == null) return false;
		// 必须经 gameMode：menu.clicked 只是本地预测，发包由 handleContainerInput 负责
		mode.handleContainerInput(screen.getMenu().containerId, slot, button, input, player);
		return true;
	}

	@Nullable
	private static AbstractContainerScreen<?> boundScreen(ContainerHandle handle) {
		AbstractContainerScreen<?> screen = handle.screen();
		if (screen == null || screen != Minecraft.getInstance().screen) return null;
		return screen;
	}
}

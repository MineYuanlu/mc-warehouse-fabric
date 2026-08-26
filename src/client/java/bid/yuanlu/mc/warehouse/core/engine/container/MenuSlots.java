package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Menu 槽位索引工具（PDD §15.12 红线：槽位归属一律用
 * {@code Slot.container instanceof Inventory} 判定，禁止索引算术）。
 * <p>
 * 容器侧快照索引 = {@link BlockEntityDetectorLike#containerSlotCount} 口径下的
 * menu 前缀区（标准容器菜单容器槽在前）；背包侧取 Inventory 归属且为
 * 主背包 36 格（{@code getContainerSlot() < 36}）的槽位。
 */
public final class MenuSlots {

	/** 容器侧槽位数（与 BlockEntityDetector.scan 的快照口径一致） */
	public static int containerSlotCount(AbstractContainerMenu menu) {
		for (int i = 0; i < menu.slots.size(); i++) {
			if (menu.slots.get(i).container instanceof Inventory) return i;
		}
		return menu.slots.size();
	}

	/**
	 * 容器侧槽位的 menu 索引（按 menu 出现序）。快照的容器槽 k 对应本列表第 k 项——
	 * 前提：容器槽全部位于背包槽之前（原版标准菜单成立；前置布局的模组菜单需专用 Detector）。
	 */
	public static List<Integer> containerSlotIndexes(AbstractContainerMenu menu) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < menu.slots.size(); i++) {
			if (!(menu.slots.get(i).container instanceof Inventory)) out.add(i);
		}
		return out;
	}

	/** 背包侧（主背包 36 格）的 menu 槽位索引列表，按 Inventory 内槽位序 */
	public static List<Integer> packSlotIndexes(AbstractContainerMenu menu) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot s = menu.slots.get(i);
			if (s.container instanceof Inventory && s.getContainerSlot() < 36) out.add(i);
		}
		out.sort((a, b) -> Integer.compare(menu.slots.get(a).getContainerSlot(),
				menu.slots.get(b).getContainerSlot()));
		return out;
	}

	private MenuSlots() {
	}
}

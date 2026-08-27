package bid.yuanlu.mc.warehouse.impl.container;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.container.ContainerOpenContext;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;

/**
 * 基于方块实体类型的检测器基类（PDD §8.1）：
 * 身份组合判定 = 方块实体类型 + MenuType + 容器侧槽位数。
 * <p>
 * 快照只含容器侧槽位——以 {@code Slot.container instanceof Inventory} 判定边界
 * （§15.12 红线：禁止槽位索引算术）。
 */
public abstract class BlockEntityDetector implements ContainerDetector {

	private final String id;
	private final Set<BlockEntityType<?>> blockEntities;
	private final Set<MenuType<?>> menuTypes;

	protected BlockEntityDetector(String id, Set<BlockEntityType<?>> blockEntities, Set<MenuType<?>> menuTypes) {
		this.id = java.util.Objects.requireNonNull(id, "id");
		this.blockEntities = Set.copyOf(blockEntities);
		this.menuTypes = Set.copyOf(menuTypes);
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public boolean matchesBlock(BlockInWorld pos) {
		BlockEntity be = pos.getEntity();
		return be != null && blockEntities.contains(be.getType());
	}

	@Override
	public boolean matches(AbstractContainerScreen<?> screen, ContainerOpenContext ctx) {
		if (!menuTypes.contains(screen.getMenu().getType())) return false;
		BlockEntity be = ctx == null ? null : ctx.block().getEntity();
		if (be == null || !blockEntities.contains(be.getType())) return false;
		// §8.1 标题一致性：26.1 开屏包标题即 provider.getDisplayName()（ServerPlayer.openMenu），
		// 与预检 BE 的 DisplayName 同源——改名容器不误杀，打开的容器与预检方块不符必杀。
		// 非 Nameable BE（如末影箱）跳过。
		if (be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity named
				&& !titleMatches(screen.getTitle(), named.getDisplayName())) {
			return false;
		}
		return slotCountMatches(containerSlotCount(screen.getMenu()));
	}

	/** 标题一致性（§8.1）；可被插件子类覆盖放宽 */
	protected boolean titleMatches(net.minecraft.network.chat.Component screenTitle,
			net.minecraft.network.chat.Component beDisplayName) {
		return screenTitle.equals(beDisplayName);
	}

	/** 槽位数约束；默认不限 */
	protected boolean slotCountMatches(int containerSlots) {
		return true;
	}

	/** 槽位角色表（§8.2）；默认全 GENERIC 双向可用 */
	protected SlotInfo roleFor(int slot, int containerSlots) {
		return SlotInfo.GENERIC;
	}

	@Override
	public ContainerSnapshot scan(AbstractContainerScreen<?> screen) {
		AbstractContainerMenu menu = screen.getMenu();
		int n = containerSlotCount(menu);
		Map<Integer, ItemStack> slots = new LinkedHashMap<>();
		Map<Integer, SlotInfo> infos = new LinkedHashMap<>();
		for (int i = 0; i < n && i < menu.slots.size(); i++) {
			SlotInfo info = roleFor(i, n);
			infos.put(i, info);
			ItemStack stack = menu.slots.get(i).getItem();
			if (!stack.isEmpty()) slots.put(i, stack.copy());
		}
		return new ContainerSnapshot(slots, infos, screen.getTitle().getString(), n);
	}

	/**
	 * 容器侧槽位数：Menu 中第一个「container 为玩家背包」的槽位之前的数量。
	 */
	public static int containerSlotCount(AbstractContainerMenu menu) {
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot s = menu.slots.get(i);
			if (s.container instanceof Inventory) return i;
		}
		return menu.slots.size();
	}

	@SafeVarargs
	protected static Set<BlockEntityType<?>> beTypes(BlockEntityType<?>... types) {
		return Set.of(types);
	}
}

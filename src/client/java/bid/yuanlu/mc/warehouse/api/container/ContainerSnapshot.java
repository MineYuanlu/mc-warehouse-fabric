package bid.yuanlu.mc.warehouse.api.container;

import java.util.Map;

import net.minecraft.world.item.ItemStack;

/**
 * 容器快照：最后一次扫描的容器侧内容（PDD §3.8）。
 * <p>
 * 只含容器侧槽位——玩家背包/盔甲/副手一律不进入快照（依据 Menu 中槽位的 container 归属判定）。
 * 空位不包含在 {@link #slots} 中。只有对账完成后的状态才允许写入（PDD §6.3）。
 */
public final class ContainerSnapshot {

	/** slotIndex → 物品堆（非空槽位；副本） */
	private final Map<Integer, ItemStack> slots;
	/** 槽位能力（来自 Detector，§8.2）；缺失的槽位按 {@link SlotInfo#GENERIC} 处理 */
	private final Map<Integer, SlotInfo> slotInfos;
	/** 容器 UI 标题（用于识别） */
	private final String title;
	/** 容器侧总槽位数 */
	private final int slotCount;

	public ContainerSnapshot(Map<Integer, ItemStack> slots, Map<Integer, SlotInfo> slotInfos, String title, int slotCount) {
		this.slots = Map.copyOf(slots);
		this.slotInfos = Map.copyOf(slotInfos);
		this.title = title;
		this.slotCount = slotCount;
	}

	/** 非空槽位视图（只读） */
	public Map<Integer, ItemStack> slots() {
		return slots;
	}

	public Map<Integer, SlotInfo> slotInfos() {
		return slotInfos;
	}

	public String title() {
		return title;
	}

	/** 容器侧总槽位数 */
	public int slotCount() {
		return slotCount;
	}

	/** 槽位能力，未知槽位回退 GENERIC */
	public SlotInfo slotInfo(int slot) {
		return slotInfos.getOrDefault(slot, SlotInfo.GENERIC);
	}
}

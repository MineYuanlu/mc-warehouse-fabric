package bid.yuanlu.mc.warehouse.api.item;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import net.minecraft.world.item.ItemStack;

/**
 * 槽位分配器 SPI（PDD §3.6）：把「对 item 的 amount 个增删」分配到具体槽位。
 * <p>
 * 通过 Registry 注册，全局配置 {@code slotAllocator} 选择当前实现。
 */
public interface SlotAllocator {

	String id();

	/**
	 * 把「对 item 的 amount 个增删」分配到具体槽位。
	 *
	 * @param snapshot    容器快照（分配只依据对账后的槽位级状态）
	 * @param item        物品样本（判定同类与容量）
	 * @param amount      增删总量（恒为正）
	 * @param toContainer true 放入（只用 canPutTo 槽位），false 取出（只用 canTakeFrom 槽位）
	 * @return 分配列表；总 count 可能小于 amount（容器空间不足时截断），不超发
	 */
	List<SlotAllocation> allocate(ContainerSnapshot snapshot, ItemStack item, int amount, boolean toContainer);

	/**
	 * 带槽位过滤的分配（§8.2 第 3 条：机器类槽位按 Detector.acceptsPut 二次过滤）。
	 * 默认忽略 filter（插件实现兼容）；实现宜覆写以获得精确落位模拟。
	 */
	default List<SlotAllocation> allocate(ContainerSnapshot snapshot, ItemStack item, int amount,
			boolean toContainer, @Nullable Predicate<SlotInfo> slotFilter) {
		return allocate(snapshot, item, amount, toContainer);
	}
}

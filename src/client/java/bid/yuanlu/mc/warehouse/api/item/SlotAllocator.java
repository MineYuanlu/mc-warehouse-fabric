package bid.yuanlu.mc.warehouse.api.item;

import java.util.List;

import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
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
}

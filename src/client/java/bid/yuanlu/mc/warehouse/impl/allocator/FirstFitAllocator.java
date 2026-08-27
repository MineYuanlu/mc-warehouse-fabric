package bid.yuanlu.mc.warehouse.impl.allocator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import bid.yuanlu.mc.warehouse.api.item.SlotAllocation;
import bid.yuanlu.mc.warehouse.api.item.SlotAllocator;
import net.minecraft.world.item.ItemStack;

/**
 * 首适配分配器（PDD §3.6/§6.2 两遍式落位）：
 * <ul>
 *   <li>放入：先并入已有同类不满堆（按槽序），再依次填充空槽</li>
 *   <li>取出：按槽序从持有该物品的 canTakeFrom 槽位逐槽取用</li>
 * </ul>
 * 只依据对账后的槽位级快照；空间不足时返回截断的分配（不超发）。
 */
public final class FirstFitAllocator implements SlotAllocator {

	public static final String ID = "first_fit";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public List<SlotAllocation> allocate(ContainerSnapshot snapshot, ItemStack item, int amount, boolean toContainer) {
		return allocate(snapshot, item, amount, toContainer, null);
	}

	@Override
	public List<SlotAllocation> allocate(ContainerSnapshot snapshot, ItemStack item, int amount,
			boolean toContainer, @Nullable Predicate<SlotInfo> slotFilter) {
		if (amount <= 0) return List.of();
		return toContainer ? allocatePut(snapshot, item, amount, slotFilter) : allocateTake(snapshot, item, amount);
	}

	/** 放入：两遍式——同类不满堆 → 空槽（槽序）；slotFilter 非空时额外过滤 */
	private List<SlotAllocation> allocatePut(ContainerSnapshot snapshot, ItemStack item, int amount,
			@Nullable Predicate<SlotInfo> slotFilter) {
		List<SlotAllocation> out = new ArrayList<>();
		int max = item.getMaxStackSize();
		int remaining = amount;

		List<Integer> occupied = new ArrayList<>(snapshot.slots().keySet());
		occupied.sort(Integer::compareTo);
		for (int slot : occupied) {
			if (remaining <= 0) break;
			ItemStack stack = snapshot.slots().get(slot);
			SlotInfo info = snapshot.slotInfo(slot);
			if (!info.canPutTo()) continue;
			if (slotFilter != null && !slotFilter.test(info)) continue;
			if (!ItemStack.isSameItemSameComponents(stack, item)) continue;
			if (stack.getCount() >= max) continue;
			int take = Math.min(max - stack.getCount(), remaining);
			out.add(new SlotAllocation(slot, take));
			remaining -= take;
		}

		for (int slot = 0; slot < snapshot.slotCount() && remaining > 0; slot++) {
			if (snapshot.slots().containsKey(slot)) continue;
			SlotInfo info = snapshot.slotInfo(slot);
			if (!info.canPutTo()) continue;
			if (slotFilter != null && !slotFilter.test(info)) continue;
			int take = Math.min(max, remaining);
			out.add(new SlotAllocation(slot, take));
			remaining -= take;
		}
		return out;
	}

	/** 取出：按槽序逐槽取用 */
	private List<SlotAllocation> allocateTake(ContainerSnapshot snapshot, ItemStack item, int amount) {
		List<SlotAllocation> out = new ArrayList<>();
		int remaining = amount;

		List<Integer> occupied = new ArrayList<>(snapshot.slots().keySet());
		occupied.sort(Integer::compareTo);
		for (int slot : occupied) {
			if (remaining <= 0) break;
			ItemStack stack = snapshot.slots().get(slot);
			if (!snapshot.slotInfo(slot).canTakeFrom()) continue;
			if (!ItemStack.isSameItemSameComponents(stack, item)) continue;
			int take = Math.min(stack.getCount(), remaining);
			out.add(new SlotAllocation(slot, take));
			remaining -= take;
		}
		return out;
	}
}

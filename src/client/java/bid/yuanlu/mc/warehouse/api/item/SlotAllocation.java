package bid.yuanlu.mc.warehouse.api.item;

/**
 * 单个槽位的分配结果（PDD §3.6）。
 *
 * @param slot  目标槽位（容器侧槽位索引）
 * @param count 该槽位增删的数量（恒为正，方向由调用方决定）
 */
public record SlotAllocation(int slot, int count) {

	public SlotAllocation {
		if (count <= 0) throw new IllegalArgumentException("count <= 0: " + count);
	}
}

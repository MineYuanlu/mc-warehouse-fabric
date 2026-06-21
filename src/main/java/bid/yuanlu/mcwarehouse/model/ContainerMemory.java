package bid.yuanlu.mcwarehouse.model;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

public class ContainerMemory {

	private final Map<BlockPos, ContainerSnapshot> snapshots = new HashMap<>();

	public void snapshot(BlockPos pos, ContainerSnapshot data) {
		this.snapshots.put(pos, data);
	}

	public void clear() {
		this.snapshots.clear();
	}

	public void clear(BlockPos pos) {
		this.snapshots.remove(pos);
	}

	public ContainerSnapshot get(BlockPos pos) {
		return this.snapshots.get(pos);
	}
}

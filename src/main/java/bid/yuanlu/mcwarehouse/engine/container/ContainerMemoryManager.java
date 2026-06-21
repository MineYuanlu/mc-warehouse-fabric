package bid.yuanlu.mcwarehouse.engine.container;

import net.minecraft.core.BlockPos;

import bid.yuanlu.mcwarehouse.model.ContainerMemory;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;

public class ContainerMemoryManager {

	private static final ContainerMemoryManager INSTANCE = new ContainerMemoryManager();

	private final ContainerMemory memory = new ContainerMemory();

	private ContainerMemoryManager() {
	}

	public static ContainerMemoryManager getInstance() {
		return INSTANCE;
	}

	public void snapshot(BlockPos pos, ContainerSnapshot data) {
		this.memory.snapshot(pos, data);
	}

	public ContainerSnapshot get(BlockPos pos) {
		return this.memory.get(pos);
	}

	public void clearAll() {
		this.memory.clear();
	}

	public void clear(BlockPos pos) {
		this.memory.clear(pos);
	}
}

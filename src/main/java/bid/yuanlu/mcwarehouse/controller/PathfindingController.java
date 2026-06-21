package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class PathfindingController {

	private static final PathfindingController INSTANCE = new PathfindingController();

	private boolean running;
	private List<BlockPos> targets;
	private int currentTargetIndex;
	private int retryCount;
	private static final int MAX_RETRIES = 3;

	public static PathfindingController getInstance() {
		return INSTANCE;
	}

	private PathfindingController() {
		this.running = false;
		this.targets = new ArrayList<>();
		this.currentTargetIndex = 0;
		this.retryCount = 0;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean startRun(String pathfinderType) {
		if (running) {
			return false;
		}
		WarehouseController wc = WarehouseController.getInstance();
		String name = wc.getActiveWarehouse();
		if (name == null) {
			return false;
		}
		Warehouse warehouse = wc.getWarehouse(name);
		if (warehouse == null || warehouse.containers == null || warehouse.containers.isEmpty()) {
			return false;
		}
		List<ContainerInfo> sorted = new ArrayList<>(warehouse.containers);
		sorted.removeIf(c -> c.type == ContainerType.IGNORE);
		sorted.sort(Comparator.comparingInt(c -> switch (c.type) {
			case OUTPUT -> 0;
			case INPUT -> 1;
			case RELAY -> 2;
			default -> 3;
		}));
		targets.clear();
		for (ContainerInfo info : sorted) {
			targets.add(CoordinateUtils.toAbsolute(info.relativePos, warehouse.anchor));
		}
		if (targets.isEmpty()) {
			return false;
		}
		currentTargetIndex = 0;
		retryCount = 0;
		running = true;
		return true;
	}

	public void abort() {
		running = false;
		targets.clear();
		currentTargetIndex = 0;
		retryCount = 0;
	}

	public void tick() {
		if (!running) {
			return;
		}
		if (currentTargetIndex >= targets.size()) {
			running = false;
			return;
		}
		BlockPos current = targets.get(currentTargetIndex);
		WarehouseController wc = WarehouseController.getInstance();
		String name = wc.getActiveWarehouse();
		if (name == null) {
			abort();
			return;
		}
		Warehouse warehouse = wc.getWarehouse(name);
		if (warehouse == null) {
			abort();
			return;
		}
		ContainerInfo info = null;
		for (ContainerInfo ci : warehouse.containers) {
			if (CoordinateUtils.toAbsolute(ci.relativePos, warehouse.anchor).equals(current)) {
				info = ci;
				break;
			}
		}
		if (info == null) {
			currentTargetIndex++;
			retryCount = 0;
			return;
		}
		ContainerController cc = ContainerController.getInstance();
		boolean success = cc.executeTransfer(info, current, name);
		if (success) {
			currentTargetIndex++;
			retryCount = 0;
		} else {
			retryCount++;
			if (retryCount >= MAX_RETRIES) {
				currentTargetIndex++;
				retryCount = 0;
			}
		}
	}
}

package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import bid.yuanlu.mcwarehouse.engine.container.ContainerInteractor;
import bid.yuanlu.mcwarehouse.engine.pathfinder.PathExecutor;
import bid.yuanlu.mcwarehouse.engine.pathfinder.PathExecutor.Status;
import bid.yuanlu.mcwarehouse.engine.pathfinder.executors.SimpleWalkExecutor;
import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator;
import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator.TransferPlan;
import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class PathfindingController {

	private static final PathfindingController INSTANCE = new PathfindingController();

	private boolean running;
	private PathExecutor executor;
	private int retryCount;
	private static final int MAX_RETRIES = 3;

	private String activeWarehouseName;
	private List<ContainerInfo> sortedContainers;
	private int containerIndex;

	private ContainerInteractor interactor;
	private boolean interacting;
	private BlockPos interactionPos;

	public static PathfindingController getInstance() {
		return INSTANCE;
	}

	private PathfindingController() {
		this.running = false;
		this.executor = null;
		this.retryCount = 0;
		this.sortedContainers = new ArrayList<>();
		this.containerIndex = 0;
		this.interacting = false;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean startRun(String pathfinderType) {
		if (running) return false;

		WarehouseController wc = WarehouseController.getInstance();
		String name = wc.getActiveWarehouse();
		if (name == null) return false;

		Warehouse warehouse = wc.getWarehouse(name);
		if (warehouse == null || warehouse.containers == null || warehouse.containers.isEmpty()) {
			return false;
		}

		activeWarehouseName = name;

		sortedContainers = new ArrayList<>(warehouse.containers);
		sortedContainers.removeIf(c -> c.type == ContainerType.IGNORE);
		sortedContainers.sort(Comparator.comparingInt(c -> switch (c.type) {
			case OUTPUT -> 0;
			case INPUT -> 1;
			case RELAY -> 2;
			default -> 3;
		}));

		if (sortedContainers.isEmpty()) return false;

		List<Vec3> targets = new ArrayList<>();
		for (ContainerInfo info : sortedContainers) {
			BlockPos abs = CoordinateUtils.toAbsolute(info.relativePos, warehouse.anchor);
			targets.add(new Vec3(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5));
		}

		executor = switch (pathfinderType) {
			default -> new SimpleWalkExecutor();
		};
		executor.setTargets(targets);

		containerIndex = 0;
		retryCount = 0;
		running = true;
		interacting = false;
		interactor = null;
		return true;
	}

	public void abort() {
		running = false;
		if (executor != null) {
			executor.reset();
		}
		executor = null;
		retryCount = 0;
		containerIndex = 0;
		sortedContainers.clear();
		activeWarehouseName = null;
		interacting = false;
		if (interactor != null) {
			interactor.resetState();
			interactor = null;
		}
		interactionPos = null;
	}

	public void tick() {
		if (!running || executor == null) return;

		if (interacting) {
			if (interactor == null) {
				interacting = false;
				return;
			}
			interactor.tick();
			if (interactor.isCompleted()) {
				var cc = ContainerController.getInstance();
				ContainerSnapshot updated = interactor.captureCurrentScreen();
				if (updated != null) {
					cc.snapshotMemory(interactionPos, updated);
				}
				interacting = false;
				interactor = null;
				containerIndex++;
				retryCount = 0;
			}
			return;
		}

		Status status = executor.tick();

		switch (status) {
			case ARRIVED -> {
				Vec3 arrived = executor.pollArrived();
				if (arrived != null) {
					BlockPos pos = BlockPos.containing(arrived);
					boolean ok = processContainer(pos);
					if (ok) {
						if (!interacting) {
							containerIndex++;
							retryCount = 0;
						}
					} else {
						onContainerFailed();
					}
				}
			}
			case FAILED -> {
				retryCount++;
				if (retryCount >= MAX_RETRIES) {
					executor.pollArrived();
					containerIndex++;
					retryCount = 0;
				}
			}
			case DONE -> running = false;
			case MOVING -> {}
		}
	}

	public void onBlockInteraction(BlockPos pos) {
		if (!running || executor == null) return;
	}

	private boolean processContainer(BlockPos pos) {
		if (containerIndex >= sortedContainers.size()) return false;

		ContainerInfo info = sortedContainers.get(containerIndex);

		var wc = WarehouseController.getInstance();
		Warehouse warehouse = wc.getWarehouse(activeWarehouseName);
		if (warehouse == null) return false;

		var cc = ContainerController.getInstance();
		ContainerSnapshot snapshot = cc.captureCurrentScreen();
		if (snapshot == null) return false;

		var tempInteractor = new ContainerInteractor(2);
		ContainerSnapshot playerInv = tempInteractor.capturePlayerInventory();

		TransferPlan plan = RuleApplicator.calculatePlan(info, snapshot, warehouse.rules, playerInv);

		if (plan == null || plan.moves.isEmpty()) {
			cc.snapshotMemory(pos, snapshot);
			return true;
		}

		this.interactor = new ContainerInteractor(2);
		this.interactor.startExecution(plan);
		this.interacting = true;
		this.interactionPos = pos;
		return true;
	}

	private void onContainerFailed() {
		retryCount++;
		if (retryCount >= MAX_RETRIES) {
			executor.pollArrived();
			containerIndex++;
			retryCount = 0;
		}
	}
}

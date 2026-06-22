package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
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
import bid.yuanlu.mcwarehouse.model.rule.ItemRule;
import bid.yuanlu.mcwarehouse.storage.WorldConfigStorage;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class PathfindingController {

	private static final PathfindingController INSTANCE = new PathfindingController();

	private boolean running;
	private PathExecutor executor;
	private int retryCount;
	private static final int MAX_RETRIES = 3;

	private String activeWarehouseName;
	private Phase currentPhase;
	private List<ContainerTarget> roundTargets;
	private int targetIndex;
	private boolean roundHadAction;
	private boolean roundHadNewExplore;

	private ContainerInteractor interactor;
	private boolean interacting;
	private BlockPos interactionPos;
	private ContainerTarget currentTarget;
	private boolean currentPlanHadMoves;
	private int interactionSpeed;

	private enum Phase {
		OUTPUT,
		TEMP,
		INPUT
	}

	private static class ContainerTarget {
		final BlockPos pos;
		final ContainerInfo info;
		final boolean wasExplored;

		ContainerTarget(BlockPos pos, ContainerInfo info, boolean wasExplored) {
			this.pos = pos;
			this.info = info;
			this.wasExplored = wasExplored;
		}
	}

	private static class ContainerCandidate {
		final BlockPos pos;
		final ContainerInfo info;
		final boolean wasExplored;

		ContainerCandidate(BlockPos pos, ContainerInfo info, boolean wasExplored) {
			this.pos = pos;
			this.info = info;
			this.wasExplored = wasExplored;
		}
	}

	public static PathfindingController getInstance() {
		return INSTANCE;
	}

	private PathfindingController() {
		this.running = false;
		this.executor = null;
		this.retryCount = 0;
		this.roundTargets = new ArrayList<>();
		this.targetIndex = 0;
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

		currentPhase = Phase.OUTPUT;
		roundTargets = new ArrayList<>();
		targetIndex = 0;
		roundHadAction = false;
		roundHadNewExplore = false;
		interacting = false;
		interactor = null;
		retryCount = 0;
		interactionSpeed = WorldConfigStorage.getInstance().getInteractionSpeed();

		buildPhaseTargets(warehouse);
		if (roundTargets.isEmpty()) {
			running = false;
			return false;
		}

		List<Vec3> targets = new ArrayList<>();
		for (ContainerTarget t : roundTargets) {
			targets.add(new Vec3(t.pos.getX() + 0.5, t.pos.getY() + 0.5, t.pos.getZ() + 0.5));
		}

		executor = switch (pathfinderType) {
			default -> new SimpleWalkExecutor();
		};
		executor.setTargets(targets);

		running = true;
		return true;
	}

	public void abort() {
		running = false;
		if (executor != null) {
			executor.reset();
		}
		executor = null;
		retryCount = 0;
		targetIndex = 0;
		roundTargets.clear();
		activeWarehouseName = null;
		interacting = false;
		if (interactor != null) {
			interactor.resetState();
			interactor = null;
		}
		interactionPos = null;
		currentTarget = null;
	}

	public BlockPos getInteractionPos() {
		return interactionPos;
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

				if (currentTarget != null && !currentTarget.wasExplored) {
					roundHadNewExplore = true;
				}
				if (currentPlanHadMoves) {
					roundHadAction = true;
				}

				interacting = false;
				interactor = null;
				targetIndex++;
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
					processContainer(pos);
				}
			}
			case FAILED -> {
				retryCount++;
				if (retryCount >= MAX_RETRIES) {
					executor.pollArrived();
					targetIndex++;
					retryCount = 0;
					checkPhaseOrRebuild();
				}
			}
			case DONE -> {
				if (targetIndex >= roundTargets.size()) {
					if (!advancePhase()) {
						if (!evaluateRoundCompletion()) {
							running = false;
						}
					}
				} else {
					if (!advancePhase()) {
						if (!evaluateRoundCompletion()) {
							running = false;
						}
					}
				}
			}
			case MOVING -> {}
		}
	}

	public void onBlockInteraction(BlockPos pos) {
		if (!running || executor == null) return;
	}

	private void processContainer(BlockPos pos) {
		if (targetIndex >= roundTargets.size()) return;

		currentTarget = roundTargets.get(targetIndex);

		var wc = WarehouseController.getInstance();
		Warehouse warehouse = wc.getWarehouse(activeWarehouseName);
		if (warehouse == null) return;

		var cc = ContainerController.getInstance();
		ContainerSnapshot snapshot = cc.captureCurrentScreen();
		if (snapshot == null) return;

		var tempInteractor = new ContainerInteractor(interactionSpeed);
		ContainerSnapshot playerInv = tempInteractor.capturePlayerInventory();

		TransferPlan plan;
		if (currentPhase == Phase.TEMP) {
			boolean hasUnexplored = cc.hasUnexploredOutput(warehouse);
			List<ItemRule> allOutputRules = cc.collectAllOutputRules(warehouse);
			plan = RuleApplicator.calculatePlan(currentTarget.info, snapshot, warehouse.rules, playerInv,
					hasUnexplored, allOutputRules);
		} else {
			plan = RuleApplicator.calculatePlan(currentTarget.info, snapshot, warehouse.rules, playerInv);
		}

		if (plan == null || plan.moves.isEmpty()) {
			cc.snapshotMemory(pos, snapshot);
			if (!currentTarget.wasExplored) {
				roundHadNewExplore = true;
			}
			targetIndex++;
			retryCount = 0;
			checkPhaseOrRebuild();
			return;
		}

		cc.setLastInteractionPos(pos);
		this.interactor = new ContainerInteractor(interactionSpeed);
		this.interactor.startExecution(plan);
		this.interacting = true;
		this.interactionPos = pos;
		this.currentPlanHadMoves = !plan.moves.isEmpty();
	}

	private boolean advancePhase() {
		return switch (currentPhase) {
			case OUTPUT -> {
				currentPhase = Phase.TEMP;
				yield rebuildPhase();
			}
			case TEMP -> {
				currentPhase = Phase.INPUT;
				yield rebuildPhase();
			}
			case INPUT -> false;
		};
	}

	private boolean rebuildPhase() {
		var wc = WarehouseController.getInstance();
		Warehouse warehouse = wc.getWarehouse(activeWarehouseName);
		if (warehouse == null) return false;

		targetIndex = 0;
		buildPhaseTargets(warehouse);

		if (roundTargets.isEmpty()) return false;

		List<Vec3> targets = new ArrayList<>();
		for (ContainerTarget t : roundTargets) {
			targets.add(new Vec3(t.pos.getX() + 0.5, t.pos.getY() + 0.5, t.pos.getZ() + 0.5));
		}
		executor.setTargets(targets);
		return true;
	}

	private void buildPhaseTargets(Warehouse w) {
		roundTargets.clear();

		var cc = ContainerController.getInstance();
		ContainerSnapshot playerInv = new ContainerInteractor(interactionSpeed).capturePlayerInventory();

		List<ContainerCandidate> candidates = new ArrayList<>();

		switch (currentPhase) {
			case OUTPUT -> {
				for (ContainerInfo c : w.containers) {
					if (c.type != ContainerType.OUTPUT) continue;
					BlockPos abs = toAbsolute(c, w.anchor);
					ContainerSnapshot mem = cc.getMemory(abs);

					if (mem == null) {
						candidates.add(new ContainerCandidate(abs, c, false));
					} else {
						TransferPlan plan = RuleApplicator.calculatePlan(c, mem, w.rules, playerInv);
						if (plan != null && !plan.moves.isEmpty()) {
							candidates.add(new ContainerCandidate(abs, c, true));
						}
					}
				}
			}
			case TEMP -> {
				boolean hasUnexplored = cc.hasUnexploredOutput(w);
				List<ItemRule> allOutputRules = cc.collectAllOutputRules(w);

				for (ContainerInfo c : w.containers) {
					if (c.type != ContainerType.TEMP) continue;
					BlockPos abs = toAbsolute(c, w.anchor);
					ContainerSnapshot mem = cc.getMemory(abs);

					if (mem == null) {
						candidates.add(new ContainerCandidate(abs, c, false));
					} else {
						TransferPlan plan = RuleApplicator.calculatePlan(
								c, mem, w.rules, playerInv, hasUnexplored, allOutputRules);
						if (plan != null && !plan.moves.isEmpty()) {
							candidates.add(new ContainerCandidate(abs, c, true));
						}
					}
				}
			}
			case INPUT -> {
				boolean hasOutputSpace = cc.hasAnyOutputSpace(w, playerInv);
				boolean hasTempSpace = cc.hasAnyTempSpace(w, playerInv);
				boolean invNotFull = !cc.isInventoryFull(playerInv);
				boolean needExploreInput = hasOutputSpace || hasTempSpace || invNotFull;

				for (ContainerInfo c : w.containers) {
					if (c.type != ContainerType.INPUT) continue;
					BlockPos abs = toAbsolute(c, w.anchor);
					ContainerSnapshot mem = cc.getMemory(abs);

					if (mem == null) {
						if (needExploreInput) {
							candidates.add(new ContainerCandidate(abs, c, false));
						}
					} else {
						TransferPlan plan = RuleApplicator.calculatePlan(c, mem, w.rules, playerInv);
						if (plan != null && !plan.moves.isEmpty()) {
							candidates.add(new ContainerCandidate(abs, c, true));
						}
					}
				}
			}
		}

		Player player = Minecraft.getInstance().player;
		if (player != null) {
			BlockPos playerPos = player.blockPosition();
			candidates.sort(Comparator.comparingDouble(cand -> cand.pos.distSqr(playerPos)));
		}

		for (ContainerCandidate cand : candidates) {
			roundTargets.add(new ContainerTarget(cand.pos, cand.info, cand.wasExplored));
		}
	}

	private void checkPhaseOrRebuild() {
		if (targetIndex >= roundTargets.size()) {
			if (!advancePhase()) {
				if (!evaluateRoundCompletion()) {
					running = false;
				}
			}
		}
	}

	private boolean evaluateRoundCompletion() {
		if (roundHadAction || roundHadNewExplore) {
			roundHadAction = false;
			roundHadNewExplore = false;
			currentPhase = Phase.OUTPUT;
			return rebuildPhase();
		}

		notifyStopReason();
		return false;
	}

	private void notifyStopReason() {
		var cc = ContainerController.getInstance();
		var wc = WarehouseController.getInstance();
		Warehouse w = wc.getWarehouse(activeWarehouseName);
		if (w == null) return;

		boolean outputFull = cc.isOutputFullySatisfied(w);
		boolean tempEmpty = cc.isTempFullyEmpty(w);
		boolean invEmpty = cc.isInventoryEmpty();
		boolean inputEmpty = cc.isInputFullyEmpty(w);

		String reason;
		if (outputFull && tempEmpty && invEmpty && inputEmpty) {
			reason = "完美完成：所有条件满足";
		} else if (outputFull && tempEmpty && invEmpty) {
			reason = "完成：Output已满，Temp已空，背包已空";
		} else if (outputFull && tempEmpty) {
			reason = "完成：Output已满，Temp已空";
		} else if (outputFull) {
			reason = "停止：Output已满（空间不足无法继续）";
		} else {
			reason = "异常：仍有可用空间但无搬运计划";
		}

		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(
					net.minecraft.network.chat.Component.literal("§6=== 自动搬运结束 ==="));
			mc.player.sendSystemMessage(
					net.minecraft.network.chat.Component.literal("§e" + reason));
		}
	}

	private static BlockPos toAbsolute(ContainerInfo info, BlockPos anchor) {
		return CoordinateUtils.toAbsolute(info.relativePos, anchor);
	}
}

package bid.yuanlu.mcwarehouse.engine.pathfinder.executors;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import bid.yuanlu.mcwarehouse.engine.pathfinder.PathExecutor;

public class SimpleWalkExecutor implements PathExecutor {

	private static final double ARRIVAL_DISTANCE = 2.0;
	private static final int MAX_TICKS = 200;

	private final Deque<Vec3> targets = new ArrayDeque<>();
	private Vec3 currentTarget;
	private int tickTimeout = 0;
	private Status status = Status.MOVING;
	private Vec3 arrived;

	@Override
	public void setTargets(Collection<Vec3> targets) {
		this.targets.clear();
		this.targets.addAll(targets);
		this.currentTarget = this.targets.pollFirst();
		this.tickTimeout = 0;
		this.status = Status.MOVING;
		this.arrived = null;
	}

	@Override
	public Status tick() {
		if (status == Status.DONE || status == Status.FAILED) {
			return status;
		}

		Player player = Minecraft.getInstance().player;
		if (player == null) {
			this.status = Status.FAILED;
			return status;
		}

		if (currentTarget == null) {
			this.status = Status.DONE;
			return status;
		}

		tickTimeout++;
		if (tickTimeout > MAX_TICKS) {
			this.status = Status.FAILED;
			return status;
		}

		Vec3 playerPos = player.position();
		double dist = playerPos.distanceTo(currentTarget);

		if (dist < ARRIVAL_DISTANCE) {
			this.arrived = currentTarget;
			this.status = Status.ARRIVED;
			return status;
		}

		this.status = Status.MOVING;
		return status;
	}

	@Override
	public Vec3 pollArrived() {
		Vec3 result = this.arrived;
		this.arrived = null;
		if (result != null) {
			this.currentTarget = this.targets.pollFirst();
			this.tickTimeout = 0;
			this.status = this.currentTarget != null ? Status.MOVING : Status.DONE;
		}
		return result;
	}

	@Override
	public boolean hasRemaining() {
		return status == Status.MOVING || (status == Status.ARRIVED && currentTarget != null)
				|| !targets.isEmpty();
	}

	@Override
	public void reset() {
		this.targets.clear();
		this.currentTarget = null;
		this.tickTimeout = 0;
		this.status = Status.MOVING;
		this.arrived = null;
	}
}

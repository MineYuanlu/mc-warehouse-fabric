package bid.yuanlu.mcwarehouse.engine.pathfinder;

import java.util.Collection;

import net.minecraft.world.phys.Vec3;

public interface PathExecutor {

	void setTargets(Collection<Vec3> targets);

	Status tick();

	Vec3 pollArrived();

	boolean hasRemaining();

	void reset();

	enum Status {
		MOVING,
		ARRIVED,
		FAILED,
		DONE
	}
}

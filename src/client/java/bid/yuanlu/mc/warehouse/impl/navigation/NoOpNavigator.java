package bid.yuanlu.mc.warehouse.impl.navigation;

import bid.yuanlu.mc.warehouse.api.navigation.Goal;
import bid.yuanlu.mc.warehouse.api.navigation.Navigator;
import bid.yuanlu.mc.warehouse.api.navigation.PathResult;
import bid.yuanlu.mc.warehouse.api.navigation.PathStatus;

/**
 * 空寻路器（PDD §7.1 一阶段默认）：玩家自行就位，引擎的 reach 预检失败即走
 * REACH_FAILED 路径。注册 id 为 {@value #ID}。
 */
public final class NoOpNavigator implements Navigator {

	public static final String ID = "noop";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public PathResult start(Goal goal) {
		return PathResult.ok();
	}

	@Override
	public PathStatus tick() {
		return PathStatus.ARRIVED;
	}

	@Override
	public void cancel() {
	}
}

package bid.yuanlu.mc.warehouse.api.navigation;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;

/**
 * 寻路器 SPI（PDD §7.1）：单目标，重试归引擎。
 * <p>
 * target 含 WorldDim，Navigator 必须自行处理维度切换（如经传送门）。
 */
public interface Navigator {

	String id();

	/** 开始寻路；单目标，内部不维护目标队列 */
	PathResult start(Goal goal);

	/** 每 tick 更新状态，由引擎在游戏 tick 中调用 */
	PathStatus tick();

	/** 取消当前寻路 */
	void cancel();
}

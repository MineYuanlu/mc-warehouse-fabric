package bid.yuanlu.mc.warehouse.api.plugin;

import org.jetbrains.annotations.Nullable;

/**
 * 仓库规划器 SPI（PDD §9.4）：AI/规则引擎自动规划仓库配置。一阶段仅定义接口，不提供内置实现。
 */
public interface AgentPlanner {

	String id();

	/** 可增删改查仓库所有配置：物品选择、容器设置、寻路设置等 */
	void plan(bid.yuanlu.mc.warehouse.api.warehouse.Warehouse warehouse, @Nullable PlanningContext context);
}

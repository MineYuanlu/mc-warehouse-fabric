package bid.yuanlu.mc.warehouse.api.transport;

/**
 * 计划方向（PDD §3.9）：决定操作方向，使同一段执行代码可以处理取出和放入两种操作。
 */
public enum PlanDirection {
	/** 从容器 → 玩家背包（取出） */
	TO_PLAYER,
	/** 从玩家背包 → 容器（放入） */
	TO_CONTAINER
}

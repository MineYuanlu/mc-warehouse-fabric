package bid.yuanlu.mc.warehouse.api.navigation;

/**
 * 寻路状态（PDD §7.1）。重试协议归引擎：FAILED 后由引擎决定重试
 * （同一 Goal 最多 navRetryMax 次），Navigator 不做内部重试、不持有目标队列。
 */
public enum PathStatus {
	/** 正在移动中 */
	MOVING,
	/** 已到达目标 */
	ARRIVED,
	/** 寻路失败（无法到达） */
	FAILED,
	/** 被取消 */
	CANCELLED
}

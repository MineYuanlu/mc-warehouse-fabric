package bid.yuanlu.mc.warehouse.api.transport;

/**
 * 搬运状态枚举（PDD §5.1）。
 */
public enum TransportState {
	/** 入口决策：判断背包是否有空位/不满组物品 */
	ENTRY,
	/** 从 TEMP 容器取出物品到背包 */
	GET_TEMP,
	/** 从 INPUT 容器取出物品到背包 */
	GET_INPUT,
	/** 从背包放入物品到 OUTPUT 容器 */
	PUT_OUTPUT,
	/** 从背包放入物品到 TEMP 容器 */
	PUT_TEMP,
	/** 出口条件满足，搬运结束 */
	DONE,
	/** 异常发生，搬运暂停 */
	SUSPENDED
}

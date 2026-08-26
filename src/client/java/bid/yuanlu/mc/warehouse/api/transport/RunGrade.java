package bid.yuanlu.mc.warehouse.api.transport;

/**
 * 搬运结束报告的档位（PDD §5.9）。
 */
public enum RunGrade {
	/** INPUT 清空且背包装运完毕 */
	PERFECT,
	/** OUTPUT 满 & TEMP 空 & 背包空 */
	GOOD,
	/** OUTPUT 满，TEMP 仍有剩余 */
	ACCEPTABLE,
	/** OUTPUT/TEMP 空间不足，无法继续 */
	BLOCKED,
	/** 仍有可用空间但无搬运计划（逻辑漏洞信号，附诊断上下文） */
	ABNORMAL
}

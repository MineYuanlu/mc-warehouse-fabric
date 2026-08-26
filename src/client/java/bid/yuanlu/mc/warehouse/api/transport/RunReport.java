package bid.yuanlu.mc.warehouse.api.transport;

/**
 * 搬运结束报告（PDD §5.9）：DONE 为一次性结束，达成出口条件即停止并输出。
 *
 * @param grade      五档判定
 * @param itemsMoved 成功对账移动的物品总数
 * @param rounds     执行的轮次数
 * @param durationMs 搬运耗时（毫秒）
 * @param detailKey  诊断细节 i18n key（可为 null）
 */
public record RunReport(RunGrade grade, int itemsMoved, int rounds, long durationMs, String detailKey) {

	public RunReport {
		java.util.Objects.requireNonNull(grade, "grade");
	}
}

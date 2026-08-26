package bid.yuanlu.mc.warehouse.api.container;

/**
 * 容器的用户意图标注（PDD §3.2）。
 * <p>
 * 封闭枚举：容器的真实物理能力由 {@link SlotInfo} 槽位能力模型表达，二者正交。
 */
public enum IOType {
	/** 输入容器（物资产出点）：只取出 */
	INPUT,
	/** 输出容器（存储/机器输入）：只放入 */
	OUTPUT,
	/** 中转容器：可取可放（杂项箱） */
	TEMP,
	/** 跳过：不参与搬运流程，仅用于高亮/标记 */
	IGNORE;

	/** 默认 ruleMode（PDD §3.7 表格）；IGNORE 无默认模式，返回 null */
	public RuleMode defaultRuleMode() {
		return switch (this) {
			case INPUT, TEMP -> RuleMode.BLACKLIST;
			case OUTPUT -> RuleMode.WHITELIST;
			case IGNORE -> null;
		};
	}

	/** 是否参与搬运流程 */
	public boolean participates() {
		return this != IGNORE;
	}
}

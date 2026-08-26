package bid.yuanlu.mc.warehouse.api.item;

/**
 * 物品规则（PDD §3.4）：单条匹配条目，判定顺序即优先级（首条命中生效）。
 */
public class ItemRule {

	/** 匹配物品 */
	public final ItemSelector selector;

	/** 反选：先求反匹配结果，再走命中流程 */
	public boolean negative;

	/** 数量控制；null = 不限量（∞ 语义，PDD §3.7） */
	public final QuantitySelector quantity;

	public ItemRule(ItemSelector selector, boolean negative, QuantitySelector quantity) {
		this.selector = java.util.Objects.requireNonNull(selector, "selector");
		this.negative = negative;
		this.quantity = quantity;
	}

	/** 是否为不限量规则 */
	public boolean isUnlimited() {
		return quantity == null;
	}
}

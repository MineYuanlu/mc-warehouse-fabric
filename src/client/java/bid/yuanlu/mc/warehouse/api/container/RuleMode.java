package bid.yuanlu.mc.warehouse.api.container;

/**
 * 规则判定模式（PDD §3.2/§3.7）。
 * <ul>
 *   <li>WHITELIST：无任何 ItemRule 命中 → 物品不允许放入（target=0）</li>
 *   <li>BLACKLIST：无任何 ItemRule 命中 → 物品不受限（双向 target=∞）</li>
 * </ul>
 */
public enum RuleMode {
	WHITELIST,
	BLACKLIST
}

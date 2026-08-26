package bid.yuanlu.mc.warehouse.api.container;

/**
 * 容器遍历优先级（PDD §3.2）：hard 为主排序键（降序遍历），soft 为 hard 相同时的次级排序键。
 */
public record Priority(int hard, int soft) {

	public static final Priority ZERO = new Priority(0, 0);
}

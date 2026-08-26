package bid.yuanlu.mc.warehouse.api.item;

import java.util.Set;

import bid.yuanlu.mc.warehouse.api.container.IOType;

/**
 * 数量选择器（PDD §3.6）：只回答「目标总量是多少」，具体落到哪些槽位由
 * {@link SlotAllocator} 决定。
 * <p>
 * delta = target - current 由引擎推导方向：正数需放入，负数需取出，0 表示刚好满足。
 */
public interface QuantitySelector {

	/**
	 * 计算匹配物品在该容器中的目标总量。
	 */
	int computeTargetAmount(QuantityContext ctx);

	/**
	 * 声明不兼容的 IOType 集合（selector×IOType 合法性校验，PDD §3.7）；
	 * 缺省返回空集表示全部兼容。非法组合在配置加载与命令设置时报错拒载。
	 */
	default Set<IOType> incompatibleIOTypes() {
		return Set.of();
	}
}

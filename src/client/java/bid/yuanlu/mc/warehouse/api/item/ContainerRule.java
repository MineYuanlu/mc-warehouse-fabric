package bid.yuanlu.mc.warehouse.api.item;

import java.util.ArrayList;
import java.util.List;

/**
 * 容器规则（PDD §3.3）：命名的物品规则列表，可在仓库内定义或全局共享。
 */
public class ContainerRule {

	/** 唯一标识（仓库内嵌与全局共享的命名空间合并后必须唯一，冲突拒载 §11.3） */
	public final String id;

	/** 物品规则列表（有序，首条命中生效） */
	public final List<ItemRule> itemRules = new ArrayList<>();

	public ContainerRule(String id) {
		this.id = java.util.Objects.requireNonNull(id, "id");
	}
}

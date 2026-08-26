package bid.yuanlu.mc.warehouse.api.warehouse;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * 仓库管理 API（PDD §12 api/warehouse）：命令层与未来 UI 层操作同一套接口。
 * <p>
 * 激活态为运行时单选，内存持有，不持久化（PDD §3.1 v0.2 决策）。
 */
public interface WarehouseManager {

	/** 全部仓库（id 序） */
	List<Warehouse> list();

	/** 按 id 查找；不存在返回 null */
	@Nullable
	Warehouse get(String id);

	boolean exists(String id);

	/** 创建仓库并持久化；id 已存在抛 IllegalArgumentException */
	Warehouse create(String id);

	/** 删除仓库；激活中的仓库被删除时同时取消激活 */
	void delete(String id);

	/** 保存仓库变更（原子写盘） */
	void save(Warehouse warehouse);

	/** 重载全部配置文件 */
	void reload();

	/** 激活指定仓库；null = 取消激活。不存在抛 IllegalArgumentException */
	void activate(@Nullable String id);

	/** 当前激活的仓库；未激活返回 null */
	@Nullable
	Warehouse active();
}

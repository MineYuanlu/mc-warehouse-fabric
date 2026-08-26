package bid.yuanlu.mc.warehouse.core.warehouse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.warehouse.WarehouseManager;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;

/**
 * 仓库管理实现（PDD §12 core/warehouse）：CRUD + 激活态（内存持有，不持久化）+ 持久化联动。
 */
public final class WarehouseManagerImpl implements WarehouseManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/manager");

	private final ConfigIO io;
	private final Map<String, Warehouse> warehouses = new TreeMap<>();

	/** 运行时单选；不持久化（§3.1 v0.2 决策） */
	@Nullable
	private String activeId;

	private static volatile WarehouseManagerImpl instance;

	/** 全局实例（客户端初始化时装配；懒创建使用默认 config 目录） */
	public static WarehouseManagerImpl get() {
		if (instance == null) {
			synchronized (WarehouseManagerImpl.class) {
				if (instance == null) {
					instance = new WarehouseManagerImpl(new ConfigIO(ConfigIO.defaultRoot()));
				}
			}
		}
		return instance;
	}

	public static void setInstance(@Nullable WarehouseManagerImpl manager) {
		instance = manager;
	}

	public WarehouseManagerImpl(ConfigIO io) {
		this.io = io;
		reload();
	}

	public ConfigIO configIo() {
		return io;
	}

	// ---- 查询 ----

	@Override
	public List<Warehouse> list() {
		synchronized (warehouses) {
			return warehouses.values().stream()
					.sorted(Comparator.comparing(w -> w.id))
					.toList();
		}
	}

	@Override
	@Nullable
	public Warehouse get(String id) {
		synchronized (warehouses) {
			return warehouses.get(id);
		}
	}

	@Override
	public boolean exists(String id) {
		synchronized (warehouses) {
			return warehouses.containsKey(id);
		}
	}

	// ---- 变更 ----

	@Override
	public Warehouse create(String id) {
		requireValidId(id);
		Warehouse created = new Warehouse(id);
		synchronized (warehouses) {
			if (warehouses.containsKey(id)) throw new IllegalArgumentException("warehouse exists: " + id);
			io.saveWarehouse(created);
			warehouses.put(id, created);
		}
		LOGGER.info("warehouse created: {}", id);
		return created;
	}

	@Override
	public void delete(String id) {
		synchronized (warehouses) {
			if (!warehouses.containsKey(id)) throw new IllegalArgumentException("no such warehouse: " + id);
			warehouses.remove(id);
		}
		io.deleteWarehouse(id);
		if (id.equals(activeId)) {
			activeId = null;
		}
		LOGGER.info("warehouse deleted: {}", id);
	}

	@Override
	public void save(Warehouse warehouse) {
		java.util.Objects.requireNonNull(warehouse, "warehouse");
		requireValidId(warehouse.id);
		synchronized (warehouses) {
			if (!warehouses.containsKey(warehouse.id)) {
				throw new IllegalArgumentException("not managed by this manager: " + warehouse.id);
			}
			warehouses.put(warehouse.id, warehouse);
		}
		io.saveWarehouse(warehouse);
	}

	@Override
	public void reload() {
		ConfigIO.LoadResult result = io.loadAll();
		for (String error : result.errors()) {
			LOGGER.warn("config rejected: {}", error);
		}
		synchronized (warehouses) {
			warehouses.clear();
			for (Warehouse w : result.warehouses()) {
				warehouses.put(w.id, w);
			}
			// 激活目标可能已被拒载/删除
			if (activeId != null && !warehouses.containsKey(activeId)) {
				LOGGER.warn("active warehouse vanished after reload: {}", activeId);
				activeId = null;
			}
		}
	}

	// ---- 激活态 ----

	@Override
	public void activate(@Nullable String id) {
		if (id == null) {
			activeId = null;
			return;
		}
		synchronized (warehouses) {
			if (!warehouses.containsKey(id)) throw new IllegalArgumentException("no such warehouse: " + id);
			activeId = id;
		}
	}

	@Override
	@Nullable
	public Warehouse active() {
		synchronized (warehouses) {
			return activeId != null ? warehouses.get(activeId) : null;
		}
	}

	@Nullable
	public String activeId() {
		return activeId;
	}

	// ---- 内部 ----

	private static void requireValidId(String id) {
		if (id == null || !id.matches("[A-Za-z0-9_\\-.]+")) {
			throw new IllegalArgumentException("illegal warehouse id: " + id);
		}
	}
}

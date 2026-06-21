package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.storage.WarehouseStorage;

public class WarehouseController {

	private static final WarehouseController INSTANCE = new WarehouseController();

	private final WarehouseStorage storage;
	private String activeWarehouse;

	public static WarehouseController getInstance() {
		return INSTANCE;
	}

	private WarehouseController() {
		this.storage = new WarehouseStorage();
	}

	public boolean createWarehouse(String name, BlockPos anchor) {
		if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) {
			return false;
		}
		if (storage.warehouseExists(name)) {
			return false;
		}
		Warehouse w = new Warehouse();
		w.name = name;
		w.anchor = anchor;
		w.active = false;
		w.containers = new ArrayList<>();
		w.rules = new java.util.HashMap<>();
		storage.saveWarehouse(w);
		return true;
	}

	public boolean deleteWarehouse(String name) {
		if (!storage.warehouseExists(name)) {
			return false;
		}
		storage.deleteWarehouse(name);
		if (name.equals(activeWarehouse)) {
			activeWarehouse = null;
		}
		return true;
	}

	public List<String> listWarehouses() {
		return storage.listWarehouses();
	}

	public Warehouse getWarehouse(String name) {
		return storage.loadWarehouse(name);
	}

	public boolean activateWarehouse(String name) {
		if (!storage.warehouseExists(name)) {
			return false;
		}
		activeWarehouse = name;
		return true;
	}

	public void deactivateWarehouse() {
		activeWarehouse = null;
	}

	public String getActiveWarehouse() {
		return activeWarehouse;
	}

	public boolean addContainer(String warehouseName, ContainerInfo info) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null) {
			return false;
		}
		if (w.containers == null) {
			w.containers = new ArrayList<>();
		}
		w.containers.add(info);
		storage.saveWarehouse(w);
		return true;
	}

	public boolean removeContainer(String warehouseName, BlockPos relativePos) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.containers == null) {
			return false;
		}
		boolean removed = w.containers.removeIf(c -> c.relativePos.equals(relativePos));
		if (removed) {
			storage.saveWarehouse(w);
		}
		return removed;
	}

	public boolean setContainerType(String warehouseName, BlockPos relativePos, ContainerType type) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.containers == null) {
			return false;
		}
		for (ContainerInfo c : w.containers) {
			if (c.relativePos.equals(relativePos)) {
				c.type = type;
				c.ruleMode = ContainerInfo.defaultMode(type);
				storage.saveWarehouse(w);
				return true;
			}
		}
		return false;
	}

	public boolean setContainerMode(String warehouseName, BlockPos relativePos, ContainerInfo.RuleMode mode) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.containers == null) {
			return false;
		}
		for (ContainerInfo c : w.containers) {
			if (c.relativePos.equals(relativePos)) {
				c.ruleMode = mode;
				storage.saveWarehouse(w);
				return true;
			}
		}
		return false;
	}

	public ContainerInfo getContainerInfo(String warehouseName, BlockPos relativePos) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.containers == null) {
			return null;
		}
		for (ContainerInfo c : w.containers) {
			if (c.relativePos.equals(relativePos)) {
				return c;
			}
		}
		return null;
	}

	public void showWarehouse(String name) {
		HighlightController.getInstance().showWarehouse(name);
	}

	public void hideWarehouse() {
		HighlightController.getInstance().hideAll();
	}
}

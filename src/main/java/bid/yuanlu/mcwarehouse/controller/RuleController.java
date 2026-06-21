package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.model.rule.ItemRule;
import bid.yuanlu.mcwarehouse.model.rule.ItemRules;
import bid.yuanlu.mcwarehouse.storage.WarehouseStorage;

public class RuleController {

	private static final RuleController INSTANCE = new RuleController();

	private final WarehouseStorage storage;

	public static RuleController getInstance() {
		return INSTANCE;
	}

	private RuleController() {
		this.storage = new WarehouseStorage();
	}

	public List<ItemRules> listRules(String warehouseName) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.rules == null) {
			return Collections.emptyList();
		}
		return new ArrayList<>(w.rules.values());
	}

	public ItemRules getRule(String warehouseName, String ruleName) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.rules == null) {
			return null;
		}
		return w.rules.get(ruleName);
	}

	public boolean createRule(String warehouseName, String ruleName) {
		if (ruleName == null || ruleName.isEmpty()) {
			return false;
		}
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null) {
			return false;
		}
		if (w.rules == null) {
			w.rules = new java.util.HashMap<>();
		}
		if (w.rules.containsKey(ruleName)) {
			return false;
		}
		ItemRules rules = new ItemRules();
		rules.name = ruleName;
		rules.rules = new ArrayList<>();
		w.rules.put(ruleName, rules);
		storage.saveWarehouse(w);
		return true;
	}

	public boolean deleteRule(String warehouseName, String ruleName) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.rules == null) {
			return false;
		}
		ItemRules removed = w.rules.remove(ruleName);
		if (removed != null) {
			storage.saveWarehouse(w);
			return true;
		}
		return false;
	}

	public boolean addRuleItem(String warehouseName, String ruleName, ItemRule itemRule) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.rules == null) {
			return false;
		}
		ItemRules rules = w.rules.get(ruleName);
		if (rules == null) {
			return false;
		}
		if (rules.rules == null) {
			rules.rules = new ArrayList<>();
		}
		rules.rules.add(itemRule);
		storage.saveWarehouse(w);
		return true;
	}

	public boolean removeRuleItem(String warehouseName, String ruleName, int index) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.rules == null) {
			return false;
		}
		ItemRules rules = w.rules.get(ruleName);
		if (rules == null || rules.rules == null || index < 0 || index >= rules.rules.size()) {
			return false;
		}
		rules.rules.remove(index);
		storage.saveWarehouse(w);
		return true;
	}

	public boolean editRuleItem(String warehouseName, String ruleName, int index, ItemRule itemRule) {
		Warehouse w = storage.loadWarehouse(warehouseName);
		if (w == null || w.rules == null) {
			return false;
		}
		ItemRules rules = w.rules.get(ruleName);
		if (rules == null || rules.rules == null || index < 0 || index >= rules.rules.size()) {
			return false;
		}
		rules.rules.set(index, itemRule);
		storage.saveWarehouse(w);
		return true;
	}
}

package bid.yuanlu.mcwarehouse.model;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

import bid.yuanlu.mcwarehouse.model.rule.ItemRules;

public class Warehouse {

	public String name;
	public BlockPos anchor;
	public boolean active;
	public List<ContainerInfo> containers;
	public Map<String, ItemRules> rules;
}

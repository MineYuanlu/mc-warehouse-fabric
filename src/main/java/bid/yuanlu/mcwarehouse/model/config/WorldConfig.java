package bid.yuanlu.mcwarehouse.model.config;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class WorldConfig {

	public Map<String, WorldEntry> sp;
	public Map<String, WorldEntry> mp;

	public static class WorldEntry {
		public Map<String, DimensionEntry> dimensions;
		public InteractionConfig interaction;
		public List<String> pathfinders;
	}

	public static class DimensionEntry {
		@SerializedName("warehouses")
		public Map<String, WarehouseEntry> warehouses;
	}

	public static class WarehouseEntry {
		public boolean enable;
	}

	public static class InteractionConfig {
		public int speed = 2;
	}
}

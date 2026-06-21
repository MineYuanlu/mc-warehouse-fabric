package bid.yuanlu.mcwarehouse.storage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.model.rule.ItemSelector;
import bid.yuanlu.mcwarehouse.model.rule.QuantitySelector;
import bid.yuanlu.mcwarehouse.model.selector.CompositeSelector;
import bid.yuanlu.mcwarehouse.model.selector.IdSelector;
import bid.yuanlu.mcwarehouse.model.selector.NameSelector;
import bid.yuanlu.mcwarehouse.model.selector.NbtSelector;
import bid.yuanlu.mcwarehouse.model.selector.TagSelector;
import bid.yuanlu.mcwarehouse.model.quantifier.CountSelector;
import bid.yuanlu.mcwarehouse.model.quantifier.FillSlotsSelector;
import bid.yuanlu.mcwarehouse.model.quantifier.GroupSelector;
import bid.yuanlu.mcwarehouse.model.quantifier.PercentSelector;
import net.minecraft.client.Minecraft;

public class WarehouseStorage {

	private static final String WAREHOUSE_DIR = "warehouses";
	private static final String DATA_FILE = "data.json";

	private final Gson gson;
	private final File rootDir;

	public WarehouseStorage() {
		this.gson = new GsonBuilder()
				.registerTypeAdapter(ItemSelector.class, new ItemSelectorAdapter())
				.registerTypeAdapter(QuantitySelector.class, new QuantitySelectorAdapter())
				.setPrettyPrinting()
				.create();
		this.rootDir = new File(Minecraft.getInstance().gameDirectory, "mc-warehouse" + File.separator + WAREHOUSE_DIR);
	}

	public List<String> listWarehouses() {
		List<String> names = new ArrayList<>();
		if (!rootDir.exists()) {
			return names;
		}
		File[] dirs = rootDir.listFiles(File::isDirectory);
		if (dirs != null) {
			for (File dir : dirs) {
				names.add(dir.getName());
			}
		}
		return names;
	}

	@Nullable
	public Warehouse loadWarehouse(String name) {
		File file = getDataFile(name);
		if (!file.exists()) {
			return null;
		}
		try (FileReader reader = new FileReader(file)) {
			return gson.fromJson(reader, Warehouse.class);
		} catch (IOException e) {
			return null;
		}
	}

	public void saveWarehouse(Warehouse warehouse) {
		File file = getDataFile(warehouse.name);
		File parent = file.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		try (FileWriter writer = new FileWriter(file)) {
			gson.toJson(warehouse, writer);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save warehouse: " + warehouse.name, e);
		}
	}

	public void deleteWarehouse(String name) {
		File dir = new File(rootDir, name);
		if (!dir.exists()) {
			return;
		}
		deleteRecursively(dir);
	}

	public boolean warehouseExists(String name) {
		return getDataFile(name).exists();
	}

	private File getDataFile(String name) {
		return new File(rootDir, name + File.separator + DATA_FILE);
	}

	private void deleteRecursively(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursively(child);
				}
			}
		}
		file.delete();
	}

	private static class ItemSelectorAdapter implements JsonDeserializer<ItemSelector>, JsonSerializer<ItemSelector> {

		@Override
		public JsonElement serialize(ItemSelector src, Type typeOfSrc, JsonSerializationContext context) {
			if (src instanceof IdSelector) {
				JsonObject obj = context.serialize(src, IdSelector.class).getAsJsonObject();
				obj.addProperty("type", "id");
				return obj;
			}
			if (src instanceof TagSelector) {
				JsonObject obj = context.serialize(src, TagSelector.class).getAsJsonObject();
				obj.addProperty("type", "tag");
				return obj;
			}
			if (src instanceof NbtSelector) {
				JsonObject obj = context.serialize(src, NbtSelector.class).getAsJsonObject();
				obj.addProperty("type", "nbt");
				return obj;
			}
			if (src instanceof NameSelector) {
				JsonObject obj = context.serialize(src, NameSelector.class).getAsJsonObject();
				obj.addProperty("type", "name");
				return obj;
			}
			if (src instanceof CompositeSelector) {
				JsonObject obj = context.serialize(src, CompositeSelector.class).getAsJsonObject();
				obj.addProperty("type", "composite");
				return obj;
			}
			throw new JsonParseException("Unknown ItemSelector type: " + src.getClass());
		}

		@Override
		public ItemSelector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			String type = obj.get("type").getAsString();
			return switch (type) {
				case "id" -> context.deserialize(json, IdSelector.class);
				case "tag" -> context.deserialize(json, TagSelector.class);
				case "nbt" -> context.deserialize(json, NbtSelector.class);
				case "name" -> context.deserialize(json, NameSelector.class);
				case "composite" -> context.deserialize(json, CompositeSelector.class);
				default -> throw new JsonParseException("Unknown ItemSelector type: " + type);
			};
		}
	}

	private static class QuantitySelectorAdapter implements JsonDeserializer<QuantitySelector>, JsonSerializer<QuantitySelector> {

		@Override
		public JsonElement serialize(QuantitySelector src, Type typeOfSrc, JsonSerializationContext context) {
			if (src instanceof CountSelector) {
				JsonObject obj = context.serialize(src, CountSelector.class).getAsJsonObject();
				obj.addProperty("type", "count");
				return obj;
			}
			if (src instanceof GroupSelector) {
				JsonObject obj = context.serialize(src, GroupSelector.class).getAsJsonObject();
				obj.addProperty("type", "group");
				return obj;
			}
			if (src instanceof FillSlotsSelector) {
				JsonObject obj = context.serialize(src, FillSlotsSelector.class).getAsJsonObject();
				obj.addProperty("type", "fill_slots");
				return obj;
			}
			if (src instanceof PercentSelector) {
				JsonObject obj = context.serialize(src, PercentSelector.class).getAsJsonObject();
				obj.addProperty("type", "percent");
				return obj;
			}
			throw new JsonParseException("Unknown QuantitySelector type: " + src.getClass());
		}

		@Override
		public QuantitySelector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			String type = obj.get("type").getAsString();
			return switch (type) {
				case "count" -> context.deserialize(json, CountSelector.class);
				case "group" -> context.deserialize(json, GroupSelector.class);
				case "fill_slots" -> context.deserialize(json, FillSlotsSelector.class);
				case "percent" -> context.deserialize(json, PercentSelector.class);
				default -> throw new JsonParseException("Unknown QuantitySelector type: " + type);
			};
		}
	}
}

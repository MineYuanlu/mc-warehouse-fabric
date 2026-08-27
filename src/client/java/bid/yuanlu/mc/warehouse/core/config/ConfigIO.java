package bid.yuanlu.mc.warehouse.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import org.jetbrains.annotations.NotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * 配置持久化（PDD §11）：
 * <ul>
 *   <li>目录结构：config/yuanlu-warehouse/{warehouses,rules}/…</li>
 *   <li>selector 多态经 {@link SelectorCodecs} 分发（内置与插件同机制）</li>
 *   <li>每个文件顶层携带 schemaVersion；写盘一律临时文件 + 原子移动</li>
 *   <li>读失败回退默认并告警，不静默吞掉；仓库校验失败拒载该文件并列出原因</li>
 * </ul>
 */
public final class ConfigIO {

	public static final int SCHEMA_VERSION = ModConfig.SCHEMA_VERSION;

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("yuanlu-warehouse/config");

	/** 生产环境根目录：config/yuanlu-warehouse */
	public static Path defaultRoot() {
		return FabricLoader.getInstance().getConfigDir().resolve("yuanlu-warehouse");
	}

	private final Path root;
	private final Gson gson;

	public ConfigIO(Path root) {
		this.root = root;
		this.gson = buildGson();
	}

	// ---- 路径 ----

	public Path root() {
		return root;
	}

	public Path warehousesDir() {
		return root.resolve("warehouses");
	}

	public Path rulesDir() {
		return root.resolve("rules");
	}

	// ---- 全局配置 ----

	/** 读 mod.json；缺失/损坏回退默认值并告警（不静默） */
	public ModConfig loadModConfig() {
		Path file = root.resolve("config.json");
		if (!Files.isRegularFile(file)) return new ModConfig();
		try {
			JsonObject rootObj = readJsonObject(file);
			int version = optSchemaVersion(rootObj, file.toString());
			if (version != SCHEMA_VERSION) {
				LOGGER.warn("{}: unsupported schemaVersion {} (expected {}), falling back to defaults", file, version, SCHEMA_VERSION);
				return new ModConfig();
			}
			ModConfig config = gson.fromJson(rootObj, ModConfig.class);
			return config != null ? config : new ModConfig();
		} catch (Exception e) {
			LOGGER.warn("Failed to load {}, using defaults: {}", file, e.toString());
			return new ModConfig();
		}
	}

	public void saveModConfig(ModConfig config) {
		writeWithSchema(root.resolve("config.json"), config);
	}

	// ---- 仓库 ----

	/** 加载结果：成功仓库 + 拒载原因（冲突/损坏/校验失败） */
	public record LoadResult(List<Warehouse> warehouses, List<String> errors) {

		public static final LoadResult EMPTY = new LoadResult(List.of(), List.of());
	}

	/**
	 * 加载全部全局规则 + 仓库（含冲突检测：内嵌规则 ∩ 全局规则 同名即拒载该仓库，§11.3）。
	 */
	public LoadResult loadAll() {
		Map<String, ContainerRule> globalRules = loadGlobalRules();
		List<Warehouse> ok = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		Set<String> globalIds = globalRules.keySet();

		for (Path file : listJsonFiles(warehousesDir())) {
			String name = file.getFileName().toString();
			try {
				Warehouse w = readWarehouse(file);
				if (w == null) continue;
				List<String> problems = ConfigValidator.validate(w, globalIds);
				if (!problems.isEmpty()) {
					errors.add(name + ": rejected — " + String.join("; ", problems));
					continue;
				}
				ok.add(w);
			} catch (Exception e) {
				errors.add(name + ": load failed — " + e.getMessage());
			}
		}
		ok.sort(Comparator.comparing(w -> w.id));
		return new LoadResult(List.copyOf(ok), List.copyOf(errors));
	}

	public void saveWarehouse(Warehouse warehouse) {
		writeWithSchema(warehouseFile(warehouse.id), warehouse);
	}

	public void deleteWarehouse(String id) {
		deleteIfExists(warehouseFile(id));
	}

	private Path warehouseFile(String id) {
		return warehousesDir().resolve(sanitize(id) + ".json");
	}

	// ---- 全局规则 ----

	public Map<String, ContainerRule> loadGlobalRules() {
		Map<String, ContainerRule> out = new LinkedHashMap<>();
		for (Path file : listJsonFiles(rulesDir())) {
			try {
				JsonObject rootObj = readJsonObject(file);
				if (optSchemaVersion(rootObj, file.toString()) != SCHEMA_VERSION) continue;
				ContainerRule rule = gson.fromJson(rootObj, ContainerRule.class);
				if (rule != null && rule.id != null) out.put(rule.id, rule);
			} catch (Exception e) {
				LOGGER.warn("Failed to load rule {}: {}", file, e.toString());
			}
		}
		return out;
	}

	public void saveGlobalRule(ContainerRule rule) {
		writeWithSchema(rulesDir().resolve(sanitize(rule.id) + ".json"), rule);
	}

	public void deleteGlobalRule(String id) {
		deleteIfExists(rulesDir().resolve(sanitize(id) + ".json"));
	}

	// ---- 内部：JSON 基础设施 ----

	private Gson buildGson() {
		Gson treeGson = new Gson();
		TypeAdapter<ItemSelector> itemAdapter = new TypeAdapter<>() {
			@Override
			public void write(JsonWriter out, ItemSelector value) throws IOException {
				JsonElement el = value == null ? com.google.gson.JsonNull.INSTANCE : SelectorCodecs.toJson(value);
				treeGson.toJson(el, out);
			}

			@Override
			public ItemSelector read(JsonReader in) throws IOException {
				JsonElement el = JsonParser.parseReader(in);
				if (el.isJsonNull()) return null;
				return SelectorCodecs.itemFromJson(el.getAsJsonObject());
			}
		};
		TypeAdapter<QuantitySelector> quantityAdapter = new TypeAdapter<>() {
			@Override
			public void write(JsonWriter out, QuantitySelector value) throws IOException {
				JsonElement el = value == null ? com.google.gson.JsonNull.INSTANCE : SelectorCodecs.toJson(value);
				treeGson.toJson(el, out);
			}

			@Override
			public QuantitySelector read(JsonReader in) throws IOException {
				JsonElement el = JsonParser.parseReader(in);
				if (el.isJsonNull()) return null;
				return SelectorCodecs.quantityFromJson(el.getAsJsonObject());
			}
		};
		TypeAdapter<BlockPos> posAdapter = new TypeAdapter<>() {
			@Override
			public void write(JsonWriter out, BlockPos value) throws IOException {
				out.beginObject();
				out.name("x").value(value.getX());
				out.name("y").value(value.getY());
				out.name("z").value(value.getZ());
				out.endObject();
			}

			@Override
			public BlockPos read(JsonReader in) throws IOException {
				int x = 0, y = 0, z = 0;
				in.beginObject();
				while (in.hasNext()) {
					switch (in.nextName()) {
						case "x" -> x = in.nextInt();
						case "y" -> y = in.nextInt();
						case "z" -> z = in.nextInt();
						default -> in.skipValue();
					}
				}
				in.endObject();
				return new BlockPos(x, y, z);
			}
		};
		return new GsonBuilder()
				.registerTypeAdapter(ItemSelector.class, itemAdapter)
				.registerTypeAdapter(QuantitySelector.class, quantityAdapter)
				.registerTypeHierarchyAdapter(BlockPos.class, posAdapter)
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.create();
	}

	@Nullable
	private Warehouse readWarehouse(Path file) throws IOException {
		JsonObject rootObj = readJsonObject(file);
		if (optSchemaVersion(rootObj, file.toString()) != SCHEMA_VERSION) return null;
		Warehouse w = gson.fromJson(rootObj, Warehouse.class);
		if (w == null || w.id == null || w.id.isBlank()) throw new IOException("missing warehouse id");
		fillOmittedWorlds(w);
		return w;
	}

	/** §11.3：pos 条目的 world 可省略——anchors 键中唯一的 worldId 自动补全；多世界歧义则报错 */
	private static void fillOmittedWorlds(Warehouse w) throws IOException {
		Set<String> anchorWorlds = w.anchors.keySet();
		for (ContainerInfo c : w.containers) {
			for (int pi = 0; pi < c.pos.size(); pi++) {
				var pos = c.pos.get(pi);
				if (pos.hasWorld()) continue;
				if (anchorWorlds.size() != 1) {
					throw new IOException("pos without world in multi-world warehouse: " + pos);
				}
				c.pos.set(pi, pos.withWorld(anchorWorlds.iterator().next()));
			}
		}
	}

	private JsonObject readJsonObject(@NotNull Path file) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		JsonElement el = JsonParser.parseString(text);
		if (!el.isJsonObject()) throw new IOException("not a JSON object");
		return el.getAsJsonObject();
	}

	private static int optSchemaVersion(JsonObject root, String name) throws IOException {
		if (!root.has("schemaVersion")) throw new IOException(name + ": missing schemaVersion");
		return root.get("schemaVersion").getAsInt();
	}

	private void writeWithSchema(Path file, Object model) {
		try {
			JsonObject root = new JsonObject();
			root.addProperty("schemaVersion", SCHEMA_VERSION);
			JsonElement body = gson.toJsonTree(model);
			if (body.isJsonObject()) {
				for (var entry : body.getAsJsonObject().entrySet()) {
					root.add(entry.getKey(), entry.getValue());
				}
			}
			byte[] bytes = gson.toJson(root).getBytes(StandardCharsets.UTF_8);
			bid.yuanlu.mc.warehouse.util.AtomicFiles.write(file, bytes);
		} catch (IOException e) {
			throw new UncheckedIOException("save failed: " + file, e);
		}
	}

	private static List<Path> listJsonFiles(Path dir) {
		if (!Files.isDirectory(dir)) return List.of();
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		} catch (IOException e) {
			LOGGER.warn("list failed {}: {}", dir, e.toString());
			return List.of();
		}
	}

	private static void deleteIfExists(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String sanitize(String id) {
		Objects.requireNonNull(id, "id");
		if (!id.matches("[A-Za-z0-9_\\-.]+")) throw new IllegalArgumentException("illegal id: " + id);
		return id;
	}
}

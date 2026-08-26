package bid.yuanlu.mc.warehouse.core.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;

/**
 * DISK 缓存落盘（PDD §11.1）：cache/&lt;worldId&gt;/&lt;dim&gt;__&lt;x_y_z&gt;[_player].json。
 * <p>
 * 按 worldId 分目录，键内含 dim 与坐标——切换世界时按构造即不可能读到其它世界的缓存。
 * 槽位物品以 SNBT 文本存储（{@link StackCodecs}），解码失败视为缓存缺失（自愈重扫兜底）。
 */
public final class DiskCacheStore {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/cache");
	private static final int SCHEMA_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path root;

	public DiskCacheStore(Path configRoot) {
		this.root = configRoot.resolve("cache");
	}

	public Path root() {
		return root;
	}

	public void save(CacheKey key, ContainerSnapshot snapshot) {
		try {
			Path file = fileOf(key);
			Files.createDirectories(file.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("schemaVersion", SCHEMA_VERSION);
			root.addProperty("worldId", key.worldId());
			root.addProperty("dim", key.dimId());
			root.addProperty("x", key.pos().x());
			root.addProperty("y", key.pos().y());
			root.addProperty("z", key.pos().z());
			if (key.player() != null) root.addProperty("player", key.player().toString());
			root.addProperty("title", snapshot.title());
			root.addProperty("slotCount", snapshot.slotCount());

			JsonObject slots = new JsonObject();
			snapshot.slots().forEach((slot, stack) -> {
				String snbt = StackCodecs.encode(stack);
				if (snbt != null) slots.addProperty(String.valueOf(slot), snbt);
			});
			root.add("slots", slots);

			JsonObject infos = new JsonObject();
			for (var e : snapshot.slotInfos().entrySet()) {
				JsonObject info = new JsonObject();
				info.addProperty("role", e.getValue().role().name());
				info.addProperty("take", e.getValue().canTakeFrom());
				info.addProperty("put", e.getValue().canPutTo());
				infos.add(String.valueOf(e.getKey()), info);
			}
			root.add("slotInfos", infos);

			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("disk cache save failed {}: {}", key, e.toString());
		}
	}

	public Optional<ContainerSnapshot> load(CacheKey key) {
		Path file = fileOf(key);
		if (!Files.isRegularFile(file)) return Optional.empty();
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			if (root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) return Optional.empty();
			// 双保险：文件内容与请求键必须一致
			if (!key.worldId().equals(root.get("worldId").getAsString())
					|| !key.dimId().equals(root.get("dim").getAsString())) return Optional.empty();

			Map<Integer, net.minecraft.world.item.ItemStack> slots = new LinkedHashMap<>();
			JsonObject slotsJson = root.getAsJsonObject("slots");
			for (var e : slotsJson.entrySet()) {
				var stack = StackCodecs.decode(e.getValue().getAsString());
				if (stack != null) slots.put(Integer.parseInt(e.getKey()), stack);
			}

			Map<Integer, SlotInfo> infos = new LinkedHashMap<>();
			if (root.has("slotInfos")) {
				for (var e : root.getAsJsonObject("slotInfos").entrySet()) {
					JsonObject info = e.getValue().getAsJsonObject();
					infos.put(Integer.parseInt(e.getKey()), SlotInfo.of(
							bid.yuanlu.mc.warehouse.api.container.SlotRole.valueOf(info.get("role").getAsString()),
							info.get("take").getAsBoolean(),
							info.get("put").getAsBoolean()));
				}
			}
			return Optional.of(new ContainerSnapshot(slots, infos,
					root.get("title").getAsString(), root.get("slotCount").getAsInt()));
		} catch (Exception e) {
			LOGGER.warn("disk cache load failed {}: {}", key, e.toString());
			return Optional.empty();
		}
	}

	public void delete(CacheKey key) {
		try {
			Files.deleteIfExists(fileOf(key));
		} catch (IOException e) {
			LOGGER.warn("disk cache delete failed {}: {}", key, e.toString());
		}
	}

	/** 清空某 world 的全部 DISK 缓存；返回删除数 */
	public int clearWorld(String worldId) {
		Path dir = root.resolve(StackCodecs.sanitizeWorldDir(worldId));
		if (!Files.isDirectory(dir)) return 0;
		int[] removed = {0};
		try (var stream = Files.walk(dir)) {
			stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
				try {
					Files.deleteIfExists(p);
					removed[0]++;
				} catch (IOException ignored) {
				}
			});
		} catch (IOException e) {
			LOGGER.warn("clearWorld failed {}: {}", worldId, e.toString());
		}
		return removed[0] > 0 ? removed[0] - 1 : 0; // 减去目录自身
	}

	private Path fileOf(CacheKey key) {
		String name = StackCodecs.sanitizeWorldDir(key.dimId()) + "__"
				+ key.pos().x() + "_" + key.pos().y() + "_" + key.pos().z()
				+ (key.player() != null ? "_" + key.player() : "") + ".json";
		return root.resolve(StackCodecs.sanitizeWorldDir(key.worldId())).resolve(name);
	}
}

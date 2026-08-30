package bid.yuanlu.mc.warehouse.core.world;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import bid.yuanlu.mc.warehouse.util.AtomicFiles;

/**
 * world-map.json 持久化（PDD §4.4）：serverId → worldName → worldId 的玩家可控映射。
 * <p>
 * 独立于 config.json——换游戏（服务器世界下载到本地/搬迁区块数据）时只改这一个文件，
 * 即可复用全部仓库。保插入序（LinkedHashMap），同 worldId 多别名取首个人为激活名。
 */
public final class WorldMapIO {

	public static final int SCHEMA_VERSION = 1;

	public static final class Data {
		public int schemaVersion = SCHEMA_VERSION;

		/** serverId → (worldName → worldId) */
		public Map<String, LinkedHashMap<String, String>> servers = new LinkedHashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private WorldMapIO() {
	}

	public static Data load(Path file) {
		if (!Files.isRegularFile(file)) return new Data();
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : -1;
			if (version != SCHEMA_VERSION) {
				throw new IOException("unsupported schemaVersion " + version + " (expected " + SCHEMA_VERSION + ")");
			}
			Data data = GSON.fromJson(root, Data.class);
			return data != null ? data : new Data();
		} catch (Exception e) {
			throw new UncheckedIOException("world-map load failed: " + file, e instanceof IOException io ? io : new IOException(e));
		}
	}

	public static void save(Path file, Data data) {
		JsonObject root = GSON.toJsonTree(data).getAsJsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		try {
			AtomicFiles.write(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("world-map save failed: " + file, e);
		}
	}
}

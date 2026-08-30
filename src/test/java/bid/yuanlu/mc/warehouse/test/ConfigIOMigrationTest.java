package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;

/**
 * schema v1→v2 迁移（PDD §11.2）：anchors 三层化、pos.world 旧值归一、config.json worlds→servers。
 */
public class ConfigIOMigrationTest extends McBootstrap {

	@TempDir
	Path tempDir;

	private ConfigIO io;

	@BeforeAll
	static void installCodecs() {
		TestCodecs.install();
	}

	@BeforeEach
	void setup() {
		io = new ConfigIO(tempDir.resolve("yuanlu-warehouse"));
	}

	@Test
	void warehouseV1AnchorsAndPosMigrate() throws Exception {
		String json = """
				{
				  "schemaVersion": 1,
				  "id": "old",
				  "anchors": {
				    "singleplayer:New World": {
				      "minecraft:overworld": { "x": 1, "y": 2, "z": 3 }
				    }
				  },
				  "containers": [
				    {
				      "pos": [
				        { "world": "singleplayer:New World", "dim": "minecraft:overworld", "x": 1, "y": 0, "z": 1 }
				      ],
				      "ioType": "INPUT",
				      "rules": [],
				      "cacheType": "MEMORY",
				      "priority": { "hard": 0, "soft": 0 }
				    }
				  ],
				  "rules": {}
				}
				""";
		Files.createDirectories(io.warehousesDir());
		Files.writeString(io.warehousesDir().resolve("old.json"), json);

		var result = io.loadAll();
		assertEquals(1, result.warehouses().size(), () -> "errors: " + result.errors());
		var w = result.warehouses().getFirst();

		// anchors：旧 worldId 键升为 serverId 层，插入 worldName 层 ""
		assertTrue(w.anchors.containsKey("singleplayer:New World"));
		assertTrue(w.anchors.get("singleplayer:New World").containsKey(""),
				"迁移插入缺省 worldName 层");

		// pos.world 旧值（= 旧 anchor 键）→ ""
		var pos = w.containers.getFirst().pos.getFirst();
		assertEquals("", pos.world());
	}

	@Test
	void warehouseV1HashWorldFallsBackToSuffix() throws Exception {
		String json = """
				{
				  "schemaVersion": 1,
				  "id": "old2",
				  "anchors": {
				    "mp:srv:25565": { "minecraft:overworld": { "x": 0, "y": 0, "z": 0 } }
				  },
				  "containers": [
				    {
				      "pos": [
				        { "world": "mp:srv:25565#lobby", "dim": "minecraft:overworld", "x": 0, "y": 0, "z": 0 }
				      ],
				      "ioType": "OUTPUT",
				      "rules": [],
				      "cacheType": "MEMORY",
				      "priority": { "hard": 0, "soft": 0 }
				    }
				  ],
				  "rules": {}
				}
				""";
		Files.createDirectories(io.warehousesDir());
		Files.writeString(io.warehousesDir().resolve("old2.json"), json);

		var result = io.loadAll();
		assertEquals(1, result.warehouses().size(), () -> "errors: " + result.errors());
		var pos = result.warehouses().getFirst().containers.getFirst().pos.getFirst();
		assertEquals("lobby", pos.world(), "含 # 的旧 world 取后缀为 worldName");
	}

	@Test
	void configV1WorldsRenamedToServers() throws Exception {
		String json = """
				{
				  "schemaVersion": 1,
				  "debug": true,
				  "worlds": {
				    "mp:mc.example.com:25565": {
				      "dimensions": {
				        "minecraft:overworld": { "interactionSpeed": 3 }
				      }
				    }
				  }
				}
				""";
		Files.createDirectories(io.root());
		Files.writeString(io.root().resolve("config.json"), json);

		ModConfig config = io.loadModConfig();
		assertTrue(config.debug);
		assertTrue(config.servers.containsKey("mp:mc.example.com:25565"), "worlds → servers");
		assertEquals(3, config.servers.get("mp:mc.example.com:25565")
				.dimensions.get("minecraft:overworld").interactionSpeed);
	}

	@Test
	void unknownConfigVersionStillFallsBackToDefaults() throws Exception {
		Files.createDirectories(io.root());
		Files.writeString(io.root().resolve("config.json"), "{\"schemaVersion\":99}");
		ModConfig config = io.loadModConfig();
		assertEquals(2, config.defaultInteractionSpeed, "未知版本回退默认值");
		assertEquals(List.of(), List.copyOf(config.servers.keySet()));
	}
}

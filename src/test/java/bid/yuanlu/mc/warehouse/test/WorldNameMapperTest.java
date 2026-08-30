package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.core.world.WorldMapIO;
import bid.yuanlu.mc.warehouse.core.world.WorldNameMapper;

/**
 * worldName ↔ worldId 映射（PDD §4.4）：自动建映射、别名、改名、持久化往返。
 */
public class WorldNameMapperTest {

	@TempDir
	Path tempDir;

	@Test
	void resolveActiveAutoCreatesAndPersists() {
		Path file = tempDir.resolve("world-map.json");
		var mapper = new WorldNameMapper(file, (serverId, worldId) -> serverId + ":" + worldId);

		assertEquals("mp:a:25565:", mapper.resolveActive("mp:a:25565", ""),
				"首次解析自动创建默认条目");

		// 重新加载：条目已持久化
		var reloaded = new WorldNameMapper(file, (s, w) -> "x");
		assertEquals("mp:a:25565:", reloaded.resolveActive("mp:a:25565", ""));
		assertEquals("mp:a:25565:", reloaded.resolveActive("mp:a:25565", ""), "再次解析命中既有条目");
	}

	@Test
	void aliasReturnsFirstAndRenameWorks() {
		var mapper = new WorldNameMapper(tempDir.resolve("world-map.json"), (s, w) -> w);
		assertEquals("w1", mapper.resolveActive("mp:srv", "w1"));
		mapper.rename("mp:srv", "w1", "大厅");
		assertEquals("大厅", mapper.resolveActive("mp:srv", "w1"), "改名后激活名跟随");
		assertNull(mapper.worldIdOf("mp:srv", "w1"));

		// 第二个别名映射到同一 worldId → 激活名仍取插入序第一个
		assertEquals("大厅", mapper.resolveActive("mp:srv", "w1"));
	}

	@Test
	void renameValidation() {
		var mapper = new WorldNameMapper(tempDir.resolve("world-map.json"), (s, w) -> w);
		mapper.resolveActive("srv", "w");
		assertThrows(IllegalArgumentException.class, () -> mapper.rename("srv", "nope", "x"));
		assertThrows(IllegalArgumentException.class, () -> mapper.rename("srv", "w", "w"));
		assertThrows(IllegalArgumentException.class, () -> mapper.rename("srv", "w", " "));
	}

	@Test
	void defaultNameCollisionGetsSuffix() {
		var mapper = new WorldNameMapper(tempDir.resolve("world-map.json"), (s, w) -> "same");
		assertEquals("same", mapper.resolveActive("srv", "w1"));
		assertEquals("same#2", mapper.resolveActive("srv", "w2"), "默认名冲突追加后缀");
	}

	@Test
	void migrationRebindsEmptyEntriesPreservingName() {
		Path file = tempDir.resolve("world-map.json");
		var mapper = new WorldNameMapper(file, (s, w) -> "旧名");
		// v0.4 遗留：世界绑定缺省 ""
		assertEquals("旧名", mapper.resolveActive("srv", ""), "首次解析自动创建默认条目");

		// v0.5 升级：服务端推送新文件 id → "" 条目重绑，激活名不变（锚点不断链）
		assertEquals("旧名", mapper.resolveActive("srv", "12345"), "迁移后旧 worldName 保留");
		assertEquals(0, mapper.migrateEmptyBindings("srv", "12345"), "迁移幂等：无 \"\" 条目时 no-op");
		assertEquals("12345", mapper.worldIdOf("srv", "旧名"));
		assertEquals(1, mapper.worlds("srv").size(), "不产生新条目");

		// 持久化
		var reloaded = new WorldNameMapper(file, (s, w) -> w);
		assertEquals("旧名", reloaded.resolveActive("srv", "12345"));
	}

	@Test
	void migrationSkipsEmptyWorldIdAndOtherServers() {
		var mapper = new WorldNameMapper(tempDir.resolve("world-map.json"), (s, w) -> w);
		mapper.resolveActive("srv", "");
		assertEquals(0, mapper.migrateEmptyBindings("srv", ""), "worldId 为空拒绝迁移");
		assertEquals("", mapper.worldIdOf("srv", ""));
		mapper.resolveActive("other", "");
		assertEquals(1, mapper.migrateEmptyBindings("srv", "99"), "迁移目标 serverId");
		assertEquals("99", mapper.worldIdOf("srv", ""));
		assertEquals("", mapper.worldIdOf("other", ""), "不迁移其他 serverId");
	}

	@Test
	void bindCreatesAndOverrides() {
		Path file = tempDir.resolve("world-map.json");
		var mapper = new WorldNameMapper(file, (s, w) -> w);

		mapper.bind("srv", "新世界", "777");
		assertEquals("777", mapper.worldIdOf("srv", "新世界"));

		mapper.bind("srv", "新世界", "888");
		assertEquals("888", mapper.worldIdOf("srv", "新世界"), "同名换绑覆盖");

		assertThrows(IllegalArgumentException.class, () -> mapper.bind("srv", " ", "1"));
		assertThrows(IllegalArgumentException.class, () -> mapper.bind("srv", "x", ""));
		assertThrows(IllegalArgumentException.class, () -> mapper.bind("srv", "x", null));

		var reloaded = new WorldNameMapper(file, (s, w) -> w);
		assertEquals("888", reloaded.worldIdOf("srv", "新世界"), "绑定已持久化");
	}

	@Test
	void ioRoundTripPreservesOrder() {
		Path file = tempDir.resolve("world-map.json");
		var mapper = new WorldNameMapper(file, (s, w) -> w);
		mapper.resolveActive("srv", "w1");
		mapper.resolveActive("srv", "w2");

		var reloaded = new WorldNameMapper(file, (s, w) -> w);
		var worlds = reloaded.worlds("srv");
		assertEquals(2, worlds.size());
		assertEquals(Map.entry("w1", "w1"), worlds.get(0));
		assertEquals(Map.entry("w2", "w2"), worlds.get(1));
		assertEquals(WorldMapIO.SCHEMA_VERSION,
				com.google.gson.JsonParser.parseString(read(file))
						.getAsJsonObject().get("schemaVersion").getAsInt());
		assertTrue(read(file).contains("\"servers\""));
	}

	private static String read(Path file) {
		try {
			return java.nio.file.Files.readString(file);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

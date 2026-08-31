package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.world.WorldNameMapper;
import bid.yuanlu.mc.warehouse.core.world.WorldSession;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * CacheKey worldId 解析（PDD §3.8）：canonicalPos 的 worldName 经会话/映射反查为
 * 物理 worldId；解析不出时回退 worldName。
 */
public class CacheKeyTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		WorldNameMapper.setInstance(null);
		WorldSessionTracker.setInstance(null);
	}

	@AfterEach
	void tearDown() {
		WorldNameMapper.setInstance(null);
		WorldSessionTracker.setInstance(null);
	}

	private static WorldDimPos pos(String world) {
		return new WorldDimPos(world, "minecraft:overworld", 1, 64, -2);
	}

	/** 当前会话世界：直接取会话 worldId */
	@Test
	void sessionWorldUsesWorldId() {
		WorldNameMapper mapper = new WorldNameMapper(tempDir.resolve("world-map.json"), (s, w) -> w);
		WorldNameMapper.setInstance(mapper);
		WorldSessionTracker tracker = new WorldSessionTracker(List.of(), List.of(), mapper);
		tracker.update(new WorldSession("singleplayer:demo", "42", "world"));
		WorldSessionTracker.setInstance(tracker);

		assertEquals("42", CacheKey.of(pos("world"), null).worldId());
	}

	/** 非会话世界：反查 world-map（跨世界容器的缓存隔离） */
	@Test
	void otherWorldResolvesViaMapper() {
		WorldNameMapper mapper = new WorldNameMapper(tempDir.resolve("world-map.json"), (s, w) -> w);
		mapper.bind("singleplayer:demo", "nether", "99");
		WorldNameMapper.setInstance(mapper);
		WorldSessionTracker tracker = new WorldSessionTracker(List.of(), List.of(), mapper);
		tracker.update(new WorldSession("singleplayer:demo", "42", "world"));
		WorldSessionTracker.setInstance(tracker);

		assertEquals("99", CacheKey.of(pos("nether"), null).worldId());
	}

	/** 未映射的 worldName 与多人无服务端 mod 的空 worldId：回退 worldName 隔离 */
	@Test
	void unmappedOrEmptyFallsBackToWorldName() {
		WorldNameMapper mapper = new WorldNameMapper(tempDir.resolve("world-map.json"), (s, w) -> w);
		WorldNameMapper.setInstance(mapper);
		WorldSessionTracker tracker = new WorldSessionTracker(List.of(), List.of(), mapper);
		tracker.update(new WorldSession("mp:host:25565", "", "world"));
		WorldSessionTracker.setInstance(tracker);

		assertEquals("world", CacheKey.of(pos("world"), null).worldId());
		assertEquals("floating", CacheKey.of(pos("floating"), null).worldId());
	}

	/** tracker/mapper 未装配（无客户端环境）：回退 worldName */
	@Test
	void uninitializedFallsBackToWorldName() {
		assertEquals("world", CacheKey.of(pos("world"), null).worldId());
	}
}

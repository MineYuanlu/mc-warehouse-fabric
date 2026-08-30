package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.api.world.ServerIdentifier;
import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import bid.yuanlu.mc.warehouse.core.world.WorldNameMapper;
import bid.yuanlu.mc.warehouse.core.world.WorldSession;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 会话切换检测（PDD §4.1）：serverId/worldId 变化视为会话切换并广播；同值不触发；
 * SPI 首个非 null 生效，缺省 {@code ""}。
 */
public class WorldSessionTrackerTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void resetMapper() {
		WorldNameMapper.setInstance(null);
	}

	private WorldNameMapper mapper() {
		WorldNameMapper mapper = new WorldNameMapper(tempDir.resolve("world-map.json"),
				(serverId, worldId) -> serverId + ":" + worldId);
		WorldNameMapper.setInstance(mapper);
		return mapper;
	}

	private static ServerIdentifier server(@Nullable String id) {
		return () -> id;
	}

	private static WorldIdentifier fixed(String id, @Nullable String world) {
		return new WorldIdentifier() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public String currentWorldId() {
				return world;
			}
		};
	}

	private record Change(@Nullable WorldSession oldSession, @Nullable WorldSession newSession) {
	}

	private static WorldSession session(String serverId, String worldId, String worldName) {
		return new WorldSession(serverId, worldId, worldName);
	}

	@Test
	void sessionTransitionsFireListeners() {
		var tracker = new WorldSessionTracker(List.of(server("singleplayer")), List.of(), mapper());
		List<Change> changes = new ArrayList<>();
		tracker.addListener((o, n) -> changes.add(new Change(o, n)));

		tracker.update(null);
		assertTrue(changes.isEmpty(), "初始即 null 不算切换");

		tracker.update(session("singleplayer:world_a", "", "world_a"));
		assertEquals(1, changes.size());
		assertEquals(new Change(null, session("singleplayer:world_a", "", "world_a")), changes.getFirst());

		tracker.update(session("singleplayer:world_a", "", "world_a"));
		assertEquals(1, changes.size(), "同值不重复广播");

		tracker.update(session("singleplayer:world_b", "", "world_b"));
		assertEquals(2, changes.size());

		tracker.update(null);
		assertEquals(3, changes.size());
		assertNull(changes.get(2).newSession());

		assertNull(tracker.currentSession());
	}

	@Test
	void resolutionPrefersFirstNonNullThenDefault() {
		var tracker = new WorldSessionTracker(
				List.of(server(null), server("mp:host:25565")),
				List.of(fixed("sp", null), fixed("pushed", "abc"), fixed("plugin", "x")),
				mapper());
		tracker.tick();
		assertEquals("abc", tracker.currentWorldId(), "SPI 首个非 null 生效");

		var fallback = new WorldSessionTracker(
				List.of(server("mp:host:25565")),
				List.of(fixed("a", null), fixed("b", null)),
				mapper());
		fallback.tick();
		assertEquals("", fallback.currentWorldId(), "全部不适用 → 缺省 \"\"");
		assertEquals("mp:host:25565:", fallback.currentWorldName(), "worldName 经 mapper 生成");
	}

	@Test
	void noSessionWhenNoServer() {
		var tracker = new WorldSessionTracker(List.of(server(null)), List.of(), mapper());
		tracker.tick();
		assertNull(tracker.currentSession());
		assertNull(tracker.currentWorldId());
	}

	@Test
	void listenerRemovalWorks() {
		var tracker = new WorldSessionTracker(List.of(server("sp")), List.of(), mapper());
		List<Change> changes = new ArrayList<>();
		WorldSessionTracker.SessionListener listener = (o, n) -> changes.add(new Change(o, n));
		tracker.addListener(listener);
		tracker.removeListener(listener);
		tracker.update(session("singleplayer:a", "", "a"));
		assertTrue(changes.isEmpty());
	}
}

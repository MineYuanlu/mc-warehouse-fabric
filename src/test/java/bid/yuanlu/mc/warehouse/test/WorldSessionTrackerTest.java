package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 会话切换检测（PDD §4.1）：worldId 变化视为会话切换并广播；同值不触发。
 */
public class WorldSessionTrackerTest {

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

	private record Change(@Nullable String oldId, @Nullable String newId) {
	}

	@Test
	void sessionTransitionsFireListeners() {
		var tracker = new WorldSessionTracker(List.of(fixed("singleplayer", null)));
		List<Change> changes = new ArrayList<>();
		tracker.addListener((o, n) -> changes.add(new Change(o, n)));

		tracker.update(null);
		assertTrue(changes.isEmpty(), "初始即 null 不算切换");

		tracker.update("singleplayer:world_a");
		assertEquals(1, changes.size());
		assertEquals(new Change(null, "singleplayer:world_a"), changes.getFirst());

		tracker.update("singleplayer:world_a");
		assertEquals(1, changes.size(), "同值不重复广播");

		tracker.update("singleplayer:world_b");
		assertEquals(2, changes.size());
		assertEquals(new Change("singleplayer:world_a", "singleplayer:world_b"), changes.get(1));

		tracker.update(null);
		assertEquals(3, changes.size());
		assertEquals(new Change("singleplayer:world_b", null), changes.get(2));

		assertNull(tracker.currentWorldId());
	}

	@Test
	void resolutionPrefersFirstNonNull() {
		var tracker = new WorldSessionTracker(List.of(
				fixed("sp", null),
				fixed("mp", "mp:host:25565"),
				fixed("plugin", "plugin:x")));
		tracker.update(null); // 触发解析：sp 返回 null → 取 mp
		tracker.tick();
		assertEquals("mp:host:25565", tracker.currentWorldId());

		var allNull = new WorldSessionTracker(List.of(fixed("a", null), fixed("b", null)));
		allNull.tick();
		assertNull(allNull.currentWorldId());
	}

	@Test
	void listenerRemovalWorks() {
		var tracker = new WorldSessionTracker(List.of(fixed("sp", null)));
		List<Change> changes = new ArrayList<>();
		WorldSessionTracker.SessionListener listener = (o, n) -> changes.add(new Change(o, n));
		tracker.addListener(listener);
		tracker.removeListener(listener);
		tracker.update("singleplayer:a");
		assertTrue(changes.isEmpty());
	}
}

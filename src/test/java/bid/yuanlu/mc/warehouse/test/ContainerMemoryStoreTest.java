package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.cache.ContainerMemory;
import bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.world.WorldSession;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 三级缓存语义（PDD §3.8/§5.4）：键隔离、NONE 轮次生命周期、TTL、自愈失效、会话清理。
 */
public class ContainerMemoryStoreTest {

	private static CacheKey key(String world, String dim, int x) {
		return new CacheKey(world, dim, new CacheKey.BlockPosLike(x, 64, 0), null);
	}

	private static ContainerSnapshot snapshot() {
		return new ContainerSnapshot(java.util.Map.of(), java.util.Map.of(), "test", 27);
	}

	@Test
	void keysAreIsolatedByWorldDimPlayer() {
		var sessions = new WorldSessionTracker(List.of());
		var store = new ContainerMemoryStore(new ModConfig(), sessions);
		CacheKey a = key("w1", "overworld", 1);
		CacheKey b = key("w2", "overworld", 1);
		CacheKey c = key("w1", "the_nether", 1);

		store.remember(a, CacheType.MEMORY, snapshot());
		assertFalse(store.isExplored(b), "不同 world 不串数据");
		assertFalse(store.isExplored(c));

		store.remember(b, CacheType.MEMORY, snapshot());
		assertEquals(2, store.size());

		var uuid = java.util.UUID.randomUUID();
		var ender = new CacheKey("w1", "overworld", new CacheKey.BlockPosLike(1, 64, 0), uuid);
		store.remember(ender, CacheType.MEMORY, snapshot());
		assertEquals(3, store.size(), "player 维度区分末影箱");
		assertNotNull(store.getValid(ender));
	}

	@Test
	void noneCacheOnlyValidWithinRound() {
		var sessions = new WorldSessionTracker(List.of());
		var store = new ContainerMemoryStore(new ModConfig(), sessions);
		CacheKey k = key("w", "d", 0);

		store.remember(k, CacheType.NONE, snapshot());
		assertNull(store.getValid(k), "轮次外 NONE 无效");

		store.beginRound();
		assertNotNull(store.getValid(k), "轮次内 NONE 可用");
		store.endRound();
		assertNull(store.getValid(k), "轮次结束即清除");
		assertEquals(0, store.size());
	}

	@Test
	void memoryCacheSurvivesRoundsUntilSessionSwitch() {
		var sessions = new WorldSessionTracker(List.of());
		var store = new ContainerMemoryStore(new ModConfig(), sessions);
		CacheKey k = key("w", "d", 0);

		store.remember(k, CacheType.MEMORY, snapshot());
		store.beginRound();
		store.endRound();
		assertNotNull(store.getValid(k), "MEMORY 不随轮次清除");

		sessions.update(null); // 会话切换（进入世界）
		sessions.update(new WorldSession("singleplayer:abc", "", "abc"));
		assertNull(store.getValid(k), "worldId 变化清空全部缓存");
		assertEquals(0, store.size());
	}

	@Test
	void ttlExpiryForcesRescan() {
		var sessions = new WorldSessionTracker(List.of());
		ModConfig config = new ModConfig();
		config.cacheTtlSeconds = 10;
		AtomicLong clock = new AtomicLong(1_000_000);
		var store = new ContainerMemoryStore(config, sessions, clock::get);
		CacheKey k = key("w", "d", 0);

		store.remember(k, CacheType.DISK, snapshot());
		assertNotNull(store.getValid(k));

		clock.addAndGet(9_999);
		assertNotNull(store.getValid(k), "TTL 内有效");

		clock.addAndGet(2_000);
		assertNull(store.getValid(k), "超期强制重扫");
		// 但条目仍占位（explored 状态保留，引擎据此重扫）
		assertTrue(store.isExplored(k));
	}

	@Test
	void selfHealInvalidation() {
		var sessions = new WorldSessionTracker(List.of());
		var store = new ContainerMemoryStore(new ModConfig(), sessions);
		CacheKey k = key("w", "d", 0);

		store.remember(k, CacheType.MEMORY, snapshot());
		store.invalidate(k);
		assertNull(store.getValid(k));
		assertFalse(store.isExplored(k), "失效后回到未探索状态");
	}

	@Test
	void allValidFiltersExpired() {
		var sessions = new WorldSessionTracker(List.of());
		ModConfig config = new ModConfig();
		config.cacheTtlSeconds = 5;
		AtomicLong clock = new AtomicLong(0);
		var store = new ContainerMemoryStore(config, sessions, clock::get);

		store.remember(key("w", "d", 0), CacheType.MEMORY, snapshot());
		store.remember(key("w", "d", 1), CacheType.MEMORY, snapshot());
		assertEquals(2, store.allValid().size());

		clock.addAndGet(60_000);
		assertEquals(0, store.allValid().size());
	}
}

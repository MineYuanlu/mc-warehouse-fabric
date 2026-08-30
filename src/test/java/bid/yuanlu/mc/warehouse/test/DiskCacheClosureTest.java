package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore;
import bid.yuanlu.mc.warehouse.core.cache.DiskCacheStore;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.world.WorldSession;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * DISK 缓存闭环（PDD §3.8）：落盘、跨实例回源、双清、TTL、MEMORY 不落盘。
 */
public class DiskCacheClosureTest extends McBootstrap {

	@TempDir
	Path tempDir;

	private static CacheKey key(int x) {
		return new CacheKey("w", "minecraft:overworld", new CacheKey.BlockPosLike(x, 64, 0), null);
	}

	private static ContainerSnapshot snapshot(int count) {
		return new ContainerSnapshot(Map.of(0, new ItemStack(Items.IRON_INGOT, count)), Map.of(), "box", 27);
	}

	@Test
	void diskRememberPersistsAndReloadsAcrossInstances() {
		var disk = new DiskCacheStore(tempDir);
		var sessions = new WorldSessionTracker(List.of());
		var store1 = new ContainerMemoryStore(new ModConfig(), sessions, disk);
		CacheKey k = key(1);

		store1.remember(k, CacheType.DISK, snapshot(12));
		assertNotNull(store1.getValid(k, CacheType.DISK));

		// 新实例（模拟进程重启）：内存为空，磁盘回源
		var store2 = new ContainerMemoryStore(new ModConfig(), new WorldSessionTracker(List.of()), disk);
		assertTrue(store2.isExplored(k, CacheType.DISK), "重启后 DISK 容器仍算已探索");
		var mem = store2.getValid(k, CacheType.DISK);
		assertNotNull(mem, "磁盘回源回填内存");
		assertEquals(12, mem.snapshot().slots().get(0).getCount());
	}

	@Test
	void memoryTypeDoesNotTouchDisk() {
		var disk = new DiskCacheStore(tempDir);
		var store = new ContainerMemoryStore(new ModConfig(), new WorldSessionTracker(List.of()), disk);
		CacheKey k = key(2);

		store.remember(k, CacheType.MEMORY, snapshot(1));
		assertEquals(0, countFiles(), "MEMORY 不落盘");

		var fresh = new ContainerMemoryStore(new ModConfig(), new WorldSessionTracker(List.of()), disk);
		assertFalse(fresh.isExplored(k, CacheType.MEMORY), "MEMORY 重启即失");
		assertNull(fresh.getValid(k, CacheType.MEMORY));
	}

	@Test
	void invalidateClearsBothMemoryAndDisk() {
		var disk = new DiskCacheStore(tempDir);
		var store = new ContainerMemoryStore(new ModConfig(), new WorldSessionTracker(List.of()), disk);
		CacheKey k = key(3);

		store.remember(k, CacheType.DISK, snapshot(5));
		store.invalidate(k);

		assertEquals(0, countFiles(), "invalidate 双清磁盘");
		var fresh = new ContainerMemoryStore(new ModConfig(), new WorldSessionTracker(List.of()), disk);
		assertFalse(fresh.isExplored(k, CacheType.DISK), "自愈失效跨进程生效");
	}

	@Test
	void ttlExpiryStillForcesRescanWithDisk() {
		var disk = new DiskCacheStore(tempDir);
		ModConfig config = new ModConfig();
		config.cacheTtlSeconds = 10;
		AtomicLong clock = new AtomicLong(1_000_000);
		var store = new ContainerMemoryStore(config, new WorldSessionTracker(List.of()), clock::get, disk);
		CacheKey k = key(4);

		store.remember(k, CacheType.DISK, snapshot(7));
		clock.addAndGet(20_000);

		assertNull(store.getValid(k, CacheType.DISK), "TTL 超期强制重扫（磁盘数据同龄）");
	}

	@Test
	void diskBackfillSurvivesSessionSwitchOnDiskSide() {
		var disk = new DiskCacheStore(tempDir);
		var sessions = new WorldSessionTracker(List.of());
		var store = new ContainerMemoryStore(new ModConfig(), sessions, disk);
		CacheKey k = key(5);

		store.remember(k, CacheType.DISK, snapshot(9));
		sessions.update(null);
		sessions.update(new WorldSession("other", "", "other"));
		assertEquals(0, store.size(), "会话切换清内存（§5.4）");

		var fresh = new ContainerMemoryStore(new ModConfig(), new WorldSessionTracker(List.of()), disk);
		assertNotNull(fresh.getValid(k, CacheType.DISK), "磁盘侧按 worldId 隔离持久存在");
	}

	private long countFiles() {
		try (var walk = java.nio.file.Files.walk(tempDir)) {
			return walk.filter(p -> p.toString().endsWith(".json")).count();
		} catch (Exception e) {
			return -1;
		}
	}
}

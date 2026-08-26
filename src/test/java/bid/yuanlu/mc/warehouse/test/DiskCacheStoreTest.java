package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.cache.DiskCacheStore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * DISK 缓存落盘（PDD §11.1）：按 worldId 分目录、SNBT 载荷、损坏回退。
 */
public class DiskCacheStoreTest extends McBootstrap {

	@TempDir
	Path tempDir;

	private static CacheKey key(String world, String dim, int x, UUID player) {
		return new CacheKey(world, dim, new CacheKey.BlockPosLike(x, 64, -3), player);
	}

	@Test
	void snapshotRoundTrip() {
		var store = new DiskCacheStore(tempDir);
		CacheKey k = key("singleplayer:demo", "minecraft:overworld", 10, null);

		Map<Integer, ItemStack> slots = Map.of(
				0, new ItemStack(Items.DIAMOND, 32),
				5, new ItemStack(Items.OAK_LOG, 7));
		Map<Integer, SlotInfo> infos = Map.of(2,
				new SlotInfo(bid.yuanlu.mc.warehouse.api.container.SlotRole.MACHINE_OUTPUT, true, false));
		store.save(k, new bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot(slots, infos, "箱子", 27));

		// 文件按 worldId 分目录
		assertTrue(Files.isRegularFile(store.root().resolve("singleplayer_demo")
				.resolve("minecraft_overworld__10_64_-3.json")));

		var loaded = store.load(k);
		assertTrue(loaded.isPresent());
		var snap = loaded.get();
		assertEquals(27, snap.slotCount());
		assertEquals("箱子", snap.title());
		assertEquals(2, snap.slots().size());
		assertEquals(32, snap.slots().get(0).getCount());
		assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(
				new ItemStack(Items.DIAMOND, 32), snap.slots().get(0)));
		assertFalse(snap.slotInfo(2).canPutTo());
	}

	@Test
	void playerScopedKeysAreDistinctFiles() {
		var store = new DiskCacheStore(tempDir);
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		store.save(key("w", "d", 1, alice), snap());
		store.save(key("w", "d", 1, bob), snap());
		store.save(key("w", "d", 1, null), snap());
		assertEquals(3, countCacheFiles());
		assertTrue(store.load(key("w", "d", 1, bob)).isPresent());
	}

	@Test
	void clearWorldRemovesOnlyThatWorld() {
		var store = new DiskCacheStore(tempDir);
		store.save(key("w", "d", 0, null), snap());
		store.save(key("other", "d", 0, null), snap());

		int removed = store.clearWorld("w");
		assertTrue(removed >= 1);
		assertTrue(store.load(key("w", "d", 0, null)).isEmpty());
		assertTrue(store.load(key("other", "d", 0, null)).isPresent(), "其它世界不受影响");
	}

	@Test
	void corruptedFileFallsBackToEmpty() throws Exception {
		var store = new DiskCacheStore(tempDir);
		CacheKey k = key("w", "d", 9, null);
		store.save(k, snap());
		Path file = Files.walk(store.root()).filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
		Files.writeString(file, "{broken");

		assertTrue(store.load(k).isEmpty(), "损坏文件视为缓存缺失");
	}

	private static bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot snap() {
		return new bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot(
				Map.of(0, new ItemStack(Items.STICK)), Map.of(), "t", 9);
	}

	private long countCacheFiles() {
		try (var walk = Files.walk(new DiskCacheStore(tempDir).root())) {
			return walk.filter(p -> p.toString().endsWith(".json")).count();
		} catch (Exception e) {
			return -1;
		}
	}
}

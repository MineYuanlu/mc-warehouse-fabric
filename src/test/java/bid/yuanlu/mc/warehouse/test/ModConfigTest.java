package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.core.config.ModConfig;

/**
 * 全局配置默认值与两级覆盖查找（PDD §11.4）。
 */
public class ModConfigTest {

	@Test
	void defaultsMatchSpec() {
		ModConfig c = new ModConfig();
		assertEquals(false, c.debug);
		assertEquals(2, c.defaultInteractionSpeed);
		assertEquals(0, c.interactionJitterPercent);
		assertEquals(0, c.cacheTtlSeconds);
		assertEquals("first_fit", c.slotAllocator);
		assertEquals(4.5, c.reachLimit, 1e-9);
		assertEquals(2, c.exploreFailMax);
		assertEquals(3, c.navRetryMax);
		assertEquals(20, c.timeouts().openTicks);
		assertEquals(10, c.timeouts().confirmTicks);
		assertEquals(2, c.timeouts().settleTicks);
	}

	@Test
	void overrideLookupDimThenWorldThenGlobal() {
		ModConfig c = new ModConfig();

		// 无任何覆盖 → 全局
		assertEquals(2, c.interactionSpeed("w", "d"));
		assertEquals("noop", c.pathfinder("w", "d"));

		var world = new ModConfig.WorldEntry();
		world.interactionSpeed = 3;
		world.pathfinder = "walk";
		c.worlds.put("w", world);

		// world 级默认
		assertEquals(3, c.interactionSpeed("w", "dim_a"));
		assertEquals("walk", c.pathfinder("w", "dim_a"));

		// dim 覆盖 world
		var dim = new ModConfig.DimOverride();
		dim.interactionSpeed = 5;
		world.dimensions.put("dim_b", dim);
		assertEquals(5, c.interactionSpeed("w", "dim_b"));
		assertEquals("walk", c.pathfinder("w", "dim_b"), "未覆盖的字段继续回落");

		// 其它 world 不受影响
		assertEquals(2, c.interactionSpeed("other", "dim_a"));
		assertTrue(c.pathfinder("unknown_world", null).equals("noop"));
	}
}

package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import bid.yuanlu.mc.warehouse.impl.selector.CompositeSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;
import bid.yuanlu.mc.warehouse.impl.selector.NameSelector;
import bid.yuanlu.mc.warehouse.impl.selector.NbtSelector;
import bid.yuanlu.mc.warehouse.impl.selector.TagSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 物品选择器语义 + 多态 codec 往返（PDD §3.5、§13 JVM 层）。
 */
public class SelectorTest extends McBootstrap {

	@BeforeAll
	static void installCodecs() {
		TestCodecs.install();
	}

	// ---- IdSelector ----

	@Test
	void idMatchesExact() {
		IdSelector s = new IdSelector("minecraft:diamond");
		assertTrue(s.matches(new ItemStack(Items.DIAMOND)));
		assertFalse(s.matches(new ItemStack(Items.EMERALD)));
	}

	// ---- TagSelector ----

	@Test
	void tagGracefulOnUnknownOrMalformed() {
		// Bootstrap 环境未加载原版数据包标签：合法 id 但未绑定 → 优雅返回 false
		assertFalse(new TagSelector("minecraft:logs").matches(new ItemStack(Items.OAK_LOG)));
		// 非法 id → 不抛异常，返回 false
		assertFalse(new TagSelector("!!!").matches(new ItemStack(Items.OAK_LOG)));
	}

	// ---- NameSelector ----

	@Test
	void nameExactAndFuzzy() {
		ItemStack stack = new ItemStack(Items.DIAMOND);
		String actual = stack.getHoverName().getString();

		assertTrue(new NameSelector(actual).matches(stack));
		assertFalse(new NameSelector(actual + "x").matches(stack));
		assertTrue(new NameSelector(actual.substring(1, 3), true).matches(stack));
		assertFalse(new NameSelector("\u0000nope", true).matches(stack));
	}

	// ---- NbtSelector（完整组件序列化文本包含匹配）----

	@Test
	void nbtSubstringSemantics() {
		ItemStack stack = new ItemStack(Items.DIAMOND);
		assertTrue(new NbtSelector("").matches(stack), "空串应恒匹配");
		assertFalse(new NbtSelector("zzz_no_such_component_zzz").matches(stack));
	}

	@Test
	void nbtMatchesDefaultValuedComponents() {
		// B6 修订：完整组件表可命中「恰好等于默认值」的组件（旧 patch 差异集匹配不到）
		ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
		int defaultDamage = plain.getMaxDamage();
		assertTrue(defaultDamage > 0);
		assertTrue(new NbtSelector("max_damage").matches(plain),
				"默认值组件（max_damage）应出现在完整组件文本中");
		assertTrue(new NbtSelector("=>" + defaultDamage).matches(plain),
				"默认耐久数值应可匹配");
	}

	// ---- CompositeSelector ----

	@Test
	void compositeAndOrNot() {
		var diamond = new IdSelector("minecraft:diamond");
		var emerald = new IdSelector("minecraft:emerald");

		assertTrue(new CompositeSelector(CompositeSelector.Op.AND, java.util.List.of(diamond, emerald))
				.matches(new ItemStack(Items.DIAMOND)) == false);
		assertTrue(new CompositeSelector(CompositeSelector.Op.OR, java.util.List.of(diamond, emerald))
				.matches(new ItemStack(Items.DIAMOND)));
		assertFalse(new CompositeSelector(CompositeSelector.Op.OR, java.util.List.of(diamond, emerald))
				.matches(new ItemStack(Items.STICK)));
		assertTrue(CompositeSelector.not(diamond).matches(new ItemStack(Items.STICK)));
		assertFalse(CompositeSelector.not(diamond).matches(new ItemStack(Items.DIAMOND)));
	}

	@Test
	void compositeNotRequiresExactlyOne() {
		var diamond = new IdSelector("minecraft:diamond");
		assertThrows(IllegalArgumentException.class,
				() -> new CompositeSelector(CompositeSelector.Op.NOT, java.util.List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new CompositeSelector(CompositeSelector.Op.NOT, java.util.List.of(diamond, diamond)));
	}

	// ---- codec 往返（多态分发）----

	@Test
	void codecRoundTrip() {
		ItemSelectorCase[] cases = {
				new ItemSelectorCase(new IdSelector("minecraft:diamond"), "id"),
				new ItemSelectorCase(new TagSelector("minecraft:logs"), "tag"),
				new ItemSelectorCase(new NameSelector("钻石", true), "name"),
				new ItemSelectorCase(new NbtSelector("{damage:5}"), "nbt"),
				new ItemSelectorCase(
						new CompositeSelector(CompositeSelector.Op.AND,
								java.util.List.of(new IdSelector("minecraft:diamond"),
										CompositeSelector.not(new TagSelector("minecraft:logs")))),
						"composite"),
		};
		for (ItemSelectorCase c : cases) {
			JsonObject json = SelectorCodecs.toJson(c.selector());
			assertEquals(c.expectedType(), json.get("type").getAsString(), c.selector().toString());
			assertEquals(c.selector(), SelectorCodecs.itemFromJson(json));
		}
	}

	@Test
	void codecRejectsUnknownTypeAndMissingType() {
		JsonObject bad = new JsonObject();
		bad.addProperty("type", "no_such_type");
		assertThrows(IllegalArgumentException.class, () -> SelectorCodecs.itemFromJson(bad));

		JsonObject missing = new JsonObject();
		assertThrows(IllegalArgumentException.class, () -> SelectorCodecs.itemFromJson(missing));
	}

	private record ItemSelectorCase(bid.yuanlu.mc.warehouse.api.item.ItemSelector selector, String expectedType) {
	}
}

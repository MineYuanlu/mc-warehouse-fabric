package bid.yuanlu.mc.warehouse.impl.selector;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品标签匹配（PDD §3.5）。
 *
 * @param value 标签 id，如 {@code minecraft:logs}
 */
public record TagSelector(String value) implements ItemSelector {

	public TagSelector {
		Objects.requireNonNull(value, "value");
	}

	@Override
	public boolean matches(ItemStack stack) {
		try {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(value));
			return stack.is(tag);
		} catch (Exception e) {
			return false;
		}
	}

	public static SelectorCodec<TagSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "tag";
			}

			@Override
			public Class<TagSelector> implType() {
				return TagSelector.class;
			}

			@Override
			public JsonObject toJson(TagSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				return json;
			}

			@Override
			public TagSelector fromJson(JsonObject json) {
				return new TagSelector(json.get("value").getAsString());
			}
		};
	}
}

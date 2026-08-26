package bid.yuanlu.mc.warehouse.impl.selector;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 物品 ID 精确匹配（PDD §3.5）。
 *
 * @param value 物品 id，如 {@code minecraft:diamond}
 */
public record IdSelector(String value) implements ItemSelector {

	public IdSelector {
		Objects.requireNonNull(value, "value");
	}

	@Override
	public boolean matches(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key != null && key.toString().equals(value);
	}

	public static SelectorCodec<IdSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "id";
			}

			@Override
			public Class<IdSelector> implType() {
				return IdSelector.class;
			}

			@Override
			public JsonObject toJson(IdSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				return json;
			}

			@Override
			public IdSelector fromJson(JsonObject json) {
				return new IdSelector(json.get("value").getAsString());
			}
		};
	}
}

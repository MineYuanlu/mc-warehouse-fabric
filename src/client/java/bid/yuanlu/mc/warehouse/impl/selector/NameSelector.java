package bid.yuanlu.mc.warehouse.impl.selector;

import java.util.Locale;
import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 显示名称匹配（PDD §3.5）。
 *
 * @param value 期望名称
 * @param fuzzy  true=忽略大小写包含匹配；false=精确匹配
 */
public record NameSelector(String value, boolean fuzzy) implements ItemSelector {

	public NameSelector {
		Objects.requireNonNull(value, "value");
	}

	public NameSelector(String value) {
		this(value, false);
	}

	@Override
	public boolean matches(ItemStack stack) {
		String name = stack.getHoverName().getString();
		if (fuzzy) {
			return name.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
		}
		return name.equals(value);
	}

	public static SelectorCodec<NameSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "name";
			}

			@Override
			public Class<NameSelector> implType() {
				return NameSelector.class;
			}

			@Override
			public JsonObject toJson(NameSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				json.addProperty("fuzzy", value.fuzzy);
				return json;
			}

			@Override
			public NameSelector fromJson(JsonObject json) {
				boolean fuzzy = json.has("fuzzy") && json.get("fuzzy").getAsBoolean();
				return new NameSelector(json.get("value").getAsString(), fuzzy);
			}
		};
	}
}

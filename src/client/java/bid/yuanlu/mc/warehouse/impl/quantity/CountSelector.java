package bid.yuanlu.mc.warehouse.impl.quantity;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.QuantityContext;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;

/**
 * 固定总量（PDD §3.6）：target = {@code value}。
 *
 * @param value 目标总量
 */
public record CountSelector(int value) implements QuantitySelector {

	public CountSelector {
		if (value < 0) throw new IllegalArgumentException("value < 0: " + value);
	}

	@Override
	public int computeTargetAmount(QuantityContext ctx) {
		return value;
	}

	public static SelectorCodec<CountSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "count";
			}

			@Override
			public Class<CountSelector> implType() {
				return CountSelector.class;
			}

			@Override
			public JsonObject toJson(CountSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				return json;
			}

			@Override
			public CountSelector fromJson(JsonObject json) {
				return new CountSelector(json.get("value").getAsInt());
			}
		};
	}
}

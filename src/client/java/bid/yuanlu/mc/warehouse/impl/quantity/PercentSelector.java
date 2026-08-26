package bid.yuanlu.mc.warehouse.impl.quantity;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.QuantityContext;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;

/**
 * 容量百分比（PDD §3.6）：target = {@code slotCount × maxStackSize × value%}（按容量比例取整）。
 *
 * @param value 百分比（0~100）
 */
public record PercentSelector(int value) implements QuantitySelector {

	public PercentSelector {
		if (value < 0 || value > 100) throw new IllegalArgumentException("value out of [0,100]: " + value);
	}

	@Override
	public int computeTargetAmount(QuantityContext ctx) {
		long capacity = (long) ctx.slotCount() * ctx.maxStackSize();
		return (int) (capacity * value / 100);
	}

	public static SelectorCodec<PercentSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "percent";
			}

			@Override
			public Class<PercentSelector> implType() {
				return PercentSelector.class;
			}

			@Override
			public JsonObject toJson(PercentSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				return json;
			}

			@Override
			public PercentSelector fromJson(JsonObject json) {
				return new PercentSelector(json.get("value").getAsInt());
			}
		};
	}
}

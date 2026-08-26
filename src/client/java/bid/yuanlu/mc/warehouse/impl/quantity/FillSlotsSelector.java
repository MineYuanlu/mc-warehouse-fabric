package bid.yuanlu.mc.warehouse.impl.quantity;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.QuantityContext;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;

/**
 * 占槽模式（PDD §3.6）：占满除 {@code value} 个空位外的槽位
 * ≈ {@code max(0, slotCount - value) × maxStackSize}（旧项目缺陷修复：负值钳制为 0）。
 *
 * @param value 保留的空槽数
 */
public record FillSlotsSelector(int value) implements QuantitySelector {

	public FillSlotsSelector {
		if (value < 0) throw new IllegalArgumentException("value < 0: " + value);
	}

	@Override
	public int computeTargetAmount(QuantityContext ctx) {
		int fillSlots = Math.max(0, ctx.slotCount() - value);
		return Math.multiplyExact(fillSlots, ctx.maxStackSize());
	}

	public static SelectorCodec<FillSlotsSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "fill_slots";
			}

			@Override
			public Class<FillSlotsSelector> implType() {
				return FillSlotsSelector.class;
			}

			@Override
			public JsonObject toJson(FillSlotsSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				return json;
			}

			@Override
			public FillSlotsSelector fromJson(JsonObject json) {
				return new FillSlotsSelector(json.get("value").getAsInt());
			}
		};
	}
}

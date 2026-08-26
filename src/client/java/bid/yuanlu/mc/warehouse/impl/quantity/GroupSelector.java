package bid.yuanlu.mc.warehouse.impl.quantity;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.QuantityContext;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;

/**
 * 按组数量（PDD §3.6）：target = {@code value} × maxStackSize。
 *
 * @param value 组数
 */
public record GroupSelector(int value) implements QuantitySelector {

	public GroupSelector {
		if (value < 0) throw new IllegalArgumentException("value < 0: " + value);
	}

	@Override
	public int computeTargetAmount(QuantityContext ctx) {
		return Math.multiplyExact(value, ctx.maxStackSize());
	}

	public static SelectorCodec<GroupSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "group";
			}

			@Override
			public Class<GroupSelector> implType() {
				return GroupSelector.class;
			}

			@Override
			public JsonObject toJson(GroupSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				return json;
			}

			@Override
			public GroupSelector fromJson(JsonObject json) {
				return new GroupSelector(json.get("value").getAsInt());
			}
		};
	}
}

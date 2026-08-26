package bid.yuanlu.mc.warehouse.impl.selector;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;
import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * 组合选择器（PDD §3.5）：AND（全部匹配）/ OR（任一匹配）/ NOT（全部不匹配，即取反嵌套）。
 *
 * @param op        组合方式
 * @param selectors 嵌套选择器列表；NOT 要求恰好一个
 */
public record CompositeSelector(Op op, List<ItemSelector> selectors) implements ItemSelector {

	public enum Op {
		AND,
		OR,
		NOT
	}

	public CompositeSelector {
		Objects.requireNonNull(op, "op");
		selectors = List.copyOf(Objects.requireNonNull(selectors, "selectors"));
		if (op == Op.NOT && selectors.size() != 1) {
			throw new IllegalArgumentException("NOT requires exactly one selector: " + selectors.size());
		}
	}

	public static CompositeSelector not(ItemSelector inner) {
		return new CompositeSelector(Op.NOT, List.of(inner));
	}

	@Override
	public boolean matches(ItemStack stack) {
		return switch (op) {
			case AND -> selectors.stream().allMatch(s -> s.matches(stack));
			case OR -> selectors.stream().anyMatch(s -> s.matches(stack));
			case NOT -> !selectors.getFirst().matches(stack);
		};
	}

	public static SelectorCodec<CompositeSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "composite";
			}

			@Override
			public Class<CompositeSelector> implType() {
				return CompositeSelector.class;
			}

			@Override
			public JsonObject toJson(CompositeSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("op", value.op.name());
				JsonArray arr = new JsonArray();
				for (ItemSelector s : value.selectors) {
					arr.add(SelectorCodecs.toJson(s));
				}
				json.add("selectors", arr);
				return json;
			}

			@Override
			public CompositeSelector fromJson(JsonObject json) {
				Op op = Op.valueOf(json.get("op").getAsString().toUpperCase(Locale.ROOT));
				JsonArray arr = json.getAsJsonArray("selectors");
				List<ItemSelector> inner = new java.util.ArrayList<>(arr.size());
				for (var e : arr) {
					inner.add(SelectorCodecs.itemFromJson(e.getAsJsonObject()));
				}
				return new CompositeSelector(op, inner);
			}
		};
	}
}

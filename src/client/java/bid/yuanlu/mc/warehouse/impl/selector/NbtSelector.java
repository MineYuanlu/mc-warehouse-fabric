package bid.yuanlu.mc.warehouse.impl.selector;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.ItemStack;

/**
 * 组件序列化文本包含匹配（PDD §3.5，习惯名 NbtSelector）。
 * <p>
 * 匹配对象为物品<b>完整组件表</b>（含恰等于默认值的组件）逐项
 * {@code type=>value;} 拼接文本（B6 修订：旧实现基于 getComponentsPatch 差异集，
 * 「组件恰为默认值」的物品永远匹配不到非空 value）。并非 NBT 子集匹配；
 * 插件作者请勿依赖其内部表示形式（PDD §15.11）。
 *
 * @param value 包含匹配的文本片段
 */
public record NbtSelector(String value) implements ItemSelector {

	public NbtSelector {
		Objects.requireNonNull(value, "value");
	}

	@Override
	public boolean matches(ItemStack stack) {
		StringBuilder text = new StringBuilder();
		for (TypedDataComponent<?> component : stack.getComponents()) {
			text.append(component).append(';');
		}
		return text.toString().contains(value);
	}

	public static SelectorCodec<NbtSelector> codec() {
		return new SelectorCodec<>() {
			@Override
			public String type() {
				return "nbt";
			}

			@Override
			public Class<NbtSelector> implType() {
				return NbtSelector.class;
			}

			@Override
			public JsonObject toJson(NbtSelector value) {
				JsonObject json = new JsonObject();
				json.addProperty("value", value.value);
				return json;
			}

			@Override
			public NbtSelector fromJson(JsonObject json) {
				return new NbtSelector(json.get("value").getAsString());
			}
		};
	}
}

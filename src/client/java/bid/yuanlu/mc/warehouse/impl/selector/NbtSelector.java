package bid.yuanlu.mc.warehouse.impl.selector;

import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 组件序列化文本包含匹配（PDD §3.5，习惯名 NbtSelector）。
 * <p>
 * 实际语义为组件补丁的 {@code toString().contains(value)}（旧项目实现即如此，并非 NBT 子集匹配，
 * PDD §3.5 注记）。插件作者请勿依赖其内部表示形式；是否更名/升级待调研（PDD §15.11）。
 *
 * @param value 包含匹配的文本片段
 */
public record NbtSelector(String value) implements ItemSelector {

	public NbtSelector {
		Objects.requireNonNull(value, "value");
	}

	@Override
	public boolean matches(ItemStack stack) {
		var patch = stack.getComponentsPatch();
		String text = patch != null ? patch.toString() : "";
		return text.contains(value);
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

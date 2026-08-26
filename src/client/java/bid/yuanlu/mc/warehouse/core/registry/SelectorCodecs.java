package bid.yuanlu.mc.warehouse.core.registry;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;

import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;

/**
 * SelectorCodec 分发核心（PDD §9.2/§11.2）：内置实现与插件经同一机制注册。
 * <p>
 * 序列化：实例 → 按实现类查 codec → 写入 {@code "type"} + 载荷；
 * 反序列化：JSON → 按 {@code "type"} 查 codec → 构造实例。
 * 注册期限于客户端初始化阶段，此后冻结。
 */
public final class SelectorCodecs {

	private static final Map<String, SelectorCodec<? extends ItemSelector>> ITEM_BY_TYPE = new LinkedHashMap<>();
	private static final Map<Class<?>, SelectorCodec<? extends ItemSelector>> ITEM_BY_CLASS = new HashMap<>();

	private static final Map<String, SelectorCodec<? extends QuantitySelector>> QUANTITY_BY_TYPE = new LinkedHashMap<>();
	private static final Map<Class<?>, SelectorCodec<? extends QuantitySelector>> QUANTITY_BY_CLASS = new HashMap<>();

	private static volatile boolean frozen = false;

	private SelectorCodecs() {
	}

	// ---- 注册（初始化期间）----

	public static <T extends ItemSelector> void registerItem(SelectorCodec<T> codec) {
		Objects.requireNonNull(codec, "codec");
		synchronized (ITEM_BY_TYPE) {
			assertNotFrozen();
			if (ITEM_BY_TYPE.putIfAbsent(codec.type(), codec) != null) {
				throw new IllegalArgumentException("Duplicate item selector codec type: " + codec.type());
			}
			ITEM_BY_CLASS.put(codec.implType(), codec);
		}
	}

	public static <T extends QuantitySelector> void registerQuantity(SelectorCodec<T> codec) {
		Objects.requireNonNull(codec, "codec");
		synchronized (QUANTITY_BY_TYPE) {
			assertNotFrozen();
			if (QUANTITY_BY_TYPE.putIfAbsent(codec.type(), codec) != null) {
				throw new IllegalArgumentException("Duplicate quantity selector codec type: " + codec.type());
			}
			QUANTITY_BY_CLASS.put(codec.implType(), codec);
		}
	}

	/** 冻结注册表（客户端初始化完成后调用）；此后注册一律拒绝 */
	public static void freeze() {
		frozen = true;
	}

	public static boolean isFrozen() {
		return frozen;
	}

	/** 仅测试用：清空全部 codec 并解冻（生产代码禁止调用） */
	public static void resetForTest() {
		synchronized (ITEM_BY_TYPE) {
			ITEM_BY_TYPE.clear();
			ITEM_BY_CLASS.clear();
		}
		synchronized (QUANTITY_BY_TYPE) {
			QUANTITY_BY_TYPE.clear();
			QUANTITY_BY_CLASS.clear();
		}
		frozen = false;
	}

	// ---- 分发 ----

	public static JsonObject toJson(ItemSelector selector) {
		Objects.requireNonNull(selector, "selector");
		var codec = (SelectorCodec<ItemSelector>) ITEM_BY_CLASS.get(selector.getClass());
		if (codec == null) throw new IllegalArgumentException("No item selector codec for " + selector.getClass());
		JsonObject json = codec.toJson(selector);
		json.addProperty("type", codec.type());
		return json;
	}

	public static ItemSelector itemFromJson(JsonObject json) {
		String type = requireType(json);
		var codec = (SelectorCodec<ItemSelector>) ITEM_BY_TYPE.get(type);
		if (codec == null) throw new IllegalArgumentException("Unknown item selector type: " + type);
		return codec.fromJson(json);
	}

	public static JsonObject toJson(QuantitySelector selector) {
		Objects.requireNonNull(selector, "selector");
		var codec = (SelectorCodec<QuantitySelector>) QUANTITY_BY_CLASS.get(selector.getClass());
		if (codec == null) throw new IllegalArgumentException("No quantity selector codec for " + selector.getClass());
		JsonObject json = codec.toJson(selector);
		json.addProperty("type", codec.type());
		return json;
	}

	public static QuantitySelector quantityFromJson(JsonObject json) {
		String type = requireType(json);
		var codec = (SelectorCodec<QuantitySelector>) QUANTITY_BY_TYPE.get(type);
		if (codec == null) throw new IllegalArgumentException("Unknown quantity selector type: " + type);
		return codec.fromJson(json);
	}

	private static String requireType(JsonObject json) {
		if (!json.has("type")) throw new IllegalArgumentException("Missing \"type\" field: " + json);
		return json.get("type").getAsString();
	}

	private static void assertNotFrozen() {
		if (frozen) throw new IllegalStateException("SelectorCodec registry is frozen");
	}
}

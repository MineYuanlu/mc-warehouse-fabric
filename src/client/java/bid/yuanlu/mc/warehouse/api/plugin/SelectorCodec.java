package bid.yuanlu.mc.warehouse.api.plugin;

import com.google.gson.JsonObject;

/**
 * JSON 多态编解码 SPI（PDD §9.2）：type 即 JSON "type" 字段值，全局唯一。
 * <p>
 * Gson TypeAdapter 按 codec 注册表分发（PDD §11.2）——插件自定义 selector 因此获得同等的持久化能力。
 * 注意：codec 的 {@link #toJson} 只产出载荷字段；{@code "type"} 字段由分发层统一写入。
 *
 * @param <T> 编解码的目标实现类型
 */
public interface SelectorCodec<T> {

	String type();

	/** 本 codec 负责的实现类型（序列化时按实例类型分发表）；注册表据此建立 类→codec 索引 */
	Class<T> implType();

	JsonObject toJson(T value);

	T fromJson(JsonObject json);
}

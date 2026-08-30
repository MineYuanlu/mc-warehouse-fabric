package bid.yuanlu.mc.warehouse.api.world;

import org.jetbrains.annotations.Nullable;

/**
 * world 标识 SPI（PDD §4.1）：回答「当前服务器内的哪个 world」。
 * <p>
 * worldId 是世界的物理身份，由服务器侧响应（本 mod 服务端经网络推送），缺省 {@code ""}，
 * 代表单世界服务器（原版/单人）。多世界服务器（如 multiverse 类插件/mod）可注册额外实现识别。
 * <p>
 * worldId 用于：会话切换判定（serverId 或 worldId 变化均视为会话切换）、
 * 缓存命名空间的一部分。玩家可见/可编辑的名字是 worldName（见 world-map.json 映射），
 * anchors 与 pos.world 引用 worldName 而非 worldId。
 */
public interface WorldIdentifier {

	String id();

	/**
	 * 当前会话的 worldId；不适用返回 null（由后续实现或缺省 {@code ""} 接管）。
	 * 返回 {@code ""} 是明确的「单世界/未推送」答案，不是「不适用」。
	 */
	@Nullable
	String currentWorldId();
}

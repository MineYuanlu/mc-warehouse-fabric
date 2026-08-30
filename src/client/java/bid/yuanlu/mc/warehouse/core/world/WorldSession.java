package bid.yuanlu.mc.warehouse.core.world;

import java.util.Objects;

/**
 * 当前会话快照（PDD §4.1）：(serverId, worldId, worldName)。
 * <p>
 * serverId/worldId 任一变化视为会话切换；worldName 由 {@link WorldNameMapper} 依
 * (serverId, worldId) 解析（玩家可改名，改名同样触发会话切换——坐标体系随之重解释）。
 */
public record WorldSession(String serverId, String worldId, String worldName) {

	public WorldSession {
		Objects.requireNonNull(serverId, "serverId");
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(worldName, "worldName");
	}

	@Override
	public String toString() {
		return serverId + "#" + worldId + "(" + worldName + ")";
	}
}

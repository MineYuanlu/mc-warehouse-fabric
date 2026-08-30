package bid.yuanlu.mc.warehouse.api.world;

import java.util.Objects;

/**
 * (serverId, worldName, dimId) 三元组（PDD §4.3）：anchor、容器坐标、寻路目标的完整限定。
 * <p>
 * serverId 来自 {@link ServerIdentifier}（会话相对），worldName 是玩家可控的世界名
 * （world-map.json 映射的键，仓库 JSON 引用它而非 worldId）。仅 dim 不构成唯一性。
 */
public record WorldDim(String serverId, String worldName, String dimId) {

	public WorldDim {
		Objects.requireNonNull(serverId, "serverId");
		Objects.requireNonNull(worldName, "worldName");
		Objects.requireNonNull(dimId, "dimId");
	}

	@Override
	public String toString() {
		return serverId + "|" + worldName + "|" + dimId;
	}
}

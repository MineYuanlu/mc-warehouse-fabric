package bid.yuanlu.mc.warehouse.api.world;

import java.util.Objects;

/**
 * (worldId, dimId) 二元组（PDD §4.3）：anchor、容器坐标、寻路目标的完整限定；
 * 仅 dim 不构成唯一性（不同服务器/存档的同名维度是不同的世界）。
 */
public record WorldDim(String worldId, String dimId) {

	public WorldDim {
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(dimId, "dimId");
	}

	@Override
	public String toString() {
		return worldId + "|" + dimId;
	}
}

package bid.yuanlu.mc.warehouse.core.cache;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;

/**
 * 容器缓存键（PDD §3.8 v0.2 正确性约束）：必须含 worldId 与 dimId——
 * 裸 BlockPos 键会跨维度串数据；末影箱类因玩家而异的容器附加 playerUUID；
 * 多格容器用 canonicalPos。
 */
public record CacheKey(String worldId, String dimId, BlockPosLike pos, @Nullable UUID player) {

	/** 轻量坐标（避免 api 层与 MC BlockPos 的耦合面） */
	public record BlockPosLike(int x, int y, int z) {
	}

	public CacheKey {
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(dimId, "dimId");
		Objects.requireNonNull(pos, "pos");
	}

	public static CacheKey of(WorldDimPos canonicalPos, @Nullable UUID player) {
		return new CacheKey(canonicalPos.world(), canonicalPos.dim(),
				new BlockPosLike(canonicalPos.x(), canonicalPos.y(), canonicalPos.z()), player);
	}

	@Override
	public String toString() {
		return worldId + "|" + dimId + "|" + pos.x() + "," + pos.y() + "," + pos.z()
				+ (player != null ? "|player:" + player : "");
	}
}

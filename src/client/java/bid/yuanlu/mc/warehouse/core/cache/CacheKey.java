package bid.yuanlu.mc.warehouse.core.cache;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.world.WorldNameMapper;
import bid.yuanlu.mc.warehouse.core.world.WorldSession;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 容器缓存键（PDD §3.8 v0.2 正确性约束）：必须含 worldId 与 dimId——
 * 裸 BlockPos 键会跨维度串数据；末影箱类因玩家而异的容器附加 playerUUID；
 * 多格容器用 canonicalPos。
 * <p>
 * worldId 解析（{@link #of}）：canonicalPos 携带的是 worldName（玩家可改名），
 * 经会话/映射反查为物理 worldId 后入键——重命名不再孤儿化缓存条目。
 * 解析不出（多人无服务端 mod 时 worldId 为空、或 tracker 未装配的单测环境）回退
 * worldName，键仍按名字隔离。
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
		return new CacheKey(resolveWorldId(canonicalPos.world()), canonicalPos.dim(),
				new BlockPosLike(canonicalPos.x(), canonicalPos.y(), canonicalPos.z()), player);
	}

	/** worldName → worldId；当前会话世界直接取会话值，其余世界反查 world-map */
	private static String resolveWorldId(@Nullable String worldName) {
		String fallback = worldName != null ? worldName : "";
		try {
			WorldSession s = WorldSessionTracker.get().currentSession();
			if (s != null) {
				if (worldName == null || worldName.equals(s.worldName())) {
					if (!s.worldId().isEmpty()) return s.worldId();
				} else {
					String mapped = WorldNameMapper.get().worldIdOf(s.serverId(), worldName);
					if (mapped != null && !mapped.isEmpty()) return mapped;
				}
			}
		} catch (IllegalStateException e) {
			// tracker/mapper 未装配（JVM 单测）：按 worldName 隔离
		}
		return fallback;
	}

	@Override
	public String toString() {
		return worldId + "|" + dimId + "|" + pos.x() + "," + pos.y() + "," + pos.z()
				+ (player != null ? "|player:" + player : "");
	}
}

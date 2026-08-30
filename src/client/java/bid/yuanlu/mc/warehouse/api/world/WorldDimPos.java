package bid.yuanlu.mc.warehouse.api.world;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * 完整限定的容器/寻路坐标（PDD §3.2/§4.3）。
 * <p>
 * 组件名与仓库 JSON 的 pos 条目字段一致（world/dim/x/y/z，§11.3），可直接参与 Gson 序列化：
 * {@code world} 是 worldName（玩家可控的世界名，world-map.json 映射的键），
 * 可省略（null，加载时按 anchors 展平后唯一 worldName 补全）；
 * 服务器维度是会话相对的（解析时由当前会话的 serverId 提供），
 * 坐标为相对 anchor 的偏移，经 {@code Warehouse.resolveAbsolute} 转绝对坐标。
 */
public record WorldDimPos(@Nullable String world, String dim, int x, int y, int z) {

	public WorldDimPos {
		java.util.Objects.requireNonNull(dim, "dim");
	}

	/** 是否已带 worldName；{@code null} = 缺失（可按 §11.3 补全），{@code ""} = 缺省世界（合法名字） */
	public boolean hasWorld() {
		return world != null;
	}

	/** 补全 worldName 后的坐标 */
	public WorldDimPos withWorld(String worldName) {
		return new WorldDimPos(java.util.Objects.requireNonNull(worldName, "worldName"), dim, x, y, z);
	}

	public BlockPos toBlockPos() {
		return new BlockPos(x, y, z);
	}

	/** 相对 → 绝对：加上同 (serverId, worldName, dim) 的 anchor */
	public WorldDimPos plus(BlockPos anchor) {
		return new WorldDimPos(world, dim, x + anchor.getX(), y + anchor.getY(), z + anchor.getZ());
	}

	/** 绝对 → 相对：减去同 (serverId, worldName, dim) 的 anchor */
	public WorldDimPos minus(BlockPos anchor) {
		return new WorldDimPos(world, dim, x - anchor.getX(), y - anchor.getY(), z - anchor.getZ());
	}

	public static WorldDimPos of(WorldDim dim, BlockPos pos) {
		return new WorldDimPos(dim.worldName(), dim.dimId(), pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public String toString() {
		return (hasWorld() ? world + "|" : "") + dim + " " + x + " " + y + " " + z;
	}
}

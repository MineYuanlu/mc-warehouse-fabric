package bid.yuanlu.mc.warehouse.api.world;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * 完整限定的容器/寻路坐标（PDD §3.2/§4.3）。
 * <p>
 * 组件名与仓库 JSON 的 pos 条目字段一致（world/dim/x/y/z，§11.3），可直接参与 Gson 序列化：
 * 配置中 {@code world} 可省略（null，加载时按 anchors 键归一化），
 * 坐标为相对 anchor 的偏移，经 {@code Warehouse.resolveAbsolute} 转绝对坐标。
 */
public record WorldDimPos(@Nullable String world, String dim, int x, int y, int z) {

	public WorldDimPos {
		java.util.Objects.requireNonNull(dim, "dim");
	}

	/** 是否已带 world 标识 */
	public boolean hasWorld() {
		return world != null && !world.isEmpty();
	}

	/** 补全 world 标识后的坐标 */
	public WorldDimPos withWorld(String worldId) {
		return new WorldDimPos(java.util.Objects.requireNonNull(worldId, "worldId"), dim, x, y, z);
	}

	/** 完整限定的 WorldDim；world 缺失时抛 IllegalStateException */
	public WorldDim worldDim() {
		if (!hasWorld()) throw new IllegalStateException("WorldDimPos without world: " + this);
		return new WorldDim(world, dim);
	}

	public BlockPos toBlockPos() {
		return new BlockPos(x, y, z);
	}

	/** 相对 → 绝对：加上同 (world,dim) 的 anchor */
	public WorldDimPos plus(BlockPos anchor) {
		return new WorldDimPos(world, dim, x + anchor.getX(), y + anchor.getY(), z + anchor.getZ());
	}

	/** 绝对 → 相对：减去同 (world,dim) 的 anchor */
	public WorldDimPos minus(BlockPos anchor) {
		return new WorldDimPos(world, dim, x - anchor.getX(), y - anchor.getY(), z - anchor.getZ());
	}

	public static WorldDimPos of(WorldDim dim, BlockPos pos) {
		return new WorldDimPos(dim.worldId(), dim.dimId(), pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public String toString() {
		return (hasWorld() ? world + "|" : "") + dim + " " + x + " " + y + " " + z;
	}
}

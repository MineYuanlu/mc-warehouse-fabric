package bid.yuanlu.mc.warehouse.util;

import net.minecraft.core.BlockPos;

/**
 * 坐标换算工具（相对 ↔ 绝对，分量独立加减 anchor）。
 * <p>
 * 仓库级坐标解析优先走 {@code Warehouse.resolveAbsolute/toRelative}（带 world/dim 校验）；
 * 本类用于无世界语义的纯 BlockPos 运算。
 */
public final class CoordinateUtils {

	public static BlockPos toAbsolute(BlockPos relative, BlockPos anchor) {
		return new BlockPos(
				anchor.getX() + relative.getX(),
				anchor.getY() + relative.getY(),
				anchor.getZ() + relative.getZ());
	}

	public static BlockPos toRelative(BlockPos absolute, BlockPos anchor) {
		return new BlockPos(
				absolute.getX() - anchor.getX(),
				absolute.getY() - anchor.getY(),
				absolute.getZ() - anchor.getZ());
	}

	private CoordinateUtils() {
	}
}

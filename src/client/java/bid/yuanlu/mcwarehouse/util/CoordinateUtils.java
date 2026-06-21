package bid.yuanlu.mcwarehouse.util;

import java.util.ArrayList;
import java.util.List;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import net.minecraft.core.BlockPos;

public final class CoordinateUtils {

	public static BlockPos toAbsolute(BlockPos relative, BlockPos anchor) {
		return new BlockPos(
				anchor.getX() + relative.getX(),
				anchor.getY() + relative.getY(),
				anchor.getZ() + relative.getZ()
		);
	}

	public static BlockPos toRelative(BlockPos absolute, BlockPos anchor) {
		return new BlockPos(
				absolute.getX() - anchor.getX(),
				absolute.getY() - anchor.getY(),
				absolute.getZ() - anchor.getZ()
		);
	}

	public static List<BlockPos> toAbsolute(List<ContainerInfo> containers, BlockPos anchor) {
		List<BlockPos> result = new ArrayList<>(containers.size());
		for (ContainerInfo info : containers) {
			result.add(toAbsolute(info.relativePos(), anchor));
		}
		return result;
	}

	private CoordinateUtils() {
	}
}

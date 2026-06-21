package bid.yuanlu.mcwarehouse.engine.container;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class ContainerScanner {

	public static Map<BlockPos, ContainerInfo> scanRegion(BlockPos pos1, BlockPos pos2, BlockPos anchor) {
		Map<BlockPos, ContainerInfo> result = new HashMap<>();
		Level level = Minecraft.getInstance().level;
		if (level == null) return result;

		int minX = Math.min(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockEntity be = level.getBlockEntity(pos);
					if (be instanceof BaseContainerBlockEntity || be instanceof Container) {
						ContainerInfo info = new ContainerInfo();
						info.relativePos = CoordinateUtils.toRelative(pos, anchor);
						result.put(pos, info);
					}
				}
			}
		}

		return result;
	}
}

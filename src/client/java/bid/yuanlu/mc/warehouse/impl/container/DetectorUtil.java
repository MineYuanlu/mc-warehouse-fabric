package bid.yuanlu.mc.warehouse.impl.container;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 检测器公共工具：以当前会话构造完整限定的坐标。
 */
final class DetectorUtil {

	/** 当前 (worldId, dimId) 下的绝对坐标；会话未就绪时 world 置 null */
	static WorldDimPos dimPos(BlockPos pos) {
		String worldId = null;
		try {
			worldId = WorldSessionTracker.get().currentWorldId();
		} catch (IllegalStateException ignored) {
			// 未初始化（测试/极早期），world 留空
		}
		var level = Minecraft.getInstance().level;
		String dimId = level != null ? level.dimension().identifier().toString() : "minecraft:overworld";
		return new WorldDimPos(worldId, dimId, pos.getX(), pos.getY(), pos.getZ());
	}

	private DetectorUtil() {
	}
}

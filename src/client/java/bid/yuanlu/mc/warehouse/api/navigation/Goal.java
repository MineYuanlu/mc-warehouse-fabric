package bid.yuanlu.mc.warehouse.api.navigation;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import net.minecraft.core.Direction;

/**
 * 单目标寻路目标点（PDD §7.1）：Navigator 内部不维护目标队列，队列只在引擎一侧。
 *
 * @param target             目标坐标（含 WorldDim，跨维度由 Navigator 自行处理）
 * @param acceptableDistance 可接受的距离（格数）
 * @param faceHint           到达后的朝向建议（引擎按方块中心计算传入，供开容器用）
 */
public record Goal(WorldDimPos target, double acceptableDistance, @Nullable Direction faceHint) {

	public Goal {
		java.util.Objects.requireNonNull(target, "target");
		if (acceptableDistance < 0) throw new IllegalArgumentException("acceptableDistance < 0");
	}
}

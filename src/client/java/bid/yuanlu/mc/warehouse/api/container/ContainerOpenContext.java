package bid.yuanlu.mc.warehouse.api.container;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

/**
 * 容器打开上下文：PRECHECK 阶段捕获的方块信息，供打开后身份校验使用（PDD §6.1/§8.1）。
 *
 * @param pos   目标坐标
 * @param block 打开前方块快照（含方块实体类型）
 */
public record ContainerOpenContext(WorldDimPos pos, BlockInWorld block) {

	public ContainerOpenContext {
		java.util.Objects.requireNonNull(pos, "pos");
		java.util.Objects.requireNonNull(block, "block");
	}
}

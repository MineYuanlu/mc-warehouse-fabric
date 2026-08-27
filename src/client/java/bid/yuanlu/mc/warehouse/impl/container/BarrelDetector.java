package bid.yuanlu.mc.warehouse.impl.container;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;

/**
 * 木桶（PDD §8.5）：GENERIC_9x3，27 格。
 */
public final class BarrelDetector extends BlockEntityDetector {

	public static final String ID = "barrel";

	public BarrelDetector() {
		super(ID, beTypes(BlockEntityType.BARREL), Set.of(MenuType.GENERIC_9x3));
	}

	@Override
	protected boolean slotCountMatches(int containerSlots) {
		return containerSlots == 27;
	}

	@Override
	@Nullable
	public ContainerInfo resolveMultiBlock(BlockPos[] positions) {
		if (positions.length != 1) return null; // 非多格容器拒绝多坐标（§3.2：不裁剪）
		var info = new ContainerInfo(IOType.INPUT);
		info.pos.add(DetectorUtil.dimPos(positions[0]));
		return info;
	}
}

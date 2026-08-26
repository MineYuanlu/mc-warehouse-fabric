package bid.yuanlu.mc.warehouse.impl.container;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;

/**
 * 漏斗（PDD §8.5）：5 格。
 */
public final class HopperDetector extends BlockEntityDetector {

	public static final String ID = "hopper";

	public HopperDetector() {
		super(ID, beTypes(BlockEntityType.HOPPER), Set.of(MenuType.HOPPER));
	}

	@Override
	protected boolean slotCountMatches(int containerSlots) {
		return containerSlots == 5;
	}

	@Override
	@Nullable
	public ContainerInfo resolveMultiBlock(BlockPos[] positions) {
		if (positions.length == 0) return null;
		var info = new ContainerInfo(IOType.INPUT);
		info.pos.add(DetectorUtil.dimPos(positions[0]));
		return info;
	}
}

package bid.yuanlu.mc.warehouse.impl.container;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import bid.yuanlu.mc.warehouse.api.container.SlotRole;

/**
 * 酿造台（PDD §8.2）：3 药水位=MACHINE_INPUT、材料位=SPECIAL、粉末位=SPECIAL（5 格）。
 * <p>
 * 槽位布局（BrewingStandMenu）：0-2 药水位、3 材料、4 粉末。
 */
public final class BrewingStandDetector extends BlockEntityDetector {

	public static final String ID = "brewing_stand";

	public BrewingStandDetector() {
		super(ID, beTypes(BlockEntityType.BREWING_STAND), Set.of(MenuType.BREWING_STAND));
	}

	@Override
	protected boolean slotCountMatches(int containerSlots) {
		return containerSlots == 5;
	}

	@Override
	protected SlotInfo roleFor(int slot, int containerSlots) {
		return switch (slot) {
			case 0, 1, 2 -> SlotInfo.of(SlotRole.MACHINE_INPUT, true, true);
			case 3, 4 -> SlotInfo.of(SlotRole.SPECIAL, true, true);
			default -> SlotInfo.GENERIC;
		};
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

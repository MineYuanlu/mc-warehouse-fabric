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
 * 熔炉系（PDD §8.2/§8.5）：slot0=MACHINE_INPUT、slot1=MACHINE_FUEL、
 * slot2=MACHINE_OUTPUT（仅可取）。
 */
public final class FurnaceDetector extends BlockEntityDetector {

	public static final FurnaceDetector FURNACE = new FurnaceDetector("furnace",
			BlockEntityType.FURNACE, MenuType.FURNACE);
	public static final FurnaceDetector BLAST_FURNACE = new FurnaceDetector("blast_furnace",
			BlockEntityType.BLAST_FURNACE, MenuType.BLAST_FURNACE);
	public static final FurnaceDetector SMOKER = new FurnaceDetector("smoker",
			BlockEntityType.SMOKER, MenuType.SMOKER);

	private FurnaceDetector(String id, BlockEntityType<?> beType, MenuType<?> menuType) {
		super(id, Set.of(beType), Set.of(menuType));
	}

	@Override
	protected boolean slotCountMatches(int containerSlots) {
		return containerSlots == 3;
	}

	@Override
	protected SlotInfo roleFor(int slot, int containerSlots) {
		return switch (slot) {
			case 0 -> SlotInfo.of(SlotRole.MACHINE_INPUT, true, true);
			case 1 -> SlotInfo.of(SlotRole.MACHINE_FUEL, true, true);
			case 2 -> SlotInfo.of(SlotRole.MACHINE_OUTPUT, true, false);
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

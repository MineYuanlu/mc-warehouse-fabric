package bid.yuanlu.mc.warehouse.impl.container;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;

/**
 * 发射器/投掷器（PDD §8.5）：共用 GENERIC_3x3 菜单，9 格全 GENERIC。
 */
public final class DispenserDropperDetector extends BlockEntityDetector {

	public static final String ID = "dispenser_dropper";

	public DispenserDropperDetector() {
		super(ID, beTypes(BeTypes.of("dispenser"), BeTypes.of("dropper")), Set.of(MenuType.GENERIC_3x3));
	}

	@Override
	protected boolean slotCountMatches(int containerSlots) {
		return containerSlots == 9;
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

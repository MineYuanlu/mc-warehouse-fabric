package bid.yuanlu.mc.warehouse.impl.container;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;

/**
 * 潜影盒（PDD §8.5/§8.6）：27 格，视为普通容器整体搬运。
 */
public final class ShulkerBoxDetector extends BlockEntityDetector {

	public static final String ID = "shulker_box";

	public ShulkerBoxDetector() {
		super(ID, beTypes(BeTypes.of("shulker_box")), Set.of(MenuType.SHULKER_BOX));
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

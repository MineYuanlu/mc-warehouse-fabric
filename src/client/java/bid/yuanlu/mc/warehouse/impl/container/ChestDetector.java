package bid.yuanlu.mc.warehouse.impl.container;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 箱子/大箱子/陷阱箱（PDD §8.5）：GENERIC_9x1..9x6 菜单；双格自动合并关联（§3.2）。
 */
public final class ChestDetector extends BlockEntityDetector {

	public static final String ID = "chest";

	public ChestDetector() {
		super(ID, beTypes(BeTypes.of("chest"), BeTypes.of("trapped_chest")),
				Set.of(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
						MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6));
	}

	@Override
	protected boolean slotCountMatches(int containerSlots) {
		return containerSlots > 0 && containerSlots % 9 == 0;
	}

	/**
	 * 大箱子标题放宽（§8.5）：26.1 双箱菜单标题 = container.chestDouble（"Large Chest"，
	 * 任一半边有自定义名时为该名），与半边 BE 默认名 container.chest 不同源——
	 * 标题差异不构成身份否定。仅对 54 格（GENERIC_9x6）菜单放行，单箱校验保持严格。
	 */
	@Override
	protected boolean titleMismatchAllowed(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
			@Nullable bid.yuanlu.mc.warehouse.api.container.ContainerOpenContext ctx) {
		if (screen.getMenu().getType() != MenuType.GENERIC_9x6) return false;
		net.minecraft.network.chat.Component title = screen.getTitle();
		if (title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
				&& "container.chestDouble".equals(tc.getKey())) {
			return true;
		}
		// 自定义名分支：标题 = 任一半边的 DisplayName
		if (ctx == null) return false;
		BlockState state = ctx.block().getState();
		if (!(state.getBlock() instanceof ChestBlock)) return false;
		if (ctx.block().getEntity() instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity named
				&& title.equals(named.getDisplayName())) {
			return true;
		}
		BlockPos other = ChestBlock.getConnectedBlockPos(ctx.block().getPos(), state);
		var level = ctx.block().getLevel();
		return other != null && level != null
				&& level.getBlockEntity(other) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity named2
				&& title.equals(named2.getDisplayName());
	}

	private static String safeWorldId() {
		try {
			return java.util.Objects.requireNonNullElse(WorldSessionTracker.get().currentWorldId(), "");
		} catch (IllegalStateException e) {
			return "";
		}
	}

	@Override
	@Nullable
	public ContainerInfo resolveMultiBlock(BlockPos[] positions) {
		var level = Minecraft.getInstance().level;
		if (level == null || positions.length == 0) return null;

		LinkedHashSet<BlockPos> merged = new LinkedHashSet<>();
		for (BlockPos pos : positions) {
			BlockPos immutable = pos.immutable();
			merged.add(immutable);
			BlockState state = level.getBlockState(immutable);
			if (state.getBlock() instanceof ChestBlock) {
				BlockPos other = ChestBlock.getConnectedBlockPos(immutable, state);
				if (other != null && level.getBlockState(other).getBlock() instanceof ChestBlock) {
					merged.add(other);
				}
			}
		}

		ContainerInfo info = new ContainerInfo(IOType.INPUT);
		String worldId = safeWorldId();
		String dimId = level.dimension().identifier().toString();
		for (BlockPos p : merged.stream().sorted().toList()) {
			info.pos.add(new WorldDimPos(worldId.isEmpty() ? null : worldId, dimId, p.getX(), p.getY(), p.getZ()));
		}
		return info;
	}
}

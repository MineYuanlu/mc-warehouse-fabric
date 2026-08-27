package bid.yuanlu.mc.warehouse.impl.container;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.block.entity.BlockEntityType;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.SlotInfo;
import bid.yuanlu.mc.warehouse.api.container.SlotRole;

/**
 * 熔炉系（PDD §8.2/§8.5）：slot0=MACHINE_INPUT、slot1=MACHINE_FUEL、
 * slot2=MACHINE_OUTPUT（仅可取）。
 * <p>
 * 放入过滤镜像原版 AbstractFurnaceMenu.quickMoveStack 路由（§8.2 第 3 条）：
 * 可熔炼（同步的 {@link RecipePropertySet} 判定）→ 输入槽、可燃 → 燃料槽、
 * 两者皆非 → 服务端拒收（quickMove no-op，会逐击超时暂停整轮——须在需求生成期过滤）。
 */
public final class FurnaceDetector extends BlockEntityDetector {

	public static final FurnaceDetector FURNACE = new FurnaceDetector("furnace",
			BlockEntityType.FURNACE, MenuType.FURNACE, RecipePropertySet.FURNACE_INPUT);
	public static final FurnaceDetector BLAST_FURNACE = new FurnaceDetector("blast_furnace",
			BlockEntityType.BLAST_FURNACE, MenuType.BLAST_FURNACE, RecipePropertySet.BLAST_FURNACE_INPUT);
	public static final FurnaceDetector SMOKER = new FurnaceDetector("smoker",
			BlockEntityType.SMOKER, MenuType.SMOKER, RecipePropertySet.SMOKER_INPUT);

	/** 各类型对应的可熔炼物品集（客户端同步，与服务端菜单判定同源） */
	private final ResourceKey<RecipePropertySet> inputPropertySet;

	/** 可熔炼判定缓存（物品级；进程内足够） */
	private final Set<ItemStack> smeltableCache = ConcurrentHashMap.newKeySet();
	private final Set<ItemStack> nonSmeltableCache = ConcurrentHashMap.newKeySet();

	private FurnaceDetector(String id, BlockEntityType<?> beType, MenuType<?> menuType,
			ResourceKey<RecipePropertySet> inputPropertySet) {
		super(id, Set.of(beType), Set.of(menuType));
		this.inputPropertySet = inputPropertySet;
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
	public boolean acceptsPut(SlotInfo slot, ItemStack stack) {
		return switch (slot.role()) {
			case MACHINE_FUEL -> isFuel(stack);
			case MACHINE_INPUT -> canSmelt(stack);
			default -> slot.canPutTo();
		};
	}

	private static boolean isFuel(ItemStack stack) {
		var level = Minecraft.getInstance().level;
		return level != null && level.fuelValues().isFuel(stack);
	}

	private boolean canSmelt(ItemStack stack) {
		if (smeltableCache.contains(stack)) return true;
		if (nonSmeltableCache.contains(stack)) return false;
		var level = Minecraft.getInstance().level;
		boolean smeltable = level != null
				&& level.recipeAccess().propertySet(inputPropertySet).test(stack.copyWithCount(1));
		(smeltable ? smeltableCache : nonSmeltableCache).add(stack);
		return smeltable;
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

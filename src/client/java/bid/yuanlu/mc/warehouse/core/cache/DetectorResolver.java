package bid.yuanlu.mc.warehouse.core.cache;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;

/**
 * Detector 定位与 playerScoped 缓存键解析（PDD §3.8）。
 * <p>
 * 引擎（keyOf）、命令层（container memory）与标记模式（缓存种子）共用——
 * 仅当该位置的容器确属 {@link ContainerDetector#playerScoped()} 时才在缓存键附 playerUUID。
 */
public final class DetectorResolver {

	private DetectorResolver() {
	}

	/** 该方块位置命中的 Detector；无则 null */
	@Nullable
	public static ContainerDetector at(Level level, BlockPos pos) {
		BlockInWorld biw = new BlockInWorld(level, pos, true);
		for (ContainerDetector d : WarehouseRegistryImpl.detectors()) {
			if (d.matchesBlock(biw)) return d;
		}
		return null;
	}

	/** 当前客户端世界下该坐标命中的 Detector；不在世界/无命中返回 null */
	@Nullable
	public static ContainerDetector at(WorldDimPos pos) {
		Level level = Minecraft.getInstance().level;
		return level == null ? null : at(level, pos.toBlockPos());
	}

	/** 当前客户端世界下该绝对坐标命中的 Detector */
	@Nullable
	public static ContainerDetector at(BlockPos pos) {
		Level level = Minecraft.getInstance().level;
		return level == null ? null : at(level, pos);
	}

	/** playerScoped Detector → 当前玩家 UUID；否则 null（缓存键不附玩家） */
	@Nullable
	public static UUID playerUuidIfScoped(@Nullable ContainerDetector detector) {
		if (detector == null || !detector.playerScoped()) return null;
		LocalPlayer p = Minecraft.getInstance().player;
		return p == null ? null : p.getUUID();
	}
}

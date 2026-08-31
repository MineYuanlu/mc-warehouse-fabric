package bid.yuanlu.mc.warehouse.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;

import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 客户端会话助手：当前 (serverId, worldName, dimId) 三元组——命令、快捷键、UI 屏
 * 共用同一语义（对齐 CommandSupport.currentDim）：会话未就绪 / 未在世界中返回 null。
 */
public final class ClientSession {

	@Nullable
	public static WorldDim currentDim(Minecraft mc) {
		WorldSessionTracker trk;
		try {
			trk = WorldSessionTracker.get();
		} catch (IllegalStateException e) {
			return null;
		}
		String serverId = trk.currentServerId();
		String worldName = trk.currentWorldName();
		String dimId = mc.level == null ? null : mc.level.dimension().identifier().toString();
		if (serverId == null || worldName == null || dimId == null) {
			return null;
		}
		return new WorldDim(serverId, worldName, dimId);
	}

	/** 准星指向方块；无命中时玩家脚下（等价命令缺省/`--look` 兜底语义）。 */
	public static net.minecraft.core.BlockPos lookOrFeet(Minecraft mc) {
		if (mc.player == null) {
			return net.minecraft.core.BlockPos.ZERO;
		}
		return mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getBlockPos() != null
				? hit.getBlockPos()
				: mc.player.blockPosition();
	}

	private ClientSession() {
	}
}

package bid.yuanlu.mc.warehouse.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import bid.yuanlu.mc.warehouse.core.cache.PlayerOpenRefresher;
import bid.yuanlu.mc.warehouse.core.engine.container.OpenScreenCapture;

/**
 * 捕获开屏同步包的 containerId 作为会话身份键（PDD §6.1，MVP 已验证的手法）；
 * F2 追加：关屏包与开合块事件信号，供玩家开箱缓存刷新配对。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

	@Inject(method = "handleOpenScreen", at = @At("HEAD"))
	private void warehouse$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		OpenScreenCapture.fireOpenScreen(packet.getContainerId());
	}

	@Inject(method = "handleContainerClose", at = @At("HEAD"))
	private void warehouse$onContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
		PlayerOpenRefresher.get().onServerCloseContainer();
	}

	@Inject(method = "handleBlockEvent", at = @At("HEAD"))
	private void warehouse$onBlockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
		PlayerOpenRefresher.get().onBlockEvent(packet.getPos(), packet.getB0(), packet.getB1());
	}
}

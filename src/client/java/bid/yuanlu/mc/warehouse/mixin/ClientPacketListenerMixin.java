package bid.yuanlu.mc.warehouse.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import bid.yuanlu.mc.warehouse.core.engine.container.OpenScreenCapture;

/**
 * 捕获开屏同步包的 containerId 作为会话身份键（PDD §6.1，MVP 已验证的手法）。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

	@Inject(method = "handleOpenScreen", at = @At("HEAD"))
	private void warehouse$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		OpenScreenCapture.fireOpenScreen(packet.getContainerId());
	}
}

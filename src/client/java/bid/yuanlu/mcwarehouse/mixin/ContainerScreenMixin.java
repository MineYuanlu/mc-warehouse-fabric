package bid.yuanlu.mcwarehouse.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import bid.yuanlu.mcwarehouse.engine.container.ContainerInteractor;

@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {

	@Inject(method = "onClose", at = @At("HEAD"))
	private void onClose(CallbackInfo ci) {
		ContainerInteractor.onScreenClosed();
	}
}

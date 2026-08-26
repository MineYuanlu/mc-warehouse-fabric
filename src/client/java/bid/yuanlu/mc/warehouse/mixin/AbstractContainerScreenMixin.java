package bid.yuanlu.mc.warehouse.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import bid.yuanlu.mc.warehouse.core.engine.container.ScreenHooks;

/**
 * 容器 UI 关闭时自动刷新 ContainerMemory（PDD §8.7）。
 * 会话绑定校验由 ScreenHooks 的处理器（协议层）负责。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Inject(method = "onClose", at = @At("HEAD"))
	private void warehouse$onClose(CallbackInfo ci) {
		ScreenHooks.fireOnClose((AbstractContainerScreen<?>) (Object) this);
	}
}

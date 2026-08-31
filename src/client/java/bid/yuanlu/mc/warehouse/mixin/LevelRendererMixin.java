package bid.yuanlu.mc.warehouse.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import bid.yuanlu.mc.warehouse.ui.mc.UiHooks;

/**
 * 世界渲染帧钩子（UI-PDD §7）：renderLevel 头部 = per-frame Gizmos 收集窗口内、
 * {@code finalizeGizmoCollection()}（L644）之前——此处提交的 Gizmo 同帧渲染。
 * 只 fire 事件，不含任何业务/渲染逻辑（Wurst7 事件解耦模式）。
 */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class LevelRendererMixin {

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void yuanlu$warehouse$onRenderLevelHead(CallbackInfo ci) {
		UiHooks.firePerFrameWorld();
	}
}

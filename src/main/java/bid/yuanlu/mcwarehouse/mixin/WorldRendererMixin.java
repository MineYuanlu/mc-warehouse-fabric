package bid.yuanlu.mcwarehouse.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import bid.yuanlu.mcwarehouse.engine.highlight.ContainerOutlineRenderer;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {

	@Inject(method = "collectPerFrameGizmos", at = @At("RETURN"))
	private void onCollectGizmos(CallbackInfoReturnable<TemporaryCollection> cir) {
		ContainerOutlineRenderer.renderGizmos();
	}
}

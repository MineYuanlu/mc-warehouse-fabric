package bid.yuanlu.mcwarehouse.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import bid.yuanlu.mcwarehouse.controller.PathfindingController;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void onUseItemOn(Player player, InteractionHand hand, BlockHitResult result,
			CallbackInfoReturnable<InteractionResult> cir) {
		PathfindingController.getInstance().onBlockInteraction(result.getBlockPos());
	}
}

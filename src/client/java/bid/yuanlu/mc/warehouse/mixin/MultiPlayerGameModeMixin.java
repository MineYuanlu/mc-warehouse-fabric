package bid.yuanlu.mc.warehouse.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import bid.yuanlu.mc.warehouse.core.cache.PlayerOpenRefresher;

/**
 * 捕获玩家点击容器的瞬间（F2）：useItemOn 返回 consumed 时记录坐标。
 * <p>
 * 坐标在点击发包时定格——网络延时中移开视线不影响配对。
 * 排除两类误报：潜行+手持物品（方块 use 分支被跳过，走放置分支）、界面上点击。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void warehouse$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit,
			CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = cir.getReturnValue();
		if (result == null || !result.consumesAction()) return;
		// 潜行+手持物品 = 物品放置分支（performUseItemOn 的 suppressUsingBlock 跳过方块 use）
		if (player.isSecondaryUseActive()
				&& (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) return;
		if (Minecraft.getInstance().screen != null) return;
		PlayerOpenRefresher.get().onClickConsumed(blockHit.getBlockPos());
	}
}

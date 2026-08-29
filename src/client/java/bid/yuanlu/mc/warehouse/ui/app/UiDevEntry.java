package bid.yuanlu.mc.warehouse.ui.app;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * M0 临时开发入口：/whui 打开 demo 屏（UI-PDD §14 M0）。M3 起由 /wh UI 按钮取代后移除。
 */
public final class UiDevEntry {

	private UiDevEntry() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher
				.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("whui")
						.executes(ctx -> {
							UiPlatform.openScreen(UiDemoScreens::create);
							return 1;
						})));
	}
}

package bid.yuanlu.mc.warehouse.ui.app;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import bid.yuanlu.mc.warehouse.ui.app.screen.WarehouseScreens;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * 开发入口：/whui 打开仓库管理主屏（快捷键 wh.ui.open 的命令等价入口）。
 */
public final class UiDevEntry {

	private UiDevEntry() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher
				.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("whui")
						.executes(ctx -> {
							WarehouseScreens.open();
							return 1;
						})));
	}
}

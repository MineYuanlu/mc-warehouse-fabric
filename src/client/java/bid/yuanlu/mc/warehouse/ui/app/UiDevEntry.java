package bid.yuanlu.mc.warehouse.ui.app;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import bid.yuanlu.mc.warehouse.ui.app.screen.WarehouseScreens;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * 开发入口：/whui 打开仓库管理主屏（快捷键 wh.ui.open 的命令等价入口）。
 * <p>
 * 命令在 ChatScreen 回车路径上同步执行：此时立即 setScreen 会被聊天窗
 * 关闭时的 {@code setScreen(null)} 覆盖（ChatScreen.keyPressed 的
 * closeOnSubmit 分支在命令返回后才执行），故只置标记、下一 tick 再开屏。
 */
public final class UiDevEntry {

	private static volatile boolean pendingOpen;

	private UiDevEntry() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher
				.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("whui")
						.executes(ctx -> {
							pendingOpen = true;
							return 1;
						})));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (pendingOpen) {
				pendingOpen = false;
				WarehouseScreens.open();
			}
		});
	}
}

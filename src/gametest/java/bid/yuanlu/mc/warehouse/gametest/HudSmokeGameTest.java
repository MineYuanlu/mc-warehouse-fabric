package bid.yuanlu.mc.warehouse.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.app.hud.HudRootFactory;
import bid.yuanlu.mc.warehouse.ui.app.presenter.HudPresenter;
import bid.yuanlu.mc.warehouse.ui.app.screen.HudSettingsScreens;
import bid.yuanlu.mc.warehouse.ui.mc.mc261.Mc261ScreenHost;

/**
 * HUD E2E 冒烟（UI-PDD §14 M1 验收）：进入世界 → HUD extract 渲染（截图）→
 * HUD 设置屏开屏/关闭。断言用原生 assert（与其他 gametest 一致）。
 * CI grep {@code yuanlu-warehouse HUD smoke assertions passed} 判定成功。
 */
@SuppressWarnings("UnstableApiUsage")
public class HudSmokeGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(singleplayer);
			context.waitTicks(20);

			context.runOnClient(mc -> {
				assert mc.player != null : "应已在世界中";
				HudPresenter.get().refresh();
			});
			// HUD extract 全链路（有 player 才渲染）：截图即证明渲染路径无异常
			context.takeScreenshot("yuanlu-warehouse-hud");

			// HUD 设置屏开屏冒烟
			context.setScreen(() -> new Mc261ScreenHost(
					Component.translatable("ui.wh.hud.settings.title"), HudSettingsScreens::create));
			context.waitForScreen(Mc261ScreenHost.class);
			context.waitTicks(5);
			context.takeScreenshot("yuanlu-warehouse-hud-settings");
			context.runOnClient(mc -> mc.setScreen(null));
			context.waitTicks(2);

			LOGGER.info("yuanlu-warehouse HUD smoke assertions passed");

			// 绕开 close() 死锁（见 WarehouseClientGameTest 注释）
			singleplayer.getServer().runOnServer(server -> server.halt(false));
		}
	}
}

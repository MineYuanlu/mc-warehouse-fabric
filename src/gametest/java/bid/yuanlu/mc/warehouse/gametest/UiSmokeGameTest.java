package bid.yuanlu.mc.warehouse.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.app.UiDemoScreens;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.mc.mc261.Mc261ScreenHost;

/**
 * UI 层 E2E 冒烟（UI-PDD §14 M0 验收）：真实启动 MC → 打开 demo Screen →
 * 断言元素树完成布局 → 截图 → 关闭。不需要单机世界（主菜单即可）。
 * 断言用原生 assert（与其他 gametest 一致：MC 运行时无 JUnit 类）。
 * CI grep {@code yuanlu-warehouse UI smoke assertions passed} 判定成功。
 */
@SuppressWarnings("UnstableApiUsage")
public class UiSmokeGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> new Mc261ScreenHost(Component.translatable("ui.wh.demo.title"),
				UiDemoScreens::create));
		context.waitForScreen(Mc261ScreenHost.class);
		context.waitTicks(10);

		context.runOnClient(mc -> {
			var host = (Mc261ScreenHost) mc.screen;
			assert host != null : "demo screen 应处于打开状态";
			UiRoot root = host.root();
			assert root != null : "根元素应已构建";

			// 布局已展开：面板有正尺寸、水平居中
			assert root.children().size() == 1 : "根应只有一个内容面板";
			PanelElement panel = (PanelElement) root.children().get(0);
			assert panel.width() > 0 && panel.height() > 0 : "面板应完成 measure/arrange";
			int centeredX = (mc.getWindow().getGuiScaledWidth() - panel.width()) / 2;
			assert Math.abs(panel.absX() - centeredX) <= 1 : "面板应水平居中: " + panel.absX() + " vs " + centeredX;
		});

		context.takeScreenshot("yuanlu-warehouse-ui-demo");

		context.runOnClient(mc -> mc.setScreen(null));
		context.waitTicks(2);
		context.runOnClient(mc -> {
			assert !(mc.screen instanceof Mc261ScreenHost) : "demo 屏应已关闭";
		});

		LOGGER.info("yuanlu-warehouse UI smoke assertions passed");
	}
}

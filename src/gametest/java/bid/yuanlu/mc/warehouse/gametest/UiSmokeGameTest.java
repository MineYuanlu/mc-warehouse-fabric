package bid.yuanlu.mc.warehouse.gametest;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.app.screen.ConfigScreens;
import bid.yuanlu.mc.warehouse.ui.app.screen.HudSettingsScreens;
import bid.yuanlu.mc.warehouse.ui.app.screen.RuleScreens;
import bid.yuanlu.mc.warehouse.ui.app.screen.SelectionPanelScreens;
import bid.yuanlu.mc.warehouse.ui.app.screen.WarehouseScreens;
import bid.yuanlu.mc.warehouse.ui.app.screen.WorldScreens;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.mc.mc261.Mc261ScreenHost;

/**
 * UI 层 E2E 冒烟（UI-PDD §13）：真实启动 MC → 打开各业务 Screen →
 * 断言元素树完成布局 → 截图 → 关闭。v0.3 起覆盖七页导航的每个页面。
 * 不需要单机世界（主菜单即可）。断言用原生 assert（与其他 gametest 一致：
 * MC 运行时无 JUnit 类）。CI grep {@code yuanlu-warehouse UI smoke assertions passed} 判定成功。
 */
@SuppressWarnings("UnstableApiUsage")
public class UiSmokeGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	/** 逐页冒烟条目：keepHud 页（HUD 设置屏 passthrough）用专用构造。 */
	private record Page(String name, Supplier<UiRoot> factory, boolean keepHud) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> new Mc261ScreenHost(Component.translatable("ui.wh.demo.title"),
				WarehouseScreens::create));
		context.waitForScreen(Mc261ScreenHost.class);
		context.waitTicks(10);

		context.runOnClient(mc -> {
			var host = (Mc261ScreenHost) mc.screen;
			assert host != null : "demo screen 应处于打开状态";
			UiRoot root = host.root();
			assert root != null : "根元素应已构建";

			// 布局已展开：脚手架经根级 grow 权重全屏满铺（flex 布局）
			assert root.children().size() == 1 : "根应只有一个脚手架";
			assert !root.children().get(0).children().isEmpty() : "主屏应有页签内容";
			var scaffold = root.children().get(0);
			assert scaffold.absX() == 0 && scaffold.absY() == 0 : "脚手架应满铺原点: "
					+ scaffold.absX() + "," + scaffold.absY();
			assert scaffold.width() == root.width() && scaffold.height() == root.height()
					: "脚手架应为全屏尺寸: " + scaffold.width() + "x" + scaffold.height()
					+ " vs " + root.width() + "x" + root.height();
		});

		context.takeScreenshot("yuanlu-warehouse-ui-demo");

		context.runOnClient(mc -> mc.setScreen(null));
		context.waitTicks(2);
		context.runOnClient(mc -> {
			assert !(mc.screen instanceof Mc261ScreenHost) : "demo 屏应已关闭";
		});

		// 七页导航逐页开屏冒烟（UI-PDD §5 v0.3）：root 构建 + 布局展开 + 截图
		var pages = List.of(
				new Page("warehouse", WarehouseScreens::create, false),
				new Page("engine", () -> WarehouseScreens.create(1), false),
				new Page("rules", RuleScreens::create, false),
				new Page("selection", SelectionPanelScreens::create, false),
				new Page("world", WorldScreens::create, false),
				new Page("config", ConfigScreens::create, false),
				new Page("hud-settings", HudSettingsScreens::create, true));
		for (Page p : pages) {
			context.setScreen(() -> new Mc261ScreenHost(Component.translatable("ui.wh.demo.title"),
					p.factory(), p.keepHud()));
			context.waitForScreen(Mc261ScreenHost.class);
			context.waitTicks(2);
			context.runOnClient(mc -> {
				var host = (Mc261ScreenHost) mc.screen;
				assert host != null : p.name() + " 屏应处于打开状态";
				UiRoot root = host.root();
				assert root != null : p.name() + " 根元素应已构建";
				assert !root.children().isEmpty() : p.name() + " 应有内容";
			});
			context.takeScreenshot("yuanlu-warehouse-ui-" + p.name());
			context.runOnClient(mc -> mc.setScreen(null));
			context.waitTicks(2);
		}

		LOGGER.info("yuanlu-warehouse UI smoke assertions passed");
	}
}

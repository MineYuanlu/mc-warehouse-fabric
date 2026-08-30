package bid.yuanlu.mc.warehouse.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.app.hud.HudConfig;
import bid.yuanlu.mc.warehouse.ui.app.hud.HudLayout;
import bid.yuanlu.mc.warehouse.ui.app.hud.HudRootFactory;
import bid.yuanlu.mc.warehouse.ui.app.presenter.HudPresenter;
import bid.yuanlu.mc.warehouse.ui.app.screen.HudSettingsScreens;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import bid.yuanlu.mc.warehouse.ui.mc.mc261.Mc261ScreenHost;

/**
 * HUD E2E 冒烟（UI-PDD §14 M1 验收）：进入世界 → 建仓库使 HUD 有内容 →
 * HUD extract 渲染（截图）→ HUD 设置屏开屏（HUD 组置于屏幕中央与面板重叠，
 * 验证 HUD 渲染在面板之上）→ 模拟按住 HUD 本体拖拽（bounds 抓取 + 子像素累加）。
 * 断言用原生 assert（与其他 gametest 一致）。
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

			// 建仓库让 HUD 有内容；把 TOP_LEFT 组挪到屏幕中央（后面与设置面板重叠验证 z 序）
			context.runOnClient(mc -> {
				assert mc.player != null : "应已在世界中";
				var mgr = WarehouseManagerImpl.get();
				if (mgr.get("hud-smoke") == null) {
					mgr.create("hud-smoke");
				}
				mgr.activate("hud-smoke");
				int cx = mc.getWindow().getGuiScaledWidth() / 2 - 30;
				int cy = mc.getWindow().getGuiScaledHeight() / 2 - 30;
				for (HudConfig.Block b : HudConfig.Block.values()) {
					HudConfig.get().get(b).offsetX = cx;
					HudConfig.get().get(b).offsetY = cy;
				}
				HudConfig.save(HudConfig.get());
				UiPlatform.resetHud("hud");
				HudPresenter.get().refresh();
			});
			// HUD extract 全链路（有 player 才渲染）：截图即证明渲染路径无异常
			context.takeScreenshot("yuanlu-warehouse-hud");

			// HUD 设置屏开屏冒烟：与生产入口一致走 hudPassthrough=true（HUD 由本屏
			// 代渲染在面板之上）；两参构造（非 passthrough）会隐藏 HUD，测不到真实路径
			context.setScreen(() -> new Mc261ScreenHost(
					Component.translatable("ui.wh.hud.settings.title"), HudSettingsScreens::create, true));
			context.waitForScreen(Mc261ScreenHost.class);
			context.waitTicks(5);
			context.takeScreenshot("yuanlu-warehouse-hud-settings");

			// 模拟按住 HUD 本体拖拽：按下点在 HUD bounds 中心（同时也在中央面板行上，
			// 即用户"HUD 拖进面板后再也拖不动"的场景——capture 抢占后必须仍可拖走）。
			// 子像素序列：0.4+0.4+0.7=1.5 → 旧代码 (int) 每帧截断为 0，新代码累计位移 1px。
			context.runOnClient(mc -> {
				var host = (Mc261ScreenHost) mc.screen;
				UiRoot root = host.root();
				assert root != null : "设置屏根元素应已构建";
				int[] b = HudLayout.groupBounds(HudConfig.Corner.TOP_LEFT);
				assert b != null : "TOP_LEFT 组应有布局 bounds（设置屏期间 HUD 代渲染）";
				int gx = b[0] + b[2] / 2;
				int gy = b[1] + b[3] / 2;
				var bc = HudConfig.get().get(HudConfig.Block.WAREHOUSE);
				int beforeX = bc.offsetX;
				int beforeY = bc.offsetY;
				assert root.mouseDown(gx, gy, 0) : "按住 HUD 本体应命中设置屏拖拽层";
				root.mouseMoved(gx + 10, gy + 10); // 越过 3px 阈值 → DRAG_START（不位移）
				root.mouseMoved(gx + 10.4, gy + 10.4); // DRAG +0.4 → 残量累加
				root.mouseMoved(gx + 10.8, gy + 10.8); // DRAG +0.4 → 残量 0.8
				root.mouseMoved(gx + 11.5, gy + 11.5); // DRAG +0.7 → 累计 1.5 → 位移 1px
				root.mouseUp(gx + 11.5, gy + 11.5, 0);
				assert bc.offsetX == beforeX + 1 && bc.offsetY == beforeY + 1
						: "按住 HUD 本体拖拽应平移组且子像素不丢失: "
								+ bc.offsetX + "," + bc.offsetY + " vs " + (beforeX + 1) + "," + (beforeY + 1);
			});

			// 还原 HUD 位置（测试不留痕）
			context.runOnClient(mc -> {
				for (HudConfig.Block b : HudConfig.Block.values()) {
					HudConfig.get().get(b).offsetX = 4;
					HudConfig.get().get(b).offsetY = 4;
				}
				HudConfig.save(HudConfig.get());
				UiPlatform.resetHud("hud");
			});
			context.runOnClient(mc -> mc.setScreen(null));
			context.waitTicks(2);

			LOGGER.info("yuanlu-warehouse HUD smoke assertions passed");

			// 绕开 close() 死锁（见 WarehouseClientGameTest 注释）
			singleplayer.getServer().runOnServer(server -> server.halt(false));
		}
	}
}

package bid.yuanlu.mc.warehouse.ui.app.screen;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.app.widget.Modal;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * 共享页头（UI-PDD §5.1 UX 决策）：所有页面在面板顶部同一位置出现同一排导航，
 * 位置不随页面切换变化（Litematica/malilib 左上角式）；当前页按钮禁用。
 * 页面切换 = 整屏重建（构建时求解），页头不变因此无跳动感。
 */
public final class ScreenHeader {

	public enum Page {
		WAREHOUSE, ENGINE, RULES, SELECTION, WORLD, CONFIG, HUD_SETTINGS
	}

	private ScreenHeader() {
	}

	/** 全页共用的导航按钮排。 */
	public static PanelElement create(Page current) {
		var row = PanelElement.plain().padding(2).layout(new Row(4));
		row.add(navButton(current, Page.WAREHOUSE, "ui.wh.main.tab.warehouse"));
		row.add(navButton(current, Page.ENGINE, "ui.wh.main.tab.engine"));
		row.add(navButton(current, Page.RULES, "ui.wh.main.tab.rules"));
		row.add(navButton(current, Page.SELECTION, "ui.wh.main.tab.select"));
		row.add(navButton(current, Page.WORLD, "ui.wh.main.tab.world"));
		row.add(navButton(current, Page.CONFIG, "ui.wh.main.tab.config"));
		row.add(navButton(current, Page.HUD_SETTINGS, "ui.wh.main.tab.hudsettings"));
		return row;
	}

	private static ButtonElement navButton(Page current, Page target, String key) {
		var b = new ButtonElement(Component.translatable(key));
		if (current == target) {
			return b.enabled(false);
		}
		return b.onClick(() -> {
			Modal.close();
			open(target);
		});
	}

	/** 打开目标页（仓库/引擎共用主屏，其余为独立子屏）。 */
	public static void open(Page page) {
		switch (page) {
			case WAREHOUSE -> WarehouseScreens.open(0);
			case ENGINE -> WarehouseScreens.open(1);
			case RULES -> RuleScreens.open();
			case SELECTION -> SelectionPanelScreens.open();
			case WORLD -> WorldScreens.open();
			case CONFIG -> ConfigScreens.open();
			case HUD_SETTINGS -> HudSettingsScreens.open();
		}
	}

	/** 子屏取消/完成：回主屏（不直接回游戏）。 */
	public static void backToMain() {
		UiPlatform.openScreen(WarehouseScreens::create);
	}
}

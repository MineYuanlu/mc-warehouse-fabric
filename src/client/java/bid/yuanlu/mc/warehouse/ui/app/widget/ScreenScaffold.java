package bid.yuanlu.mc.warehouse.ui.app.widget;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 全屏页面脚手架（UI-PDD §5.1，布局引擎 flex 化后）：作根唯一子级并 grow(1)——
 * 经 UiRoot 根级权重铺满整个屏幕，无固定外框；drawElement 铺一层半透明黑统一底色
 * （普通屏叠加在 MC 模糊背景之上，HUD 设置 passthrough 屏直接透出世界）。
 * 内容为 padding(10) 的 Column：页头固定、body grow(1) 撑满、底部操作行固定。
 */
public final class ScreenScaffold extends PanelElement {

	public ScreenScaffold() {
		filled(false).bordered(false);
		grow(1);
		padding(10);
		layout(new Column(6));
	}

	@Override
	protected void drawElement(UiDraw g) {
		int c = Theme.active().bgScreen();
		g.fill(absX(), absY(), absX() + width(), absY() + height(), c);
	}
}

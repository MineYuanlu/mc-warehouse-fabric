package bid.yuanlu.mc.warehouse.ui.core.element;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 渐变面板（BoxElement 思路：程序化上下渐变 + 描边，零贴图）。
 * 默认取主题 token；可覆盖颜色。
 */
public class PanelElement extends UiElement {

	private int fillTop = -1;
	private int fillBottom = -1;
	private int border = -1;
	private int borderBottom = -1;

	public PanelElement() {
	}

	public PanelElement colors(int fillTop, int fillBottom, int border, int borderBottom) {
		this.fillTop = fillTop;
		this.fillBottom = fillBottom;
		this.border = border;
		this.borderBottom = borderBottom;
		return this;
	}

	@Override
	protected void drawElement(UiDraw g) {
		var t = Theme.active();
		int top = fillTop != -1 ? fillTop : t.bgPanel();
		int bottom = fillBottom != -1 ? fillBottom : t.bgPanelGradient();
		g.fillGradient(absX(), absY(), absX() + width(), absY() + height(), top, bottom);
		g.outline(absX(), absY(), width(), height(), border != -1 ? border : t.border());
		if (height() >= 2) {
			g.fill(absX(), absY(), absX() + width(), absY() + 1,
					borderBottom != -1 ? borderBottom : t.borderGradient());
		}
	}
}

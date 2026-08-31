package bid.yuanlu.mc.warehouse.ui.core.element;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 渐变面板（BoxElement 思路：程序化上下渐变 + 描边，零贴图）。
 * 默认取主题 token；可覆盖颜色。作纯布局容器时应关闭填充与描边
 * （{@link #plain()}），避免嵌套面板层层叠线（UI-PDD §6.1 视觉决策）。
 */
public class PanelElement extends UiElement<PanelElement> {

	private int fillTop = -1;
	private int fillBottom = -1;
	private int border = -1;
	private int borderBottom = -1;
	private boolean filled = true;
	private boolean bordered = true;

	public PanelElement() {
	}

	/** 纯布局容器：不画任何背景/边框。 */
	public static PanelElement plain() {
		return new PanelElement().filled(false).bordered(false);
	}

	public PanelElement filled(boolean f) {
		filled = f;
		return this;
	}

	public PanelElement bordered(boolean b) {
		bordered = b;
		return this;
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
		if (filled) {
			int top = fillTop != -1 ? fillTop : t.bgPanel();
			int bottom = fillBottom != -1 ? fillBottom : t.bgPanelGradient();
			g.fillGradient(absX(), absY(), absX() + width(), absY() + height(), top, bottom);
		}
		if (bordered) {
			g.outline(absX(), absY(), width(), height(), border != -1 ? border : t.border());
			if (height() >= 2) {
				g.fill(absX(), absY(), absX() + width(), absY() + 1,
						borderBottom != -1 ? borderBottom : t.borderGradient());
			}
		}
	}
}

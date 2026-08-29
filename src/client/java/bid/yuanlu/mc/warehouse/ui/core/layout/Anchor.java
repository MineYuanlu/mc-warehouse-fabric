package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 定角锚点布局（HUD 用，UI-PDD §6）：把每个子级定位到容器（=整屏）指定角 + 偏移。
 * 建议单子级（内容列自带排布）。
 */
public final class Anchor implements Layout {

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}

	private final Corner corner;
	private final int offsetX;
	private final int offsetY;

	public Anchor(Corner corner, int offsetX, int offsetY) {
		this.corner = corner;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}

	@Override
	public void arrange(UiElement container, UiDraw g) {
		int cw = container.width() - container.padding() * 2;
		int ch = container.height() - container.padding() * 2;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			int w = Layout.effectiveWidth(c);
			int h = Layout.effectiveHeight(c);
			int x = switch (corner) {
				case TOP_LEFT, BOTTOM_LEFT -> offsetX;
				case TOP_RIGHT, BOTTOM_RIGHT -> cw - w - offsetX;
			};
			int y = switch (corner) {
				case TOP_LEFT, TOP_RIGHT -> offsetY;
				case BOTTOM_LEFT, BOTTOM_RIGHT -> ch - h - offsetY;
			};
			c.pos(x, y);
		}
	}

	@Override
	public int measureWidth(UiElement container, UiDraw g) {
		Layout.measureChildren(container, g);
		return container.padding() * 2;
	}

	@Override
	public int measureHeight(UiElement container, UiDraw g) {
		Layout.measureChildren(container, g);
		return container.padding() * 2;
	}
}

package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 横向堆叠。子级 AUTO 高度拉伸至容器内容高，AUTO 宽用首选宽。
 */
public final class Row implements Layout {

	private final int gap;

	public Row(int gap) {
		this.gap = gap;
	}

	@Override
	public void arrange(UiElement<?> container, UiDraw g) {
		int contentHeight = Math.max(0, container.height() - container.padding() * 2);
		int cursor = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			if (c.height() == UiElement.AUTO) {
				c.size(c.width(), contentHeight);
			}
			int cw = Layout.effectiveWidth(c);
			c.pos(cursor, 0);
			cursor += cw + gap;
		}
	}

	@Override
	public int measureWidth(UiElement<?> container, UiDraw g) {
		Layout.measureChildren(container, g);
		int total = 0;
		int count = 0;
		for (var c : container.children()) {
			if (c.visible()) {
				total += Layout.effectiveWidth(c);
				count++;
			}
		}
		return total + Math.max(0, count - 1) * gap + container.padding() * 2;
	}

	@Override
	public int measureHeight(UiElement<?> container, UiDraw g) {
		Layout.measureChildren(container, g);
		int max = 0;
		for (var c : container.children()) {
			if (c.visible()) {
				max = Math.max(max, Layout.effectiveHeight(c));
			}
		}
		return max + container.padding() * 2;
	}
}

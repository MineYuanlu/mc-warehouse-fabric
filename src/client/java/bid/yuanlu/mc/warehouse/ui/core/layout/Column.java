package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 纵向堆叠。子级 AUTO 宽度默认拉伸至容器内容宽（stretch 策略），AUTO 高用首选高。
 */
public final class Column implements Layout {

	private final int gap;

	public Column(int gap) {
		this.gap = gap;
	}

	@Override
	public void arrange(UiElement container, UiDraw g) {
		int contentWidth = Math.max(0, container.width() - container.padding() * 2);
		int cursor = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			if (c.width() == UiElement.AUTO) {
				c.size(contentWidth, c.height());
			}
			int ch = Layout.effectiveHeight(c);
			c.pos(0, cursor);
			cursor += ch + gap;
		}
	}

	@Override
	public int measureWidth(UiElement container, UiDraw g) {
		Layout.measureChildren(container, g);
		int max = 0;
		for (var c : container.children()) {
			if (c.visible()) {
				max = Math.max(max, Layout.effectiveWidth(c));
			}
		}
		return max + container.padding() * 2;
	}

	@Override
	public int measureHeight(UiElement container, UiDraw g) {
		Layout.measureChildren(container, g);
		int total = 0;
		int count = 0;
		for (var c : container.children()) {
			if (c.visible()) {
				total += Layout.effectiveHeight(c);
				count++;
			}
		}
		return total + Math.max(0, count - 1) * gap + container.padding() * 2;
	}
}

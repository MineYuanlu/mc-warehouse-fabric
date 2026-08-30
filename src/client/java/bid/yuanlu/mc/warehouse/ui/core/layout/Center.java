package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 居中布局：每个可见子级在容器内容区内水平垂直居中，越界时钳到 margin 内
 * （子级大于容器时钉在 margin 角）。定位发生在布局期（每帧 relayout），
 * 不依赖 tick 定时——onTick 居中在整屏重建后的首帧会闪现在左上角。
 */
public final class Center implements Layout {

	private final int margin;

	public Center(int margin) {
		this.margin = margin;
	}

	@Override
	public void arrange(UiElement<?> container, UiDraw g) {
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			int w = Layout.effectiveWidth(c);
			int h = Layout.effectiveHeight(c);
			int x = Math.max(margin, (container.width() - w) / 2);
			int y = Math.max(margin, (container.height() - h) / 2);
			x = Math.min(x, Math.max(margin, container.width() - w - margin));
			y = Math.min(y, Math.max(margin, container.height() - h - margin));
			c.pos(x, y);
		}
	}

	@Override
	public int measureWidth(UiElement<?> container, UiDraw g) {
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

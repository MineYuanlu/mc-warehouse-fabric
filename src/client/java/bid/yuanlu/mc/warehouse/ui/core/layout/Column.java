package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 纵向堆叠。子级 AUTO 宽度默认拉伸至容器内容宽（stretch 策略），AUTO 高用首选高；
 * 有 grow 权重的子级在容器主轴（高）尺寸为定值时瓜分剩余高度，显式声明高作为最小值。
 */
public final class Column implements Layout {

	private final int gap;

	public Column(int gap) {
		this.gap = gap;
	}

	@Override
	public void arrange(UiElement<?> container, UiDraw g) {
		int contentWidth = Math.max(0, container.width() - container.padding() * 2);
		int contentHeight = Math.max(0, container.height() - container.padding() * 2);
		// 第一遍：固定子级占位，求权重子级可瓜分的剩余高度
		int fixedTotal = 0;
		float weightSum = 0;
		int count = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			count++;
			if (c.grow() > 0) {
				weightSum += c.grow();
			} else {
				fixedTotal += Layout.effectiveHeight(c);
			}
		}
		int free = contentHeight - fixedTotal - Math.max(0, count - 1) * gap;
		// 第二遍：拉伸 + 权重分配 + 顺序定位（截断分配保证不超出剩余空间）
		int cursor = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			if (c.width() == UiElement.AUTO) {
				c.applySize(contentWidth, c.height());
			}
			if (c.grow() > 0 && weightSum > 0 && free > 0) {
				int share = (int) (free * (c.grow() / weightSum));
				c.applySize(c.width(), Math.max(Math.max(0, c.declaredHeight()), share));
			}
			c.pos(0, cursor);
			cursor += Layout.effectiveHeight(c) + gap;
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
		int total = 0;
		int count = 0;
		for (var c : container.children()) {
			if (c.visible()) {
				// 权重子级不贡献内容高（占剩余空间），仅声明最小值参与求和
				total += c.grow() > 0 ? Math.max(0, c.declaredHeight()) : Layout.effectiveHeight(c);
				count++;
			}
		}
		return total + Math.max(0, count - 1) * gap + container.padding() * 2;
	}
}

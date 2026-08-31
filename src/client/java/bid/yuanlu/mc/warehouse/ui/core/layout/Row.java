package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 横向堆叠。子级 AUTO 高度拉伸至容器内容高，AUTO 宽用首选宽；有 grow 权重的子级
 * 在容器主轴（宽）尺寸为定值时瓜分剩余宽度，显式声明宽作为最小值。
 */
public final class Row implements Layout {

	private final int gap;

	public Row(int gap) {
		this.gap = gap;
	}

	@Override
	public void arrange(UiElement<?> container, UiDraw g) {
		int contentHeight = Math.max(0, container.height() - container.padding() * 2);
		int contentWidth = Math.max(0, container.width() - container.padding() * 2);
		// 第一遍：固定子级占位，求权重子级可瓜分的剩余宽度
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
				fixedTotal += Layout.effectiveWidth(c);
			}
		}
		int free = contentWidth - fixedTotal - Math.max(0, count - 1) * gap;
		// 第二遍：拉伸 + 权重分配 + 顺序定位（截断分配保证不超出剩余空间）
		int cursor = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			if (c.height() == UiElement.AUTO) {
				c.applySize(c.width(), contentHeight);
			}
			if (c.grow() > 0 && weightSum > 0 && free > 0) {
				int share = (int) (free * (c.grow() / weightSum));
				c.applySize(Math.max(Math.max(0, c.declaredWidth()), share), c.height());
			}
			c.pos(cursor, 0);
			cursor += Layout.effectiveWidth(c) + gap;
		}
	}

	@Override
	public int measureWidth(UiElement<?> container, UiDraw g) {
		Layout.measureChildren(container, g);
		int total = 0;
		int count = 0;
		for (var c : container.children()) {
			if (c.visible()) {
				// 权重子级不贡献内容宽（占剩余空间），仅声明最小值参与求和
				total += c.grow() > 0 ? Math.max(0, c.declaredWidth()) : Layout.effectiveWidth(c);
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

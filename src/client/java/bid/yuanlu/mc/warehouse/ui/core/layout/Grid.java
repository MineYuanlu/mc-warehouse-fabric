package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 等宽等高网格。子级 AUTO 尺寸强制为单元格尺寸。
 */
public final class Grid implements Layout {

	private final int columns;
	private final int cellWidth;
	private final int cellHeight;
	private final int gapX;
	private final int gapY;

	public Grid(int columns, int cellWidth, int cellHeight, int gap) {
		this(columns, cellWidth, cellHeight, gap, gap);
	}

	public Grid(int columns, int cellWidth, int cellHeight, int gapX, int gapY) {
		this.columns = columns;
		this.cellWidth = cellWidth;
		this.cellHeight = cellHeight;
		this.gapX = gapX;
		this.gapY = gapY;
	}

	private int rows(UiElement<?> container) {
		int n = 0;
		for (var c : container.children()) {
			if (c.visible()) {
				n++;
			}
		}
		return Math.max(1, (n + columns - 1) / columns);
	}

	@Override
	public void arrange(UiElement<?> container, UiDraw g) {
		int i = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			int col = i % columns;
			int row = i / columns;
			if (c.width() == UiElement.AUTO && c.height() == UiElement.AUTO) {
				c.applySize(cellWidth, cellHeight);
			}
			c.pos(col * (cellWidth + gapX), row * (cellHeight + gapY));
			i++;
		}
	}

	@Override
	public int measureWidth(UiElement<?> container, UiDraw g) {
		return columns * cellWidth + (columns - 1) * gapX + container.padding() * 2;
	}

	@Override
	public int measureHeight(UiElement<?> container, UiDraw g) {
		return rows(container) * cellHeight + (rows(container) - 1) * gapY + container.padding() * 2;
	}
}

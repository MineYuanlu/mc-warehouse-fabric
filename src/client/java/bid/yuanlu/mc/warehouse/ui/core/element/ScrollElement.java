package bid.yuanlu.mc.warehouse.ui.core.element;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.layout.Layout;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 纵向滚动视口（对应 CSS overflow-y: auto）：子级像 Column 一样纵向堆叠、AUTO 宽
 * 拉伸到视口内容宽，超出视口高度的内容被裁剪且不可命中；滚轮滚动，右侧画 2px
 * 滚动条指示。视口尺寸由父级布局给定（配合 grow 撑满），内容按自然尺寸堆叠。
 */
public final class ScrollElement extends UiElement<ScrollElement> {

	private static final int BAR_WIDTH = 2;
	private static final int WHEEL_LINES = 2;
	/** 无 UiDraw 上下文（纯单测）时的行高兜底。 */
	private static final int DEFAULT_LINE_HEIGHT = 12;

	private final int gap;
	private int scrollY;
	/** 内容总高（arrange 回写），用于滚动钳制与滚动条比例。 */
	private int contentHeight;

	public ScrollElement(int gap) {
		this.gap = gap;
		clipContent(true);
		layout(new Layout() {
			@Override
			public void arrange(UiElement<?> container, UiDraw g) {
				arrangeChildren(container);
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
				return max + container.padding() * 2 + BAR_WIDTH;
			}

			@Override
			public int measureHeight(UiElement<?> container, UiDraw g) {
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
		});
		on(UiEvent.Type.WHEEL, e -> {
			scrollBy((int) (-e.scrollY * WHEEL_LINES * lineHeight()));
			e.consume();
		});
	}

	/** 相对滚动（正数向内容尾部看），钳制到内容范围。 */
	public void scrollBy(int dy) {
		int newY = clamp(dy + scrollY);
		if (newY != scrollY) {
			scrollY = newY;
			markLayoutDirty();
		}
	}

	public int scrollY() {
		return scrollY;
	}

	private int lineHeight() {
		return root() instanceof UiRoot r && r.currentDraw() != null
				? r.currentDraw().lineHeight()
				: DEFAULT_LINE_HEIGHT;
	}

	private int clamp(int v) {
		int viewport = Math.max(0, height() - padding() * 2);
		return Math.max(0, Math.min(v, Math.max(0, contentHeight - viewport)));
	}

	/** 子级堆叠定位（视口版 Column）：先累计内容高并回钳（内容收缩时），再统一定位。 */
	private void arrangeChildren(UiElement<?> container) {
		int contentWidth = Math.max(0, container.width() - container.padding() * 2 - BAR_WIDTH);
		int cursor = 0;
		int count = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			if (c.width() == UiElement.AUTO) {
				c.applySize(contentWidth, c.height());
			}
			cursor += Layout.effectiveHeight(c) + gap;
			count++;
		}
		contentHeight = Math.max(0, cursor - (count > 0 ? gap : 0));
		scrollY = clamp(scrollY);
		int y = 0;
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			c.pos(0, y - scrollY);
			y += Layout.effectiveHeight(c) + gap;
		}
	}

	@Override
	protected void drawElement(UiDraw g) {
		int viewport = Math.max(0, height() - padding() * 2);
		if (contentHeight <= viewport || viewport <= 0) {
			return;
		}
		int x = absX() + width() - padding() - BAR_WIDTH;
		int trackY = absY() + padding();
		g.fill(x, trackY, x + BAR_WIDTH, trackY + viewport, Theme.active().overlayScrim());
		int thumbH = Math.max(8, (int) ((long) viewport * viewport / contentHeight));
		int maxScroll = contentHeight - viewport;
		int thumbY = trackY + (int) ((long) (viewport - thumbH) * scrollY / maxScroll);
		g.fill(x, thumbY, x + BAR_WIDTH, thumbY + thumbH, Theme.active().textMuted());
	}
}

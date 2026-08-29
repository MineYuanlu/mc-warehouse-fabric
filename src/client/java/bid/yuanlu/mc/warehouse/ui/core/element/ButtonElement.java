package bid.yuanlu.mc.warehouse.ui.core.element;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 主题按钮：悬浮/按下渐变色 + 居中标签；disabled 用暗色且不触发 onClick。
 * 语义色变体（success/danger）经 {@link #semantic}。
 */
public class ButtonElement extends UiElement<ButtonElement> {

	public enum Semantic {
		ACCENT, SUCCESS, DANGER
	}

	private Component label;
	private Semantic semantic = Semantic.ACCENT;

	public ButtonElement(String label) {
		this(Component.literal(label));
	}

	public ButtonElement(Component label) {
		this.label = label;
	}

	/** 运行期改标签（循环选择器等）。 */
	public ButtonElement label(Component l) {
		label = l;
		markLayoutDirty();
		return this;
	}

	public ButtonElement semantic(Semantic s) {
		semantic = s;
		return this;
	}

	private int baseColor(Theme t) {
		return switch (semantic) {
			case ACCENT -> t.accent();
			case SUCCESS -> t.success();
			case DANGER -> t.danger();
		};
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return g.textWidthComponent(label) + padding() * 4;
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return g.lineHeight() + padding() * 2;
	}

	@Override
	protected void drawElement(UiDraw g) {
		var t = Theme.active();
		int bg;
		if (!enabled()) {
			bg = 0xFF3A3D42;
		} else if (pressed()) {
			bg = t.accentPressed();
		} else if (hovered()) {
			bg = t.accentHover();
		} else {
			bg = baseColor(t);
		}
		g.fill(absX(), absY(), absX() + width(), absY() + height(), bg);
		g.outline(absX(), absY(), width(), height(), t.border());
		g.textComponent(label, absX() + width() / 2, absY() + (height() - g.lineHeight()) / 2,
				enabled() ? t.textPrimary() : t.textMuted(), true, TextAnchor.CENTER);
	}
}

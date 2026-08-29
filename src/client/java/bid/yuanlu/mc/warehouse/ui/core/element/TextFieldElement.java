package bid.yuanlu.mc.warehouse.ui.core.element;

import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import org.lwjgl.glfw.GLFW;

/**
 * 单行文本框（M3）：点击聚焦 → 键盘输入追加/退格删除；焦点外点击失焦。
 * 闪烁光标经 partialTick + tickCounter 合成（tick 级缓存纪律）。
 */
public class TextFieldElement extends UiElement<TextFieldElement> {

	private final StringBuilder text = new StringBuilder();
	private Runnable onChange = () -> {
	};
	private int max_length = 32;

	public TextFieldElement(String initial) {
		focusable(true);
		onClick(() -> requestFocus());
		on(UiEvent.Type.BLUR, e -> markLayoutDirty());
		text.append(initial);
		registerInputHandlers();
	}

	private void registerInputHandlers() {
		on(UiEvent.Type.CHAR, e -> {
			if (!focused() || e.target != this) {
				return;
			}
			String c = new String(Character.toChars(e.keyCode));
			if (text.length() < max_length && !c.isBlank()) {
				text.append(c);
				onChange.run();
				markLayoutDirty();
			}
		});
		on(UiEvent.Type.KEY_DOWN, e -> {
			if (!focused() || e.target != this) {
				return;
			}
			if (e.keyCode == GLFW.GLFW_KEY_BACKSPACE && text.length() > 0) {
				text.setLength(text.length() - 1);
				onChange.run();
				markLayoutDirty();
				e.consume();
			}
		}, true);
	}

	public TextFieldElement onChange(Runnable r) {
		onChange = r;
		return this;
	}

	public TextFieldElement maxLength(int n) {
		max_length = n;
		return this;
	}

	public String text() {
		return text.toString();
	}

	public void setText(String s) {
		text.setLength(0);
		text.append(s);
		markLayoutDirty();
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return 120; // 固定默认宽，可 size() 覆盖
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return g.lineHeight() + padding() * 2 + 2;
	}

	@Override
	protected void drawElement(UiDraw g) {
		var t = Theme.active();
		g.fill(absX(), absY(), absX() + width(), absY() + height(),
				focused() ? 0xFF101317 : 0xFF0C0E11);
		g.outline(absX(), absY(), width(), height(), focused() ? t.accent() : t.border());
		int ty = absY() + (height() - g.lineHeight()) / 2;
		String s = text.toString();
		// 简单截断：超宽从尾部省略
		while (s.length() > 1 && g.textWidth(s) > width() - padding() * 2 - 2) {
			s = s.substring(0, s.length() - 1);
		}
		g.text(s, absX() + padding(), ty, t.textPrimary(), false, TextAnchor.LEFT);
		if (focused() && (((g.tickCounter() / 10) % 2) == 0)) {
			int cx = absX() + padding() + g.textWidth(s);
			g.fill(cx, ty - 1, cx + 1, ty + g.lineHeight() + 1, t.textPrimary());
		}
	}

}

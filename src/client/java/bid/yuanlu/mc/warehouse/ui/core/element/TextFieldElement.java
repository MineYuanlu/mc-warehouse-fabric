package bid.yuanlu.mc.warehouse.ui.core.element;

import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 单行文本框（原版风格，M3）：widget/text_field sprite 两态（聚焦高亮），
 * 文字内边距与原版 EditBox 一致（4px）；点击聚焦 → 键盘输入追加/退格删除。
 */
public class TextFieldElement extends UiElement<TextFieldElement> {

	private static final String SPRITE_NORMAL = "minecraft:widget/text_field";
	private static final String SPRITE_FOCUSED = "minecraft:widget/text_field_highlighted";

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
		return ButtonElement.VANILLA_HEIGHT;
	}

	@Override
	protected void drawElement(UiDraw g) {
		g.sprite(focused() ? SPRITE_FOCUSED : SPRITE_NORMAL, absX(), absY(), width(), height(), 0xFFFFFFFF);
		// 原版 EditBox 有边框时文字内缩 4px
		int inset = 4;
		int ty = absY() + (height() - g.lineHeight()) / 2;
		String s = text.toString();
		// 简单截断：超宽从尾部省略
		while (s.length() > 1 && g.textWidth(s) > width() - inset * 2) {
			s = s.substring(0, s.length() - 1);
		}
		g.text(s, absX() + inset, ty, enabled() ? 0xFFFFFFFF : 0xFFA0A0A0, false, TextAnchor.LEFT);
		if (focused() && (((g.tickCounter() / 10) % 2) == 0)) {
			int cx = absX() + inset + g.textWidth(s);
			g.fill(cx, ty - 1, cx + 1, ty + g.lineHeight() + 1, 0xFFFFFFFF);
		}
	}

}

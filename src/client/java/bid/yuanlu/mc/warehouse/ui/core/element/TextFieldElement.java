package bid.yuanlu.mc.warehouse.ui.core.element;

import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import bid.yuanlu.mc.warehouse.ui.core.draw.ClipboardPort;
import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;

/**
 * 单行文本框（原版风格）：widget/text_field sprite 两态（聚焦高亮），文字内边距与
 * 原版 EditBox 一致（4px）。支持光标移动（←/→/Home/End）、Backspace/Delete、
 * Ctrl+V 粘贴（逐字符过 filter）、Ctrl+C 复制全文；字符过滤经 {@link #filter(Predicate)}。
 * 文本缓冲为纯模型 {@link EditBuffer}；可视窗口在绘制期纯推导（不落状态）。
 */
public class TextFieldElement extends UiElement<TextFieldElement> {

	private static final String SPRITE_NORMAL = "minecraft:widget/text_field";
	private static final String SPRITE_FOCUSED = "minecraft:widget/text_field_highlighted";

	protected static final int INSET = 4;

	protected final EditBuffer buffer = new EditBuffer();
	private Runnable onChange = () -> {
	};
	private int max_length = 32;
	private Predicate<String> filter = c -> c.charAt(0) >= ' ' && c.charAt(0) != 127;

	public TextFieldElement(String initial) {
		focusable(true);
		onClick(() -> requestFocus());
		on(UiEvent.Type.BLUR, e -> markLayoutDirty());
		buffer.setText(initial);
		registerInputHandlers();
	}

	private void registerInputHandlers() {
		on(UiEvent.Type.CHAR, e -> {
			if (!focused() || e.target != this) {
				return;
			}
			String c = new String(Character.toChars(e.keyCode));
			if (filter.test(c) && insertText(c)) {
				textChanged();
				markLayoutDirty();
			}
			e.consume();
		});
		on(UiEvent.Type.KEY_DOWN, e -> {
			if (!focused() || e.target != this) {
				return;
			}
			boolean changed = onKey(e.keyCode, e.modifiers);
			if (isEditingKey(e.keyCode)) {
				e.consume();
			}
			if (changed) {
				textChanged();
				markLayoutDirty();
			}
		}, true);
		on(UiEvent.Type.FOCUS, e -> buffer.end());
	}

	/** 键盘编辑动作；文本有变更返回 true。光标移动不触发 onChange（只消费）。 */
	private boolean onKey(int keyCode, int modifiers) {
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		return switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT -> {
				buffer.moveLeft();
				yield false;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				buffer.moveRight();
				yield false;
			}
			case GLFW.GLFW_KEY_HOME -> {
				buffer.home();
				yield false;
			}
			case GLFW.GLFW_KEY_END -> {
				buffer.end();
				yield false;
			}
			case GLFW.GLFW_KEY_BACKSPACE -> buffer.backspace();
			case GLFW.GLFW_KEY_DELETE -> buffer.delete();
			case GLFW.GLFW_KEY_V -> ctrl && paste();
			case GLFW.GLFW_KEY_C -> {
				if (ctrl) {
					ClipboardPort.set(buffer.text());
				}
				yield false;
			}
			default -> false;
		};
	}

	private static boolean isEditingKey(int keyCode) {
		return switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END,
					GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_C -> true;
			default -> false;
		};
	}

	/** 粘贴：剪贴板逐字符过 filter 与 {@link #insertText}（换行等控制字符一律拒绝，超长自然截断）。 */
	private boolean paste() {
		String clip = ClipboardPort.get();
		if (clip.isEmpty()) {
			return false;
		}
		boolean changed = false;
		for (int i = 0; i < clip.length(); ) {
			int cp = clip.codePointAt(i);
			String s = new String(Character.toChars(cp));
			if (filter.test(s) && insertText(s)) {
				changed = true;
			}
			i += Character.charCount(cp);
		}
		return changed;
	}

	/** 单字符插入（CHAR 输入与粘贴共用入口）；返回是否实际变更。子类可覆盖实现域语义。 */
	protected boolean insertText(String c) {
		return buffer.insert(c, max_length);
	}

	public TextFieldElement onChange(Runnable r) {
		onChange = r;
		return this;
	}

	public TextFieldElement maxLength(int n) {
		max_length = n;
		return this;
	}

	/** 字符过滤器：CHAR 输入与粘贴均逐字符调用（入参为单字符）。 */
	public TextFieldElement filter(Predicate<String> f) {
		filter = f;
		return this;
	}

	public String text() {
		return buffer.text();
	}

	public void setText(String s) {
		buffer.setText(s);
		markLayoutDirty();
	}

	protected final void textChanged() {
		onChange.run();
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return 120; // 固定默认宽，可 size() 覆盖
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return ButtonElement.VANILLA_HEIGHT;
	}

	protected int textColor() {
		return enabled() ? 0xFFFFFFFF : 0xFFA0A0A0;
	}

	@Override
	protected void drawElement(UiDraw g) {
		g.sprite(focused() ? SPRITE_FOCUSED : SPRITE_NORMAL, absX(), absY(), width(), height(), 0xFFFFFFFF);
		// 可视窗口：以光标可见为约束贪心展开（纯推导，无状态变更）
		int avail = width() - INSET * 2;
		String full = buffer.text();
		int s0 = buffer.cursor();
		int s1 = buffer.cursor();
		while (s1 < full.length() && g.textWidth(full.substring(s0, s1 + 1)) <= avail) {
			s1++;
		}
		while (s0 > 0 && g.textWidth(full.substring(s0 - 1, s1)) <= avail) {
			s0--;
		}
		int ty = absY() + (height() - g.lineHeight()) / 2;
		int tx = absX() + INSET;
		g.text(full.substring(s0, s1), tx, ty, textColor(), false, TextAnchor.LEFT);
		if (focused() && (((g.tickCounter() / 10) % 2) == 0)) {
			int cx = tx + g.textWidth(full.substring(s0, Math.min(buffer.cursor(), s1)));
			g.fill(cx, ty - 1, cx + 1, ty + g.lineHeight() + 1, 0xFFFFFFFF);
		}
	}

}

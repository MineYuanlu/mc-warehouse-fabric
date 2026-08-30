package bid.yuanlu.mc.warehouse.ui.core.element;

/**
 * 单行文本缓冲（纯模型，无绘制/输入依赖，可 JVM 单测）：文本 + 光标索引，
 * 供 TextFieldElement 驱动。光标按 char 索引（与原版 EditBox 同粒度，不处理代理对内部拆分）。
 */
public final class EditBuffer {

	private final StringBuilder text = new StringBuilder();
	private int cursor;

	public String text() {
		return text.toString();
	}

	public int length() {
		return text.length();
	}

	public int cursor() {
		return cursor;
	}

	public void setText(String s) {
		text.setLength(0);
		text.append(s);
		cursor = text.length();
	}

	/** 光标移动到 index（钳位到 [0, length]）。 */
	public void setCursor(int index) {
		cursor = Math.max(0, Math.min(text.length(), index));
	}

	public void moveLeft() {
		if (cursor > 0) {
			cursor--;
		}
	}

	public void moveRight() {
		if (cursor < text.length()) {
			cursor++;
		}
	}

	public void home() {
		cursor = 0;
	}

	public void end() {
		cursor = text.length();
	}

	/** 光标前插入，整体超长时拒绝并保持原状；返回是否成功。 */
	public boolean insert(String s, int maxLength) {
		if (text.length() + s.length() > maxLength) {
			return false;
		}
		text.insert(cursor, s);
		cursor += s.length();
		return true;
	}

	/** 删除光标前一字符；无可删返回 false。 */
	public boolean backspace() {
		if (cursor == 0) {
			return false;
		}
		text.deleteCharAt(cursor - 1);
		cursor--;
		return true;
	}

	/** 删除光标处字符；无可删返回 false。 */
	public boolean delete() {
		if (cursor >= text.length()) {
			return false;
		}
		text.deleteCharAt(cursor);
		return true;
	}
}

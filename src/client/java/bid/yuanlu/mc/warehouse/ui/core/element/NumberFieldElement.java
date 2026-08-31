package bid.yuanlu.mc.warehouse.ui.core.element;

import java.util.function.IntConsumer;

import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 整数输入框：字符过滤（数字/负号/可选 {@code ~} 相对标记），即时解析 + 范围校验。
 * 文本不强制钳制（保留原输入便于修改）——{@link #intValue()} 返回解析钳制后的值，
 * {@link #valid()} 报告当前输入可否解析并在范围内（空串/越界 = 无效）；调用方以
 * valid 门控提交。{@code ~} 前缀仅标记相对语义（如坐标相对玩家），数值部分仍按
 * min/max 校验，绝对值由调用方结合共享解析工具求取。
 */
public class NumberFieldElement extends TextFieldElement {

	/** 相对值前缀（等价命令的 ~ 语法）。 */
	public static final char RELATIVE_PREFIX = '~';

	private final int min;
	private final int max;
	private final boolean allowRelative;
	private IntConsumer onChange;
	private boolean valid;
	private int value;
	private boolean lastValid = true;
	private int lastValue;

	public NumberFieldElement(int min, int max, int initial) {
		this(min, max, initial, false);
	}

	public NumberFieldElement(int min, int max, int initial, boolean allowRelative) {
		super(String.valueOf(initial));
		this.min = min;
		this.max = max;
		this.allowRelative = allowRelative;
		filter(c -> {
			char ch = c.charAt(0);
			return (ch >= '0' && ch <= '9') || ch == '-' || (allowRelative && ch == RELATIVE_PREFIX);
		});
		super.onChange(this::revalidate);
		revalidate();
	}

	/** {@code ~} 输入语义：开启新的相对 token（覆盖原内容，等价命令语法从绝对切换到相对）。 */
	@Override
	protected boolean insertText(String c) {
		if (allowRelative && c.length() == 1 && c.charAt(0) == RELATIVE_PREFIX && !relative()) {
			buffer.setText("~");
			return true;
		}
		return super.insertText(c);
	}

	/** 数值变化（含相对值；无有效值时为钳制兜底值）；注册即回调一次。
	 *  命名区别于父类 {@code onChange(Runnable)}，避免 lambda 重载歧义。 */
	public NumberFieldElement onValue(IntConsumer r) {
		onChange = r;
		onChange.accept(value);
		return this;
	}

	public boolean valid() {
		return valid;
	}

	/** 当前输入是否为相对标记（{@code ~}[数值] 形态）。 */
	public boolean relative() {
		String t = buffer.text().trim();
		return allowRelative && !t.isEmpty() && t.charAt(0) == RELATIVE_PREFIX;
	}

	/** 解析并钳制后的当前值（无效输入时为最近的合法兜底，仅供展示，提交前应先查 valid()）。 */
	public int intValue() {
		return value;
	}

	public void setIntValue(int v) {
		setText(String.valueOf(v));
	}

	@Override
	public void setText(String s) {
		super.setText(s);
		revalidate();
	}

	private void revalidate() {
		String t = buffer.text().trim();
		String num = relative() ? t.substring(1) : t;
		int v;
		try {
			v = num.isEmpty() && relative() ? 0 : Integer.parseInt(num);
		} catch (NumberFormatException e) {
			valid = false;
			value = Math.max(min, Math.min(max, 0));
			fireIfChanged();
			return;
		}
		valid = v >= min && v <= max;
		value = Math.max(min, Math.min(max, v));
		fireIfChanged();
	}

	/** 同值去重（镜像 Value 语义）：监听器写回 setIntValue 不会形成回环。 */
	private void fireIfChanged() {
		if (valid != lastValid || value != lastValue) {
			lastValid = valid;
			lastValue = value;
			if (onChange != null) {
				onChange.accept(value);
			}
		}
	}

	@Override
	protected int textColor() {
		if (!valid) {
			return Theme.active().danger();
		}
		return super.textColor();
	}

}

package bid.yuanlu.mc.warehouse.ui.core.element;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 复选框（方块勾选 + 标签）：点击切换，可绑定 {@link Value<Boolean>}。
 */
public class CheckboxElement extends UiElement<CheckboxElement> {

	private final Component label;
	private boolean checked;
	private Value<Boolean> bound;

	public CheckboxElement(String label, boolean checked) {
		this(Component.literal(label), checked);
	}

	public CheckboxElement(Component label, boolean checked) {
		this.label = label;
		this.checked = checked;
		onClick(this::toggle);
	}

	private void toggle() {
		setChecked(!checked);
	}

	public void setChecked(boolean c) {
		checked = c;
		if (bound != null) {
			bound.set(c);
		}
	}

	public boolean checked() {
		return checked;
	}

	public CheckboxElement bind(Value<Boolean> value) {
		bound = value;
		value.listen(this::setChecked);
		return this;
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return 10 + 3 + g.textWidthComponent(label) + padding() * 2;
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return Math.max(10, g.lineHeight()) + padding() * 2;
	}

	@Override
	protected void drawElement(UiDraw g) {
		var t = Theme.active();
		int box = absX();
		int by = absY() + (height() - 10) / 2;
		g.fill(box, by, box + 10, by + 10, checked ? t.accent() : 0xFF20242B);
		g.outline(box, by, 10, 10, hovered() ? t.accentHover() : t.border());
		if (checked) {
			g.fill(box + 2, by + 2, box + 8, by + 8, 0xFFFFFFFF);
		}
		g.textComponent(label, box + 13, absY() + (height() - g.lineHeight()) / 2,
				enabled() ? t.textPrimary() : t.textMuted(), true, TextAnchor.LEFT);
	}
}

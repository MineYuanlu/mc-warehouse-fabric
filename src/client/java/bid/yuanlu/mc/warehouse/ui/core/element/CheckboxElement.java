package bid.yuanlu.mc.warehouse.ui.core.element;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;

/**
 * 复选框（原版风格）：widget/checkbox 系列 sprite（boxSize = 行高+8，与原版
 * Checkbox 一致），点击切换，可绑定 {@link Value<Boolean>}。
 */
public class CheckboxElement extends UiElement<CheckboxElement> {

	private static final String SPRITE_CHECKED = "minecraft:widget/checkbox_selected";
	private static final String SPRITE_CHECKED_HL = "minecraft:widget/checkbox_selected_highlighted";
	private static final String SPRITE_UNCHECKED = "minecraft:widget/checkbox";
	private static final String SPRITE_UNCHECKED_HL = "minecraft:widget/checkbox_highlighted";

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

	private int boxSize(UiDraw g) {
		return g.lineHeight() + 8;
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return boxSize(g) + 4 + g.textWidthComponent(label) + padding() * 2;
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return Math.max(boxSize(g), g.lineHeight()) + padding() * 2;
	}

	@Override
	protected void drawElement(UiDraw g) {
		int box = boxSize(g);
		int bx = absX();
		int by = absY() + (height() - box) / 2;
		boolean hl = hovered() || focused();
		g.sprite(checked ? (hl ? SPRITE_CHECKED_HL : SPRITE_CHECKED)
				: (hl ? SPRITE_UNCHECKED_HL : SPRITE_UNCHECKED),
				bx, by, box, box, 0xFFFFFFFF);
		g.textComponent(label, bx + box + 4, absY() + (height() - g.lineHeight()) / 2,
				enabled() ? 0xFFFFFFFF : 0xFFA0A0A0, true, TextAnchor.LEFT);
	}
}

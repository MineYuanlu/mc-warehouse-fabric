package bid.yuanlu.mc.warehouse.ui.core.element;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;

/**
 * 原版风格按钮：widget/button 系列 nine-slice sprite（普通/悬停/禁用三态，
 * UI-PDD §6.1 视觉决策——按钮尺寸材质与原版一致）。语义色经 sprite 着色实现。
 */
public class ButtonElement extends UiElement<ButtonElement> {

	private static final String SPRITE_ENABLED = "minecraft:widget/button";
	private static final String SPRITE_HOVERED = "minecraft:widget/button_highlighted";
	private static final String SPRITE_DISABLED = "minecraft:widget/button_disabled";

	/** 原版按钮标准高度。 */
	public static final int VANILLA_HEIGHT = 20;

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

	/** 语义色 → sprite 着色（乘法白平衡），ACCENT 不着色。 */
	private int tint() {
		return switch (semantic) {
			case ACCENT -> 0xFFFFFFFF;
			case SUCCESS -> 0xFFA8E6A8;
			case DANGER -> 0xFFE6A8A8;
		};
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return g.textWidthComponent(label) + padding() * 2 + 16;
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return VANILLA_HEIGHT;
	}

	@Override
	protected void drawElement(UiDraw g) {
		String sprite = !enabled() ? SPRITE_DISABLED
				: hovered() || focused() ? SPRITE_HOVERED
				: SPRITE_ENABLED;
		g.sprite(sprite, absX(), absY(), width(), height(), tint());
		g.textComponent(label, absX() + width() / 2, absY() + (height() - g.lineHeight()) / 2,
				enabled() ? 0xFFFFFFFF : 0xFFA0A0A0, true, TextAnchor.CENTER);
	}
}

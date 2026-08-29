package bid.yuanlu.mc.warehouse.ui.core.element;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 文本标签。text 与 component 二选一（component 优先）；颜色默认主题 textPrimary。
 * 可绑定 {@link Value}（监听器推送，UI-PDD §3.3）。
 */
public class LabelElement extends UiElement<LabelElement> {

	@Nullable
	private Component component;
	@Nullable
	private String plain;
	private int color = -1;
	private boolean shadow = true;
	private TextAnchor anchor = TextAnchor.LEFT;
	private float textScale = 1f;

	public LabelElement(String text) {
		this.plain = text;
	}

	public LabelElement(Component text) {
		this.component = text;
	}

	public LabelElement text(String text) {
		this.plain = text;
		this.component = null;
		markLayoutDirty();
		return this;
	}

	public LabelElement text(Component text) {
		this.component = text;
		this.plain = null;
		markLayoutDirty();
		return this;
	}

	/** 绑定值：值变化即更新文本（推送式绑定）。 */
	public LabelElement bindText(Value<String> value) {
		value.listen(this::text);
		return this;
	}

	/** 绑定 Component 值（i18n 文本）。 */
	public LabelElement bindComponent(Value<Component> value) {
		value.listen(this::text);
		return this;
	}

	public LabelElement color(int argb) {
		color = argb;
		return this;
	}

	public LabelElement shadow(boolean s) {
		shadow = s;
		return this;
	}

	public LabelElement anchor(TextAnchor a) {
		anchor = a;
		return this;
	}

	/** 文本缩放（HUD 字号调整用）；measure/绘制同步生效，占用空间按缩放后计算。 */
	public LabelElement scale(float s) {
		textScale = s;
		markLayoutDirty();
		return this;
	}

	public float scale() {
		return textScale;
	}

	private String display() {
		return component != null ? component.getString() : (plain != null ? plain : "");
	}

	/** 当前显示文本（测试/调试用）。 */
	public String displayText() {
		return display();
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		int w = component != null ? g.textWidthComponent(component) : g.textWidth(display());
		return Math.round(w * textScale) + padding() * 2;
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return Math.round(g.lineHeight() * textScale) + padding() * 2;
	}

	@Override
	protected void drawElement(UiDraw g) {
		var t = Theme.active();
		int c = color != -1 ? color : t.textPrimary();
		if (textScale != 1f) {
			// 缩放绘制：pose 平移到元素原点后按局部坐标画（measure 已按缩放占用空间）
			g.pushPose();
			g.translate(absX(), absY());
			g.scale(textScale);
			int cx = switch (anchor) {
				case LEFT -> padding();
				case CENTER -> (int) (width() / textScale) / 2;
				case RIGHT -> (int) (width() / textScale) - padding();
			};
			int cy = Math.max(0, ((int) (height() / textScale) - g.lineHeight()) / 2);
			if (component != null) {
				g.textComponent(component, cx, cy, c, shadow, anchor);
			} else {
				g.text(display(), cx, cy, c, shadow, anchor);
			}
			g.popPose();
			return;
		}
		int cx = switch (anchor) {
			case LEFT -> absX() + padding();
			case CENTER -> absX() + width() / 2;
			case RIGHT -> absX() + width() - padding();
		};
		int cy = Math.max(absY(), absY() + (height() - g.lineHeight()) / 2);
		if (component != null) {
			g.textComponent(component, cx, cy, c, shadow, anchor);
		} else {
			g.text(display(), cx, cy, c, shadow, anchor);
		}
	}
}

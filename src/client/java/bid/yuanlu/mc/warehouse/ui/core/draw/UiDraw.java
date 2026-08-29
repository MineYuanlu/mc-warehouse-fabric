package bid.yuanlu.mc.warehouse.ui.core.draw;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * L1 绘制端口（UI-PDD §4.1）——引擎唯一的 2D 绘制契约，由 L2 按 MC 版本实现。
 * 全部坐标为 GUI 缩放坐标、绝对屏幕坐标（元素树在布局期已展开绝对位置）。
 * 实现不得有副作用（26.x 提取式渲染约束）。
 */
public interface UiDraw {

	void fill(int x0, int y0, int x1, int y1, int argb);

	void fillGradient(int x0, int y0, int x1, int y1, int topArgb, int bottomArgb);

	void outline(int x, int y, int width, int height, int argb);

	void text(String s, int x, int y, int argb, boolean shadow, TextAnchor anchor);

	void textComponent(Component c, int x, int y, int argb, boolean shadow, TextAnchor anchor);

	int textWidth(String s);

	int textWidthComponent(Component c);

	/** 单行文本行高（含字体内边距）。 */
	int lineHeight();

	void pushClip(int x0, int y0, int x1, int y1);

	void popClip();

	void pushPose();

	void popPose();

	void translate(float x, float y);

	void scale(float s);

	void itemIcon(ItemStack stack, int x, int y);

	void itemDecorations(ItemStack stack, int x, int y);

	/** tooltip 通道（→ setTooltipForNextFrame），永远置顶由底层保证。 */
	void setTooltip(List<Component> lines, int x, int y);

	void requestCursor(CursorKind kind);

	int screenWidth();

	int screenHeight();

	float partialTick();

	long tickCounter();
}

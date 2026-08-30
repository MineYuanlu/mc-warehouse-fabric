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

	/**
	 * 原版 GUI sprite 绘制（如 {@code "minecraft:widget/button"}）。
	 * L1 用中立字符串定位（不依赖 net.minecraft 资源类），L2 负责 parse 与 nine-slice 拉伸。
	 * argb 为乘法着色（0xFFFFFFFF = 原色）。
	 */
	void sprite(String id, int x, int y, int width, int height, int argb);

	/**
	 * 原版 GUI 纹理区域 blit（完整纹理路径 + UV 子区域，256 网格），如
	 * {@code "minecraft:textures/gui/container/generic_54.png"}——模拟容器等需要
	 * 原版贴图片段的场景（sprite atlas 之外的原生纹理）。
	 */
	void blit(String texturePath, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight);

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

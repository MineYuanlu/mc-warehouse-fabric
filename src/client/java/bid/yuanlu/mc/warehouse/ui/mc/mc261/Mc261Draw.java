package bid.yuanlu.mc.warehouse.ui.mc.mc261;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.draw.CursorKind;
import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.mc.RawGraphics;
import com.mojang.blaze3d.platform.cursor.CursorType;

/**
 * UiDraw 的 26.1 实现：薄封装 {@link GuiGraphicsExtractor}（UI-PDD §4.1）。
 * 每帧新建（提取式管线的既定用法），无跨帧可变状态。
 */
public final class Mc261Draw implements UiDraw {

	private final GuiGraphicsExtractor g;
	private final Font font;
	private final float partialTick;
	private final long tickCounter;

	public Mc261Draw(GuiGraphicsExtractor g, float partialTick) {
		this(g, partialTick, Mc261UiPlatform.tickCounter());
	}

	public Mc261Draw(GuiGraphicsExtractor g, float partialTick, long tickCounter) {
		this.g = g;
		this.font = Minecraft.getInstance().font;
		this.partialTick = partialTick;
		this.tickCounter = tickCounter;
		RawGraphics.setCurrent(g);
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		g.fill(x0, y0, x1, y1, argb);
	}

	@Override
	public void fillGradient(int x0, int y0, int x1, int y1, int topArgb, int bottomArgb) {
		g.fillGradient(x0, y0, x1, y1, topArgb, bottomArgb);
	}

	@Override
	public void outline(int x, int y, int width, int height, int argb) {
		g.outline(x, y, width, height, argb);
	}

	@Override
	public void sprite(String id, int x, int y, int width, int height, int argb) {
		g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				net.minecraft.resources.Identifier.parse(id), x, y, width, height, argb);
	}

	@Override
	public void blit(String texturePath, int x, int y, int u, int v, int width, int height,
			int textureWidth, int textureHeight) {
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				net.minecraft.resources.Identifier.parse(texturePath), x, y, u, v, width, height,
				textureWidth, textureHeight);
	}

	@Override
	public void text(String s, int x, int y, int argb, boolean shadow, TextAnchor anchor) {
		g.text(font, s, anchoredX(x, font.width(s), anchor), y, argb, shadow);
	}

	@Override
	public void textComponent(Component c, int x, int y, int argb, boolean shadow, TextAnchor anchor) {
		g.text(font, c, anchoredX(x, font.width(c), anchor), y, argb, shadow);
	}

	private int anchoredX(int x, int width, TextAnchor anchor) {
		return switch (anchor) {
			case LEFT -> x;
			case CENTER -> x - width / 2;
			case RIGHT -> x - width;
		};
	}

	@Override
	public int textWidth(String s) {
		return font.width(s);
	}

	@Override
	public int textWidthComponent(Component c) {
		return font.width(c);
	}

	@Override
	public int lineHeight() {
		return font.lineHeight;
	}

	@Override
	public void pushClip(int x0, int y0, int x1, int y1) {
		g.enableScissor(x0, y0, x1, y1);
	}

	@Override
	public void popClip() {
		g.disableScissor();
	}

	@Override
	public void pushPose() {
		g.pose().pushMatrix();
	}

	@Override
	public void popPose() {
		g.pose().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		g.pose().translate(x, y);
	}

	@Override
	public void scale(float s) {
		g.pose().scale(s, s);
	}

	@Override
	public void itemIcon(net.minecraft.world.item.ItemStack stack, int x, int y) {
		g.item(stack, x, y);
	}

	@Override
	public void itemDecorations(net.minecraft.world.item.ItemStack stack, int x, int y) {
		g.itemDecorations(font, stack, x, y);
	}

	@Override
	public void setTooltip(List<Component> lines, int x, int y) {
		if (lines.isEmpty()) {
			return;
		}
		if (lines.size() == 1) {
			g.setTooltipForNextFrame(lines.get(0), x, y);
		} else {
			g.setTooltipForNextFrame(joinLines(lines), x, y);
		}
	}

	private static Component joinLines(List<Component> lines) {
		var joined = lines.get(0).copy();
		for (int i = 1; i < lines.size(); i++) {
			joined.append("\n").append(lines.get(i));
		}
		return joined;
	}

	@Override
	public void requestCursor(CursorKind kind) {
		g.requestCursor(CursorType.DEFAULT);
	}

	@Override
	public int screenWidth() {
		return g.guiWidth();
	}

	@Override
	public int screenHeight() {
		return g.guiHeight();
	}

	@Override
	public float partialTick() {
		return partialTick;
	}

	@Override
	public long tickCounter() {
		return tickCounter;
	}
}

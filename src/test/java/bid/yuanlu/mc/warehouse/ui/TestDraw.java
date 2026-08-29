package bid.yuanlu.mc.warehouse.ui;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mc.warehouse.ui.core.draw.CursorKind;
import bid.yuanlu.mc.warehouse.ui.core.draw.TextAnchor;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;

/**
 * 无头 UiDraw：文本宽 = len*6、行高 9，供 L1 布局/事件单测使用。
 */
public final class TestDraw implements UiDraw {

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
	}

	@Override
	public void fillGradient(int x0, int y0, int x1, int y1, int topArgb, int bottomArgb) {
	}

	@Override
	public void outline(int x, int y, int width, int height, int argb) {
	}

	@Override
	public void text(String s, int x, int y, int argb, boolean shadow, TextAnchor anchor) {
	}

	@Override
	public void textComponent(Component c, int x, int y, int argb, boolean shadow, TextAnchor anchor) {
	}

	@Override
	public int textWidth(String s) {
		return s.length() * 6;
	}

	@Override
	public int textWidthComponent(Component c) {
		return c.getString().length() * 6;
	}

	@Override
	public int lineHeight() {
		return 9;
	}

	@Override
	public void pushClip(int x0, int y0, int x1, int y1) {
	}

	@Override
	public void popClip() {
	}

	@Override
	public void pushPose() {
	}

	@Override
	public void popPose() {
	}

	@Override
	public void translate(float x, float y) {
	}

	@Override
	public void scale(float s) {
	}

	@Override
	public void itemIcon(ItemStack stack, int x, int y) {
	}

	@Override
	public void itemDecorations(ItemStack stack, int x, int y) {
	}

	@Override
	public void setTooltip(List<Component> lines, int x, int y) {
	}

	@Override
	public void requestCursor(CursorKind kind) {
	}

	@Override
	public int screenWidth() {
		return 480;
	}

	@Override
	public int screenHeight() {
		return 270;
	}

	@Override
	public float partialTick() {
		return 0;
	}

	@Override
	public long tickCounter() {
		return 0;
	}
}

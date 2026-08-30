package bid.yuanlu.mc.warehouse.ui.app.widget;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 模拟容器（HUD 设置屏的贴靠参照物，UI-PDD §6.2 v0.3）：按原版 GUI 尺寸与贴图
 * 分段 blit 渲染常见容器预设（含玩家背包段）。仅作视觉参照——透明交互层，
 * zIndex 低于中央面板、命中时不可拦截（drawElement 可见但点击落在其下）。
 */
public final class ContainerGhostElement extends UiElement<ContainerGhostElement> {

	public static final int GUI_WIDTH = 176;

	/**
	 * 常见容器预设：每段为 (u, v, w, h) 顺序纵向拼接。
	 * generic_54 为箱子通用贴图：body 段高 rows*18+17，玩家背包段 (0,126,176,96)。
	 */
	public enum Preset {
		CHEST_27("minecraft:textures/gui/container/generic_54.png",
				new int[] { 0, 0, 176, 3 * 18 + 17 }, new int[] { 0, 126, 176, 96 }),
		CHEST_54("minecraft:textures/gui/container/generic_54.png",
				new int[] { 0, 0, 176, 6 * 18 + 17 }, new int[] { 0, 126, 176, 96 }),
		SHULKER_BOX("minecraft:textures/gui/container/shulker_box.png",
				new int[] { 0, 0, 176, 166 }),
		DISPENSER("minecraft:textures/gui/container/dispenser.png",
				new int[] { 0, 0, 176, 133 }),
		FURNACE("minecraft:textures/gui/container/furnace.png",
				new int[] { 0, 0, 176, 133 }),
		PLAYER_INVENTORY("minecraft:textures/gui/container/inventory.png",
				new int[] { 0, 0, 176, 166 });

		final String texture;
		final int[][] segments;

		Preset(String texture, int[]... segments) {
			this.texture = texture;
			this.segments = segments;
		}

		public int height() {
			int h = 0;
			for (int[] s : segments) {
				h += s[3];
			}
			return h;
		}
	}

	private final Preset preset;

	public ContainerGhostElement(Preset preset) {
		this.preset = preset;
	}

	public Preset preset() {
		return preset;
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return GUI_WIDTH;
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return preset.height();
	}

	@Override
	protected void onTick() {
		// 模拟真实打开容器：始终屏幕居中
		pos(Math.max(0, (root().width() - width()) / 2), Math.max(0, (root().height() - height()) / 2));
	}

	@Override
	protected void drawElement(UiDraw g) {
		int y = absY();
		for (int[] seg : preset.segments) {
			g.blit(preset.texture, absX(), y, seg[0], seg[1], seg[2], seg[3], 256, 256);
			y += seg[3];
		}
	}
}

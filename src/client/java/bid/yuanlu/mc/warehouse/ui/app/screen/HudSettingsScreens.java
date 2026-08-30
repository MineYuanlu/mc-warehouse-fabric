package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.app.hud.HudConfig;
import bid.yuanlu.mc.warehouse.ui.app.hud.HudLayout;
import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.CheckboxElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.SliderElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.layout.Center;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * HUD 设置屏（UI-PDD §6.2，按用户规格重设计）：
 * <ul>
 *   <li>本屏打开期间 HUD 按工作状态真实显示且渲染在本屏内容之上（passthrough + 代渲染），
 *       设置即所见，被面板遮住也能先拖走；</li>
 *   <li>按住 HUD 本体（按布局 bounds 命中）拖拽 = 平移该角 HUD 组，附近空白退回象限
 *       猜测；方向键微调 1px；offset 钳在屏内防失联；</li>
 *   <li>屏幕中央列表逐块：开关 checkbox、拖拽排序、滑条调整该块文字缩放；</li>
 *   <li>外框尺寸由内容（行数/字数/字号）自动决定，无独立尺寸控制；文字行
 *       只能开关/排序，逐行排列，不可单独设位置。</li>
 * </ul>
 * 全部修改实时生效并落盘（malilib 配置屏惯例，无取消语义）。
 */
public final class HudSettingsScreens {

	private static final int ROW_HEIGHT = 20;
	private static final float SCALE_STEP = 0.1f;
	private static final float SCALE_MIN = 0.5f;
	private static final float SCALE_MAX = 2f;

	private HudSettingsScreens() {
	}

	/** 打开 HUD 设置屏（HUD 保持显示以实时预览）。 */
	public static void open() {
		UiPlatform.openScreenKeepHud(HudSettingsScreens::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		// 中央列表挂在满屏层下（root 直接子级会被 SCREEN_MARGIN 摆放，pos 无效）；
		// 本屏无主面板——HUD 设置模式只留中央操作面板（标题+帮助+区块行+完成）
		var overlay = new OverlayLayer(root);
		overlay.add(centerList());
		root.add(overlay);
		return root;
	}

	// ---- 中央面板：标题 + 提示 + 区块行 + 完成 ----

	private static PanelElement centerList() {
		var list = new PanelElement().padding(8).id("hud-block-list").layout(new Column(4));
		rebuildList(list);
		return list;
	}

	private static void rebuildList(PanelElement list) {
		List<HudConfig.Block> blocks = orderedBlocks();
		list.clearChildren();
		list.add(new LabelElement(Component.translatable("ui.wh.hud.settings.title")).padding(2));
		list.add(new LabelElement(Component.translatable("ui.wh.hud.settings.tip"))
				.color(Theme.active().textMuted()));
		list.add(new LabelElement(Component.translatable("ui.wh.hud.settings.blocks")).padding(2));
		for (HudConfig.Block block : blocks) {
			list.add(blockRow(list, block));
		}
		var actions = PanelElement.plain().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.hud.settings.done"))
				.onClick(ScreenHeader::backToMain));
		list.add(actions);
	}

	private static List<HudConfig.Block> orderedBlocks() {
		List<HudConfig.Block> blocks = new ArrayList<>(List.of(HudConfig.Block.values()));
		blocks.sort(Comparator.comparingInt(b -> HudConfig.get().get(b).order));
		return blocks;
	}

	private static PanelElement blockRow(PanelElement list, HudConfig.Block block) {
		var cfg = HudConfig.get().get(block);
		var row = PanelElement.plain().padding(2).layout(new Row(4)).size(300, ROW_HEIGHT - 4);
		row.add(new CheckboxElement(Component.translatable(block.labelKey), cfg.enabled).bind(bindOf(block)));
		// 缩放滑条：拖动实时预览并就地刷新标签——不整屏重建（重建首帧中央面板闪现
		// 左上角，onTick 居中是 20Hz tick 驱动的）；落盘推迟到拖拽/点击结束
		var scaleText = Value.of(scaleLabel(cfg.scale));
		var slider = new SliderElement(SCALE_MIN, SCALE_MAX, SCALE_STEP, cfg.scale)
				.grow(1)
				.onChange(v -> {
					cfg.scale = v;
					UiPlatform.resetHud("hud");
					scaleText.set(scaleLabel(v));
				});
		slider.on(UiEvent.Type.DRAG_END, e -> HudConfig.save(HudConfig.get()));
		slider.on(UiEvent.Type.CLICK, e -> HudConfig.save(HudConfig.get()));
		row.add(slider.id("hud-scale-" + block.name()));
		row.add(new LabelElement(scaleText.get()).bindComponent(scaleText).color(Theme.active().textMuted()));
		attachReorder(list, row, block);
		return row;
	}

	private static Component scaleLabel(float scale) {
		return Component.translatable("ui.wh.hud.settings.scale", Math.round(scale * 10) / 10.0);
	}

	/** 行拖拽排序：捕获阶段监听（行内任意子级被按住皆可拖），累计位移越过行高即换位；
	 *  排序期间消费事件——行拖拽不得同时平移 HUD 组。 */
	private static void attachReorder(PanelElement list, PanelElement row, HudConfig.Block block) {
		int[] fromIndex = { -1 };
		double[] accDy = { 0 };
		row.on(UiEvent.Type.DRAG_START, e -> {
			if (e.target instanceof SliderElement) {
				return; // 滑条拖拽调值：不启动行排序，让事件落到滑条
			}
			fromIndex[0] = orderedBlocks().indexOf(block);
			accDy[0] = 0;
			e.consume();
		}, true);
		row.on(UiEvent.Type.DRAG, e -> {
			int from = fromIndex[0];
			if (from < 0) {
				return;
			}
			e.consume();
			accDy[0] += e.dy;
			int to = Math.max(0, Math.min(orderedBlocks().size() - 1,
					from + (int) Math.round(accDy[0] / ROW_HEIGHT)));
			if (to == from) {
				return;
			}
			var blocks = orderedBlocks();
			blocks.remove(from);
			blocks.add(to, block);
			for (int i = 0; i < blocks.size(); i++) {
				HudConfig.get().get(blocks.get(i)).order = i;
			}
			fromIndex[0] = to;
			accDy[0] -= (to - from) * (double) ROW_HEIGHT;
			applyStructural();
			rebuildList(list); // 树序重排 → relayout 重摆
		}, true);
	}

	private static void applyStructural() {
		HudConfig.save(HudConfig.get());
		UiPlatform.resetHud("hud");
	}

	private static bid.yuanlu.mc.warehouse.ui.core.bind.Value<Boolean> bindOf(HudConfig.Block block) {
		var cfg = HudConfig.get().get(block);
		var v = bid.yuanlu.mc.warehouse.ui.core.bind.Value.of(cfg.enabled);
		v.listen(b -> {
			cfg.enabled = b;
			applyStructural();
		});
		return v;
	}

	// ---- 满屏交互层：拖拽平移角落 HUD 组 + 方向键微调 ----

	private static final class OverlayLayer extends PanelElement {

		/** 组矩形命中余量（GUI px）。 */
		private static final int GRAB_MARGIN = 2;
		/** 拖出屏幕时组至少保留的可见像素（防失联，配合 bounds 抓取任何位置都能再拖走）。 */
		private static final int MIN_VISIBLE = 8;

		private final UiRoot root;
		private double lastMouseX = -1;
		private double lastMouseY = -1;
		@Nullable
		private HudConfig.Corner dragCorner;
		/** 每帧增量的小数残量：GUI scale≥2 时单帧位移不足 1px，直接取整会全部丢掉（慢拖卡顿根因）。 */
		private double accX, accY;

		OverlayLayer(UiRoot root) {
			this.root = root;
			filled(false).bordered(false).id("hud-drag-layer");
			// 根级权重满铺 + Center 布局期居中：原 onTick 定位由 20Hz tick 驱动，
			// 整屏重建后的首帧（update 不跑 tick）中央面板会闪现在左上角
			grow(1);
			layout(new Center(8));
			on(UiEvent.Type.MOUSE_MOVE, e -> {
				lastMouseX = e.x;
				lastMouseY = e.y;
			});
			// 组拖拽走捕获阶段：OverlayLayer 是中央面板的祖先，先于面板行处理器——
			// 按住 HUD 本体（bounds 命中）即抢占整条拖拽链并消费，行排序/按钮不再干扰
			on(UiEvent.Type.DRAG_START, e -> {
				dragCorner = groupAt(e.x, e.y);
				accX = 0;
				accY = 0;
				if (dragCorner != null) {
					e.consume();
					HudConfig.save(HudConfig.get());
				}
			}, true);
			on(UiEvent.Type.DRAG, e -> {
				if (dragCorner == null) {
					return;
				}
				e.consume();
				accX += e.dx;
				accY += e.dy;
				int sx = (int) accX; // 向零截断，小数残量留回累加器——任意速度逐像素跟手
				int sy = (int) accY;
				if (sx != 0 || sy != 0) {
					accX -= sx;
					accY -= sy;
					shiftGroup(dragCorner, sx, sy);
				}
			}, true);
			on(UiEvent.Type.DRAG_END, e -> {
				if (dragCorner != null) {
					dragCorner = null;
					HudConfig.save(HudConfig.get());
				}
			}, true);
			root.on(UiEvent.Type.KEY_DOWN, e -> {
				int dx = 0;
				int dy = 0;
				switch (e.keyCode) {
					case GLFW.GLFW_KEY_LEFT -> dx = -1;
					case GLFW.GLFW_KEY_RIGHT -> dx = 1;
					case GLFW.GLFW_KEY_UP -> dy = -1;
					case GLFW.GLFW_KEY_DOWN -> dy = 1;
					default -> {
						return;
					}
				}
				e.consume();
				var corner = groupAt(lastMouseX, lastMouseY);
				if (corner != null) {
					shiftGroup(corner, dx, dy);
					HudConfig.save(HudConfig.get());
				}
			});
		}

		@Override
		protected void drawElement(UiDraw g) {
			// 全透明：仅交互层
		}

		/** 按真实 HUD 组 bounds 抓取（±余量）；未命中时退回象限猜测，但该角无启用块则为 null。 */
		private @Nullable HudConfig.Corner groupAt(double x, double y) {
			for (HudConfig.Corner corner : HudConfig.Corner.values()) {
				int[] b = HudLayout.groupBounds(corner);
				if (b == null || !hasBlocks(corner)) {
					// hasBlocks 门：区块全关后 bounds 是上一帧的残留，不能据此抢占
					continue;
				}
				if (x >= b[0] - GRAB_MARGIN && x < b[0] + b[2] + GRAB_MARGIN
						&& y >= b[1] - GRAB_MARGIN && y < b[1] + b[3] + GRAB_MARGIN) {
					return corner;
				}
			}
			if (x < 0 || y < 0) {
				return hasBlocks(HudConfig.Corner.TOP_LEFT) ? HudConfig.Corner.TOP_LEFT : null;
			}
			boolean right = x >= root.width() / 2.0;
			boolean bottom = y >= root.height() / 2.0;
			var corner = !right ? (bottom ? HudConfig.Corner.BOTTOM_LEFT : HudConfig.Corner.TOP_LEFT)
					: (bottom ? HudConfig.Corner.BOTTOM_RIGHT : HudConfig.Corner.TOP_RIGHT);
			return hasBlocks(corner) ? corner : null;
		}

		private static boolean hasBlocks(HudConfig.Corner corner) {
			for (HudConfig.Block b : HudConfig.Block.values()) {
				var bc = HudConfig.get().get(b);
				if (bc.enabled && bc.corner() == corner) {
					return true;
				}
			}
			return false;
		}

		/** 平移指定角落全部启用块的 offset（组移动，UI-PDD §6.2）；offset 钳在
		 *  [0, 屏幕尺寸-MIN_VISIBLE]，组永远留在屏内可再抓取。 */
		private void shiftGroup(HudConfig.Corner corner, int dx, int dy) {
			int maxX = Math.max(0, root.width() - MIN_VISIBLE);
			int maxY = Math.max(0, root.height() - MIN_VISIBLE);
			for (HudConfig.Block b : HudConfig.Block.values()) {
				var bc = HudConfig.get().get(b);
				if (bc.enabled && bc.corner() == corner) {
					bc.offsetX = Math.max(0, Math.min(maxX, bc.offsetX + dx));
					bc.offsetY = Math.max(0, Math.min(maxY, bc.offsetY + dy));
				}
			}
			UiPlatform.resetHud("hud");
		}
	}
}

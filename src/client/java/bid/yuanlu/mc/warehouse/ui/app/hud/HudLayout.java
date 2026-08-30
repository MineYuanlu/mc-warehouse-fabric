package bid.yuanlu.mc.warehouse.ui.app.hud;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.layout.Layout;

/**
 * HUD 根布局（UI-PDD §6）：定位每角的 wrapper 面板（同角区块在其内部由 Column 堆叠）；
 * offsetX/offsetY 取该角首个启用区块的值（设置屏拖拽即对整组同值修改）。
 */
public final class HudLayout implements Layout {

	/** 每角 wrapper 最近一次 arrange 的屏幕矩形 {x,y,w,h}（设置屏按真实 bounds 抓取拖拽用）。 */
	private static final Map<HudConfig.Corner, int[]> LAST_BOUNDS = new EnumMap<>(HudConfig.Corner.class);

	/** 贴靠触发阈值（GUI px）：组边缘距目标边缘不超过此值即吸附（HUD 设置屏模拟容器贴靠）。 */
	public static final int SNAP_THRESHOLD = 6;

	/**
	 * 轴向贴靠修正量（纯函数，可 JVM 单测）：宽 size 的组平移到 pos 后，与目标矩形
	 * [target, target+targetSize] 四种边缘对齐（组左↔目标左/右、组右↔目标左/右）中
	 * |d| 最小者；超过 threshold 返回 0（不吸附）。返回值加到 pos 上即对齐。
	 */
	public static int snapDelta(int pos, int size, int target, int targetSize, int threshold) {
		int best = Integer.MAX_VALUE;
		for (int d : new int[] { target - pos, target + targetSize - size - pos,
				target + targetSize - pos, target - size - pos }) {
			if (Math.abs(d) < Math.abs(best)) {
				best = d;
			}
		}
		return Math.abs(best) <= threshold ? best : 0;
	}

	@Override
	public void arrange(UiElement<?> container, UiDraw g) {
		for (var wrapper : container.children()) {
			if (!wrapper.visible()) {
				// 该角区块全关（wrapper 隐藏）时清掉 bounds 残留，防设置屏据此抢占拖拽
				var corner = cornerOf(wrapper);
				if (corner != null) {
					LAST_BOUNDS.remove(corner);
				}
				continue;
			}
			HudConfig.Corner corner = cornerOf(wrapper);
			if (corner == null) {
				// 未知子级：居中兜底
				wrapper.pos((container.width() - Layout.effectiveWidth(wrapper)) / 2,
						(container.height() - Layout.effectiveHeight(wrapper)) / 2);
				continue;
			}
			var cfg = groupConfig(corner);
			int ox = cfg != null ? cfg.offsetX : 0;
			int oy = cfg != null ? cfg.offsetY : 0;
			int w = Layout.effectiveWidth(wrapper);
			int h = Layout.effectiveHeight(wrapper);
			int x = switch (corner) {
				case TOP_LEFT, BOTTOM_LEFT -> ox;
				case TOP_RIGHT, BOTTOM_RIGHT -> container.width() - w - ox;
			};
			int y = switch (corner) {
				case TOP_LEFT, TOP_RIGHT -> oy;
				case BOTTOM_LEFT, BOTTOM_RIGHT -> container.height() - h - oy;
			};
			wrapper.pos(x, y);
			LAST_BOUNDS.put(corner, new int[] { x, y, w, h });
		}
	}

	/** 该角组最近一次布局的屏幕矩形 {x,y,w,h}；该角无启用块（wrapper 隐藏）时为 null。 */
	public static int @Nullable [] groupBounds(HudConfig.Corner corner) {
		return LAST_BOUNDS.get(corner);
	}

	private static HudConfig.@Nullable Corner cornerOf(UiElement<?> el) {
		String id = el.id();
		if (!id.startsWith(HudRootFactory.WRAPPER_PREFIX)) {
			return null;
		}
		try {
			return HudConfig.Corner.valueOf(id.substring(HudRootFactory.WRAPPER_PREFIX.length()));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** 该角首个启用区块的配置（order 最小），整组 offset 以它为准。 */
	private static HudConfig.@Nullable BlockConfig groupConfig(HudConfig.Corner corner) {
		Optional<HudConfig.BlockConfig> first = Arrays.stream(HudConfig.Block.values())
				.map(b -> HudConfig.get().get(b))
				.filter(c -> c.enabled && c.corner() == corner)
				.min(Comparator.comparingInt(c -> c.order));
		return first.orElse(null);
	}

	@Override
	public int measureWidth(UiElement<?> container, UiDraw g) {
		Layout.measureChildren(container, g);
		return container.padding() * 2;
	}

	@Override
	public int measureHeight(UiElement<?> container, UiDraw g) {
		Layout.measureChildren(container, g);
		return container.padding() * 2;
	}
}

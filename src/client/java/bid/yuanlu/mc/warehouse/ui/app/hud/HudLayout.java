package bid.yuanlu.mc.warehouse.ui.app.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.layout.Layout;

/**
 * HUD 根布局（UI-PDD §6）：按区块配置的角落 + 偏移定位；同角区块按 order 纵向堆叠。
 */
public final class HudLayout implements Layout {

	private static final int STACK_GAP = 2;

	@Override
	public void arrange(UiElement<?> container, UiDraw g) {
		record Entry(int order, UiElement el, HudConfig.BlockConfig cfg) {
		}
		var entries = new ArrayList<Entry>();
		for (var c : container.children()) {
			if (!c.visible()) {
				continue;
			}
			var cfg = configOf(c);
			if (cfg != null && cfg.enabled) {
				entries.add(new Entry(cfg.order, c, cfg));
			} else if (cfg == null) {
				entries.add(new Entry(0, c, null));
			}
		}
		for (var corner : HudConfig.Corner.values()) {
			int cursor = 0;
			var inCorner = entries.stream()
					.filter(e -> e.cfg() == null || e.cfg().corner() == corner)
					.sorted(Comparator.comparingInt(Entry::order))
					.toList();
			for (var e : inCorner) {
				int w = Layout.effectiveWidth(e.el());
				int h = Layout.effectiveHeight(e.el());
				int ox = e.cfg() != null ? e.cfg().offsetX : 0;
				int oy = e.cfg() != null ? e.cfg().offsetY : cursor;
				int x = switch (corner) {
					case TOP_LEFT, BOTTOM_LEFT -> ox;
					case TOP_RIGHT, BOTTOM_RIGHT -> container.width() - w - ox;
				};
				int y = switch (corner) {
					case TOP_LEFT, TOP_RIGHT -> oy;
					case BOTTOM_LEFT, BOTTOM_RIGHT -> container.height() - h - oy;
				};
				if (e.cfg() == null) {
					x = (container.width() - w) / 2;
					y = (container.height() - h) / 2;
				}
				e.el().pos(x, y);
				cursor = (e.cfg() != null ? e.cfg().offsetY : oy) + h + STACK_GAP;
			}
		}
	}

	private static HudConfig.@Nullable BlockConfig configOf(UiElement<?> el) {
		try {
			return HudConfig.get().get(HudConfig.Block.valueOf(el.id()));
		} catch (IllegalArgumentException e) {
			return null;
		}
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

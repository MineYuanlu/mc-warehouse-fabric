package bid.yuanlu.mc.warehouse.ui.app.widget;

import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.ScrollElement;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import org.jetbrains.annotations.Nullable;

/**
 * 下拉选择（选项多于 CycleSelector 适中范围时用，如规则 id / 寻路器 / 世界名）：
 * 按钮 + 根级弹出滚动列表（zIndex 100+）。点击列表外或 Esc 关闭；超过
 * {@link #VISIBLE_ROWS} 项时列表内滚动。打开时请求焦点（Esc 经按键冒泡到交互层）。
 */
public final class DropdownElement extends ButtonElement {

	public record Option(String id, Component label) {
		@Override
		public String toString() {
			return id;
		}
	}

	private static final int VISIBLE_ROWS = 8;
	private static final int ROW_HEIGHT = 20;
	private static final int MIN_WIDTH = 80;

	private final List<Option> options;
	private final Consumer<Integer> onSelect;
	private int index;
	@Nullable
	private PanelElement popup;

	public DropdownElement(List<Option> options, int initialIndex, Consumer<Integer> onSelect) {
		super(options.get(initialIndex).label());
		if (options.isEmpty()) {
			throw new IllegalArgumentException("DropdownElement requires options");
		}
		this.options = options;
		this.onSelect = onSelect;
		this.index = initialIndex;
		onClick(this::openPopup);
	}

	public Option current() {
		return options.get(index);
	}

	public String selectedId() {
		return options.get(index).id();
	}

	public int selectedIndex() {
		return index;
	}

	/** 程序化选择（不回调 onSelect）。 */
	public void select(int i) {
		if (i >= 0 && i < options.size()) {
			index = i;
			label(options.get(index).label());
		}
	}

	/** 列表当前是否展开（供父级重建时清理）。 */
	public boolean popupOpen() {
		return popup != null;
	}

	private void openPopup() {
		closePopup();
		requestFocus();
		// 根级满铺透明交互层：吃掉列表外的点击与 Esc
		var layer = new PanelElement() {
			@Override
			protected void onTick() {
				size(root().width(), root().height());
			}
		}.filled(false).bordered(false).zIndex(100).grow(1).id("dropdown-layer");
		layer.onClick(this::closePopup);
		layer.on(UiEvent.Type.KEY_DOWN, e -> {
			if (e.keyCode == GLFW.GLFW_KEY_ESCAPE) {
				e.consume();
				closePopup();
			}
		}, true);
		layer.add(listPanel());
		root().add(layer);
		popup = layer;
	}

	private PanelElement listPanel() {
		var list = new PanelElement().padding(2).layout(new Column(1)).id("dropdown-list");
		for (int i = 0; i < options.size(); i++) {
			var opt = options.get(i);
			int thisIndex = i;
			list.add(new ButtonElement(opt.label()).onClick(() -> {
				select(thisIndex);
				closePopup();
				onSelect.accept(thisIndex);
			}));
		}
		int rows = Math.min(options.size(), VISIBLE_ROWS);
		var wrap = new PanelElement().filled(false).bordered(false);
		if (options.size() > VISIBLE_ROWS) {
			var scroll = new ScrollElement(1).size(listWidth(), rows * ROW_HEIGHT);
			scroll.add(list);
			wrap.add(scroll);
		} else {
			wrap.add(list);
		}
		// 按钮下方展开；贴屏底翻转、横向钳屏内
		int px = Math.max(0, Math.min(root().width() - listWidth() - 4, absX()));
		int py = absY() + height() + 2;
		if (py + rows * ROW_HEIGHT > root().height()) {
			py = Math.max(0, absY() - rows * ROW_HEIGHT - 4);
		}
		wrap.pos(px, py);
		return wrap;
	}

	private int listWidth() {
		return Math.max(MIN_WIDTH, declaredWidth() > 0 ? declaredWidth() : MIN_WIDTH);
	}

	private void closePopup() {
		if (popup != null) {
			popup.removeFromParent();
			popup = null;
		}
	}
}

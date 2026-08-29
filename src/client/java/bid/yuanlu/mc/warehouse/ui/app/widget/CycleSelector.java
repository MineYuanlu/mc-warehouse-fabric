package bid.yuanlu.mc.warehouse.ui.app.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;

/**
 * 循环选择按钮（M3 简化下拉）：点击循环到下一项，标签显示当前项。
 * 选项少（≤6）时比下拉少一次点击，无浮层复杂度。
 */
public final class CycleSelector<T> extends ButtonElement {

	private final List<T> options;
	private final Function<T, Component> label;
	private final Consumer<T> onSelect;
	private int index;

	public CycleSelector(List<T> options, Function<T, Component> label, int initialIndex, Consumer<T> onSelect) {
		super(label.apply(options.get(initialIndex)));
		if (options.isEmpty()) {
			throw new IllegalArgumentException("CycleSelector requires options");
		}
		this.options = options;
		this.label = label;
		this.onSelect = onSelect;
		this.index = initialIndex;
		onClick(() -> {
			index = (index + 1) % options.size();
			this.label(label.apply(options.get(index)));
			onSelect.accept(options.get(index));
		});
	}

	public T current() {
		return options.get(index);
	}

	public void select(T option) {
		int i = options.indexOf(option);
		if (i >= 0) {
			index = i;
			this.label(label.apply(options.get(index)));
		}
	}
}

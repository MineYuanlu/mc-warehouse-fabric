package bid.yuanlu.mc.warehouse.ui.core.event;

import java.util.ArrayList;
import java.util.List;

import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import org.jetbrains.annotations.Nullable;

/**
 * DOM 式三阶段事件派发器（UI-PDD §3.2，LDLib2 UIEventDispatcher 思想的自研简化版）。
 * 同一事件对象沿 capture（根→父）→ target（两类监听器）→ bubble（父→根）派发；
 * 任一监听器 {@link UiEvent#consume()} 即断链。
 */
public final class UiEventDispatcher {

	private UiEventDispatcher() {
	}

	/** 构造 target 到根的路径（含 target 自身，索引 0 = target）。 */
	private static List<UiElement> pathToRoot(@Nullable UiElement target) {
		var path = new ArrayList<UiElement>();
		for (UiElement e = target; e != null; e = e.parent()) {
			path.add(e);
		}
		return path;
	}

	/** 派发事件；返回是否被消费。 */
	public static boolean dispatch(@Nullable UiElement target, UiEvent event) {
		if (target == null) {
			return false;
		}
		var path = pathToRoot(target);
		// capture：根 → 父
		for (int i = path.size() - 1; i >= 1; i--) {
			if (dispatchToListeners(path.get(i), event.withPhase(UiEvent.Phase.CAPTURE), true)) {
				return true;
			}
		}
		// target：capture + bubble 监听器都执行
		if (dispatchToListeners(target, event.withPhase(UiEvent.Phase.CAPTURE), true)) {
			return true;
		}
		if (dispatchToListeners(target, event.withPhase(UiEvent.Phase.BUBBLE), false)) {
			return true;
		}
		// bubble：父 → 根
		for (int i = 1; i < path.size(); i++) {
			if (dispatchToListeners(path.get(i), event.withPhase(UiEvent.Phase.BUBBLE), false)) {
				return true;
			}
		}
		return event.consumed();
	}

	private static boolean dispatchToListeners(UiElement node, UiEvent event, boolean capture) {
		var listeners = node.listeners(event.type, capture);
		if (listeners.isEmpty()) {
			return false;
		}
		// 拷贝防遍历中增删
		for (var l : new ArrayList<>(listeners)) {
			l.accept(event);
			if (event.consumed()) {
				return true;
			}
		}
		return false;
	}
}

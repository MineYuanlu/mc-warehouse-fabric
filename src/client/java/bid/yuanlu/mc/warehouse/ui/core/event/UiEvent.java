package bid.yuanlu.mc.warehouse.ui.core.event;

import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * UI 事件（UI-PDD §3.2）。由 L2 输入适配层翻译 MC 输入并投入根元素，
 * 经 capture（根→父）→ target → bubble（父→根）三阶段派发。
 */
public final class UiEvent {

	public enum Type {
		MOUSE_DOWN, MOUSE_UP, CLICK, DOUBLE_CLICK, MOUSE_MOVE, ENTER, LEAVE, WHEEL,
		DRAG_START, DRAG, DRAG_END, KEY_DOWN, KEY_UP, CHAR, FOCUS, BLUR
	}

	/** 派发阶段。 */
	public enum Phase {
		CAPTURE, BUBBLE
	}

	public final Type type;
	/** 事件目标（非当前派发节点——当前节点见 dispatcher 回调上下文）。 */
	public final UiElement target;
	public final double x;
	public final double y;
	/** DRAG 事件的本次移动增量（其余事件为 0）。 */
	public final double dx;
	public final double dy;
	public final int button;
	public final int keyCode;
	public final int modifiers;
	public final double scrollY;
	public final Phase phase;
	private boolean consumed;

	public UiEvent(Type type, UiElement target, double x, double y, int button,
			int keyCode, int modifiers, double scrollY) {
		this(type, target, x, y, 0, 0, button, keyCode, modifiers, scrollY, Phase.BUBBLE);
	}

	public UiEvent(Type type, UiElement target, double x, double y, double dx, double dy,
			int button, int keyCode, int modifiers, double scrollY) {
		this(type, target, x, y, dx, dy, button, keyCode, modifiers, scrollY, Phase.BUBBLE);
	}

	public UiEvent(Type type, UiElement target, double x, double y, int button,
			int keyCode, int modifiers, double scrollY, Phase phase) {
		this(type, target, x, y, 0, 0, button, keyCode, modifiers, scrollY, phase);
	}

	public UiEvent(Type type, UiElement target, double x, double y, double dx, double dy,
			int button, int keyCode, int modifiers, double scrollY, Phase phase) {
		this.type = type;
		this.target = target;
		this.x = x;
		this.y = y;
		this.dx = dx;
		this.dy = dy;
		this.button = button;
		this.keyCode = keyCode;
		this.modifiers = modifiers;
		this.scrollY = scrollY;
		this.phase = phase;
	}

	/** 消费事件：断冒泡链（capture 阶段消费则 bubble 不再派发）。 */
	public void consume() {
		consumed = true;
	}

	public boolean consumed() {
		return consumed;
	}

	UiEvent withPhase(Phase p) {
		var e = new UiEvent(type, target, x, y, dx, dy, button, keyCode, modifiers, scrollY, p);
		e.consumed = consumed;
		return e;
	}
}

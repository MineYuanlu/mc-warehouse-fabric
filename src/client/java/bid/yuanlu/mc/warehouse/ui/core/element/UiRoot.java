package bid.yuanlu.mc.warehouse.ui.core.element;

import java.util.ArrayList;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEventDispatcher;

/**
 * 元素树根（UI-PDD §3.1/§3.2）：布局脏驱动（构建时求解，§3.7）、hover 缓存
 * （每帧一次命中测试）、CLICK/DOUBLE_CLICK/ENTER/LEAVE 合成、焦点管理、tooltip 通道。
 */
public final class UiRoot extends UiElement {

	private static final long DOUBLE_CLICK_MS = 300;

	private final LongSupplier clock;
	private boolean layoutDirty = true;
	/** relayout 过程中布局器写坐标会触发 markLayoutDirty，需抑制（否则每帧重排）。 */
	private boolean inLayout;
	private int lastWidth = -1;
	private int lastHeight = -1;
	private long tickCount;

	private double mouseX = -1;
	private double mouseY = -1;
	@Nullable
	private UiElement hover;
	@Nullable
	private UiElement pressed;
	@Nullable
	private UiElement focused;
	@Nullable
	private UiElement lastClickTarget;
	private long lastClickTime;
	private int lastClickButton;

	@Nullable
	private UiDraw draw;

	public UiRoot() {
		this(System::currentTimeMillis);
	}

	public UiRoot(LongSupplier clock) {
		this.clock = clock;
	}

	public void markDirty() {
		if (!inLayout) {
			layoutDirty = true;
		}
	}

	public long tickCount() {
		return tickCount;
	}

	@Nullable
	public UiDraw currentDraw() {
		return draw;
	}

	/** 每帧提取前调用：尺寸变化/脏标记 → 重布局；hover 缓存更新。 */
	public void update(UiDraw g, int width, int height, double mouseX, double mouseY) {
		this.draw = g;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		if (layoutDirty || width != lastWidth || height != lastHeight) {
			lastWidth = width;
			lastHeight = height;
			relayout(g, width, height);
		}
		updateHover(mouseX, mouseY);
	}

	private void relayout(UiDraw g, int width, int height) {
		inLayout = true;
		try {
			relayoutInner(g, width, height);
		} finally {
			inLayout = false;
			layoutDirty = false;
		}
	}

	private void relayoutInner(UiDraw g, int width, int height) {
		this.width = width;
		this.height = height;
		for (var c : children()) {
			c.measurePass(g);
			int cw = c.width() == AUTO ? Math.min(c.prefWidth(), width) : c.width();
			int ch = c.height() == AUTO ? c.prefHeight() : c.height();
			if (c.width() == AUTO) {
				c.size(cw, c.height());
			}
			if (c.height() == AUTO) {
				c.size(c.width(), ch);
			}
			c.arrangePass(g, (width - cw) / 2, (height - ch) / 2);
		}
		layoutDirty = false;
	}

	private void updateHover(double mx, double my) {
		var hit = mx >= 0 ? hit((int) mx, (int) my) : null;
		if (hit != hover) {
			if (hover != null) {
				hover.hovered = false;
				UiEventDispatcher.dispatch(hover, new UiEvent(UiEvent.Type.LEAVE, hover, mx, my, -1, -1, 0, 0));
			}
			hover = hit;
			if (hit != null) {
				hit.hovered = true;
				UiEventDispatcher.dispatch(hit, new UiEvent(UiEvent.Type.ENTER, hit, mx, my, -1, -1, 0, 0));
			}
		}
	}

	public @Nullable UiElement hover() {
		return hover;
	}

	// ---- 输入入口（L2 挂载点翻译 MC 输入后调用）----

	public boolean mouseDown(double mx, double my, int button) {
		var target = hit((int) mx, (int) my);
		if (target == null) {
			return false;
		}
		pressed = target;
		if (target.enabled) {
			target.pressed = true;
		}
		if (target.focusable) {
			requestFocus(target);
		}
		UiEventDispatcher.dispatch(target, new UiEvent(UiEvent.Type.MOUSE_DOWN, target, mx, my, button, -1, 0, 0));
		return true;
	}

	public boolean mouseUp(double mx, double my, int button) {
		var target = hit((int) mx, (int) my);
		UiEventDispatcher.dispatch(target != null ? target : this,
				new UiEvent(UiEvent.Type.MOUSE_UP, target != null ? target : this, mx, my, button, -1, 0, 0));
		if (pressed != null) {
			pressed.pressed = false;
			if (target == pressed && pressed.enabled) {
				UiEventDispatcher.dispatch(pressed, new UiEvent(UiEvent.Type.CLICK, pressed, mx, my, button, -1, 0, 0));
				long now = clock.getAsLong();
				if (target == lastClickTarget && lastClickButton == button && now - lastClickTime <= DOUBLE_CLICK_MS) {
					UiEventDispatcher.dispatch(pressed,
							new UiEvent(UiEvent.Type.DOUBLE_CLICK, pressed, mx, my, button, -1, 0, 0));
					lastClickTarget = null;
				} else {
					lastClickTarget = pressed;
					lastClickTime = now;
					lastClickButton = button;
				}
			}
			pressed = null;
		}
		return target != null;
	}

	public boolean mouseScroll(double mx, double my, double scrollY) {
		var target = hit((int) mx, (int) my);
		if (target == null) {
			return false;
		}
		return UiEventDispatcher.dispatch(target,
				new UiEvent(UiEvent.Type.WHEEL, target, mx, my, -1, -1, 0, scrollY));
	}

	public boolean mouseMoved(double mx, double my) {
		this.mouseX = mx;
		this.mouseY = my;
		if (hover == null) {
			return false;
		}
		return UiEventDispatcher.dispatch(hover, new UiEvent(UiEvent.Type.MOUSE_MOVE, hover, mx, my, -1, -1, 0, 0));
	}

	public boolean keyDown(int keyCode, int scancode, int modifiers) {
		var target = focused != null ? focused : this;
		return UiEventDispatcher.dispatch(target,
				new UiEvent(UiEvent.Type.KEY_DOWN, target, mouseX, mouseY, -1, keyCode, modifiers, 0));
	}

	public boolean keyUp(int keyCode, int scancode, int modifiers) {
		var target = focused != null ? focused : this;
		return UiEventDispatcher.dispatch(target,
				new UiEvent(UiEvent.Type.KEY_UP, target, mouseX, mouseY, -1, keyCode, modifiers, 0));
	}

	public boolean charTyped(int codepoint) {
		var target = focused != null ? focused : this;
		return UiEventDispatcher.dispatch(target,
				new UiEvent(UiEvent.Type.CHAR, target, mouseX, mouseY, -1, codepoint, 0, 0));
	}

	// ---- 焦点 ----

	public void requestFocus(@Nullable UiElement element) {
		if (focused == element) {
			return;
		}
		if (focused != null) {
			focused.focused = false;
			UiEventDispatcher.dispatch(focused, new UiEvent(UiEvent.Type.BLUR, focused, mouseX, mouseY, -1, -1, 0, 0));
		}
		focused = element;
		if (element != null) {
			element.focused = true;
			UiEventDispatcher.dispatch(element, new UiEvent(UiEvent.Type.FOCUS, element, mouseX, mouseY, -1, -1, 0, 0));
		}
	}

	@Nullable
	public UiElement focusedElement() {
		return focused;
	}

	// ---- tick / 渲染 ----

	public void tick() {
		tickCount++;
		tickTree();
	}

	@Override
	public void markLayoutDirty() {
		markDirty();
	}

	@Override
	public @Nullable UiElement hit(int px, int py) {
		// 根自身不做命中目标，只按绘制序分发子级
		var sorted = new ArrayList<>(children());
		sorted.sort((a, b) -> Integer.compare(a.zIndex, b.zIndex));
		for (int i = sorted.size() - 1; i >= 0; i--) {
			var h = sorted.get(i).hit(px, py);
			if (h != null) {
				return h;
			}
		}
		return null;
	}

	@Override
	public void extract(UiDraw g) {
		var sorted = new ArrayList<>(children());
		sorted.sort((a, b) -> Integer.compare(a.zIndex, b.zIndex));
		for (var c : sorted) {
			c.extract(g);
		}
		// tooltip 通道（置顶由底层保证）
		if (hover != null && draw != null) {
			var lines = hover.currentTooltip();
			if (lines != null && !lines.isEmpty()) {
				g.setTooltip(lines, (int) mouseX, (int) mouseY + 12);
			}
		}
	}

	@Override
	protected void drawElement(UiDraw g) {
		// 根默认自绘 nothing（Screen 背景由宿主/HUD 层负责）
	}

	/** 便捷：为根挂 tooltip 组件辅助（供测试）。 */
	public static Component literal(String s) {
		return Component.literal(s);
	}
}

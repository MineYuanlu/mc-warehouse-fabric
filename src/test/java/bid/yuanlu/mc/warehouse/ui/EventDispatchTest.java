package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;

/**
 * L1 事件系统（UI-PDD §3.2）：三阶段派发、consume 断链、CLICK/DOUBLE_CLICK 合成、焦点。
 * 注意：根把 100x100 的 panel 锚定左上 SCREEN_MARGIN(10,10)-(110,110)，按钮 a 在 (10,10)、b 在 (10,30)。
 */
class EventDispatchTest {

	private final TestDraw g = new TestDraw();
	private long nowMs = 0;

	private UiRoot rootOf(UiElement content) {
		var root = new UiRoot(() -> nowMs);
		root.add(content);
		root.update(g, 400, 300, -1, -1);
		return root;
	}

	@Test
	void captureThenBubbleOrder() {
		var panel = new PanelElement().padding(0).layout(new Column(0)).size(100, 100);
		var button = new ButtonElement("ok").padding(0).size(100, 20);
		panel.add(button);
		var root = rootOf(panel);
		root.update(g, 400, 300, 60, 20); // hover 命中 button（abs 10,10）

		var order = new ArrayList<String>();
		root.on(UiEvent.Type.CLICK, e -> order.add("root-bubble"), false);
		root.on(UiEvent.Type.CLICK, e -> order.add("root-capture"), true);
		panel.on(UiEvent.Type.CLICK, e -> order.add("panel-bubble"), false);
		panel.on(UiEvent.Type.CLICK, e -> order.add("panel-capture"), true);
		button.on(UiEvent.Type.CLICK, e -> order.add("button-target"), false);

		assertTrue(root.mouseDown(60, 20, 0));
		assertTrue(root.mouseUp(60, 20, 0));
		assertEquals(List.of("root-capture", "panel-capture", "button-target", "panel-bubble", "root-bubble"), order);
	}

	@Test
	void consumeStopsPropagation() {
		var panel = new PanelElement().padding(0).layout(new Column(0)).size(100, 100);
		var button = new ButtonElement("ok").padding(0).size(100, 20);
		panel.add(button);
		var root = rootOf(panel);
		root.update(g, 400, 300, 60, 20);

		var hits = new ArrayList<String>();
		button.on(UiEvent.Type.CLICK, e -> {
			hits.add("button");
			e.consume();
		}, true); // target 阶段 capture 消费 → bubble 链断
		panel.on(UiEvent.Type.CLICK, e -> hits.add("panel"), false);
		root.on(UiEvent.Type.CLICK, e -> hits.add("root"), false);

		root.mouseDown(60, 20, 0);
		root.mouseUp(60, 20, 0);
		assertEquals(List.of("button"), hits);
	}

	@Test
	void clickSynthesisRequiresPressAndReleaseOnSameElement() {
		var panel = new PanelElement().padding(0).layout(new Column(0)).size(100, 100);
		var a = new ButtonElement("a").padding(0).size(100, 20);
		var b = new ButtonElement("b").padding(0).size(100, 20);
		panel.add(a);
		panel.add(b);
		var root = rootOf(panel);
		root.update(g, 400, 300, 60, 20);

		var clicks = new ArrayList<String>();
		a.onClick(() -> clicks.add("a"));
		b.onClick(() -> clicks.add("b"));
		panel.onClick(() -> clicks.add("panel"));

		// 按下 a，移到 b 上松开 → 不合成 click
		root.mouseDown(60, 20, 0);
		root.mouseUp(60, 40, 0);
		assertEquals(List.of(), clicks);
		// 原位按下松开 → click（onClick 自动消费，不冒泡到 panel）
		root.mouseDown(60, 20, 0);
		root.mouseUp(60, 20, 0);
		assertEquals(List.of("a"), clicks);
	}

	@Test
	void doubleClickWithinWindow() {
		var panel = new PanelElement().padding(0).layout(new Column(0)).size(100, 100);
		var a = new ButtonElement("a").padding(0).size(100, 20);
		panel.add(a);
		var root = rootOf(panel);
		root.update(g, 400, 300, 60, 20);

		var dbl = new ArrayList<Boolean>();
		a.on(UiEvent.Type.DOUBLE_CLICK, e -> dbl.add(true));
		root.mouseDown(60, 20, 0);
		root.mouseUp(60, 20, 0);
		nowMs += 100; // 窗口内
		root.mouseDown(60, 20, 0);
		root.mouseUp(60, 20, 0);
		assertEquals(1, dbl.size());

		nowMs += 1000; // 窗口外
		root.mouseDown(60, 20, 0);
		root.mouseUp(60, 20, 0);
		assertEquals(1, dbl.size());
	}

	@Test
	void hoverAndEnterLeave() {
		var panel = new PanelElement().padding(0).layout(new Column(0)).size(100, 100);
		var a = new ButtonElement("a").padding(0).size(100, 20);
		panel.add(a);
		var root = rootOf(panel);

		root.update(g, 400, 300, 60, 20);
		assertTrue(a.hovered());
		assertEquals(a, root.hover());

		root.update(g, 400, 300, 60, 80); // 移到 button 之外 → hover 落在 panel 容器上
		assertFalse(a.hovered());
		assertEquals(panel, root.hover());
	}

	@Test
	void focusRequestAndBlur() {
		var panel = new PanelElement().padding(0).layout(new Column(0)).size(100, 100);
		var a = new LabelElement("a").padding(0).focusable(true).size(100, 20);
		var b = new LabelElement("b").padding(0).focusable(true).size(100, 20);
		panel.add(a);
		panel.add(b);
		var root = rootOf(panel);
		root.update(g, 400, 300, -1, -1);

		var events = new ArrayList<String>();
		a.on(UiEvent.Type.FOCUS, e -> events.add("a-focus"));
		a.on(UiEvent.Type.BLUR, e -> events.add("a-blur"));
		b.on(UiEvent.Type.FOCUS, e -> events.add("b-focus"));

		a.requestFocus();
		b.requestFocus();
		assertEquals(List.of("a-focus", "a-blur", "b-focus"), events);
		assertEquals(b, root.focusedElement());
	}

	@Test
	void keyEventsGoToFocused() {
		var panel = new PanelElement().padding(0).layout(new Column(0)).size(100, 100);
		var a = new LabelElement("a").padding(0).focusable(true).size(100, 20);
		panel.add(a);
		var root = rootOf(panel);
		root.update(g, 400, 300, -1, -1);

		var targets = new ArrayList<UiElement>();
		a.on(UiEvent.Type.KEY_DOWN, e -> {
			targets.add(e.target);
			e.consume();
		});
		a.requestFocus();
		assertTrue(root.keyDown(65, 0, 0));
		assertEquals(1, targets.size());
		assertEquals(a, targets.get(0));
	}
}

package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Grid;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;

/** L1 布局器数学（UI-PDD §3.4）：经 UiRoot.update 的公开 API 驱动 measure/arrange。 */
class LayoutTest {

	private final TestDraw g = new TestDraw();

	private UiRoot rootOf(UiElement content) {
		var root = new UiRoot();
		root.add(content);
		root.update(g, 400, 300, -1, -1);
		return root;
	}

	@Test
	void columnAutoHeightSumsChildren() {
		// 标签 "ab"：宽 2*6+2*2(padding)=16，高 9+4=13；按钮 "xy"：原版高 20，宽 2*6+2*4+16=36
		var panel = new PanelElement().padding(2).layout(new Column(3));
		panel.add(new LabelElement("ab").padding(2));
		panel.add(new ButtonElement("xy").padding(4));
		rootOf(panel);
		// 容器高 = 13 + 3 + 20 + 2*2(padding)
		assertEquals(13 + 3 + ButtonElement.VANILLA_HEIGHT + 4, panel.height());
		// 容器宽 = max(16,36) + 4
		assertEquals(36 + 4, panel.width());
		// 子级纵向排布（相对内容区）
		assertEquals(0, panel.children().get(0).y());
		assertEquals(13 + 3, panel.children().get(1).y());
	}

	@Test
	void rowAutoWidthSumsChildren() {
		var panel = new PanelElement().padding(0).layout(new Row(2));
		panel.add(new LabelElement("ab").padding(0)); // 12 x 9
		panel.add(new LabelElement("cd").padding(0)); // 12 x 9
		rootOf(panel);
		assertEquals(12 + 2 + 12, panel.width());
		assertEquals(9, panel.height());
		assertEquals(0, panel.children().get(0).x());
		assertEquals(12 + 2, panel.children().get(1).x());
	}

	@Test
	void fixedSizeChildIsRespected() {
		var panel = new PanelElement().padding(0).layout(new Column(0));
		var fixed = new LabelElement("zz").padding(0).size(50, 20);
		panel.add(fixed);
		rootOf(panel);
		assertEquals(50, fixed.width());
		assertEquals(20, fixed.height());
		assertEquals(50, panel.width());
		assertEquals(20, panel.height());
	}

	@Test
	void rootAnchorsTopLeft() {
		var label = new LabelElement("abcd").padding(0); // 24 x 9
		var root = rootOf(label);
		assertEquals(UiRoot.SCREEN_MARGIN, label.absX());
		assertEquals(UiRoot.SCREEN_MARGIN, label.absY());
		assertEquals(400, root.width());
	}

	@Test
	void gridForcesCellSizeForAutoChildren() {
		var grid = new PanelElement().padding(0).layout(new Grid(2, 18, 18, 0));
		grid.add(new LabelElement("a").padding(0));
		grid.add(new LabelElement("b").padding(0));
		rootOf(grid);
		assertEquals(18, grid.children().get(0).width());
		assertEquals(18, grid.children().get(1).width());
		assertEquals(36, grid.width());
		assertEquals(18, grid.height());
		assertEquals(18, grid.children().get(1).x());
	}

	@Test
	void absoluteCoordinatesNestThroughPadding() {
		var outer = new PanelElement().padding(5).layout(new Column(0)).size(100, -1);
		var inner = new PanelElement().padding(3).layout(new Column(0));
		inner.add(new LabelElement("x").padding(0)); // 6 x 9
		outer.add(inner);
				var root = rootOf(outer);
		// outer 锚定左上：absX = SCREEN_MARGIN；inner 位于 outer 内容区 (absX+5, absY+5)
		assertEquals(UiRoot.SCREEN_MARGIN + 5, inner.absX());
		assertEquals(300, root.height());
	}
}

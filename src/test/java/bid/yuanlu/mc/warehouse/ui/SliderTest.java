package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.ui.core.element.SliderElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;

/** SliderElement：步进吸附/钳位、按下与拖拽取值、滚轮微调、事件消费。 */
class SliderTest {

	private final TestDraw g = new TestDraw();

	@Test
	void setValueSnapsToStepAndClamps() {
		var s = new SliderElement(0.5f, 2f, 0.1f, 1f);
		List<Float> seen = new ArrayList<>();
		s.onChange(seen::add);
		s.setValue(1.24f); // 吸附到 0.1 网格 → 1.2
		assertEquals(1.2f, s.value(), 1e-4);
		s.setValue(9f);
		assertEquals(2f, s.value(), 1e-4);
		s.setValue(-1f);
		assertEquals(0.5f, s.value(), 1e-4);
		assertEquals(3, seen.size());
		s.setValue(0.5f); // 与当前值相同 → 不触发监听器
		assertEquals(3, seen.size());
	}

	@Test
	void mousePressAndDragTrackValue() {
		// 滑条 100 宽：根 SCREEN_MARGIN 锚定 → abs 10..110
		var s = new SliderElement(0f, 1f, 0.05f, 0f).size(100, 12);
		var root = new UiRoot();
		root.add(s);
		root.update(g, 400, 300, -1, -1);
		root.mouseDown(60, 16, 0); // t=(60-10-2)/96=0.5
		assertEquals(0.5f, s.value(), 0.02);
		root.mouseMoved(109, 16); // 越过阈值 → DRAG_START → 取 x=109 → 满
		assertEquals(1f, s.value(), 1e-4);
		assertTrue(root.mouseUp(109, 16, 0));
	}

	@Test
	void wheelNudgesByStepAndConsumes() {
		var s = new SliderElement(0f, 1f, 0.05f, 0.5f).size(100, 12);
		var root = new UiRoot();
		root.add(s);
		root.update(g, 400, 300, -1, -1);
		assertTrue(root.mouseScroll(60, 16, 1));
		assertEquals(0.55f, s.value(), 1e-4);
		assertTrue(root.mouseScroll(60, 16, -1));
		assertEquals(0.5f, s.value(), 1e-4);
		s.setValue(1f);
		assertTrue(root.mouseScroll(60, 16, 1)); // 顶格滚动仍消费（值不再变化）
		assertEquals(1f, s.value(), 1e-4);
	}
}

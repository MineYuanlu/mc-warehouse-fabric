package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.ScrollElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;

/** ScrollElement（overflow-y: auto）：滚轮滚动、边界钳制、裁剪区外不可命中。 */
class ScrollTest {

	private final TestDraw g = new TestDraw();

	@Test
	void wheelScrollsAndClampsToContent() {
		// 内容高 10 行 * 10 = 100，视口 50 → 最大滚动 50；滚轮步长 2*9(TestDraw 行高)=18
		var scroll = new ScrollElement(0).padding(0).size(100, 50);
		for (int i = 0; i < 10; i++) {
			scroll.add(new LabelElement("row" + i).padding(0).size(90, 10));
		}
		var root = new UiRoot();
		root.add(scroll);
		root.update(g, 400, 300, -1, -1);

		root.mouseScroll(30, 30, -1); // 滚轮向下 → scrollY + 18
		assertEquals(18, scroll.scrollY());
		root.mouseScroll(30, 30, -1);
		assertEquals(36, scroll.scrollY());
		scroll.scrollBy(100); // 超上限 → 钳到内容底
		assertEquals(50, scroll.scrollY());
		scroll.scrollBy(-100); // 超下限 → 钳到顶
		assertEquals(0, scroll.scrollY());
		root.mouseScroll(30, 30, 1); // 顶部再向上滚 → 保持 0
		assertEquals(0, scroll.scrollY());
	}

	@Test
	void clippedChildNotHittable() {
		var scroll = new ScrollElement(0).padding(0).size(100, 50);
		var rows = new java.util.ArrayList<LabelElement>();
		for (int i = 0; i < 10; i++) {
			var row = new LabelElement("row" + i).padding(0).size(90, 10);
			scroll.add(row);
			rows.add(row);
		}
		var root = new UiRoot();
		root.add(scroll);
		root.update(g, 400, 300, -1, -1);
		assertSame(rows.get(0), root.hit(20, 15)); // row0 在视口内（abs y 10..20）
		// 滚动 30 后：row3 占据原位置，row0 滚出视口上方（abs y -20..-10）
		scroll.scrollBy(30);
		root.update(g, 400, 300, -1, -1);
		assertSame(rows.get(3), root.hit(20, 15));
		assertNull(root.hit(20, -15)); // 裁剪区外（视口上方）的 row0 不可命中
		assertNull(root.hit(20, 70)); // 视口矩形（abs y 10..60）外不可命中
	}
}

package bid.yuanlu.mc.warehouse.ui.core.layout;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;

/**
 * 布局器端口（UI-PDD §3.4）。两遍协议：
 * <ol>
 *   <li>measure：自底向上求首选尺寸（{@link UiElement} measurePass 缓存）。</li>
 *   <li>arrange：自顶向下定位子级（子级坐标相对容器内容区原点）。</li>
 * </ol>
 * 构建时求解一次（resize 全量重建），运行期零布局重算。
 */
public interface Layout {

	void arrange(UiElement container, UiDraw g);

	int measureWidth(UiElement container, UiDraw g);

	int measureHeight(UiElement container, UiDraw g);

	/** 子级生效尺寸：显式固定值优先，AUTO 用 measure 结果。 */
	static int effectiveWidth(UiElement child) {
		return child.width() == UiElement.AUTO ? child.prefWidth() : child.width();
	}

	static int effectiveHeight(UiElement child) {
		return child.height() == UiElement.AUTO ? child.prefHeight() : child.height();
	}

	static void measureChildren(UiElement container, UiDraw g) {
		for (var c : container.children()) {
			if (c.visible()) {
				c.measurePass(g);
			}
		}
	}
}

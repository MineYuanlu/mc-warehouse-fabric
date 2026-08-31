package bid.yuanlu.mc.warehouse.ui.mc;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 版本逃生舱（UI-PDD §4.4，@CompatDebt）：绕过 UiDraw 门面直接使用原生提取器。
 * <p>
 * <b>使用纪律</b>：每个使用点都是兼容债，版本升级时必须逐个审查；目标使用数为 0。
 * 仅供 {@code ui/mc/} 与 {@code ui/app/} 内部特殊绘制场景，禁止扩散到业务屏。
 */
public final class RawGraphics {

	private static final ThreadLocal<GuiGraphicsExtractor> CURRENT = new ThreadLocal<>();

	private RawGraphics() {
	}

	/** 由各版本 UiDraw 实现在每帧开始时设置。 */
	public static void setCurrent(GuiGraphicsExtractor g) {
		CURRENT.set(g);
	}

	static void clearCurrent() {
		CURRENT.remove();
	}

	/** @CompatDebt 当前帧原生提取器；不在渲染帧内调用时为 null。 */
	public static void raw(Consumer<GuiGraphicsExtractor> consumer) {
		var g = CURRENT.get();
		if (g != null) {
			consumer.accept(g);
		}
	}
}

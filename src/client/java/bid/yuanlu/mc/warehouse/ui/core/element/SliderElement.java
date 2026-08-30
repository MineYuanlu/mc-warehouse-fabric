package bid.yuanlu.mc.warehouse.ui.core.element;

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 滑条（原版 Slider 的程序化简化版）：横向轨道 + 滑块，按下/拖拽按 x 位置取值，
 * 滚轮按步进微调。值始终吸附到 step 网格并钳在 [min, max]；变化经 {@link #onChange}
 * 推送（拖拽中每帧触发，频率由使用方消化）。提交语义（如落盘）由使用方监听
 * DRAG_END / CLICK 自行接线——无拖拽的按下-抬起以 CLICK 收尾，拖拽以 DRAG_END 收尾。
 */
public class SliderElement extends UiElement<SliderElement> {

	private static final int THUMB_WIDTH = 4;
	private static final int THUMB_HEIGHT = 10;
	private static final int TRACK_HEIGHT = 2;
	/** AUTO 宽度兜底最小宽（通常由布局器拉伸，见 grow）。 */
	private static final int MIN_TRACK = 40;

	private final float min;
	private final float max;
	private final float step;
	private float value;
	@Nullable
	private Consumer<Float> listener;

	public SliderElement(float min, float max, float step, float value) {
		this.min = min;
		this.max = max;
		this.step = step;
		this.value = snap(value);
		// 注意：构造器内 lambda 里裸写 value/step 会捕获构造器参数（参数遮蔽字段），
		// 必须用 this. 限定读字段——否则滚轮永远从初始值起算
		on(UiEvent.Type.MOUSE_DOWN, e -> {
			if (this.enabled) {
				trackTo(e.x);
				e.consume();
			}
		});
		on(UiEvent.Type.DRAG_START, e -> {
			if (this.enabled) {
				trackTo(e.x);
				e.consume();
			}
		});
		on(UiEvent.Type.DRAG, e -> {
			if (this.enabled) {
				trackTo(e.x);
				e.consume();
			}
		});
		on(UiEvent.Type.WHEEL, e -> {
			if (this.enabled) {
				setValue(this.value + (e.scrollY > 0 ? this.step : -this.step));
				e.consume();
			}
		});
	}

	public SliderElement onChange(Consumer<Float> l) {
		listener = l;
		return this;
	}

	public float value() {
		return value;
	}

	/** 设值：吸附 step 网格并钳位；与当前值相同不触发监听器。 */
	public void setValue(float v) {
		float snapped = snap(v);
		if (snapped == value) {
			return;
		}
		value = snapped;
		if (listener != null) {
			listener.accept(value);
		}
	}

	/** 按事件 x（绝对坐标）定位滑块。 */
	private void trackTo(double x) {
		int trackW = width() - THUMB_WIDTH;
		if (trackW <= 0) {
			return;
		}
		float t = (float) ((x - absX() - THUMB_WIDTH / 2.0) / trackW);
		setValue(min + Math.clamp(t, 0f, 1f) * (max - min));
	}

	private float snap(float v) {
		return Math.clamp(Math.round((v - min) / step) * step + min, min, max);
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return Math.max(MIN_TRACK, padding() * 2);
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		return Math.max(THUMB_HEIGHT, g.lineHeight()) + padding() * 2;
	}

	@Override
	protected void drawElement(UiDraw g) {
		var t = Theme.active();
		int trackY = absY() + (height() - TRACK_HEIGHT) / 2;
		g.fill(absX(), trackY, absX() + width(), trackY + TRACK_HEIGHT, t.border());
		float frac = max > min ? (value - min) / (max - min) : 0;
		int thumbX = absX() + Math.round(frac * (width() - THUMB_WIDTH));
		g.fill(absX(), trackY, thumbX + THUMB_WIDTH / 2, trackY + TRACK_HEIGHT, t.accent());
		int thumbY = absY() + (height() - THUMB_HEIGHT) / 2;
		int thumbColor = !enabled ? t.textMuted()
				: hovered() || pressed() ? t.accentHover() : t.textPrimary();
		g.fill(thumbX, thumbY, thumbX + THUMB_WIDTH, thumbY + THUMB_HEIGHT, thumbColor);
	}
}

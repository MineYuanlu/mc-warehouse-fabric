package bid.yuanlu.mc.warehouse.ui.core.anim;

/**
 * tick 步进缓动（UI-PDD §3.6）：每 tick 向 target 逼近 speed，帧渲染直接读
 * {@link #value()}（含 partialTick 插值由调用方决定，M0 从简）。
 * 纯数学，可 JUnit。
 */
public final class TickLerp {

	private float value;
	private float target;
	private final float speed;

	public TickLerp(float speed) {
		this(0, speed);
	}

	public TickLerp(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public void setTarget(float t) {
		target = t;
	}

	public void snap(float v) {
		value = target = v;
	}

	/** 每 tick 推进一次。 */
	public void tick() {
		float d = target - value;
		if (Math.abs(d) <= speed) {
			value = target;
		} else {
			value += Math.signum(d) * speed;
		}
	}

	public float value() {
		return value;
	}

	public float target() {
		return target;
	}

	public boolean done() {
		return value == target;
	}
}

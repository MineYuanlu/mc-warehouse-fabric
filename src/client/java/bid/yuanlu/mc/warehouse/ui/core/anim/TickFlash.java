package bid.yuanlu.mc.warehouse.ui.core.anim;

/**
 * 折返呼吸 alpha（UI-PDD §3.6，AppleSkin 模式）：每 tick 步进 alpha，
 * 在 max 与 min 之间折返。纯数学，可 JUnit。
 */
public final class TickFlash {

	private static final float DEFAULT_SPEED = 0.125F;

	private float alpha;
	private float dir = 1;
	private final float min;
	private final float max;
	private final float speed;

	public TickFlash(float min, float max) {
		this(min, max, DEFAULT_SPEED);
	}

	public TickFlash(float min, float max, float speed) {
		this.min = min;
		this.max = max;
		this.speed = speed;
		this.alpha = min;
	}

	public void tick() {
		alpha += dir * speed;
		if (alpha >= max) {
			alpha = max;
			dir = -1;
		} else if (alpha <= min) {
			alpha = min;
			dir = 1;
		}
	}

	public float alpha() {
		return alpha;
	}

	public void reset() {
		alpha = min;
		dir = 1;
	}
}

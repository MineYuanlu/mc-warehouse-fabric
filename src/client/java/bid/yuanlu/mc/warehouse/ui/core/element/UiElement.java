package bid.yuanlu.mc.warehouse.ui.core.element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEventDispatcher;
import bid.yuanlu.mc.warehouse.ui.core.layout.Layout;

/**
 * L1 元素基类（UI-PDD §3.1）。
 * <ul>
 *   <li>坐标：{@code x/y} 相对父级内容区原点（父级 abs + 父级 padding），由布局器写入；
 *       {@code absX/absY} 为布局期展开的绝对屏幕坐标（GUI 缩放坐标）。</li>
 *   <li>尺寸：{@code AUTO(-1)} 表示自动，由 measure/arrange 两遍求解（构建时一次性，
 *       运行期零重算，UI-PDD §3.7）。</li>
 *   <li>提取式渲染：{@link #extract} 只读状态绘制，禁止改状态。</li>
 *   <li>hovered/focused/pressed 为引擎管理的伪状态。</li>
 * </ul>
 */
public abstract class UiElement<S extends UiElement<S>> {

	public static final int AUTO = -1;

	private UiElement<?> parent;
	private final List<UiElement<?>> children = new ArrayList<>();

	protected int x, y, width = AUTO, height = AUTO;
	/** 声明尺寸：使用者显式 size() 的值；布局器写入的生效尺寸不回写这里（见 {@link #applySize}）。 */
	private int declaredWidth = AUTO, declaredHeight = AUTO;
	private int absX, absY;
	private int prefWidth, prefHeight;

	private boolean visible = true;
	boolean enabled = true;
	boolean focusable = false;
	boolean hovered, focused, pressed;
	int zIndex;
	private String id = "";
	private final Set<String> classes = new LinkedHashSet<>();
	private int padding;
	private boolean clipContent;
	/** 主轴权重（轻量 flex-grow）：0 = 固定/内容尺寸。 */
	private float grow;

	@Nullable
	private Layout layout;

	private final Map<UiEvent.Type, List<Consumer<UiEvent>>> captureListeners = new EnumMap<>(UiEvent.Type.class);
	private final Map<UiEvent.Type, List<Consumer<UiEvent>>> bubbleListeners = new EnumMap<>(UiEvent.Type.class);
	@Nullable
	private Supplier<List<Component>> tooltip;

	@SuppressWarnings("unchecked")
	private S self() {
		return (S) this;
	}

	// ---- 树 ----

	public S add(UiElement<?> child) {
		if (child.parent != null) {
			child.parent.children.remove(child);
		}
		child.parent = this;
		children.add(child);
		markLayoutDirty();
		return self();
	}

	public S remove(UiElement<?> child) {
		if (children.remove(child)) {
			child.parent = null;
			markLayoutDirty();
		}
		return self();
	}

	public void removeFromParent() {
		if (parent != null) {
			parent.remove(this);
		}
	}

	public S clearChildren() {
		for (var c : new ArrayList<>(children)) {
			c.parent = null;
		}
		children.clear();
		markLayoutDirty();
		return self();
	}

	public UiElement parent() {
		return parent;
	}

	public List<UiElement> children() {
		return Collections.unmodifiableList(children);
	}

	// ---- 几何 ----

	public S pos(int x, int y) {
		this.x = x;
		this.y = y;
		markLayoutDirty();
		return self();
	}

	public S size(int width, int height) {
		this.width = width;
		this.height = height;
		this.declaredWidth = width;
		this.declaredHeight = height;
		markLayoutDirty();
		return self();
	}

	/** 布局器写入生效尺寸（不记为声明值，resetAutoSizes 后会被重新求解）。 */
	public void applySize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	/** relayout 前调用：把整棵子树的生效尺寸复位为声明值，AUTO 重新测量（文本变化即跟随）。 */
	public void resetAutoSizes() {
		width = declaredWidth;
		height = declaredHeight;
		for (var c : children) {
			c.resetAutoSizes();
		}
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int absX() {
		return absX;
	}

	public int absY() {
		return absY;
	}

	public S padding(int p) {
		padding = p;
		markLayoutDirty();
		return self();
	}

	public int padding() {
		return padding;
	}

	public S clipContent(boolean clip) {
		clipContent = clip;
		return self();
	}

	/**
	 * 主轴权重（UI-PDD §3.4 轻量 flex）：容器主轴尺寸为定值时，按权重瓜分扣除固定
	 * 尺寸兄弟与 gap 后的剩余空间；显式 {@link #size(int, int)} 此时作为最小尺寸。
	 * 权重只在直接子级上生效（不递归），容器主轴尺寸不定（AUTO）时退化为普通流式。
	 */
	public S grow(float g) {
		grow = g;
		markLayoutDirty();
		return self();
	}

	public float grow() {
		return grow;
	}

	/** 使用者显式声明的宽度（AUTO = 未声明）；布局器写入的生效尺寸不在此列。 */
	public int declaredWidth() {
		return declaredWidth;
	}

	/** 使用者显式声明的高度（AUTO = 未声明）；布局器写入的生效尺寸不在此列。 */
	public int declaredHeight() {
		return declaredHeight;
	}

	// ---- 状态 ----

	public S visible(boolean v) {
		if (visible != v) {
			visible = v;
			markLayoutDirty();
		}
		return self();
	}

	public boolean visible() {
		return visible;
	}

	public S enabled(boolean e) {
		enabled = e;
		return self();
	}

	public boolean enabled() {
		return enabled;
	}

	public S focusable(boolean f) {
		focusable = f;
		return self();
	}

	public boolean hovered() {
		return hovered;
	}

	public boolean focused() {
		return focused;
	}

	public boolean pressed() {
		return pressed;
	}

	public S zIndex(int z) {
		zIndex = z;
		markLayoutDirty();
		return self();
	}

	public S id(String id) {
		this.id = id;
		return self();
	}

	public String id() {
		return id;
	}

	public S classes(String... cs) {
		classes.addAll(List.of(cs));
		return self();
	}

	public boolean hasClass(String c) {
		return classes.contains(c);
	}

	/** 按 id 深度优先查找子树（含自身）。 */
	public <E extends UiElement<?>> @Nullable E findId(String id) {
		if (id.equals(this.id)) {
			@SuppressWarnings("unchecked")
			var self = (E) this;
			return self;
		}
		for (UiElement<?> c : children) {
			var r = c.<E>findId(id);
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	// ---- 布局 ----

	public S layout(@Nullable Layout l) {
		layout = l;
		markLayoutDirty();
		return self();
	}

	@Nullable
	public Layout layout() {
		return layout;
	}

	/** 根元素（未入树时为自身）；供跨包元素在事件期解析根（如浮层挂载）。 */
	public UiElement<?> root() {
		UiElement<?> e = this;
		while (e.parent != null) {
			e = e.parent;
		}
		return e;
	}

	public void markLayoutDirty() {
		if (root() instanceof UiRoot r) {
			r.markDirty();
		}
	}

	/**
	 * measure 遍：求解首选尺寸（自底向上；树小，重复调用无碍）。
	 * <p>
	 * 无条件递归测量可见子树：自身尺寸固定时 layout 的 measureChildren 不会被
	 * onMeasure 触达，若不在此兜底，整棵子树的 pref 将保持 0（多行重叠/命中框
	 * 为 0 的根因）。
	 */
	public void measurePass(UiDraw g) {
		prefWidth = width >= 0 ? width : onMeasureWidth(g);
		prefHeight = height >= 0 ? height : onMeasureHeight(g);
		for (var c : children) {
			if (c.visible()) {
				c.measurePass(g);
			}
		}
	}

	public int prefWidth() {
		return prefWidth;
	}

	public int prefHeight() {
		return prefHeight;
	}

	protected int onMeasureWidth(UiDraw g) {
		return layout != null ? layout.measureWidth(this, g) : padding * 2;
	}

	protected int onMeasureHeight(UiDraw g) {
		return layout != null ? layout.measureHeight(this, g) : padding * 2;
	}

	/** arrange 遍：展开绝对坐标并让布局器定位子级。 */
	final void arrangePass(UiDraw g, int absX, int absY) {
		this.absX = absX;
		this.absY = absY;
		if (width == AUTO) {
			width = prefWidth;
		}
		if (height == AUTO) {
			height = prefHeight;
		}
		if (layout != null) {
			layout.arrange(this, g);
		}
		for (var c : children) {
			if (c.visible) {
				c.arrangePass(g, absX + padding + c.x, absY + padding + c.y);
			}
		}
	}

	// ---- 命中 ----

	public boolean containsAbs(int px, int py) {
		return px >= absX && px < absX + width && py >= absY && py < absY + height;
	}

	/** 命中测试：与绘制顺序一致（zIndex 升序绘制、逆序命中取最上层）。 */
	public @Nullable UiElement<?> hit(int px, int py) {
		if (!visible) {
			return null;
		}
		if (clipContent) {
			// 裁剪区外的内容（如滚出视口的行）虽在元素矩形内，也不可命中
			if (px < absX + padding || px >= absX + width - padding
					|| py < absY + padding || py >= absY + height - padding) {
				return null;
			}
		} else if (!containsAbs(px, py)) {
			return null;
		}
		var sorted = new ArrayList<>(children);
		sorted.sort((a, b) -> Integer.compare(a.zIndex, b.zIndex));
		for (int i = sorted.size() - 1; i >= 0; i--) {
			var h = sorted.get(i).hit(px, py);
			if (h != null) {
				return h;
			}
		}
		return this;
	}

	// ---- 事件 ----

	public S on(UiEvent.Type type, Consumer<UiEvent> listener) {
		return on(type, listener, false);
	}

	public S on(UiEvent.Type type, Consumer<UiEvent> listener, boolean capture) {
		var map = capture ? captureListeners : bubbleListeners;
		map.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
		return self();
	}

	public S onClick(Runnable action) {
		return on(UiEvent.Type.CLICK, e -> {
			if (enabled) {
				action.run();
				e.consume(); // 便捷 API 自动消费：按钮点击不冒泡到父容器
			}
		});
	}

	public List<Consumer<UiEvent>> listeners(UiEvent.Type type, boolean capture) {
		var map = capture ? captureListeners : bubbleListeners;
		var l = map.get(type);
		return l == null ? List.of() : l;
	}

	/** 键盘焦点请求（经根的焦点管理）。 */
	public void requestFocus() {
		if (root() instanceof UiRoot r) {
			r.requestFocus(this);
		}
	}

	public S tooltip(Supplier<List<Component>> lines) {
		tooltip = lines;
		return self();
	}

	@Nullable
	List<Component> currentTooltip() {
		return tooltip == null ? null : tooltip.get();
	}

	// ---- 渲染 ----

	/** 提取式绘制：自绘 → （裁剪）→ 子级按 zIndex 升序。禁止在此改状态。 */
	public void extract(UiDraw g) {
		if (!visible) {
			return;
		}
		drawElement(g);
		boolean clip = clipContent && width > 0 && height > 0;
		if (clip) {
			g.pushClip(absX + padding, absY + padding, absX + width - padding, absY + height - padding);
		}
		var sorted = new ArrayList<>(children);
		sorted.sort((a, b) -> Integer.compare(a.zIndex, b.zIndex));
		for (var c : sorted) {
			c.extract(g);
		}
		if (clip) {
			g.popClip();
		}
	}

	/** 自绘内容（绝对坐标）。 */
	protected abstract void drawElement(UiDraw g);

	/** 每 tick 回调（动画推进等），子树递归；不可见子树跳过。 */
	void tickTree() {
		if (!visible) {
			return;
		}
		onTick();
		for (var c : children) {
			c.tickTree();
		}
	}

	protected void onTick() {
	}
}

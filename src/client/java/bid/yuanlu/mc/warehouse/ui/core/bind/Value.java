package bid.yuanlu.mc.warehouse.ui.core.bind;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * 监听器推送式数据绑定值（UI-PDD §3.3）。业务 Presenter 持有/更新 Value，
 * UI 元素 listen 后在值变化时更新自身可见状态。全部发生在客户端主线程。
 */
public final class Value<T> {

	private T current;
	private final List<Consumer<T>> listeners = new ArrayList<>();

	private Value(T initial) {
		this.current = initial;
	}

	public static <T> Value<T> of(T initial) {
		return new Value<>(initial);
	}

	public T get() {
		return current;
	}

	public void set(T v) {
		if (v == current || (v != null && v.equals(current))) {
			return;
		}
		current = v;
		notifyListeners();
	}

	public void listen(Consumer<T> listener) {
		listeners.add(listener);
		listener.accept(current);
	}

	private void notifyListeners() {
		for (var l : new ArrayList<>(listeners)) {
			l.accept(current);
		}
	}

	/** 派生值：任一源变化即重算（弱一致：源间更新跨帧可见）。 */
	@SafeVarargs
	public static <T> Value<T> derive(Function<Object[], T> fn, Value<?>... sources) {
		Value<T> out = new Value<>(fn.apply(extract(sources)));
		for (Value<?> s : sources) {
			s.listen(v -> out.set(fn.apply(extract(sources))));
		}
		return out;
	}

	private static Object[] extract(Value<?>[] sources) {
		var out = new Object[sources.length];
		for (int i = 0; i < sources.length; i++) {
			out[i] = sources[i].get();
		}
		return out;
	}

	@Nullable
	public T getOrNull() {
		return current;
	}
}

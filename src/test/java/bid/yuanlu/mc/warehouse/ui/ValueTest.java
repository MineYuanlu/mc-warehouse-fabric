package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;

/** L1 数据绑定（UI-PDD §3.3）：监听器推送、注册即回调、派生值。 */
class ValueTest {

	@Test
	void listenCallsImmediately() {
		var v = Value.of(42);
		AtomicInteger seen = new AtomicInteger(-1);
		v.listen(seen::set);
		assertEquals(42, seen.get());
	}

	@Test
	void setNotifiesOnlyOnChange() {
		var v = Value.of("a");
		AtomicInteger count = new AtomicInteger();
		v.listen(x -> count.incrementAndGet());
		assertEquals(1, count.get()); // 注册即回调

		v.set("a"); // 同值不通知
		assertEquals(1, count.get());

		v.set("b");
		assertEquals(2, count.get());
	}

	@Test
	void deriveRecomputesOnSourceChange() {
		Value<Integer> a = Value.of(1);
		Value<Integer> b = Value.of(2);
		Value<Integer> sum = Value.derive(args -> (Integer) args[0] + (Integer) args[1], a, b);
		assertEquals(3, sum.get());
		a.set(10);
		assertEquals(12, sum.get());
	}

	@Test
	void labelBindsToValue() {
		var label = new LabelElement("");
		Value<String> text = Value.of("hello");
		label.bindText(text);
		assertEquals("hello", label.displayText());

		text.set("world");
		assertEquals("world", label.displayText());
	}
}

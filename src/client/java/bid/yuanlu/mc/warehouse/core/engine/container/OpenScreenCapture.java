package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * 开屏包捕获的解耦层：Mixin → {@link #fireOpenScreen} → 各消费者（§6.1 WAIT_SCREEN、标记模式等）。
 * <p>
 * 以 ClientboundOpenScreenPacket 为确认信号（MVP 验证过的手法）：
 * 包内自带 containerId——随即成为本次容器会话的身份键，后续一切回调按其门控。
 */
public final class OpenScreenCapture {

	private static final List<IntConsumer> HANDLERS = new CopyOnWriteArrayList<>();

	/** 注册处理器（可多个：协议层 + 标记模式并存） */
	public static void register(IntConsumer consumer) {
		HANDLERS.add(consumer);
	}

	public static void unregister(IntConsumer consumer) {
		HANDLERS.remove(consumer);
	}

	/** 由 Mixin 在 handleOpenScreen 调用 */
	public static void fireOpenScreen(int containerId) {
		for (IntConsumer h : HANDLERS) {
			h.accept(containerId);
		}
	}

	private OpenScreenCapture() {
	}
}

package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.function.IntConsumer;

import org.jetbrains.annotations.Nullable;

/**
 * 开屏包捕获的解耦层：Mixin → {@link #fireOpenScreen} → 协议层（§6.1 WAIT_SCREEN）。
 * <p>
 * 以 ClientboundOpenScreenPacket 为确认信号（MVP 验证过的手法）：
 * 包内自带 containerId——随即成为本次容器会话的身份键，后续一切回调按其门控。
 */
public final class OpenScreenCapture {

	@Nullable
	private static volatile IntConsumer handler;

	/** 协议层注册处理器 */
	public static void register(IntConsumer consumer) {
		handler = consumer;
	}

	/** 由 Mixin 在 handleOpenScreen 调用 */
	public static void fireOpenScreen(int containerId) {
		IntConsumer h = handler;
		if (h != null) {
			h.accept(containerId);
		}
	}

	private OpenScreenCapture() {
	}
}

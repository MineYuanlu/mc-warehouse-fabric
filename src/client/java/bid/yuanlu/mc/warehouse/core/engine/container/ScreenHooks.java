package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Screen 关闭钩子的解耦层：Mixin → {@link #fireOnClose} → 各消费者。
 * <p>
 * 多监听者：协议层（引擎会话）+ 玩家开箱缓存刷新（F2）并存。
 * §3.8 硬性约束：写入缓存前必须校验正在关闭的 Screen 与哪个容器会话绑定
 * （打开时建立映射），禁止用「上一个交互坐标」猜测——校验逻辑归协议层（L8）。
 */
public final class ScreenHooks {

	private static final List<Consumer<AbstractContainerScreen<?>>> ON_CLOSE = new CopyOnWriteArrayList<>();

	/** 协议层在客户端初始化时注册；可重复注册多个消费者 */
	public static void registerOnClose(Consumer<AbstractContainerScreen<?>> handler) {
		if (handler != null) ON_CLOSE.add(handler);
	}

	/** 由 Mixin 在 onClose HEAD 调用（仅容器界面） */
	public static void fireOnClose(AbstractContainerScreen<?> screen) {
		for (Consumer<AbstractContainerScreen<?>> h : ON_CLOSE) {
			try {
				h.accept(screen);
			} catch (Exception ignored) {
			}
		}
	}

	private ScreenHooks() {
	}
}

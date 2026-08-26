package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Screen 关闭钩子的解耦层：Mixin → {@link #fireOnClose} → 协议层处理器。
 * <p>
 * §3.8 硬性约束：写入缓存前必须校验正在关闭的 Screen 与哪个容器会话绑定
 * （打开时建立映射），禁止用「上一个交互坐标」猜测——校验逻辑归协议层（L8）。
 */
public final class ScreenHooks {

	@Nullable
	private static volatile Consumer<AbstractContainerScreen<?>> onCloseHandler;

	/** 协议层在客户端初始化时注册；重复注册覆盖 */
	public static void registerOnClose(Consumer<AbstractContainerScreen<?>> handler) {
		onCloseHandler = handler;
	}

	/** 由 Mixin 在 onClose HEAD 调用（仅容器界面） */
	public static void fireOnClose(AbstractContainerScreen<?> screen) {
		Consumer<AbstractContainerScreen<?>> handler = onCloseHandler;
		if (handler != null) {
			handler.accept(screen);
		}
	}

	private ScreenHooks() {
	}
}

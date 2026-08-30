package bid.yuanlu.mc.warehouse.ui.core.draw;

import org.jetbrains.annotations.Nullable;

/**
 * L1 端口：系统剪贴板（纯文本）。L2 平台装配时注入实现（26.1 转发
 * {@code KeyboardHandler}）；未注入（纯 JVM 单测）时读侧返回空串、写侧为空操作。
 * 输入处理期使用（此时无 UiDraw 实例），故为静态端口而非 UiDraw 方法。
 */
public final class ClipboardPort {

	public interface Provider {
		String get();

		void set(String value);
	}

	private static volatile @Nullable Provider provider;

	private ClipboardPort() {
	}

	public static void install(@Nullable Provider p) {
		provider = p;
	}

	public static String get() {
		var p = provider;
		return p == null ? "" : p.get();
	}

	public static void set(String value) {
		var p = provider;
		if (p != null) {
			p.set(value);
		}
	}
}

package bid.yuanlu.mc.warehouse.ui.mc;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.world.WorldHighlighter;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * L2 平台门面（UI-PDD §4.4）：客户端入口期 {@link #init()} 做 feature probe
 * 选择版本实现；L3 只经本门面访问挂载点，永不直接触碰 MC client GUI。
 */
public final class UiPlatform {

	private static final Logger LOG = LogUtils.getLogger();

	public interface ScreenOpener {
		void open(Supplier<UiRoot> rootFactory);

		/** 打开屏且 HUD 保持显示（HUD 设置屏实时预览用）。 */
		default void openKeepHud(Supplier<UiRoot> rootFactory) {
			open(rootFactory);
		}
	}

	private static ScreenOpener screenOpener = f -> LOG.error("[ui] UiPlatform 未初始化，无法打开 Screen");
	private static Runnable screenCloser = () -> {
	};
	private static HudRegistrar hudRegistrar = (id, f) -> LOG.error("[ui] UiPlatform 未初始化，无法注册 HUD: {}", id);
	private static WorldHighlighter highlighter = () -> WorldHighlighter.WorldFrame.NOOP;
	private static boolean ready;

	private UiPlatform() {
	}

	public static void init() {
		if (ready) {
			return;
		}
		if (McPlatformDetector.hasMc261Gui()) {
			bid.yuanlu.mc.warehouse.ui.mc.mc261.Mc261UiPlatform.install();
			ready = true;
			LOG.info("[ui] UI 平台: mc261");
		} else {
			LOG.error("[ui] 未找到兼容的 UI 平台实现（GuiGraphicsExtractor 缺失），UI 功能禁用");
		}
	}

	public static boolean ready() {
		return ready;
	}

	public static void openScreen(Supplier<UiRoot> rootFactory) {
		screenOpener.open(rootFactory);
	}

	/** 打开屏且 HUD 保持显示（HUD 设置屏实时预览）。 */
	public static void openScreenKeepHud(Supplier<UiRoot> rootFactory) {
		screenOpener.openKeepHud(rootFactory);
	}

	public static void closeScreen() {
		screenCloser.run();
	}

	/** 注册 HUD 根（同一 id 重复注册覆盖）。 */
	public static void registerHud(String id, Supplier<UiRoot> rootFactory) {
		hudRegistrar.register(id, rootFactory);
	}

	/** HUD 配置变化后调用：下一帧重建该 HUD 根。 */
	public static void resetHud(String id) {
		if (hudRegistrar instanceof Resettable r) {
			r.reset(id);
		}
	}

	public static WorldHighlighter worldHighlighter() {
		return highlighter;
	}

	// ---- 由版本实现装配 ----

	public interface HudRegistrar {
		void register(String id, Supplier<UiRoot> rootFactory);
	}

	public interface Resettable {
		void reset(String id);
	}

	/** 由版本实现装配（ui/mc/<版本>/ 子包调用）。 */
	public static void install(ScreenOpener opener, Runnable closer, HudRegistrar registrar,
			WorldHighlighter highlighter) {
		UiPlatform.screenOpener = opener;
		UiPlatform.screenCloser = closer;
		UiPlatform.hudRegistrar = registrar;
		UiPlatform.highlighter = highlighter;
	}
}

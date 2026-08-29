package bid.yuanlu.mc.warehouse.ui.mc.mc261;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.world.WorldHighlighter;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * MC 26.1 平台装配（UI-PDD §4.4）。
 */
public final class Mc261UiPlatform {

	private static long tickCounter;
	private static final Mc261WorldHighlighter HIGHLIGHTER = new Mc261WorldHighlighter();

	private Mc261UiPlatform() {
	}

	/** 由 UiPlatform 门面在探测成功后调用。 */
	public static void install() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tickCounter++);
		UiPlatform.install(
				new UiPlatform.ScreenOpener() {
					@Override
					public void open(Supplier<UiRoot> factory) {
						Minecraft.getInstance().setScreen(new Mc261ScreenHost(defaultTitle(), factory));
					}

					@Override
					public void openKeepHud(Supplier<UiRoot> factory) {
						Minecraft.getInstance().setScreen(new Mc261ScreenHost(defaultTitle(), factory, true));
					}
				},
				() -> {
					var mc = Minecraft.getInstance();
					if (mc.screen instanceof Mc261ScreenHost) {
						mc.setScreen(null);
					}
				},
				new Mc261HudHost(),
				HIGHLIGHTER);
	}

	private static Component defaultTitle() {
		return Component.translatable("ui.wh.title");
	}

	static long tickCounter() {
		return tickCounter;
	}

	/** 打开带自定义标题的屏（demo/业务屏入口）。 */
	public static void openScreen(Component title, Supplier<UiRoot> factory) {
		Minecraft.getInstance().setScreen(new Mc261ScreenHost(title, factory));
	}
}

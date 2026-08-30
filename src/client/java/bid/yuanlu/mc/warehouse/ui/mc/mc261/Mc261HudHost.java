package bid.yuanlu.mc.warehouse.ui.mc.mc261;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/**
 * HUD 挂载点（UI-PDD §4.2/§6）：Fabric HudElementRegistry.addLast 包装。
 * HUD 无输入；根缓存 + reset 通道（配置变化后下一帧重建，UI-PDD §3.7）。
 */
final class Mc261HudHost implements UiPlatform.HudRegistrar, UiPlatform.Resettable {

	private static final String NAMESPACE = "yuanlu-warehouse";

	@Nullable
	private static Mc261HudHost instance;

	private final Map<String, Supplier<UiRoot>> factories = new HashMap<>();
	private final Map<String, @Nullable UiRoot> roots = new HashMap<>();

	Mc261HudHost() {
		if (instance == null) {
			instance = this;
		}
	}

	@Override
	public void register(String id, Supplier<UiRoot> factory) {
		factories.put(id, factory);
		roots.put(id, null);
		HudElementRegistry.addLast(Identifier.parse(NAMESPACE + ":" + id), this::extract);
	}

	@Override
	public void reset(String id) {
		roots.put(id, null);
	}

	private void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		// 宿主屏打开期间不在 HUD 层渲染：非 passthrough 屏隐藏 HUD（现状语义），
		// passthrough 屏（HUD 设置）改由 screen 在自身内容之上代渲染——否则 HUD
		// 永远在更早 stratum，被设置屏面板盖住（GameRenderer.extractGui 顺序）
		if (mc.screen instanceof Mc261ScreenHost) {
			return;
		}
		extractRoots(graphics, delta.getGameTimeDeltaPartialTick(true));
	}

	/** passthrough 屏在自身内容提取后调用：HUD 绘制在屏幕面板之上，设置期间永远可见可抓。 */
	static void extractOverlay(GuiGraphicsExtractor graphics, float partialTick) {
		var mc = Minecraft.getInstance();
		if (instance == null || mc.player == null || mc.options.hideGui) {
			return;
		}
		instance.extractRoots(graphics, partialTick);
	}

	private void extractRoots(GuiGraphicsExtractor graphics, float partialTick) {
		var draw = new Mc261Draw(graphics, partialTick);
		int w = draw.screenWidth();
		int h = draw.screenHeight();
		for (var e : roots.entrySet()) {
			var factory = factories.get(e.getKey());
			if (factory == null) {
				continue;
			}
			var root = e.getValue();
			if (root == null) {
				root = factory.get();
				roots.put(e.getKey(), root);
			}
			root.update(draw, w, h, -1, -1);
			root.extract(draw);
		}
	}
}

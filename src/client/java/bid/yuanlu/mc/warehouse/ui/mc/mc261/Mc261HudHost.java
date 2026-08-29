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

	private final Map<String, Supplier<UiRoot>> factories = new HashMap<>();
	private final Map<String, @Nullable UiRoot> roots = new HashMap<>();

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
		// HUD 设置屏（passthrough）期间保持渲染，供实时预览/拖拽定位
		if (mc.screen instanceof Mc261ScreenHost h && !h.hudPassthrough()) {
			return;
		}
		var draw = new Mc261Draw(graphics, delta.getGameTimeDeltaPartialTick(true));
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

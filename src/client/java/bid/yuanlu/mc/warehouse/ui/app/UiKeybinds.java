package bid.yuanlu.mc.warehouse.ui.app;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.mark.MarkMode;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.ui.app.screen.HudSettingsScreens;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * UI 快捷键（UI-PDD §8）：vanilla KeyMapping + client tick 轮询（声明式表）。
 * 除打开主 UI 外默认未绑定，避免与玩家键位冲突。
 * 注意：expand（按住+滚轮 grab 语义）需滚轮拦截，留 M2/M3。
 */
public final class UiKeybinds {

	private static final Logger LOG = LogUtils.getLogger();

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.parse("yuanlu-warehouse:general"));

	private static KeyMapping open;
	private static KeyMapping pos1;
	private static KeyMapping pos2;
	private static KeyMapping showSelection;
	private static KeyMapping clearSelection;
	private static KeyMapping markToggle;
	private static KeyMapping engineToggle;

	private UiKeybinds() {
	}

	public static void register() {
		open = bind("open", InputConstants.KEY_K);
		pos1 = bind("select.pos1", -1);
		pos2 = bind("select.pos2", -1);
		showSelection = bind("select.show", -1);
		clearSelection = bind("select.clear", -1);
		markToggle = bind("mark.toggle", -1);
		engineToggle = bind("engine.toggle", -1);
		ClientTickEvents.END_CLIENT_TICK.register(UiKeybinds::poll);
	}

	private static KeyMapping bind(String id, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.wh." + id, InputConstants.Type.KEYSYM, defaultKey, CATEGORY));
	}

	private static void poll(Minecraft mc) {
		while (open.consumeClick()) {
			UiPlatform.openScreen(HudSettingsScreens::create); // M3 换为仓库管理主屏
		}
		while (pos1.consumeClick()) {
			setCorner(mc, true);
		}
		while (pos2.consumeClick()) {
			setCorner(mc, false);
		}
		while (showSelection.consumeClick()) {
			bid.yuanlu.mc.warehouse.ui.app.highlight.HighlightRenderer.get().toggleSelectionVisible();
		}
		while (clearSelection.consumeClick()) {
			SelectionState.get().clear();
		}
		while (markToggle.consumeClick()) {
			toggleMark();
		}
		while (engineToggle.consumeClick()) {
			toggleEngine();
		}
	}

	private static void setCorner(Minecraft mc, boolean first) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		BlockPos p;
		if (mc.options.keyShift.isDown()) {
			// shift = 准星方块（等价 --look）
			p = mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos() != null
					? hit.getBlockPos()
					: mc.player.blockPosition();
		} else {
			p = mc.player.blockPosition();
		}
		WorldDim dim = currentDim(mc);
		var pos = new WorldDimPos(dim.worldId(), dim.dimId(), p.getX(), p.getY(), p.getZ());
		if (first) {
			SelectionState.get().set1(pos);
		} else {
			SelectionState.get().set2(pos);
		}
	}

	private static WorldDim currentDim(Minecraft mc) {
		String worldId = WorldSessionTracker.get() == null ? null : WorldSessionTracker.get().currentWorldId();
		String dimId = mc.level == null ? null : mc.level.dimension().identifier().toString();
		return new WorldDim(worldId, dimId == null ? "unknown" : dimId);
	}

	private static void toggleMark() {
		// 无参标记模式：IOType INPUT + 无规则（完整交互在 M3 主屏/选区面板）
		MarkMode.get().toggle(bid.yuanlu.mc.warehouse.api.container.IOType.INPUT, null, null);
	}

	private static void toggleEngine() {
		var engine = bid.yuanlu.mc.warehouse.core.WarehouseServices.transportEngine();
		if (engine == null) {
			return;
		}
		if (engine.isRunning()) {
			engine.stop();
		} else {
			engine.start();
		}
	}
}

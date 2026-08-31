package bid.yuanlu.mc.warehouse.ui.app;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
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
 * expand（UI-PDD §8 v0.3 修订）：按压 = 角 2 沿视线主轴扩展 1 格，潜行 = 反向收缩；
 * 精确多格扩展在选区面板完成（零新 mixin）。
 */
public final class UiKeybinds {

	private static final Logger LOG = LogUtils.getLogger();

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.parse("yuanlu-warehouse:general"));

	private static KeyMapping open;
	private static KeyMapping pos1;
	private static KeyMapping pos2;
	private static KeyMapping expand;
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
		expand = bind("select.expand", -1);
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
			bid.yuanlu.mc.warehouse.ui.app.screen.WarehouseScreens.open();
		}
		while (pos1.consumeClick()) {
			setCorner(mc, true);
		}
		while (pos2.consumeClick()) {
			setCorner(mc, false);
		}
		while (expand.consumeClick()) {
			expandSelection(mc);
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
		if (dim == null) {
			return;
		}
		var pos = new WorldDimPos(dim.worldName(), dim.dimId(), p.getX(), p.getY(), p.getZ());
		if (first) {
			SelectionState.get().set1(pos);
		} else {
			SelectionState.get().set2(pos);
		}
	}

	/** 当前 (serverId, worldName, dimId)；会话未就绪/无维度返回 null（与命令/UI 共用 util）。 */
	private static @Nullable WorldDim currentDim(Minecraft mc) {
		return bid.yuanlu.mc.warehouse.util.ClientSession.currentDim(mc);
	}

	/** expand 快捷键：按压 = 角 2 沿视线主轴扩展 1 格，潜行 = 反向收缩 1 格（等价 /wh select expand）。 */
	private static void expandSelection(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		var look = mc.player.getLookAngle();
		int count = mc.options.keyShift.isDown() ? -1 : 1;
		bid.yuanlu.mc.warehouse.core.selection.SelectionOps.expand(SelectionState.get(),
				bid.yuanlu.mc.warehouse.core.selection.SelectionOps.dominantAxis(look.x, look.y, look.z), count);
	}

	private static void toggleMark() {
		// 使用最近一次配置的标记参数（UI 标记 Modal 配置后沿用）；缺省 INPUT + 无规则
		var last = bid.yuanlu.mc.warehouse.core.mark.MarkMode.get().lastConfigured();
		bid.yuanlu.mc.warehouse.core.mark.MarkMode.get().toggle(
				last != null ? last.type() : bid.yuanlu.mc.warehouse.api.container.IOType.INPUT,
				last != null ? last.ruleId() : null,
				last != null ? last.templateId() : null);
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

package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.core.world.ServerWorldIdHolder;
import bid.yuanlu.mc.warehouse.core.world.WorldNameMapper;
import bid.yuanlu.mc.warehouse.core.world.WorldSession;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.ui.app.widget.ScreenScaffold;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.ScrollElement;
import bid.yuanlu.mc.warehouse.ui.core.element.TextFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import bid.yuanlu.mc.warehouse.util.ClientSession;

/**
 * 世界映射页（等价 /wh world list/info/bind/rename）：会话身份总览 + worldName→worldId
 * 映射（绑定到当前会话 / 手输 worldId / 重命名）+ 服务端报告维度。
 * 写操作直接经 {@link WorldNameMapper}（与命令 WorldGroup 同一调用序列）。
 */
public final class WorldScreens {

	private WorldScreens() {
	}

	public static void open() {
		UiPlatform.openScreen(WorldScreens::create);
	}

	private static void refresh() {
		UiPlatform.openScreen(WorldScreens::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		var scaffold = new ScreenScaffold();
		scaffold.add(ScreenHeader.create(ScreenHeader.Page.WORLD));
		scaffold.add(new LabelElement(Component.translatable("ui.wh.world.title")));

		var scroll = new ScrollElement(6).grow(1);
		scaffold.add(scroll);

		String serverId;
		WorldSession session;
		try {
			var trk = WorldSessionTracker.get();
			serverId = trk.currentServerId();
			session = trk.currentSession();
		} catch (IllegalStateException e) {
			serverId = null;
			session = null;
		}

		if (serverId == null || session == null) {
			scroll.add(new LabelElement(Component.translatable("ui.wh.select.need_world"))
					.color(Theme.active().textMuted()));
			root.add(scaffold);
			return root;
		}

		// 会话身份（等价 /wh world info）
		var info = PanelElement.plain().padding(4).layout(new Column(2));
		info.add(new LabelElement(Component.translatable("ui.wh.world.server", session.serverId())));
		info.add(new LabelElement(Component.translatable("ui.wh.world.worldid", session.worldId())));
		info.add(new LabelElement(Component.translatable("ui.wh.world.active", session.worldName())));
		var dim = ClientSession.currentDim(Minecraft.getInstance());
		if (dim != null) {
			info.add(new LabelElement(Component.translatable("ui.wh.world.dim", dim.dimId()))
					.color(Theme.active().textMuted()));
		}
		scroll.add(info);

		// 名称映射（等价 /wh world list；`*` = 当前激活名）
		scroll.add(new LabelElement(Component.translatable("ui.wh.world.mappings")).padding(2));
		var mapper = WorldNameMapper.get();
		for (var e : mapper.worlds(serverId)) {
			scroll.add(mappingRow(root, serverId, session, e.getKey(), e.getValue(),
					e.getKey().equals(session.worldName())));
		}

		// 服务端报告维度
		var levels = ServerWorldIdHolder.getLevels();
		if (!levels.isEmpty()) {
			scroll.add(new LabelElement(Component.translatable("ui.wh.world.levels")).padding(2));
			for (String level : levels) {
				scroll.add(new LabelElement(Component.literal("· " + level))
						.color(Theme.active().textMuted()).padding(1));
			}
		}

		var actions = PanelElement.plain().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.back"))
				.onClick(ScreenHeader::backToMain));
		scaffold.add(actions);
		root.add(scaffold);
		return root;
	}

	private static PanelElement mappingRow(UiRoot root, String serverId, WorldSession session,
			String name, String worldId, boolean active) {
		var theme = Theme.active();
		var row = PanelElement.plain().padding(2).layout(new Row(4));
		row.add(new LabelElement(Component.translatable(active
				? "ui.wh.world.entry_active" : "ui.wh.world.entry", name, worldId))
				.color(active ? theme.textAccent() : theme.textPrimary()).grow(1));
		if (!active) {
			row.add(new ButtonElement(Component.translatable("ui.wh.world.bind.current"))
					.tooltip(() -> List.of(Component.translatable("ui.wh.world.bind.current.tip")))
					.onClick(() -> {
						try {
							WorldNameMapper.get().bind(serverId, name, session.worldId());
						} catch (IllegalArgumentException ignored) {
							// 非法绑定由 mapper 拒绝（存档校验），界面保持原状
						}
						refresh();
					}));
			row.add(new ButtonElement(Component.translatable("ui.wh.world.bind.custom"))
					.onClick(() -> bindModal(root, serverId, name, session.worldId())));
		}
		row.add(new ButtonElement(Component.translatable("ui.wh.world.rename"))
				.onClick(() -> renameModal(root, serverId, name)));
		return row;
	}

	/** 换绑 Modal（等价 /wh world bind <name> [worldId]；worldId 缺省=当前会话）。 */
	private static void bindModal(UiRoot root, String serverId, String name, String currentWorldId) {
		var overlay = bid.yuanlu.mc.warehouse.ui.app.widget.Modal.overlay(root);
		var dialog = bid.yuanlu.mc.warehouse.ui.app.widget.Modal.centeredDialog(root);
		dialog.padding(10).layout(new Column(6)).id("world-bind-modal");
		dialog.add(new LabelElement(Component.translatable("ui.wh.world.bind.modal", name)));
		var field = new TextFieldElement(currentWorldId).maxLength(128).size(260, -1);
		dialog.add(field);
		var err = new LabelElement(Component.empty()).color(Theme.active().danger()).padding(1);
		dialog.add(err);
		var actions = PanelElement.plain().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.confirm"))
				.semantic(ButtonElement.Semantic.SUCCESS)
				.onClick(() -> {
					String worldId = field.text().trim();
					if (worldId.isEmpty()) {
						err.text(Component.translatable("ui.wh.world.bind.no_worldid"));
						return;
					}
					try {
						WorldNameMapper.get().bind(serverId, name, worldId);
					} catch (IllegalArgumentException e) {
						err.text(Component.literal(String.valueOf(e.getMessage())));
						return;
					}
					overlay.removeFromParent();
					refresh();
				}));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.cancel"))
				.onClick(overlay::removeFromParent));
		dialog.add(actions);
		overlay.add(dialog);
	}

	/** 重命名 Modal（等价 /wh world rename <from> <to>）。 */
	private static void renameModal(UiRoot root, String serverId, String from) {
		var overlay = bid.yuanlu.mc.warehouse.ui.app.widget.Modal.overlay(root);
		var dialog = bid.yuanlu.mc.warehouse.ui.app.widget.Modal.centeredDialog(root);
		dialog.padding(10).layout(new Column(6)).id("world-rename-modal");
		dialog.add(new LabelElement(Component.translatable("ui.wh.world.rename.modal", from)));
		var field = new TextFieldElement("").maxLength(64).size(260, -1);
		dialog.add(field);
		var err = new LabelElement(Component.empty()).color(Theme.active().danger()).padding(1);
		dialog.add(err);
		var actions = PanelElement.plain().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.confirm"))
				.semantic(ButtonElement.Semantic.SUCCESS)
				.onClick(() -> {
					String to = field.text().trim();
					if (to.isEmpty() || to.equals(from)) {
						return;
					}
					try {
						WorldNameMapper.get().rename(serverId, from, to);
					} catch (IllegalArgumentException e) {
						err.text(Component.literal(String.valueOf(e.getMessage())));
						return;
					}
					overlay.removeFromParent();
					refresh();
				}));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.cancel"))
				.onClick(overlay::removeFromParent));
		dialog.add(actions);
		overlay.add(dialog);
	}
}

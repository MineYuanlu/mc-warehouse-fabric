package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.core.selection.SelectionOps;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.app.presenter.HudPresenter;
import bid.yuanlu.mc.warehouse.ui.app.widget.CycleSelector;
import bid.yuanlu.mc.warehouse.ui.app.widget.ScreenScaffold;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.NumberFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.ScrollElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import bid.yuanlu.mc.warehouse.util.ClientSession;
import bid.yuanlu.mc.warehouse.util.RelativeCoords;

/**
 * 选区面板（UI-PDD §5.2 v0.3 全功能版）：pos1/pos2 展示与三种设定形态（脚下/准星/
 * 坐标输入，~ 相对语法与命令一致）、expand 次数+六方向、选区信息（world/dim/体积/
 * 选区内容器数）、批量操作（set-type / set-rule / set-cache）。
 * inBox 与 expand 走 {@link SelectionOps} 共享实现（与命令层零漂移）。
 */
public final class SelectionPanelScreens {

	private SelectionPanelScreens() {
	}

	public static void open() {
		UiPlatform.openScreen(SelectionPanelScreens::create);
	}

	private static void refresh() {
		UiPlatform.openScreen(SelectionPanelScreens::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		var scaffold = new ScreenScaffold();
		scaffold.add(ScreenHeader.create(ScreenHeader.Page.SELECTION));
		scaffold.add(new LabelElement(Component.translatable("ui.wh.select.title")));

		SelectionState sel = SelectionState.get();

		var scroll = new ScrollElement(6).grow(1);
		scaffold.add(scroll);
		scroll.add(infoPanel(sel));

		var cornerOps = PanelElement.plain().padding(2).layout(new Column(2));
		cornerOps.add(cornerRows(true));
		cornerOps.add(cornerRows(false));
		scroll.add(cornerOps);
		scroll.add(expandRow());

		// 批量操作（激活仓库存在且有选区时可用）
		Warehouse wh = activeWarehouse();
		boolean ready = wh != null && sel.hasBox();
		scroll.add(batchRows(wh, sel, ready));

		var actions = PanelElement.plain().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.select.clear"))
				.onClick(() -> {
					SelectionState.get().clear();
					refresh();
				}));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.back"))
				.onClick(ScreenHeader::backToMain));
		scaffold.add(actions);
		root.add(scaffold);
		return root;
	}

	// ---- 选区信息 ----

	private static PanelElement infoPanel(SelectionState sel) {
		var theme = Theme.active();
		var info = PanelElement.plain().padding(4).layout(new Column(2));
		if (sel.hasBox()) {
			var p1 = sel.pos1();
			var p2 = sel.pos2();
			int w = Math.abs(p1.x() - p2.x()) + 1;
			int h = Math.abs(p1.y() - p2.y()) + 1;
			int d = Math.abs(p1.z() - p2.z()) + 1;
			info.add(new LabelElement(Component.translatable("ui.wh.hud.selection.box",
					p1.x(), p1.y(), p1.z(), p2.x(), p2.y(), p2.z(), w, h, d)));
			info.add(new LabelElement(Component.translatable("ui.wh.select.world",
					p1.world(), p1.dim())).color(theme.textMuted()));
			Warehouse wh = activeWarehouse();
			if (wh != null) {
				info.add(new LabelElement(Component.translatable("ui.wh.select.in_box",
						SelectionOps.countInBox(sel, wh))).color(theme.textMuted()));
			}
		} else if (sel.pos1() != null || sel.pos2() != null) {
			var p = sel.pos1() != null ? sel.pos1() : sel.pos2();
			info.add(new LabelElement(Component.translatable("ui.wh.hud.selection.corner",
					p.x(), p.y(), p.z())));
			info.add(new LabelElement(Component.translatable("ui.wh.select.incomplete"))
					.color(theme.textMuted()));
		} else {
			info.add(new LabelElement(Component.translatable("ui.wh.select.empty"))
					.color(theme.textMuted()));
		}
		return info;
	}

	// ---- 角点设定：脚下 / 准星 / 坐标输入（~ 相对语法与命令一致）----

	private static PanelElement cornerRows(boolean first) {
		var theme = Theme.active();
		var box = PanelElement.plain().padding(2).layout(new Column(2));
		var sel = SelectionState.get();
		var current = first ? sel.pos1() : sel.pos2();

		var display = PanelElement.plain().padding(1).layout(new Row(4));
		display.add(new LabelElement(Component.translatable(
				first ? "ui.wh.select.corner1" : "ui.wh.select.corner2")).padding(1));
		if (current != null) {
			display.add(new LabelElement(Component.translatable("ui.wh.hud.selection.corner",
					current.x(), current.y(), current.z())).color(theme.textMuted()).grow(1));
		} else {
			display.add(new LabelElement(Component.translatable("ui.wh.select.unset"))
					.color(theme.textMuted()).grow(1));
		}
		display.add(new ButtonElement(Component.translatable("ui.wh.select.feet"))
				.onClick(() -> setCornerAbs(first, feetPos())));
		display.add(new ButtonElement(Component.translatable("ui.wh.select.look"))
				.onClick(() -> setCornerAbs(first, lookPos())));
		box.add(display);

		var coords = PanelElement.plain().padding(1).layout(new Row(4));
		var fx = new NumberFieldElement(-30_000_000, 30_000_000, current != null ? current.x() : 0, true);
		var fy = new NumberFieldElement(-30_000_000, 30_000_000, current != null ? current.y() : 0, true);
		var fz = new NumberFieldElement(-30_000_000, 30_000_000, current != null ? current.z() : 0, true);
		fx.size(64, -1);
		fy.size(64, -1);
		fz.size(64, -1);
		coords.add(fx);
		coords.add(fy);
		coords.add(fz);
		coords.add(new ButtonElement(Component.translatable("ui.wh.select.set"))
				.onClick(() -> setCornerFromFields(first, fx, fy, fz)));
		box.add(coords);
		return box;
	}

	private static BlockPos feetPos() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
	}

	private static BlockPos lookPos() {
		return ClientSession.lookOrFeet(Minecraft.getInstance());
	}

	private static void setCornerAbs(boolean first, BlockPos p) {
		var dim = ClientSession.currentDim(Minecraft.getInstance());
		if (dim == null) {
			HudPresenter.get().selectionLine.set(Component.translatable("ui.wh.select.need_world"));
			return;
		}
		var pos = SelectionOps.cornerAt(dim, p);
		if (first) {
			SelectionState.get().set1(pos);
		} else {
			SelectionState.get().set2(pos);
		}
		refresh();
	}

	/** 坐标字段 → 角点：~ 相对语法按玩家位置解析（与命令 coord 语义一致，共用 util）。 */
	private static void setCornerFromFields(boolean first, NumberFieldElement fx, NumberFieldElement fy,
			NumberFieldElement fz) {
		Minecraft mc = Minecraft.getInstance();
		var dim = ClientSession.currentDim(mc);
		if (dim == null) {
			HudPresenter.get().selectionLine.set(Component.translatable("ui.wh.select.need_world"));
			return;
		}
		var origin = mc.player != null ? mc.player.position() : net.minecraft.world.phys.Vec3.ZERO;
		int x;
		int y;
		int z;
		try {
			x = RelativeCoords.parse(fx.text(), origin.x);
			y = RelativeCoords.parse(fy.text(), origin.y);
			z = RelativeCoords.parse(fz.text(), origin.z);
		} catch (NumberFormatException e) {
			HudPresenter.get().selectionLine.set(Component.translatable("ui.wh.select.bad_coords"));
			return;
		}
		if (first) {
			SelectionState.get().set1(SelectionOps.cornerAt(dim, new BlockPos(x, y, z)));
		} else {
			SelectionState.get().set2(SelectionOps.cornerAt(dim, new BlockPos(x, y, z)));
		}
		refresh();
	}

	// ---- expand：次数 + 六方向（等价 /wh select expand <count> <dir>）----

	private static PanelElement expandRow() {
		var row = PanelElement.plain().padding(2).layout(new Row(4));
		row.add(new LabelElement(Component.translatable("ui.wh.select.expand")).padding(1));
		var count = new NumberFieldElement(-256, 256, 1);
		count.size(56, -1);
		row.add(count);
		for (Direction dir : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST,
				Direction.WEST, Direction.UP, Direction.DOWN)) {
			row.add(new ButtonElement(Component.literal(dir.name().toLowerCase(java.util.Locale.ROOT)))
					.onClick(() -> {
						if (SelectionOps.expand(SelectionState.get(), dir, count.intValue())) {
							refresh();
						} else {
							HudPresenter.get().selectionLine.set(
									Component.translatable("ui.wh.select.no_corners"));
						}
					}));
		}
		return row;
	}

	// ---- 批量操作 ----

	private static PanelElement batchRows(@Nullable Warehouse wh, SelectionState sel, boolean ready) {
		var body = PanelElement.plain().padding(2).layout(new Column(4));
		if (!ready) {
			body.add(new LabelElement(Component.translatable("ui.wh.select.need_active_box"))
					.color(Theme.active().textMuted()));
			return body;
		}

		// 批量改 IOType
		var typeRow = PanelElement.plain().padding(2).layout(new Row(4));
		List<IOType> types = List.of(IOType.values());
		CycleSelector<IOType> typeSel = new CycleSelector<>(types,
				t -> Component.translatable("ui.wh.main.container.type", t.name()), 0, t -> {
				});
		typeRow.add(typeSel);
		typeRow.add(applyButton(() -> {
			int changed = apply(wh, c -> {
				c.ioType = typeSel.current();
				c.ruleMode = null; // 等价命令 set-type：mode 回落默认
			});
			return Component.translatable("ui.wh.select.applied", changed);
		}));
		body.add(typeRow);

		// 批量绑规则
		List<String> ruleIds = new ArrayList<>(wh.rules.keySet());
		if (!ruleIds.isEmpty()) {
			var ruleRow = PanelElement.plain().padding(2).layout(new Row(4));
			CycleSelector<String> ruleSel = new CycleSelector<>(ruleIds,
					id -> Component.translatable("ui.wh.main.container.rule", id), 0, id -> {
					});
			ruleRow.add(ruleSel);
			ruleRow.add(applyButton(() -> {
				int changed = apply(wh, c -> {
					if (!c.rules.contains(ruleSel.current())) {
						c.rules.add(ruleSel.current());
					}
				});
				return Component.translatable("ui.wh.select.applied", changed);
			}));
			ruleRow.add(applyButton(() -> {
				int changed = apply(wh, c -> c.rules.remove(ruleSel.current()));
				return Component.translatable("ui.wh.select.applied", changed);
			}));
			body.add(ruleRow);
		}

		// 批量改缓存类型
		var cacheRow = PanelElement.plain().padding(2).layout(new Row(4));
		List<CacheType> caches = List.of(CacheType.NONE, CacheType.MEMORY, CacheType.DISK);
		CycleSelector<CacheType> cacheSel = new CycleSelector<>(caches,
				ct -> Component.translatable("ui.wh.main.container.cache", ct.name()), 0, ct -> {
				});
		cacheRow.add(cacheSel);
		cacheRow.add(applyButton(() -> {
			int changed = apply(wh, c -> c.cacheType = cacheSel.current());
			return Component.translatable("ui.wh.select.applied", changed);
		}));
		body.add(cacheRow);
		return body;
	}

	private static ButtonElement applyButton(java.util.function.Supplier<Component> action) {
		return new ButtonElement(Component.translatable("ui.wh.select.apply"))
				.onClick(() -> HudPresenter.get().selectionLine.set(action.get()));
	}

	private interface ContainerMutator {
		void apply(ContainerInfo c);
	}

	/** 选区内批量应用（等价 /wh select set-*），返回改动容器数。 */
	private static int apply(Warehouse wh, ContainerMutator mutator) {
		SelectionState sel = SelectionState.get();
		int changed = 0;
		for (ContainerInfo c : wh.containers) {
			if (SelectionOps.inBox(sel, wh, c)) {
				mutator.apply(c);
				changed++;
			}
		}
		if (changed > 0) {
			WarehouseManagerImpl.get().save(wh);
			bid.yuanlu.mc.warehouse.core.highlight.HighlightManager.get().refresh();
		}
		return changed;
	}

	private static @Nullable Warehouse activeWarehouse() {
		try {
			return WarehouseManagerImpl.get().active();
		} catch (Exception e) {
			return null;
		}
	}
}

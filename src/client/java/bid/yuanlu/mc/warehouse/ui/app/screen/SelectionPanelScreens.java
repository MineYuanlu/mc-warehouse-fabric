package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.app.presenter.HudPresenter;
import bid.yuanlu.mc.warehouse.ui.app.widget.CycleSelector;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * 选区面板（UI-PDD §5.2）：显示 pos1/pos2/尺寸 + 批量操作
 * （set-type / set-rule / set-cache，互斥按钮组 + 循环选择 + 应用确认）。
 * 选区状态与命令层共享（core/selection）。
 */
public final class SelectionPanelScreens {

	private SelectionPanelScreens() {
	}

	public static void open() {
		UiPlatform.openScreen(SelectionPanelScreens::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		var panel = new PanelElement().padding(10).layout(new Column(6)).size(360, -1);
		panel.add(new LabelElement(Component.translatable("ui.wh.select.title")));

		SelectionState sel = SelectionState.get();
		WorldDimPos p1 = sel.pos1();
		WorldDimPos p2 = sel.pos2();

		// 选区信息
		var info = new PanelElement().padding(4).layout(new Column(2));
		if (p1 == null && p2 == null) {
			info.add(new LabelElement(Component.translatable("ui.wh.select.empty"))
					.color(Theme.active().textMuted()));
		} else if (sel.hasBox()) {
			int w = Math.abs(p1.x() - p2.x()) + 1;
			int h = Math.abs(p1.y() - p2.y()) + 1;
			int d = Math.abs(p1.z() - p2.z()) + 1;
			info.add(new LabelElement(Component.translatable("ui.wh.hud.selection.box",
					p1.x(), p1.y(), p1.z(), p2.x(), p2.y(), p2.z(), w, h, d)));
		} else {
			WorldDimPos p = p1 != null ? p1 : p2;
			info.add(new LabelElement(Component.translatable("ui.wh.hud.selection.corner",
					p.x(), p.y(), p.z())));
		}
		panel.add(info);

		// 批量操作（激活仓库存在且有选区时可用）
		Warehouse wh = activeWarehouse();
		boolean ready = wh != null && sel.hasBox();
		panel.add(batchRows(root, wh, sel, ready));

		var actions = new PanelElement().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.select.clear"))
				.onClick(() -> {
					SelectionState.get().clear();
					UiPlatform.openScreen(SelectionPanelScreens::create); // 重建刷新
				}));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.cancel"))
				.onClick(UiPlatform::closeScreen));
		panel.add(actions);
		root.add(panel);
		return root;
	}

	private static PanelElement batchRows(UiRoot root, @Nullable Warehouse wh,
			SelectionState sel, boolean ready) {
		var body = new PanelElement().padding(2).layout(new Column(4));
		if (!ready) {
			body.add(new LabelElement(Component.translatable("ui.wh.select.need_active_box"))
					.color(Theme.active().textMuted()));
			return body;
		}

		// 批量改 IOType
		var typeRow = new PanelElement().padding(2).layout(new Row(4));
		List<IOType> types = List.of(IOType.values());
		CycleSelector<IOType> typeSel = new CycleSelector<>(types,
				t -> Component.translatable("ui.wh.main.container.type", t.name()), 0, t -> {
				});
		typeRow.add(typeSel);
		typeRow.add(applyButton(root, () -> {
			int changed = apply(wh, c -> c.ioType = typeSel.current());
			return Component.translatable("ui.wh.select.applied", changed);
		}));
		body.add(typeRow);

		// 批量绑规则
		List<String> ruleIds = new ArrayList<>(wh.rules.keySet());
		if (!ruleIds.isEmpty()) {
			var ruleRow = new PanelElement().padding(2).layout(new Row(4));
			CycleSelector<String> ruleSel = new CycleSelector<>(ruleIds,
					id -> Component.translatable("ui.wh.main.container.rule", id), 0, id -> {
					});
			ruleRow.add(ruleSel);
			ruleRow.add(applyButton(root, () -> {
				int changed = apply(wh, c -> {
					if (!c.rules.contains(ruleSel.current())) {
						c.rules.add(ruleSel.current());
					}
				});
				return Component.translatable("ui.wh.select.applied", changed);
			}));
			ruleRow.add(applyButton(root, () -> {
				int changed = apply(wh, c -> c.rules.remove(ruleSel.current()));
				return Component.translatable("ui.wh.select.applied", changed);
			}));
			body.add(ruleRow);
		}

		// 批量改缓存类型
		var cacheRow = new PanelElement().padding(2).layout(new Row(4));
		List<CacheType> caches = List.of(CacheType.NONE, CacheType.MEMORY, CacheType.DISK);
		CycleSelector<CacheType> cacheSel = new CycleSelector<>(caches,
				ct -> Component.translatable("ui.wh.main.container.cache", ct.name()), 0, ct -> {
				});
		cacheRow.add(cacheSel);
		cacheRow.add(applyButton(root, () -> {
			int changed = apply(wh, c -> c.cacheType = cacheSel.current());
			return Component.translatable("ui.wh.select.applied", changed);
		}));
		body.add(cacheRow);
		return body;
	}

	private static ButtonElement applyButton(UiRoot root, java.util.function.Supplier<Component> action) {
		return new ButtonElement(Component.translatable("ui.wh.select.apply"))
				.onClick(() -> {
					Component feedback = action.get();
					HudPresenter.get().selectionLine.set(feedback); // 反馈进 HUD 选区行（屏内消息条的简化实现）
				});
	}

	private interface ContainerMutator {
		void apply(ContainerInfo c);
	}

	/** 选区内批量应用（等价 /wh select set-*），返回改动容器数。 */
	private static int apply(Warehouse wh, ContainerMutator mutator) {
		SelectionState sel = SelectionState.get();
		int changed = 0;
		for (ContainerInfo c : wh.containers) {
			if (inBox(wh, sel, c)) {
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

	/** 选区内判定（等价 WhCommands.inBox，含 world 缺省补全）。 */
	private static boolean inBox(Warehouse wh, SelectionState sel, ContainerInfo c) {
		if (!sel.hasBox() || c.pos.isEmpty()) {
			return false;
		}
		for (WorldDimPos p : c.pos) {
			WorldDim dim = new WorldDim(p.world(), p.dim());
			var anchor = wh.anchorOf(dim);
			if (anchor == null) {
				continue;
			}
			var abs = p.plus(anchor).toBlockPos();
			if (sel.contains(abs.getX(), abs.getY(), abs.getZ())) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Warehouse activeWarehouse() {
		try {
			return WarehouseManagerImpl.get().active();
		} catch (Exception e) {
			return null;
		}
	}
}

package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.WarehouseServices;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.core.transfer.TransferOverlay;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.app.presenter.HudPresenter;
import bid.yuanlu.mc.warehouse.ui.app.widget.CycleSelector;
import bid.yuanlu.mc.warehouse.ui.app.widget.Modal;
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

/**
 * 仓库管理主屏（UI-PDD §5.1）：页签 A 仓库列表+详情侧栏，页签 B 引擎控制+跨仓库搬运。
 * 全部写操作走 api/（WarehouseManager/TransportEngine/TransferOverlay），与 /wh 同一调用序列。
 * 数据变化即整树重建（构建时求解，§3.7）。
 */
public final class WarehouseScreens {

	private WarehouseScreens() {
	}

	public static void open() {
		open(0);
	}

	/** 打开主屏并定位页签（0=仓库 1=引擎），供 ScreenHeader 导航。 */
	public static void open(int tab) {
		UiPlatform.openScreen(() -> create(tab));
	}

	public static UiRoot create() {
		return create(0);
	}

	public static UiRoot create(int initialTab) {
		var state = new State(initialTab);
		return state.build();
	}

	private static final class State {
		@Nullable
		String selectedId;
		int tab;

		State(int tab) {
			this.tab = tab;
		}

		UiRoot build() {
			var root = new UiRoot();
			root.add(page(root));
			return root;
		}

		// ---- 头部页签（共享导航，UI-PDD §5.1 UX 决策）----

		private PanelElement header() {
			return ScreenHeader.create(tab == 0 ? ScreenHeader.Page.WAREHOUSE : ScreenHeader.Page.ENGINE);
		}

		/** 全屏脚手架：页头固定 + 页签内容 grow 撑满（flex 布局，UI-PDD §5.1）。 */
		private PanelElement page(UiRoot root) {
			var scaffold = new ScreenScaffold();
			scaffold.add(header());
			scaffold.add(tab == 0 ? warehousesTab(root) : engineTab(root));
			return scaffold;
		}

		private void refresh(UiRoot root) {
			Modal.close();
			root.clearChildren();
			root.add(page(root));
		}

		// ---- 页签 A：仓库 ----

		private PanelElement warehousesTab(UiRoot root) {
			var mgr = WarehouseManagerImpl.get();
			var body = PanelElement.plain().padding(2).layout(new Column(6)).grow(1);

			// 仓库列表 + 详情侧栏（各占一半剩余宽度，纵向撑满，内容超出即滚动）
			var columns = PanelElement.plain().padding(2).layout(new Row(6)).grow(1);
			columns.add(warehouseList(root, mgr));
			columns.add(detailPanel(root, mgr));
			body.add(columns);

			// 新建行（固定在底部）
			var createRow = PanelElement.plain().padding(2).layout(new Row(4));
			var nameField = new TextFieldElement("").maxLength(24);
			nameField.size(160, -1);
			createRow.add(nameField);
			createRow.add(new ButtonElement(Component.translatable("ui.wh.main.create"))
					.onClick(() -> {
						String name = nameField.text().trim();
						if (name.isEmpty()) {
							return;
						}
						try {
							mgr.create(name);
							selectedId = name;
							refresh(root);
						} catch (IllegalArgumentException e) {
							Modal.confirm(root, Component.translatable("ui.wh.main.create"),
									Component.translatable("ui.wh.main.create.duplicate", name), () -> {
									});
						}
					}));
			body.add(createRow);
			return body;
		}

		private PanelElement warehouseList(UiRoot root, WarehouseManagerImpl mgr) {
			// 权重撑满剩余宽度（声明 170 为最小），条目超出视口高度即滚动
			var panel = new PanelElement().padding(4).id("wh-list").grow(1).size(170, -1);
			panel.layout(new Column(2));
			var scroll = new ScrollElement(2).grow(1);
			panel.add(scroll);
			var active = mgr.active();
			for (Warehouse wh : mgr.list()) {
				boolean isActive = active != null && active.id.equals(wh.id);
				boolean isSelected = wh.id.equals(selectedId);
				var label = Component.translatable("ui.wh.main.entry",
						(isActive ? "● " : "") + wh.id, wh.containers.size(), wh.rules.size());
				var row = new LabelElement(label).padding(2);
				row.onClick(() -> {
					selectedId = wh.id;
					refresh(root); // 选中高亮 + 右侧详情即时刷新
				});
				// 双击激活
				row.on(bid.yuanlu.mc.warehouse.ui.core.event.UiEvent.Type.DOUBLE_CLICK,
						e -> {
							mgr.activate(wh.id);
							refresh(root); // 激活圆点即时刷新
						});
				if (isSelected) {
					row.color(Theme.active().textAccent());
				} else {
					row.color(Theme.active().textPrimary());
				}
				scroll.add(row);
			}
			if (mgr.list().isEmpty()) {
				scroll.add(new LabelElement(Component.translatable("ui.wh.main.empty"))
						.color(Theme.active().textMuted()));
			}
			return panel;
		}

		private PanelElement detailPanel(UiRoot root, WarehouseManagerImpl mgr) {
			// 权重撑满剩余宽度（声明 200 为最小），容器明细超出即滚动
			var detail = new PanelElement().padding(4).id("wh-detail").grow(1).size(200, -1);
			detail.layout(new Column(2));
			var scroll = new ScrollElement(2).grow(1);
			detail.add(scroll);
			Warehouse wh = selectedId == null ? null : mgr.get(selectedId);
			if (wh == null) {
				scroll.add(new LabelElement(Component.translatable("ui.wh.main.no_selection"))
						.color(Theme.active().textMuted()));
				return detail;
			}
			scroll.add(new LabelElement(Component.literal(wh.id)).color(Theme.active().textAccent()));
			var ops = PanelElement.plain().padding(2).layout(new Row(4));
			var active = mgr.active();
			var useBtn = new ButtonElement(Component.translatable("ui.wh.main.use"))
					.semantic(ButtonElement.Semantic.SUCCESS)
					.onClick(() -> {
						mgr.activate(wh.id);
						refresh(root);
					});
			if (active != null && active.id.equals(wh.id)) {
				useBtn.semantic(ButtonElement.Semantic.ACCENT);
			}
			ops.add(useBtn);
			ops.add(new ButtonElement(Component.translatable("ui.wh.main.remove"))
					.semantic(ButtonElement.Semantic.DANGER)
					.onClick(() -> Modal.confirm(root,
							Component.translatable("ui.wh.main.remove"),
							Component.translatable("ui.wh.main.remove.confirm", wh.id),
							() -> {
								mgr.delete(wh.id);
								if (wh.id.equals(selectedId)) {
									selectedId = null;
								}
								refresh(root);
							})));
			scroll.add(ops);
			scroll.add(new ButtonElement(Component.translatable("ui.wh.main.reload"))
					.onClick(() -> {
						mgr.reload();
						refresh(root);
					}));

			// 容器明细行（点击 → 编辑浮层）
			for (ContainerInfo c : wh.containers) {
				var line = new LabelElement(Component.translatable("ui.wh.main.container",
						c.ioType.name(), formatPos(c.canonicalPos()), c.rules.size(), c.cacheType.name()))
						.padding(1);
				line.color(Theme.active().textPrimary());
				line.onClick(() -> containerModal(root, mgr, wh, c));
				scroll.add(line);
			}
			return detail;
		}

		// ---- 容器编辑浮层（等价 container type/mode/rules/memory）----

		private void containerModal(UiRoot root, WarehouseManagerImpl mgr, Warehouse wh, ContainerInfo c) {
			// 深拷贝工作副本：确认才写回（取消不落盘）
			ContainerInfo work = new ContainerInfo(c.ioType);
			work.pos.addAll(c.pos);
			work.ruleMode = c.effectiveRuleMode();
			work.rules.addAll(c.rules);
			work.cacheType = c.cacheType;
			work.priority = c.priority;
			work.label = c.label;

			var theme = Theme.active();
			var overlay = Modal.overlay(root);
			var dialog = Modal.centeredDialog(root);
			dialog.padding(10).layout(new Column(6)).size(300, -1).id("container-edit");
			dialog.add(new LabelElement(Component.translatable("ui.wh.main.container.title",
					formatPos(c.canonicalPos()))).color(theme.textAccent()));

			// IOType 循环选择
			List<IOType> types = List.of(IOType.values());
			dialog.add(new CycleSelector<>(types, t -> Component.translatable("ui.wh.main.container.type", t.name()),
					types.indexOf(work.ioType), t -> work.ioType = t));
			// 规则模式
			List<RuleMode> modes = List.of(RuleMode.WHITELIST, RuleMode.BLACKLIST);
			dialog.add(new CycleSelector<>(modes,
					m -> Component.translatable("ui.wh.main.container.mode", m.name()),
					modes.indexOf(work.effectiveRuleMode()), m -> work.ruleMode = m));
			// 缓存类型
			List<CacheType> caches = List.of(CacheType.NONE, CacheType.MEMORY, CacheType.DISK);
			dialog.add(new CycleSelector<>(caches,
					ct -> Component.translatable("ui.wh.main.container.cache", ct.name()),
					caches.indexOf(work.cacheType), ct -> work.cacheType = ct));

			// 规则绑定：全局/仓库规则 id 循环选择 + 加/解
			List<String> ruleIds = new ArrayList<>(wh.rules.keySet());
			var ruleRow = PanelElement.plain().padding(2).layout(new Row(4));
			if (!ruleIds.isEmpty()) {
				CycleSelector<String> ruleSel = new CycleSelector<>(ruleIds,
						id -> Component.translatable("ui.wh.main.container.rule", id), 0, id -> {
						});
				ruleRow.add(ruleSel);
				ruleRow.add(new ButtonElement(Component.translatable("ui.wh.main.container.rule.add"))
						.onClick(() -> {
							if (!work.rules.contains(ruleSel.current())) {
								work.rules.add(ruleSel.current());
							}
						}));
				ruleRow.add(new ButtonElement(Component.translatable("ui.wh.main.container.rule.remove"))
						.onClick(() -> work.rules.remove(ruleSel.current())));
			} else {
				ruleRow.add(new LabelElement(Component.translatable("ui.wh.main.container.norules"))
						.color(theme.textMuted()));
			}
			dialog.add(ruleRow);

			var actions = PanelElement.plain().padding(2).layout(new Row(4));
			actions.add(new ButtonElement(Component.translatable("ui.wh.main.container.memory.clear"))
					.onClick(() -> clearMemory(c.canonicalPos())));
			actions.add(new ButtonElement(Component.translatable("ui.wh.modal.done"))
					.semantic(ButtonElement.Semantic.SUCCESS)
					.onClick(() -> {
						c.ioType = work.ioType;
						c.ruleMode = work.ruleMode;
						c.cacheType = work.cacheType;
						c.rules.clear();
						c.rules.addAll(work.rules);
						mgr.save(wh);
						closeModal(root);
						refresh(root);
					}));
			actions.add(new ButtonElement(Component.translatable("ui.wh.modal.cancel"))
					.onClick(() -> closeModal(root)));
			dialog.add(actions);
			overlay.add(dialog);
			registerModalClose(() -> root.remove(overlay));
		}

		// ---- 页签 B：引擎 ----

		private PanelElement engineTab(UiRoot root) {
			var body = PanelElement.plain().padding(2).layout(new Column(6));
			var presenter = HudPresenter.get();

			// 状态卡（复用 Presenter 的 Value，跨 UI 持续显示的样板）
			var status = PanelElement.plain().padding(4).layout(new Column(2));
			status.add(new LabelElement(Component.empty()).bindComponent(compValue(presenter, 0)));
			status.add(new LabelElement(Component.empty()).bindComponent(compValue(presenter, 1)));
			status.add(new LabelElement(Component.empty()).bindComponent(compValue(presenter, 2)));
			body.add(status);

			// 控制按钮组（按状态机合法迁移禁用）
			var engine = WarehouseServices.transportEngine();
			boolean running = engine != null && engine.isRunning();
			boolean suspended = engine != null && engine.state() == bid.yuanlu.mc.warehouse.api.transport.TransportState.SUSPENDED;
			var controls = PanelElement.plain().padding(2).layout(new Row(4));
			controls.add(engineButton("ui.wh.engine.start", !running, () -> engine.start()));
			controls.add(engineButton("ui.wh.engine.stop", running, () -> engine.stop()));
			controls.add(engineButton("ui.wh.engine.continue", suspended, () -> engine.continueRun()));
			controls.add(engineButton("ui.wh.engine.restart", running || suspended, () -> engine.restart()));
			controls.add(engineButton("ui.wh.engine.abort", running || suspended, () -> engine.abort()));
			body.add(controls);

			// 跨仓库搬运
			var transfer = PanelElement.plain().padding(4).layout(new Column(4));
			transfer.add(new LabelElement(Component.translatable("ui.wh.engine.transfer")));
			var picks = PanelElement.plain().padding(2).layout(new Row(4));
			List<Warehouse> warehouses = WarehouseManagerImpl.get().list();
			if (warehouses.size() >= 2) {
				List<String> ids = warehouses.stream().map(w -> w.id).toList();
				CycleSelector<String> src = new CycleSelector<>(ids, id -> Component.literal("src: " + id),
						0, id -> {
						});
				CycleSelector<String> dst = new CycleSelector<>(ids, id -> Component.literal("dst: " + id),
						Math.min(1, ids.size() - 1), id -> {
						});
				picks.add(src);
				picks.add(dst);
				picks.add(new ButtonElement(Component.translatable("ui.wh.engine.transfer.start"))
						.onClick(() -> {
							var s = WarehouseManagerImpl.get().get(src.current());
							var d = WarehouseManagerImpl.get().get(dst.current());
							if (s != null && d != null) {
								TransferOverlay.start(WarehouseManagerImpl.get(), engine, s, d);
							}
						}));
				picks.add(new ButtonElement(Component.translatable("ui.wh.engine.transfer.stop"))
						.onClick(() -> {
							var m = WarehouseManagerImpl.get();
							if (m.hasTransferOverlay()) {
								TransferOverlay.end(m, engine, false);
							}
						}));
			} else {
				picks.add(new LabelElement(Component.translatable("ui.wh.engine.transfer.need_two"))
						.color(Theme.active().textMuted()));
			}
			transfer.add(picks);
			body.add(transfer);
			return body;
		}

		private ButtonElement engineButton(String key, boolean enabled, Runnable action) {
			return new ButtonElement(Component.translatable(key)).enabled(enabled).onClick(() -> {
				action.run();
			});
		}

		// ---- 公共 ----

		private final List<Runnable> modalCleanups = new ArrayList<>();

		private void registerModalClose(Runnable r) {
			modalCleanups.add(r);
		}

		private void closeModal(UiRoot root) {
			for (var r : new ArrayList<>(modalCleanups)) {
				r.run();
			}
			modalCleanups.clear();
		}

		private static void clearMemory(WorldDimPos canonicalPos) {
			var store = WarehouseServices.cacheStore();
			var player = Minecraft.getInstance().player;
			UUID uuid = player != null ? player.getUUID() : null;
			if (store != null) {
				store.invalidate(CacheKey.of(canonicalPos, uuid));
			}
		}

		private static String formatPos(WorldDimPos p) {
			return p == null ? "?" : p.x() + " " + p.y() + " " + p.z();
		}

		/** Presenter Value 索引绑定（页签 B 状态卡）。 */
		private static bid.yuanlu.mc.warehouse.ui.core.bind.Value<Component> compValue(
				HudPresenter p, int i) {
			return switch (i) {
				case 0 -> p.warehouseLine;
				case 1 -> p.stateLine;
				default -> p.progressLine;
			};
		}
	}
}

package bid.yuanlu.mc.warehouse.ui.app.presenter;

import java.util.List;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.transport.TransportState;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.event.WarehouseEvents;
import bid.yuanlu.mc.warehouse.core.mark.MarkMode;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.core.bind.Value;

/**
 * HUD Presenter（UI-PDD §3.3/§6）：事件推（TRANSPORT_STATE/PROGRESS/RUN_FINISHED/…）
 * + tick 拉（选区/标记/激活仓库等无事件状态）→ 汇聚为 Value 供 HUD 元素绑定。
 * HUD 与未来 Screen 可共享同一 Presenter 实例（跨 UI 持续显示）。
 */
public final class HudPresenter {

	private static final HudPresenter INSTANCE = new HudPresenter();

	public static HudPresenter get() {
		return INSTANCE;
	}

	private HudPresenter() {
	}

	// ---- 推送值 ----
	public final Value<Component> warehouseLine = Value.of(Component.translatable("ui.wh.hud.none"));
	public final Value<Component> stateLine = Value.of(Component.translatable("wh.state.idle"));
	public final Value<Component> progressLine = Value.of(Component.empty());
	public final Value<Component> selectionLine = Value.of(Component.empty());
	public final Value<Component> markLine = Value.of(Component.empty());
	public final Value<Boolean> reportVisible = Value.of(false);
	public final Value<Component> reportLine = Value.of(Component.empty());

	private int reportTicks;
	private int itemsMoved;
	@Nullable
	private TransportState lastState;

	/** 客户端入口调用一次：订阅事件 + tick 拉取。 */
	public void attach() {
		WarehouseEvents.TRANSPORT_STATE.register(this::onState);
		WarehouseEvents.PROGRESS.register(this::onProgress);
		WarehouseEvents.ITEM_MOVED.register(this::onItemMoved);
		WarehouseEvents.RUN_FINISHED.register(this::onRunFinished);
		WarehouseEvents.WAREHOUSE_CHANGED.register(() -> refresh());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
	}

	private void onState(TransportState state, @Nullable String detailKey) {
		lastState = state;
		var line = Component.translatable("wh.state." + state.name());
		if (detailKey != null) {
			line.append(" ").append(Component.translatable(detailKey));
		}
		stateLine.set(line);
		if (state == TransportState.ENTRY || state == TransportState.DONE || state == TransportState.SUSPENDED) {
			progressLine.set(Component.empty());
		}
	}

	private void onProgress(TransportState state, @Nullable String action) {
		lastState = state;
		if (action == null) {
			return;
		}
		var line = Component.translatable("wh.progress." + action);
		if (itemsMoved > 0) {
			line.append(Component.translatable("ui.wh.hud.moved", itemsMoved));
		}
		progressLine.set(line);
	}

	private void onItemMoved(@Nullable String pos, int amount) {
		itemsMoved += amount;
	}

	private void onRunFinished(bid.yuanlu.mc.warehouse.api.transport.RunReport r) {
		var line = Component.translatable("wh.grade." + r.grade().name());
		line.append(Component.translatable("ui.wh.hud.report.summary",
				r.itemsMoved(), r.rounds(), r.durationMs() / 1000));
		if (r.detailKey() != null) {
			line.append(" ").append(Component.translatable(r.detailKey()));
		}
		reportLine.set(line);
		reportVisible.set(true);
		reportTicks = 160; // 8s（UI-PDD §6 报告行）
		itemsMoved = 0;
	}

	/** 每 tick：倒计时 + 拉取无事件状态。 */
	private void tick() {
		if (reportTicks > 0 && --reportTicks == 0) {
			reportVisible.set(false);
		}
		refresh();
	}

	/** 拉取当前激活仓库/选区/标记模式（无事件的运行时状态）。 */
	public void refresh() {
		// 激活仓库
		Warehouse active = null;
		try {
			active = WarehouseManagerImpl.get().active();
		} catch (Exception ignored) {
			// 未装配/无激活仓库
		}
		if (active == null) {
			warehouseLine.set(Component.translatable("ui.wh.hud.none"));
		} else {
			int containers = active.containers.size();
			warehouseLine.set(Component.translatable("ui.wh.hud.warehouse", active.id, containers));
		}

		// 选区
		SelectionState sel = SelectionState.get();
		WorldDimPos p1 = sel.pos1();
		WorldDimPos p2 = sel.pos2();
		if (p1 == null && p2 == null) {
			selectionLine.set(Component.empty());
		} else if (sel.hasBox()) {
			int w = Math.abs(p1.x() - p2.x()) + 1;
			int h = Math.abs(p1.y() - p2.y()) + 1;
			int d = Math.abs(p1.z() - p2.z()) + 1;
			selectionLine.set(Component.translatable("ui.wh.hud.selection.box",
					p1.x(), p1.y(), p1.z(), p2.x(), p2.y(), p2.z(), w, h, d));
		} else {
			WorldDimPos p = p1 != null ? p1 : p2;
			selectionLine.set(Component.translatable("ui.wh.hud.selection.corner", p.x(), p.y(), p.z()));
		}

		// 标记模式
		MarkMode mark = MarkMode.get();
		MarkMode.@Nullable Session session = mark.isActive() ? mark.sessionOrNull() : null;
		if (session == null) {
			markLine.set(Component.empty());
		} else {
			var line = Component.translatable("ui.wh.hud.mark.on", session.type().name());
			if (session.ruleId() != null) {
				line.append(" [" + session.ruleId() + "]");
			}
			markLine.set(line);
		}

		// 状态兜底：引擎未运行且从未收到事件时显示 idle
		var engine = bid.yuanlu.mc.warehouse.core.WarehouseServices.transportEngine();
		if (engine == null) {
			stateLine.set(Component.translatable("wh.state.idle"));
		} else if (!engine.isRunning() && lastState == null) {
			stateLine.set(Component.translatable("wh.state.idle"));
		}
	}

	/** 文本行集合（按区块分组，供 HUD 渲染）。 */
	public List<Component> blockLines(HudBlockId block) {
		return switch (block) {
			case WAREHOUSE -> List.of(warehouseLine.get());
			case STATE -> List.of(stateLine.get());
			case PROGRESS -> List.of(progressLine.get());
			case SELECTION -> List.of(selectionLine.get());
			case MARK -> List.of(markLine.get());
			case REPORT -> List.of(reportLine.get());
		};
	}

	public enum HudBlockId {
		WAREHOUSE, STATE, PROGRESS, SELECTION, MARK, REPORT
	}
}

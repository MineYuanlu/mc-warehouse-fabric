package bid.yuanlu.mc.warehouse.core.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.api.transport.TransportState;

/**
 * 事件总线（PDD §5.9）：Fabric {@code Event<T>} 类型化事件——命令层/UI 层/插件均可订阅。
 * 全部事件在客户端主线程触发，监听器不得阻塞。
 */
public final class WarehouseEvents {

	// ---- 监听器接口 ----

	@FunctionalInterface
	public interface TransportStateListener {
		void onStateChanged(TransportState newState, @Nullable String detailKey);
	}

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(TransportState state, @Nullable String action);
	}

	@FunctionalInterface
	public interface ErrorListener {
		void onError(@Nullable String pos, @Nullable String i18nKey);
	}

	@FunctionalInterface
	public interface ItemMovedListener {
		void onItemMoved(@Nullable String pos, int amount);
	}

	@FunctionalInterface
	public interface WarehouseChangedListener {
		void onWarehouseChanged();
	}

	@FunctionalInterface
	public interface HighlightChangedListener {
		void onHighlightChanged();
	}

	@FunctionalInterface
	public interface RunFinishedListener {
		void onRunFinished(RunReport report);
	}

	// ---- 事件 ----

	/** 搬运状态变化 */
	public static final Event<TransportStateListener> TRANSPORT_STATE =
			EventFactory.createArrayBacked(TransportStateListener.class, ls -> (state, detail) -> {
				for (var l : ls) l.onStateChanged(state, detail);
			});

	/** 进度描述更新（阶段级 + 动作级） */
	public static final Event<ProgressListener> PROGRESS =
			EventFactory.createArrayBacked(ProgressListener.class, ls -> (state, action) -> {
				for (var l : ls) l.onProgress(state, action);
			});

	/** 异常发生 */
	public static final Event<ErrorListener> ERROR =
			EventFactory.createArrayBacked(ErrorListener.class, ls -> (pos, key) -> {
				for (var l : ls) l.onError(pos, key);
			});

	/** 单次搬运完成（对账成功计，§6.2） */
	public static final Event<ItemMovedListener> ITEM_MOVED =
			EventFactory.createArrayBacked(ItemMovedListener.class, ls -> (pos, amount) -> {
				for (var l : ls) l.onItemMoved(pos, amount);
			});

	/** 仓库配置变更 */
	public static final Event<WarehouseChangedListener> WAREHOUSE_CHANGED =
			EventFactory.createArrayBacked(WarehouseChangedListener.class, ls -> () -> {
				for (var l : ls) l.onWarehouseChanged();
			});

	/** 高亮数据更新 */
	public static final Event<HighlightChangedListener> HIGHLIGHT_CHANGED =
			EventFactory.createArrayBacked(HighlightChangedListener.class, ls -> () -> {
				for (var l : ls) l.onHighlightChanged();
			});

	/** 搬运结束报告 */
	public static final Event<RunFinishedListener> RUN_FINISHED =
			EventFactory.createArrayBacked(RunFinishedListener.class, ls -> report -> {
				for (var l : ls) l.onRunFinished(report);
			});

	private WarehouseEvents() {
	}
}

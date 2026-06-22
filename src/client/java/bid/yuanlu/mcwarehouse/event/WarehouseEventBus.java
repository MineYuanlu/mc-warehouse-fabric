package bid.yuanlu.mcwarehouse.event;

import net.minecraft.core.BlockPos;

import bid.yuanlu.mcwarehouse.engine.rule.RuleApplicator.TransferPlan;
import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerSnapshot;
import bid.yuanlu.mcwarehouse.model.Warehouse;

/**
 * UI 事件总线 — 预留接口，供未来 UI 层监听仓库/搬运/记忆变更。
 * 当前未绑定任何实现。
 */
public interface WarehouseEventBus {

	void onWarehouseActivated(Warehouse warehouse);

	void onWarehouseDeactivated();

	void onTransferProgress(ContainerInfo current, TransferPlan plan);

	void onTransferComplete(Warehouse warehouse);

	void onTransferError(String message);

	void onContainerMemoryUpdated(BlockPos pos, ContainerSnapshot snapshot);
}

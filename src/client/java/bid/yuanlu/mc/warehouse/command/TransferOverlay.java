package bid.yuanlu.mc.warehouse.command;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.core.engine.transport.TransportEngineImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;

/**
 * 跨仓库搬运（PDD §5.6/§10.1 transfer 子树）。
 * <p>
 * 以临时覆盖视图实现：源仓库全部容器临时 INPUT、目标仓库全部容器临时 OUTPUT，
 * 引擎照常跑标准状态机；RunReport 到达（RUN_FINISHED）或 stop 时弹覆盖并恢复激活态。
 * overlay 不落盘（manager.save 特判）。
 */
final class TransferOverlay {

	private TransferOverlay() {
	}

	/** 构建临时仓库：id "<src>__to__<dst>"，anchors/rules 合并，IOType 全量改写 */
	static Warehouse build(Warehouse src, Warehouse dst) {
		Warehouse ov = new Warehouse(src.id + "__to__" + dst.id);
		ov.anchors.putAll(src.anchors);
		ov.anchors.putAll(dst.anchors);
		ov.rules.putAll(src.rules);
		for (var e : dst.rules.entrySet()) {
			ov.rules.putIfAbsent(e.getKey(), e.getValue());
		}
		for (ContainerInfo c : src.containers) {
			ContainerInfo clone = cloneAs(c, IOType.INPUT, "src");
			if (clone != null) ov.containers.add(clone);
		}
		for (ContainerInfo c : dst.containers) {
			ContainerInfo clone = cloneAs(c, IOType.OUTPUT, "dst");
			if (clone != null) ov.containers.add(clone);
		}
		return ov;
	}

	@Nullable
	private static ContainerInfo cloneAs(ContainerInfo c, IOType ioType, String tag) {
		if (c.pos.isEmpty()) return null;
		ContainerInfo n = new ContainerInfo(ioType);
		n.pos.addAll(c.pos);
		n.cacheType = c.cacheType;
		n.priority = c.priority;
		n.label = (c.label == null ? "" : c.label + " ") + "(" + tag + ")";
		n.rules.addAll(c.rules);
		return n;
	}

	/** 启动跨仓库搬运：构建覆盖 → 推入 → start */
	static String start(WarehouseManagerImpl mgr, TransportEngineImpl engine, Warehouse src, Warehouse dst) {
		Warehouse ov = build(src, dst);
		mgr.pushTransferOverlay(ov);
		engine.start();
		return ov.id;
	}

	/** 结束搬运并恢复原激活态（可安全重复调用） */
	static void end(WarehouseManagerImpl mgr, @Nullable TransportEngineImpl engine, boolean abort) {
		if (abort && engine != null && engine.isRunning()) {
			engine.abort();
		}
		mgr.popTransferOverlay();
	}
}

package bid.yuanlu.mc.warehouse.core.highlight;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.phys.AABB;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import net.minecraft.client.Minecraft;

/**
 * 高亮数据管理（PDD §5.8，UI-PDD §7）：每 tick 从激活仓库配置构建不可变快照
 * （tick 级缓存），渲染帧只读提交。
 * <p>
 * 颜色语义：INPUT 红 / OUTPUT 绿 / TEMP 黄 / IGNORE 灰。
 * HAS_SPACE/FULL 为引擎运行时临时态，待引擎接线后加入（PDD 旧项目教训：
 * 未赋值的状态不列——当前 {@link HighlightType} 仅含可赋值项）。
 */
public final class HighlightManager {

	public enum HighlightType {
		INPUT_OUTLINED(0xFFFF5555),
		OUTPUT_OUTLINED(0xFF55FF55),
		TEMP_OUTLINED(0xFFFFFF55),
		IGNORE_OUTLINED(0xFFAAAAAA),
		UNKNOWN(0xFFC368F0);

		public final int strokeArgb;

		HighlightType(int strokeArgb) {
			this.strokeArgb = strokeArgb;
		}
	}

	public record Entry(AABB box, HighlightType type) {
	}

	private static final HighlightManager INSTANCE = new HighlightManager();

	public static HighlightManager get() {
		return INSTANCE;
	}

	private HighlightManager() {
	}

	private volatile List<Entry> snapshot = List.of();

	public List<Entry> snapshot() {
		return snapshot;
	}

	/** 客户端入口调用：tick 驱动刷新（配置变更经 WAREHOUSE_CHANGED 触发事件推，此处统一拉取）。 */
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> refresh());
		bid.yuanlu.mc.warehouse.core.event.WarehouseEvents.WAREHOUSE_CHANGED.register(() -> refresh());
	}

	/** 重建快照：激活仓库中当前 (world,dim) 的容器 → 类型色盒。 */
	public void refresh() {
		List<Entry> out = new ArrayList<>();
		WorldDim dim = currentDim();
		if (dim != null) {
			Warehouse wh = activeWarehouse();
			if (wh != null) {
				for (ContainerInfo c : wh.containers) {
					HighlightType type = typeOf(c.ioType);
					for (WorldDimPos p : c.pos) {
						AABB box = boxOf(wh, dim, p);
						if (box != null) {
							out.add(new Entry(box, type));
						}
					}
				}
			}
		}
		snapshot = List.copyOf(out);
	}

	private static @Nullable HighlightType typeOf(IOType ioType) {
		return switch (ioType) {
			case INPUT -> HighlightType.INPUT_OUTLINED;
			case OUTPUT -> HighlightType.OUTPUT_OUTLINED;
			case TEMP -> HighlightType.TEMP_OUTLINED;
			case IGNORE -> HighlightType.IGNORE_OUTLINED;
		};
	}

	private static @Nullable Warehouse activeWarehouse() {
		try {
			return WarehouseManagerImpl.get().active();
		} catch (Exception e) {
			return null;
		}
	}

	private static @Nullable WorldDim currentDim() {
		var mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) {
			return null;
		}
		var trk = WorldSessionTracker.get();
		if (trk == null) return null;
		String serverId = trk.currentServerId();
		String worldName = trk.currentWorldName();
		if (serverId == null || worldName == null) return null;
		return new WorldDim(serverId, worldName, mc.level.dimension().identifier().toString());
	}

	private static @Nullable AABB boxOf(Warehouse wh, WorldDim dim, WorldDimPos p) {
		if (!dim.dimId().equals(p.dim())) {
			return null;
		}
		try {
			// 配置 pos 可省略 world（§11.3），按当前世界补全
			WorldDimPos withWorld = p.hasWorld() ? p : p.withWorld(dim.worldName());
			var abs = wh.resolveAbsolute(dim.serverId(), withWorld);
			if (abs == null) {
				return null;
			}
			return new AABB(abs.x(), abs.y(), abs.z(), abs.x() + 1, abs.y() + 1, abs.z() + 1);
		} catch (Exception e) {
			return null;
		}
	}
}

package bid.yuanlu.mc.warehouse.api.warehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import net.minecraft.core.BlockPos;

/**
 * 仓库 = 容器集合 + 规则（PDD §3.1）。可跨世界、跨维度，每个 (worldId, dimId) 有一个基准点。
 * <p>
 * 容器的 pos 存为相对坐标（相对于同 (world,dim) 的 anchor），便于整体偏移；
 * 激活态是运行时概念（{@link WarehouseManager} 内存持有），不持久化。
 */
public class Warehouse {

	/** 唯一标识 */
	public final String id;

	/** worldId → dimId → 基准点（两级嵌套，避免分隔符转义问题，§11.3） */
	public final Map<String, Map<String, BlockPos>> anchors = new LinkedHashMap<>();

	public final List<ContainerInfo> containers = new ArrayList<>();

	/** 本仓库内定义的规则（key = ruleId）；与全局规则 id 冲突 = 加载报错拒载（§11.3） */
	public final Map<String, ContainerRule> rules = new LinkedHashMap<>();

	public Warehouse(String id) {
		this.id = java.util.Objects.requireNonNull(id, "id");
	}

	/** 查询某 (world,dim) 的 anchor */
	@Nullable
	public BlockPos anchorOf(WorldDim dim) {
		Map<String, BlockPos> dims = anchors.get(dim.worldId());
		return dims != null ? dims.get(dim.dimId()) : null;
	}

	/** 设置某 (world,dim) 的 anchor */
	public void setAnchor(WorldDim dim, BlockPos pos) {
		anchors.computeIfAbsent(dim.worldId(), k -> new LinkedHashMap<>()).put(dim.dimId(), pos);
	}

	/**
	 * 相对坐标 → 绝对坐标。要求 pos 已带 world 且对应 anchor 存在；否则返回 null。
	 */
	@Nullable
	public WorldDimPos resolveAbsolute(WorldDimPos relative) {
		if (!relative.hasWorld()) return null;
		BlockPos anchor = anchorOf(relative.worldDim());
		if (anchor == null) return null;
		return relative.plus(anchor);
	}

	/**
	 * 绝对坐标 → 相对坐标（按给定 dim 的 anchor）；无 anchor 时返回 null。
	 */
	@Nullable
	public WorldDimPos toRelative(WorldDim dim, BlockPos absolute) {
		BlockPos anchor = anchorOf(dim);
		if (anchor == null) return null;
		return WorldDimPos.of(dim, absolute).minus(anchor);
	}

	/** 查找包含指定 canonical 坐标的容器（同 dim 下匹配任一 pos 条目） */
	public ContainerInfo containerAt(WorldDim dim, BlockPos pos) {
		for (ContainerInfo c : containers) {
			for (WorldDimPos p : c.pos) {
				if (dim.dimId().equals(p.dim()) && dim.worldId().equals(p.world()) && p.toBlockPos().equals(pos)) {
					return c;
				}
			}
		}
		return null;
	}
}

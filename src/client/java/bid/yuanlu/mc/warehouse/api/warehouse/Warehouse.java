package bid.yuanlu.mc.warehouse.api.warehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import net.minecraft.core.BlockPos;

/**
 * 仓库 = 容器集合 + 规则（PDD §3.1）。可跨 world、跨维度，每个 (serverId, worldName, dimId) 有一个基准点。
 * <p>
 * 容器的 pos 存为相对坐标（相对于同 (world,dim) 的 anchor），便于整体偏移；
 * 服务器维度是会话相对的（运行时按当前 serverId 解析，天然跨游戏复用）；
 * 激活态是运行时概念（{@link WarehouseManager} 内存持有），不持久化。
 */
public class Warehouse {

	/** 唯一标识 */
	public final String id;

	/** serverId → worldName → dimId → 基准点（三层嵌套，避免分隔符转义问题，§11.3） */
	public final Map<String, Map<String, Map<String, BlockPos>>> anchors = new LinkedHashMap<>();

	public final List<ContainerInfo> containers = new ArrayList<>();

	/** 本仓库内定义的规则（key = ruleId）；与全局规则 id 冲突 = 加载报错拒载（§11.3） */
	public final Map<String, ContainerRule> rules = new LinkedHashMap<>();

	public Warehouse(String id) {
		this.id = java.util.Objects.requireNonNull(id, "id");
	}

	/** 查询某 (server, world, dim) 的 anchor */
	@Nullable
	public BlockPos anchorOf(WorldDim dim) {
		Map<String, Map<String, BlockPos>> worlds = anchors.get(dim.serverId());
		Map<String, BlockPos> dims = worlds != null ? worlds.get(dim.worldName()) : null;
		return dims != null ? dims.get(dim.dimId()) : null;
	}

	/** 设置某 (server, world, dim) 的 anchor */
	public void setAnchor(WorldDim dim, BlockPos pos) {
		anchors.computeIfAbsent(dim.serverId(), k -> new LinkedHashMap<>())
				.computeIfAbsent(dim.worldName(), k -> new LinkedHashMap<>())
				.put(dim.dimId(), pos);
	}

	/**
	 * 相对坐标 → 绝对坐标。serverId 来自当前会话（会话相对解析）；
	 * 要求 pos 已带 worldName 且对应 anchor 存在；否则返回 null。
	 */
	@Nullable
	public WorldDimPos resolveAbsolute(@Nullable String serverId, WorldDimPos relative) {
		if (serverId == null || !relative.hasWorld()) return null;
		BlockPos anchor = anchorOf(new WorldDim(serverId, relative.world(), relative.dim()));
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

	/** 查找包含指定 canonical 坐标的容器（同 (world, dim) 下匹配任一 pos 条目） */
	public ContainerInfo containerAt(WorldDim dim, BlockPos pos) {
		for (ContainerInfo c : containers) {
			for (WorldDimPos p : c.pos) {
				if (dim.dimId().equals(p.dim()) && dim.worldName().equals(p.world()) && p.toBlockPos().equals(pos)) {
					return c;
				}
			}
		}
		return null;
	}

	/** anchors 中出现过的全部 worldName（跨 server 展平，去重，保插入序） */
	public Set<String> worldNames() {
		Set<String> out = new LinkedHashSet<>();
		for (Map<String, Map<String, BlockPos>> worlds : anchors.values()) {
			out.addAll(worlds.keySet());
		}
		return out;
	}
}

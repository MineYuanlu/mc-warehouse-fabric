package bid.yuanlu.mc.warehouse.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * 全局配置（PDD §11.4）：模组级参数 + 世界级覆盖（worlds 两级结构）。
 * <p>
 * 世界相关查找顺序：{@code worlds[world].dimensions[dim]} → {@code worlds[world]} 级默认 → 全局默认。
 */
public class ModConfig {

	public static final int SCHEMA_VERSION = 1;

	public boolean debug = false;

	/** 默认交互速度（每次操作后的 tick 等待数） */
	public int defaultInteractionSpeed = 2;

	/** 每次操作的随机额外延迟百分比（反作弊缓解，§6.4） */
	public int interactionJitterPercent = 0;

	/** MEMORY/DISK 缓存的 TTL 秒数保险，0=关（§5.4） */
	public int cacheTtlSeconds = 0;

	/** 槽位分配器 id */
	public String slotAllocator = "first_fit";

	/** 开容器的最大触及距离（格，§6.5） */
	public double reachLimit = 4.5;

	/** 同一容器连续探索失败上限（§6.5） */
	public int exploreFailMax = 2;

	/** 同一寻路目标的引擎侧重试上限（§6.5） */
	public int navRetryMax = 3;

	public Timeouts timeouts = new Timeouts();

	/** worldId → 世界条目（两级结构，v0.2 决策） */
	public Map<String, WorldEntry> worlds = new LinkedHashMap<>();

	/** 运行期一次性寻路器覆盖（/wh start --pathfinder 置入；不持久化） */
	@Nullable
	public transient String pathfinderOnce;

	// ---- 时间参数（§6.5）----

	public static class Timeouts {
		/** 等待容器 UI 打开的超时（tick） */
		public int openTicks = 20;
		/** 单次点击等待服务端对账的超时（tick） */
		public int confirmTicks = 10;
		/** 一组操作后的稳定等待（tick） */
		public int settleTicks = 2;
	}

	// ---- 世界级覆盖 ----

	public static class WorldEntry {
		/** 世界级默认交互速度；null = 用全局 */
		@Nullable
		public Integer interactionSpeed;

		/** 世界级默认寻路器；null = 用全局 */
		@Nullable
		public String pathfinder;

		/** dimId → 维度级覆盖 */
		public Map<String, DimOverride> dimensions = new LinkedHashMap<>();
	}

	public static class DimOverride {
		@Nullable
		public Integer interactionSpeed;

		@Nullable
		public String pathfinder;
	}

	// ---- 有效值解析 ----

	@Nullable
	private WorldEntry world(@Nullable String worldId) {
		return worldId != null ? worlds.get(worldId) : null;
	}

	/** 生效交互速度：dim 覆盖 → world 默认 → 全局 */
	public int interactionSpeed(@Nullable String worldId, @Nullable String dimId) {
		WorldEntry w = world(worldId);
		if (w != null && dimId != null) {
			DimOverride d = w.dimensions.get(dimId);
			if (d != null && d.interactionSpeed != null) return d.interactionSpeed;
		}
		if (w != null && w.interactionSpeed != null) return w.interactionSpeed;
		return defaultInteractionSpeed;
	}

	/** 生效寻路器：dim 覆盖 → world 默认 → "noop" */
	public String pathfinder(@Nullable String worldId, @Nullable String dimId) {
		WorldEntry w = world(worldId);
		if (w != null && dimId != null) {
			DimOverride d = w.dimensions.get(dimId);
			if (d != null && d.pathfinder != null) return d.pathfinder;
		}
		if (w != null && w.pathfinder != null) return w.pathfinder;
		return "noop";
	}

	/** 生效超时参数（无世界级覆盖，全局一份） */
	public Timeouts timeouts() {
		return timeouts == null ? new Timeouts() : timeouts;
	}
}

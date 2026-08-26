package bid.yuanlu.mc.warehouse.core.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 容器内存管理（PDD §3.8/§5.4）：三级缓存 + TTL + 自愈失效 + 会话切换清理。
 *
 * <ul>
 *   <li>NONE：只在当前搬运轮次内有效，轮次结束清除（{@link #beginRound}/{@link #endRound}）</li>
 *   <li>MEMORY/DISK：随 Session 生命周期；worldId 变化时全部卸载</li>
 *   <li>TTL：cacheTtlSeconds > 0 时，MEMORY/DISK 快照超期视为无效（强制重扫）</li>
 *   <li>自愈：任何基于缓存的预检被现实否定即 {@link #invalidate}——缓存不是正确性依赖</li>
 * </ul>
 * DISK 类型的落盘由 {@link DiskCacheStore} 承担；本类只管内存态。
 */
public final class ContainerMemoryStore {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/cache");

	public static final class Entry {
		public final ContainerMemory memory;
		public final boolean valid;

		Entry(ContainerMemory memory, boolean valid) {
			this.memory = memory;
			this.valid = valid;
		}
	}

	private final Map<CacheKey, ContainerMemory> memories = new HashMap<>();
	private final LongSupplier clock;
	private final int ttlSeconds;
	private boolean roundActive;

	public ContainerMemoryStore(ModConfig config, WorldSessionTracker sessions) {
		this(config, sessions, System::currentTimeMillis);
	}

	/** 注入时钟（测试用） */
	public ContainerMemoryStore(ModConfig config, WorldSessionTracker sessions, LongSupplier clock) {
		this.clock = clock;
		this.ttlSeconds = Math.max(0, config.cacheTtlSeconds);
		sessions.addListener(this::onSessionChanged);
	}

	// ---- 生命周期 ----

	/** 搬运轮次开始：NONE 缓存在本轮内可用 */
	public void beginRound() {
		roundActive = true;
	}

	/** 搬运轮次结束：清除 NONE 缓存 */
	public void endRound() {
		roundActive = false;
		memories.keySet().removeIf(k -> {
			boolean none = memories.get(k).cacheType() == CacheType.NONE;
			if (none) LOGGER.debug("round end, evict NONE {}", k);
			return none;
		});
	}

	private void onSessionChanged(@Nullable String oldWorldId, @Nullable String newWorldId) {
		if (java.util.Objects.equals(oldWorldId, newWorldId)) return;
		int size = memories.size();
		memories.clear();
		LOGGER.info("session switch {} → {}, unloaded {} cache entries", oldWorldId, newWorldId, size);
	}

	// ---- 访问 ----

	/**
	 * 取有效缓存；无效（缺失/TTL 超期/NONE 出轮）返回 null。
	 * 命中会更新 lastAccessTime。
	 */
	@Nullable
	public ContainerMemory getValid(CacheKey key) {
		ContainerMemory m = memories.get(key);
		if (m == null) return null;
		if (!isUsable(m)) return null;
		m.snapshot(); // 触碰访问时间
		return m;
	}

	/** 是否存在已探索条目（无论是否过期）；引擎的「未探索容器」判定用 */
	public boolean isExplored(CacheKey key) {
		return memories.containsKey(key) && memories.get(key).explored();
	}

	/** 写入对账后的快照（§6.3），自动置 explored */
	public ContainerMemory remember(CacheKey key, CacheType type, ContainerSnapshot snapshot) {
		long now = clock.getAsLong();
		ContainerMemory existing = memories.get(key);
		if (existing != null && existing.cacheType() == type) {
			existing.refresh(snapshot, now);
			return existing;
		}
		ContainerMemory created = new ContainerMemory(key, type, snapshot, now);
		memories.put(key, created);
		return created;
	}

	/** 自愈失效：预检被现实否定时调用，下次访问将强制重扫 */
	public void invalidate(CacheKey key) {
		if (memories.remove(key) != null) LOGGER.debug("invalidated {}", key);
	}

	public void invalidateAll() {
		memories.clear();
	}

	/** 全部有效条目视图（引擎聚合判定用） */
	public List<ContainerMemory> allValid() {
		return memories.values().stream().filter(this::isUsable).toList();
	}

	public int size() {
		return memories.size();
	}

	// ---- 内部 ----

	private boolean isUsable(ContainerMemory m) {
		if (m.cacheType() == CacheType.NONE && !roundActive) return false;
		if ((m.cacheType() == CacheType.MEMORY || m.cacheType() == CacheType.DISK) && ttlSeconds > 0) {
			if (clock.getAsLong() - m.lastRefreshTime() > ttlSeconds * 1000L) return false;
		}
		return true;
	}
}

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
import bid.yuanlu.mc.warehouse.core.world.WorldSession;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 容器内存管理（PDD §3.8/§5.4）：三级缓存 + TTL + 自愈失效 + 会话切换清理。
 *
 * <ul>
 *   <li>NONE：只在当前搬运轮次内有效，轮次结束清除（{@link #beginRound}/{@link #endRound}）</li>
 *   <li>MEMORY：随 Session 生命周期；worldId 变化时全部卸载</li>
 *   <li>DISK：内存态之外同步落盘（{@link DiskCacheStore}），进程重启后经
 *       {@link #getValid(CacheKey, CacheType)} 回源加载；worldId 在键与落盘路径内，不跨世界串数据</li>
 *   <li>TTL：cacheTtlSeconds > 0 时，MEMORY/DISK 快照超期视为无效（强制重扫）</li>
 *   <li>自愈：任何基于缓存的预检被现实否定即 {@link #invalidate}——缓存不是正确性依赖；
 *       invalidate 同步清除内存与磁盘</li>
 * </ul>
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
	@Nullable
	private final DiskCacheStore disk;
	private boolean roundActive;

	public ContainerMemoryStore(ModConfig config, WorldSessionTracker sessions) {
		this(config, sessions, System::currentTimeMillis, null);
	}

	/** 生产装配：注入 DISK 落盘层（§3.8/§5.4） */
	public ContainerMemoryStore(ModConfig config, WorldSessionTracker sessions, @Nullable DiskCacheStore disk) {
		this(config, sessions, System::currentTimeMillis, disk);
	}

	/** 注入时钟（测试用） */
	public ContainerMemoryStore(ModConfig config, WorldSessionTracker sessions, LongSupplier clock) {
		this(config, sessions, clock, null);
	}

	public ContainerMemoryStore(ModConfig config, WorldSessionTracker sessions, LongSupplier clock,
			@Nullable DiskCacheStore disk) {
		this.clock = clock;
		this.ttlSeconds = Math.max(0, config.cacheTtlSeconds);
		this.disk = disk;
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

	private void onSessionChanged(@Nullable WorldSession oldSession, @Nullable WorldSession newSession) {
		if (java.util.Objects.equals(oldSession, newSession)) return;
		int size = memories.size();
		memories.clear();
		LOGGER.info("session switch {} → {}, unloaded {} cache entries", oldSession, newSession, size);
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

	/**
	 * 带预期类型的访问（§3.8 DISK 闭环）：内存缺失且预期为 DISK 时从磁盘回源并回填内存。
	 * 内存已有条目但 TTL 超期 → 仍返回 null（磁盘数据同龄，强制重扫）。
	 */
	@Nullable
	public ContainerMemory getValid(CacheKey key, CacheType expected) {
		ContainerMemory m = getValid(key);
		if (m != null) return m;
		if (memories.containsKey(key)) return null; // 内存有但超期：磁盘数据同龄，强制重扫
		if (expected != CacheType.DISK || disk == null) return null;
		return backfillFromDisk(key);
	}

	/** 是否存在已探索条目（无论是否过期）；引擎的「未探索容器」判定用 */
	public boolean isExplored(CacheKey key) {
		return memories.containsKey(key) && memories.get(key).explored();
	}

	/** 带预期类型的探索判定：内存缺失时查磁盘侧（DISK 容器重启后仍算已探索） */
	public boolean isExplored(CacheKey key, CacheType expected) {
		if (isExplored(key)) return true;
		if (expected != CacheType.DISK || disk == null) return false;
		return backfillFromDisk(key) != null;
	}

	/** 磁盘 → 内存回填；磁盘无有效数据返回 null。回填不重复落盘 */
	@Nullable
	private ContainerMemory backfillFromDisk(CacheKey key) {
		try {
			return disk.load(key)
					.map(snap -> {
						ContainerMemory m = new ContainerMemory(key, CacheType.DISK, snap, clock.getAsLong());
						memories.put(key, m);
						LOGGER.debug("disk cache backfilled {}", key);
						return m;
					})
					.orElse(null);
		} catch (Exception e) {
			LOGGER.warn("disk backfill failed {}: {}", key, e.toString());
			return null;
		}
	}

	/** 写入对账后的快照（§6.3），自动置 explored；DISK 类型同步落盘 */
	public ContainerMemory remember(CacheKey key, CacheType type, ContainerSnapshot snapshot) {
		long now = clock.getAsLong();
		ContainerMemory existing = memories.get(key);
		ContainerMemory result;
		if (existing != null && existing.cacheType() == type) {
			existing.refresh(snapshot, now);
			result = existing;
		} else {
			result = new ContainerMemory(key, type, snapshot, now);
			memories.put(key, result);
		}
		if (type == CacheType.DISK && disk != null) {
			disk.save(key, snapshot); // §3.8：DISK 持久化，重启重新加载
		}
		return result;
	}

	/** 自愈失效：预检被现实否定时调用，下次访问将强制重扫；DISK 同步删除 */
	public void invalidate(CacheKey key) {
		if (memories.remove(key) != null) LOGGER.debug("invalidated {}", key);
		if (disk != null) disk.delete(key);
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

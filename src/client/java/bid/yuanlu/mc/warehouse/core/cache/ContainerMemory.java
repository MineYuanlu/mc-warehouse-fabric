package bid.yuanlu.mc.warehouse.core.cache;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;

/**
 * 单个容器的缓存条目（PDD §3.8）。缓存只是性能优化而非正确性依赖（自愈机制兜底）。
 */
public final class ContainerMemory {

	private final CacheKey key;
	private final CacheType cacheType;
	private ContainerSnapshot snapshot;
	/** 是否已被扫描过（引擎防振荡机制依赖，§5.3） */
	private boolean explored;
	/** 最后一次访问时间（毫秒） */
	private long lastAccessTime;
	/** 最后一次快照写入时间（毫秒；TTL 依据） */
	private long lastRefreshTime;

	public ContainerMemory(CacheKey key, CacheType cacheType, ContainerSnapshot snapshot, long nowMs) {
		this.key = key;
		this.cacheType = cacheType;
		this.snapshot = snapshot;
		this.explored = true;
		this.lastAccessTime = nowMs;
		this.lastRefreshTime = nowMs;
	}

	public CacheKey key() {
		return key;
	}

	public CacheType cacheType() {
		return cacheType;
	}

	public ContainerSnapshot snapshot() {
		lastAccessTime = System.currentTimeMillis();
		return snapshot;
	}

	/** 对账后的新快照写入（§6.3：只有对账完成的状态才允许写入） */
	public void refresh(ContainerSnapshot newSnapshot, long nowMs) {
		this.snapshot = newSnapshot;
		this.lastRefreshTime = nowMs;
		this.explored = true;
	}

	public boolean explored() {
		return explored;
	}

	public long lastAccessTime() {
		return lastAccessTime;
	}

	public long lastRefreshTime() {
		return lastRefreshTime;
	}
}

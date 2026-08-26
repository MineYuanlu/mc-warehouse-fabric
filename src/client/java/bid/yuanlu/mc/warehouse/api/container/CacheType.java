package bid.yuanlu.mc.warehouse.api.container;

/**
 * 三级缓存类型（PDD §5.4）。缓存只是性能优化而非正确性依赖（§3.8 自愈机制兜底）。
 */
public enum CacheType {
	/** 只在当前搬运轮次内有效，轮次结束后清除 */
	NONE,
	/** 随游戏 Session 生命周期，worldId 变化或退出世界/服务器时清除 */
	MEMORY,
	/** 持久化到文件系统（按 worldId 分目录），重启后重新加载 */
	DISK
}

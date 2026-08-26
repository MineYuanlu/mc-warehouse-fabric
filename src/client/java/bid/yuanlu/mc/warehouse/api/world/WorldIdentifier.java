package bid.yuanlu.mc.warehouse.api.world;

import org.jetbrains.annotations.Nullable;

/**
 * 世界标识 SPI（PDD §4.1）：回答「这是哪个服务器/存档」。
 * <p>
 * world 标识用于：配置隔离、缓存命名空间、仓库可达性判断。
 * worldId 发生变化视为会话切换：MEMORY 缓存清空、DISK 缓存卸载、运行中的搬运终止并报告。
 */
public interface WorldIdentifier {

	String id();

	/** 当前会话的 world 标识；会话未就绪（主菜单/已断开）返回 null */
	@Nullable
	String currentWorldId();
}

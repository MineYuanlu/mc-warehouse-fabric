package bid.yuanlu.mc.warehouse.api.world;

import org.jetbrains.annotations.Nullable;

/**
 * 服务器标识（PDD §4.1）：回答「这是哪个服务器/存档」。
 * <p>
 * server 标识用于：配置隔离、缓存命名空间、会话生命周期。内置固定实现
 * （单人 = 存档目录名，多人 = 连接地址），不是插件 SPI。
 * 服务器内部的 world 划分见 {@link WorldIdentifier}。
 */
public interface ServerIdentifier {

	/** 当前会话的服务器标识；会话未就绪（主菜单/已断开）返回 null */
	@Nullable
	String currentServerId();
}

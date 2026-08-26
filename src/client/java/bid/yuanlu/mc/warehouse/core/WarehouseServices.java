package bid.yuanlu.mc.warehouse.core;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;

/**
 * 客户端装配的服务定位（入口装配时置入；gametest 可手动装配）。
 * <p>
 * 仅暴露无业务逻辑的运行期单例句柄，命令层经此访问缓存状态等
 * 未挂到 manager 的设施；不在 api/ 中——插件应走 Registry 与事件。
 */
public final class WarehouseServices {

	@Nullable
	private static volatile ContainerMemoryStore cacheStore;

	@Nullable
	private static volatile ModConfig modConfig;

	public static void setModConfig(@Nullable ModConfig config) {
		modConfig = config;
	}

	@Nullable
	public static ModConfig modConfig() {
		return modConfig;
	}

	public static void setCacheStore(@Nullable ContainerMemoryStore store) {
		cacheStore = store;
	}

	@Nullable
	public static ContainerMemoryStore cacheStore() {
		return cacheStore;
	}

	private WarehouseServices() {
	}
}

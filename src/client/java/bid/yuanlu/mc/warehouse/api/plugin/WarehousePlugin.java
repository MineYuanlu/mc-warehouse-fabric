package bid.yuanlu.mc.warehouse.api.plugin;

/**
 * 仓库插件入口（PDD §9.1）：通过 Fabric entrypoint {@code warehouse-plugin} 加载。
 *
 * <pre>{@code
 * // fabric.mod.json (附属mod)
 * "entrypoints": { "warehouse-plugin": ["com.example.MyWarehousePlugin"] }
 * }</pre>
 */
public interface WarehousePlugin {

	void register(WarehouseRegistry registry);
}

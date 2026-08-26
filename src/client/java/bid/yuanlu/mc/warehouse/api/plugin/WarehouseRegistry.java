package bid.yuanlu.mc.warehouse.api.plugin;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;
import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.item.SlotAllocator;
import bid.yuanlu.mc.warehouse.api.navigation.Navigator;
import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;

/**
 * 插件注册表（PDD §9.2）。注册时机：插件 entrypoint 调用期间（客户端初始化线程），
 * 早于任何功能使用；此后注册表冻结，运行期注册一律拒绝。
 * <p>
 * 内置实现由模组自身经同一机制注册（自食其力，保证 API 完备性）。
 */
public interface WarehouseRegistry {

	// ---- 能力实现注册（重复 id 抛 IllegalArgumentException，加载期快速失败）----

	void registerDetector(ContainerDetector detector);

	void registerNavigator(Navigator navigator);

	void registerWorldIdentifier(WorldIdentifier identifier);

	void registerInteraction(ContainerInteraction interaction);

	void registerSlotAllocator(SlotAllocator allocator);

	void registerAgentPlanner(AgentPlanner planner);

	// ---- 序列化 codec 注册：解决「插件自定义 selector 无法持久化」----

	void registerItemSelectorCodec(SelectorCodec<? extends ItemSelector> codec);

	void registerQuantitySelectorCodec(SelectorCodec<? extends QuantitySelector> codec);
}

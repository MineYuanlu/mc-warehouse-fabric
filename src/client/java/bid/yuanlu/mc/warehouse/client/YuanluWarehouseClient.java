package bid.yuanlu.mc.warehouse.client;

import java.nio.file.Path;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bid.yuanlu.mc.warehouse.api.plugin.WarehousePlugin;
import bid.yuanlu.mc.warehouse.command.WhCommands;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.engine.transport.TransportEngineImpl;
import bid.yuanlu.mc.warehouse.core.mark.MarkMode;
import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.impl.allocator.FirstFitAllocator;
import bid.yuanlu.mc.warehouse.impl.container.BarrelDetector;
import bid.yuanlu.mc.warehouse.impl.container.BrewingStandDetector;
import bid.yuanlu.mc.warehouse.impl.container.ChestDetector;
import bid.yuanlu.mc.warehouse.impl.container.DispenserDropperDetector;
import bid.yuanlu.mc.warehouse.impl.container.EnderChestDetector;
import bid.yuanlu.mc.warehouse.impl.container.FurnaceDetector;
import bid.yuanlu.mc.warehouse.impl.container.HopperDetector;
import bid.yuanlu.mc.warehouse.impl.container.ShulkerBoxDetector;
import bid.yuanlu.mc.warehouse.impl.container.VanillaGuiInteraction;
import bid.yuanlu.mc.warehouse.impl.navigation.NoOpNavigator;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.FillSlotsSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.GroupSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.PercentSelector;
import bid.yuanlu.mc.warehouse.impl.selector.CompositeSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;
import bid.yuanlu.mc.warehouse.impl.selector.NameSelector;
import bid.yuanlu.mc.warehouse.impl.selector.NbtSelector;
import bid.yuanlu.mc.warehouse.impl.selector.TagSelector;
import bid.yuanlu.mc.warehouse.impl.world.MultiplayerWorldIdentifier;
import bid.yuanlu.mc.warehouse.impl.world.SingleplayerWorldIdentifier;

/**
 * 客户端入口装配（PDD §12）：内置实现注册 → 插件加载 → 注册表冻结 →
 * 服务单例（世界会话/缓存/管理器/引擎）→ tick 驱动 → 命令树 + 事件聊天桥。
 */
public final class YuanluWarehouseClient implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse");

	@Override
	public void onInitializeClient() {
		registerBuiltins();
		loadPlugins();
		WarehouseRegistryImpl.freeze();
		SelectorCodecs.freeze();
		bootstrapServices();
		registerTickLoop();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				WhCommands.register(dispatcher));
		EventChatBridge.attach();
		LOGGER.info("yuanlu-warehouse client initialized");
	}

	// ---- 内置实现（自食其力，经同一 Registry，PDD §9.2）----

	public static void registerBuiltins() {
		var reg = WarehouseRegistryImpl.get();
		reg.registerDetector(new ChestDetector());
		reg.registerDetector(new BarrelDetector());
		reg.registerDetector(new ShulkerBoxDetector());
		reg.registerDetector(new HopperDetector());
		reg.registerDetector(new DispenserDropperDetector());
		reg.registerDetector(FurnaceDetector.FURNACE);
		reg.registerDetector(FurnaceDetector.BLAST_FURNACE);
		reg.registerDetector(FurnaceDetector.SMOKER);
		reg.registerDetector(new BrewingStandDetector());
		reg.registerDetector(new EnderChestDetector());
		reg.registerInteraction(new VanillaGuiInteraction());
		reg.registerNavigator(new NoOpNavigator());
		reg.registerSlotAllocator(new FirstFitAllocator());
		reg.registerItemSelectorCodec(IdSelector.codec());
		reg.registerItemSelectorCodec(TagSelector.codec());
		reg.registerItemSelectorCodec(NameSelector.codec());
		reg.registerItemSelectorCodec(NbtSelector.codec());
		reg.registerItemSelectorCodec(CompositeSelector.codec());
		reg.registerQuantitySelectorCodec(CountSelector.codec());
		reg.registerQuantitySelectorCodec(GroupSelector.codec());
		reg.registerQuantitySelectorCodec(FillSlotsSelector.codec());
		reg.registerQuantitySelectorCodec(PercentSelector.codec());
	}

	// ---- 插件 entrypoint（warehouse-plugin，PDD §9.1）----

	static void loadPlugins() {
		var entries = FabricLoader.getInstance().getEntrypointContainers(
				"warehouse-plugin", WarehousePlugin.class);
		for (var entry : entries) {
			try {
				entry.getEntrypoint().register(WarehouseRegistryImpl.get());
				LOGGER.info("warehouse plugin loaded: {}", entry.getProvider().getMetadata().getId());
			} catch (Throwable t) {
				LOGGER.error("warehouse plugin failed to load: {}",
						entry.getProvider().getMetadata().getId(), t);
			}
		}
	}

	// ---- 服务单例 ----

	static void bootstrapServices() {
		ModConfig config = new ConfigIO(ConfigIO.defaultRoot()).loadModConfig();
		WorldSessionTracker sessions = new WorldSessionTracker(List.of(
				new SingleplayerWorldIdentifier(),
				new MultiplayerWorldIdentifier()));
		WorldSessionTracker.setInstance(sessions);
		bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore store =
				new bid.yuanlu.mc.warehouse.core.cache.ContainerMemoryStore(config, sessions);
		bid.yuanlu.mc.warehouse.core.WarehouseServices.setCacheStore(store);
		bid.yuanlu.mc.warehouse.core.WarehouseServices.setModConfig(config);
		WarehouseManagerImpl manager = WarehouseManagerImpl.get();
		TransportEngineImpl.setInstance(
				new TransportEngineImpl(manager, store, config));
	}

	// ---- tick 驱动（反模式红线：全程 tick 单线程，无 worker/sleep）----

	static void registerTickLoop() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			WorldSessionTracker trk = WorldSessionTracker.get();
			if (trk != null) trk.tick();
			MarkMode.get().tick();
			try {
				TransportEngineImpl engine = TransportEngineImpl.get();
				engine.tick();
			} catch (IllegalStateException ignored) {
				// 引擎未初始化（异常装配环境）
			}
		});
	}
}

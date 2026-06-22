package bid.yuanlu.mcwarehouse;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bid.yuanlu.mcwarehouse.command.WarehouseCommand;
import bid.yuanlu.mcwarehouse.controller.PathfindingController;
import bid.yuanlu.mcwarehouse.storage.ModConfigStorage;

public class MCWarehouseClient implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("mc-warehouse");

	@Override
	public void onInitializeClient() {
		WarehouseCommand.register();

		new ModConfigStorage().load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			PathfindingController.getInstance().tick();
		});

		LOGGER.info("MC Warehouse client mod initialized!");
	}
}

package bid.yuanlu.mcwarehouse;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCWarehouseClient implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("mc-warehouse");

	@Override
	public void onInitializeClient() {
		LOGGER.info("MC Warehouse client mod initialized!");
	}
}

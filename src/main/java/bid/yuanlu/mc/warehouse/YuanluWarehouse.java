package bid.yuanlu.mc.warehouse;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YuanluWarehouse implements ModInitializer {
	public static final String MOD_ID = "yuanlu-warehouse";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 服务端增强：world 身份推送（PDD §4.2）。客户端同样执行注册（两侧同源 universal jar）。
		new bid.yuanlu.mc.warehouse.net.ServerWorldIdSync();

		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

package bid.yuanlu.mc.warehouse.core.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import bid.yuanlu.mc.warehouse.net.WhWorldIdPayload;

/**
 * 客户端 worldId 接收（PDD §4.2/§11）：接收服务端推送并写入 {@link ServerWorldIdHolder}。
 * <p>
 * 加入服务器时先清空（防止上一服务器的残留值被无推送的新服务器误用）；
 * 断开时同样清空。协议版本不匹配仅告警忽略（应用层前向兼容；
 * codec 层与旧版本 mod 错配会解码失败，见 payload javadoc）。
 */
public final class ClientWorldIdReceiver {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/world-sync");

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WhWorldIdPayload.TYPE, (payload, ctx) -> {
			if (payload.protocol() != WhWorldIdPayload.PROTOCOL) {
				LOGGER.warn("unsupported world_id protocol {} (expected {}), ignored",
						payload.protocol(), WhWorldIdPayload.PROTOCOL);
				return;
			}
			LOGGER.debug("world id received: \"{}\" ({} levels)", payload.worldId(), payload.levels().size());
			ServerWorldIdHolder.setAll(payload.worldId(), payload.levelName(), payload.levels());
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ServerWorldIdHolder.clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerWorldIdHolder.clear());
	}

	private ClientWorldIdReceiver() {
	}
}

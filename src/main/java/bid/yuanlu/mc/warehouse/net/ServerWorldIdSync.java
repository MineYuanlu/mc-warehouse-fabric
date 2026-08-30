package bid.yuanlu.mc.warehouse.net;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端 world 身份推送（PDD §4.2/§11）：玩家加入时推送存档级 worldId
 * （{@link WorldIdFile} 随机 id 文件）+ 存档名 + 全部维度列表，服务端 tick 检测
 * worldId 变化（多世界服务器切世界）后重发。
 * <p>
 * 单人与专用服统一：单人由集成服推送，客户端自动获得存档 id。
 * id 文件按 server 实例缓存（tick 每帧调用，不可每帧 IO）。
 */
public final class ServerWorldIdSync {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/server-world");

	private final Map<UUID, String> lastSent = new HashMap<>();
	private final Map<MinecraftServer, String> worldIds = new HashMap<>();

	public ServerWorldIdSync() {
		PayloadTypeRegistry.clientboundPlay().register(WhWorldIdPayload.TYPE, WhWorldIdPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			lastSent.remove(handler.player.getUUID());
			sendIfChanged(handler.player);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// 每 5s 周期性重发：客户端 JOIN 清空与推送到达存在顺序竞态，自愈丢失的推送
			boolean periodic = server.getTickCount() % 100 == 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (periodic) lastSent.remove(player.getUUID());
				sendIfChanged(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> worldIds.remove(server));
	}

	private void sendIfChanged(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, WhWorldIdPayload.TYPE)) return;
		String id = worldIdOf(player);
		if (Objects.equals(lastSent.get(player.getUUID()), id)) return;
		lastSent.put(player.getUUID(), id);
		ServerPlayNetworking.send(player, new WhWorldIdPayload(WhWorldIdPayload.PROTOCOL, id,
				levelNameOf(player), levelsOf(player)));
		LOGGER.debug("world id pushed to {}: \"{}\"", player.getName().getString(), id);
	}

	/** 当前玩家所在 world 的存档级 id；id 文件不可用时回退 {@code ""}（原版语义） */
	private String worldIdOf(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return "";
		String id = worldIds.computeIfAbsent(server, s -> {
			Path file = WorldIdFile.pathFor(s.getWorldPath(LevelResource.ROOT));
			return WorldIdFile.readOrCreate(file);
		});
		return id != null ? id : "";
	}

	@Nullable
	private static String levelNameOf(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return server != null ? server.getWorldData().getLevelName() : null;
	}

	private static List<String> levelsOf(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return List.of();
		return server.levelKeys().stream()
				.map(k -> k.identifier().toString())
				.sorted()
				.collect(Collectors.toList());
	}
}

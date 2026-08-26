package bid.yuanlu.mc.warehouse.impl.world;

import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * 单机世界标识（PDD §4.2）：{@code singleplayer:<存档目录名>}。
 */
public final class SingleplayerWorldIdentifier implements WorldIdentifier {

	public static final String ID = "singleplayer";

	@Override
	public String id() {
		return ID;
	}

	@Override
	@Nullable
	public String currentWorldId() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) return null;
		Path dir = server.getServerDirectory();
		String name = dir != null && dir.getFileName() != null ? dir.getFileName().toString() : "unknown";
		return ID + ":" + name;
	}
}

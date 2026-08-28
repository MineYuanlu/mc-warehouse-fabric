package bid.yuanlu.mc.warehouse.impl.world;

import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 单机世界标识（PDD §4.2）：{@code singleplayer:<存档目录名>}。
 * <p>
 * F1 修订：旧实现用 {@code getServerDirectory()}（26.1 返回游戏根目录/版本隔离目录名，
 * 如 "26.1.2-Fabric 0.19.3"），导致 worldId 出现版本号而非世界名。
 * 改为 {@code getWorldPath(LevelResource.ROOT)} 取存档目录（saves 下的世界文件夹名）。
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
		Path dir = server.getWorldPath(LevelResource.ROOT);
		String name = dir != null && dir.getFileName() != null ? dir.getFileName().toString() : "unknown";
		return ID + ":" + name;
	}
}

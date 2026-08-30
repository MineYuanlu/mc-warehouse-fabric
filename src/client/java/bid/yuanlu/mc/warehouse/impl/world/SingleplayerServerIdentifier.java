package bid.yuanlu.mc.warehouse.impl.world;

import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.ServerIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 单人服务器标识（PDD §4.2）：{@code singleplayer:<存档目录名>}。
 * <p>
 * F1 修订：旧实现用 {@code getServerDirectory()}（26.1 返回游戏根目录/版本隔离目录名，
 * 如 "26.1.2-Fabric 0.19.3"），导致 serverId 出现版本号而非世界名。
 * 改为 {@code getWorldPath(LevelResource.ROOT)} 取存档目录（saves 下的世界文件夹名）。
 * 单人存档即服务器，内部恒一个 world（worldId 缺省 {@code ""}）。
 */
public final class SingleplayerServerIdentifier implements ServerIdentifier {

	public static final String ID = "singleplayer";

	@Override
	@Nullable
	public String currentServerId() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) return null;
		Path dir = server.getWorldPath(LevelResource.ROOT);
		String name = "unknown";
		if (dir != null) dir = dir.toAbsolutePath().normalize();
		if (dir != null && dir.getFileName() != null) name = dir.getFileName().toString();
		return ID + ":" + name;
	}

	/** 当前存档的显示名（level.dat LevelName）；worldName 自动建映射的默认名 */
	@Nullable
	public static String currentLevelName() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		return server != null ? server.getWorldData().getLevelName() : null;
	}
}

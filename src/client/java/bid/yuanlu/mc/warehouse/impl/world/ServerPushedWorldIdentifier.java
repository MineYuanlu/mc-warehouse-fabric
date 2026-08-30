package bid.yuanlu.mc.warehouse.impl.world;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import bid.yuanlu.mc.warehouse.core.world.ServerWorldIdHolder;
import net.minecraft.client.Minecraft;

/**
 * 服务端推送 worldId（PDD §4.2）：本 mod 装在服务端（含单人的集成服）时经
 * {@code yuanlu-warehouse:world_id} payload 推送；未收到推送（原版服务端）返回 null
 * 由缺省 {@code ""} 接管。
 * <p>
 * v0.5 起单人同样生效：集成服推送存档级随机 id 文件（yuanluworldid.txt），
 * 存档目录改名不再影响 worldId。
 */
public final class ServerPushedWorldIdentifier implements WorldIdentifier {

	public static final String ID = "server_pushed";

	@Override
	public String id() {
		return ID;
	}

	@Override
	@Nullable
	public String currentWorldId() {
		if (Minecraft.getInstance().getSingleplayerServer() == null
				&& Minecraft.getInstance().getCurrentServer() == null) {
			return null;
		}
		return ServerWorldIdHolder.get();
	}
}

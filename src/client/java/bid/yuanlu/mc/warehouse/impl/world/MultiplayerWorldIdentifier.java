package bid.yuanlu.mc.warehouse.impl.world;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * 多人服务器世界标识（PDD §4.2）：{@code mp:<host>:<port>}，来源为 ServerData 连接地址。
 */
public final class MultiplayerWorldIdentifier implements WorldIdentifier {

	public static final String ID = "multiplayer";

	@Override
	public String id() {
		return ID;
	}

	@Override
	@Nullable
	public String currentWorldId() {
		ServerData data = Minecraft.getInstance().getCurrentServer();
		if (data == null || data.ip == null || data.ip.isEmpty()) return null;
		return ID + ":" + data.ip;
	}
}

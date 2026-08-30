package bid.yuanlu.mc.warehouse.impl.world;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.ServerIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * 多人服务器世界标识（PDD §4.2）：{@code mp:<host>:<port>}，来源为 ServerData 连接地址。
 * <p>
 * B8 修订：旧实现产出 {@code multiplayer:<ip>}（无端口），不符 PDD §4.2 格式——
 * 用户按文档示例写的 worlds 键永不命中。端口缺省 25565（原版默认端口）。
 * <b>破坏性变更</b>：旧格式 {@code multiplayer:*} 的配置键/缓存目录不再命中
 * （pre-release 阶段无迁移，2026-08-28 与作者确认）。
 */
public final class MultiplayerServerIdentifier implements ServerIdentifier {

	public static final String ID = "multiplayer";
	/** serverId 前缀（PDD §4.2） */
	public static final String PREFIX = "mp";
	/** ServerData 未显式携带端口时的原版默认端口 */
	public static final int DEFAULT_PORT = 25565;

	@Override
	@Nullable
	public String currentServerId() {
		ServerData data = Minecraft.getInstance().getCurrentServer();
		if (data == null || data.ip == null || data.ip.isEmpty()) return null;
		String host = data.ip;
		int port = DEFAULT_PORT;
		// IPv6 字面量含多个冒号——仅当恰好一个冒号时按 host:port 解析
		int first = data.ip.indexOf(':');
		if (first > 0 && first == data.ip.lastIndexOf(':') && first < data.ip.length() - 1) {
			try {
				port = Integer.parseInt(data.ip.substring(first + 1));
				host = data.ip.substring(0, first);
			} catch (NumberFormatException ignored) {
				// 冒号后非数字 → 整串视为主机名
			}
		}
		return PREFIX + ":" + host + ":" + port;
	}
}

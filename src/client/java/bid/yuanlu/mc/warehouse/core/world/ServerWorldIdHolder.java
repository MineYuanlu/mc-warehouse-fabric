package bid.yuanlu.mc.warehouse.core.world;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * 服务端推送的 world 身份（yuanlu-warehouse:world_id payload v2，PDD §4.2/§11）：
 * 存档级 worldId + 存档名 + 全部维度列表。
 * <p>
 * worldId null = 本会话尚未收到推送（原版服务端）——由 {@code WorldIdentifier} 缺省
 * {@code ""} 接管，语义相同。levelName/levels 仅在收到 v2 推送后有值。
 */
public final class ServerWorldIdHolder {

	private static volatile String worldId;
	private static volatile String levelName;
	private static volatile List<String> levels = List.of();

	private ServerWorldIdHolder() {
	}

	public static void set(@Nullable String id) {
		worldId = id;
	}

	@Nullable
	public static String get() {
		return worldId;
	}

	/** 服务端报告的存档名（level.dat LevelName / server.properties level-name）；未推送为 null */
	@Nullable
	public static String getLevelName() {
		return levelName;
	}

	/** 服务端报告的全部维度（ResourceKey identifier 排序）；未推送为空 */
	public static List<String> getLevels() {
		return levels;
	}

	/** v2 推送完整覆盖（三字段原子性由不可变快照保证） */
	public static void setAll(@Nullable String id, @Nullable String name, List<String> dims) {
		worldId = id;
		levelName = name;
		levels = List.copyOf(dims);
	}

	/** 清空全部（JOIN/DISCONNECT 时防上一服务器残留） */
	public static void clear() {
		worldId = null;
		levelName = null;
		levels = List.of();
	}
}

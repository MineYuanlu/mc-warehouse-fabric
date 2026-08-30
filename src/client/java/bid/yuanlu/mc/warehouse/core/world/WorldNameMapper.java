package bid.yuanlu.mc.warehouse.core.world;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * worldName ↔ worldId 映射（PDD §4.4）：玩家可控的世界命名层。
 * <p>
 * 会话激活时经 {@link #resolveActive} 解析当前 worldId 对应的 worldName；
 * 无映射则按 {@link DefaultNameResolver} 自动创建默认条目（多人 = worldId 本身，
 * 单人 = level.dat 的 LevelName）并持久化到 world-map.json。
 * 同一 worldId 允许多别名，激活名取插入序第一个。
 */
public final class WorldNameMapper {

	/** 默认 worldName 生成（生产：单人取 LevelName，其余取 worldId；测试注入固定值） */
	public interface DefaultNameResolver {
		String defaultName(String serverId, String worldId);
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/world-map");

	private final Path file;
	private final DefaultNameResolver defaultNames;
	private final WorldMapIO.Data data;

	private static volatile WorldNameMapper instance;

	/** 全局实例（客户端初始化时装配） */
	public static WorldNameMapper get() {
		WorldNameMapper m = instance;
		if (m == null) throw new IllegalStateException("WorldNameMapper not initialized");
		return m;
	}

	public static void setInstance(@Nullable WorldNameMapper mapper) {
		instance = mapper;
	}

	public WorldNameMapper(Path file, DefaultNameResolver defaultNames) {
		this.file = java.util.Objects.requireNonNull(file, "file");
		this.defaultNames = Objects.requireNonNull(defaultNames, "defaultNames");
		this.data = WorldMapIO.load(file);
	}

	/** 内存实例（不落盘；测试/临时装配用） */
	public WorldNameMapper(DefaultNameResolver defaultNames) {
		this.file = null;
		this.defaultNames = Objects.requireNonNull(defaultNames, "defaultNames");
		this.data = new WorldMapIO.Data();
	}

	/**
	 * 解析 (serverId, worldId) 的激活 worldName；无映射时自动建默认条目并持久化。
	 * 先执行 {@code ""} 绑定迁移（v0.5：worldId 从缺省 {@code ""} 变为存档 id 文件值
	 * 后，旧条目随新 id 重绑，锚点不断链；幂等）。永不返回 null（持久化失败也保证
	 * 内存视图可用）。
	 */
	public synchronized String resolveActive(String serverId, String worldId) {
		LinkedHashMap<String, String> worlds = data.servers.computeIfAbsent(serverId, k -> new LinkedHashMap<>());
		migrateEmptyBindings(serverId, worldId);
		for (Map.Entry<String, String> e : worlds.entrySet()) {
			if (e.getValue().equals(worldId)) return e.getKey();
		}
		String name = uniqueName(worlds, defaultNames.defaultName(serverId, worldId));
		worlds.put(name, worldId);
		persist();
		LOGGER.info("world mapped: {} -> \"{}\" = \"{}\"", serverId, name, worldId);
		return name;
	}

	/**
	 * 把某 serverId 下所有绑定 {@code ""} 的条目重绑为 worldId（v0.5 升级迁移，
	 * PDD §11.4）。幂等：无 {@code ""} 条目时 no-op；worldId 为空时拒绝。
	 *
	 * @return 重绑条目数
	 */
	public synchronized int migrateEmptyBindings(String serverId, String worldId) {
		if (worldId == null || worldId.isEmpty()) return 0;
		LinkedHashMap<String, String> worlds = data.servers.get(serverId);
		if (worlds == null) return 0;
		int n = 0;
		for (Map.Entry<String, String> e : worlds.entrySet()) {
			if (e.getValue().isEmpty()) {
				e.setValue(worldId);
				n++;
			}
		}
		if (n > 0) {
			persist();
			LOGGER.info("world-map migrated: {} rebind {} empty entries to \"{}\"", serverId, n, worldId);
		}
		return n;
	}

	/**
	 * 手动绑定/换绑（PDD §4.4，{@code /wh world bind}）：worldName 已存在则覆盖其
	 * worldId，否则插入。激活名仍为插入序第一个命中 worldId 的别名。
	 */
	public synchronized void bind(String serverId, String worldName, String worldId) {
		if (worldName == null || worldName.isBlank()) throw new IllegalArgumentException("blank world name");
		if (worldId == null || worldId.isEmpty()) throw new IllegalArgumentException("blank world id");
		LinkedHashMap<String, String> worlds = data.servers.computeIfAbsent(serverId, k -> new LinkedHashMap<>());
		worlds.put(worldName, worldId);
		persist();
		LOGGER.info("world bound: {} \"{}\" = \"{}\"", serverId, worldName, worldId);
	}

	/** 重命名（serverId 维度内 from 必须存在、to 必须不存在且非空白） */
	public synchronized void rename(String serverId, String from, String to) {
		if (to == null || to.isBlank()) throw new IllegalArgumentException("blank world name");
		LinkedHashMap<String, String> worlds = data.servers.get(serverId);
		if (worlds == null || !worlds.containsKey(from)) {
			throw new IllegalArgumentException("no such world name: " + from);
		}
		if (worlds.containsKey(to)) throw new IllegalArgumentException("world name exists: " + to);
		worlds.put(to, worlds.remove(from));
		persist();
		LOGGER.info("world renamed: {} \"{}\" -> \"{}\"", serverId, from, to);
	}

	/** (serverId, worldName) 的 worldId；不存在返回 null */
	@Nullable
	public synchronized String worldIdOf(String serverId, String worldName) {
		LinkedHashMap<String, String> worlds = data.servers.get(serverId);
		return worlds != null ? worlds.get(worldName) : null;
	}

	/** 某服务器的全部映射条目（worldName → worldId，保插入序） */
	public synchronized List<Map.Entry<String, String>> worlds(String serverId) {
		LinkedHashMap<String, String> worlds = data.servers.get(serverId);
		if (worlds == null || worlds.isEmpty()) return List.of();
		return new ArrayList<>(worlds.entrySet());
	}

	/** 默认名与既有名冲突时追加 #2、#3… 后缀 */
	private static String uniqueName(LinkedHashMap<String, String> worlds, String base) {
		String name = base;
		int n = 2;
		while (worlds.containsKey(name)) {
			name = base + "#" + n;
			n++;
		}
		return name;
	}

	private void persist() {
		if (file == null) return;
		try {
			WorldMapIO.save(file, data);
		} catch (Exception e) {
			LOGGER.warn("world-map persist failed: {}", e.toString());
		}
	}
}

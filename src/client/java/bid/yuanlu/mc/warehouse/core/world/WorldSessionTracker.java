package bid.yuanlu.mc.warehouse.core.world;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.ServerIdentifier;
import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.impl.world.MultiplayerServerIdentifier;
import bid.yuanlu.mc.warehouse.impl.world.SingleplayerServerIdentifier;

/**
 * 会话追踪（PDD §4.1）：每 tick 解析当前 (serverId, worldId)，经 {@link WorldNameMapper}
 * 得到 worldName 组成 {@link WorldSession}。serverId 或 worldId 任一变化（含改名导致
 * worldName 变化）视为会话切换并广播。
 * <p>
 * 订阅方（缓存层、传输引擎）在切换时分别执行：MEMORY 缓存清空、DISK 缓存卸载、
 * 运行中的搬运终止并报告。serverId 解析顺序 = 内置固定实现；worldId 解析顺序 =
 * 注册表 SPI 顺序，首个非 null 生效（null = 不适用），全部不适用时缺省 {@code ""}。
 */
public final class WorldSessionTracker {

	/** 会话切换监听；回调在客户端主线程执行，不得阻塞 */
	public interface SessionListener {
		void onSessionChanged(@Nullable WorldSession oldSession, @Nullable WorldSession newSession);
	}

	private final List<ServerIdentifier> serverIdentifiers;
	private final List<WorldIdentifier> worldIdentifiers;
	private final WorldNameMapper mapper;

	private WorldSession current;

	private static volatile WorldSessionTracker instance;

	/** 全局实例（客户端初始化时装配） */
	public static WorldSessionTracker get() {
		WorldSessionTracker t = instance;
		if (t == null) throw new IllegalStateException("WorldSessionTracker not initialized");
		return t;
	}

	public static void setInstance(@Nullable WorldSessionTracker tracker) {
		instance = tracker;
	}

	/** 默认装配：内置 server 实现 + 注册表 SPI + 全局 mapper */
	public WorldSessionTracker() {
		this(List.of(new SingleplayerServerIdentifier(), new MultiplayerServerIdentifier()),
				WarehouseRegistryImpl.worldIdentifiers(), WorldNameMapper.get());
	}

	/** 注入式构造（测试用） */
	public WorldSessionTracker(List<ServerIdentifier> serverIdentifiers,
			List<WorldIdentifier> worldIdentifiers, WorldNameMapper mapper) {
		this.serverIdentifiers = List.copyOf(serverIdentifiers);
		this.worldIdentifiers = List.copyOf(worldIdentifiers);
		this.mapper = Objects.requireNonNull(mapper, "mapper");
	}

	/** 注入式构造（测试用）：不落盘 mapper，无 server 端注入 */
	public WorldSessionTracker(List<WorldIdentifier> worldIdentifiers) {
		this(List.of(), worldIdentifiers, new WorldNameMapper((s, w) -> ""));
	}

	/** 注入式构造（测试用）：不落盘 mapper */
	public WorldSessionTracker(List<ServerIdentifier> serverIdentifiers,
			List<WorldIdentifier> worldIdentifiers) {
		this(serverIdentifiers, worldIdentifiers, new WorldNameMapper((s, w) -> ""));
	}

	private final CopyOnWriteArrayList<SessionListener> listeners = new CopyOnWriteArrayList<>();

	public void addListener(SessionListener listener) {
		listeners.add(Objects.requireNonNull(listener, "listener"));
	}

	public void removeListener(SessionListener listener) {
		listeners.remove(listener);
	}

	/** 当前会话快照；会话未就绪（主菜单/已断开）返回 null */
	@Nullable
	public WorldSession currentSession() {
		return current;
	}

	@Nullable
	public String currentServerId() {
		return current != null ? current.serverId() : null;
	}

	@Nullable
	public String currentWorldId() {
		return current != null ? current.worldId() : null;
	}

	@Nullable
	public String currentWorldName() {
		return current != null ? current.worldName() : null;
	}

	/** 每 tick 调用：解析当前会话并处理切换 */
	public void tick() {
		update(resolve());
	}

	/** 应用一次解析结果（公开以便无客户端环境测试注入）；变化时广播 */
	public void update(@Nullable WorldSession resolved) {
		if (Objects.equals(resolved, current)) return;
		WorldSession old = current;
		current = resolved;
		for (SessionListener listener : listeners) {
			listener.onSessionChanged(old, resolved);
		}
	}

	@Nullable
	private WorldSession resolve() {
		String serverId = null;
		for (ServerIdentifier si : serverIdentifiers) {
			serverId = si.currentServerId();
			if (serverId != null && !serverId.isEmpty()) break;
		}
		if (serverId == null || serverId.isEmpty()) return null;

		String worldId = null;
		for (WorldIdentifier wi : worldIdentifiers) {
			worldId = wi.currentWorldId();
			if (worldId != null) break;
		}
		if (worldId == null) worldId = "";
		String worldName = mapper.resolveActive(serverId, worldId);
		return new WorldSession(serverId, worldId, worldName != null ? worldName : worldId);
	}
}

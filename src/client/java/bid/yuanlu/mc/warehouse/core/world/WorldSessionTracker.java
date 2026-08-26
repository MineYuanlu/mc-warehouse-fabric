package bid.yuanlu.mc.warehouse.core.world;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;

/**
 * 会话追踪（PDD §4.1）：每 tick 缓存当前 worldId；变化视为会话切换并广播。
 * <p>
 * 订阅方（缓存层、传输引擎）在切换时分别执行：MEMORY 缓存清空、DISK 缓存卸载、
 * 运行中的搬运终止并报告。解析顺序 = 注册表顺序，首个返回非 null 的标识生效。
 */
public final class WorldSessionTracker {

	/** 会话切换监听；回调在客户端主线程执行，不得阻塞 */
	public interface SessionListener {
		void onSessionChanged(@Nullable String oldWorldId, @Nullable String newWorldId);
	}

	private final List<WorldIdentifier> identifiers;
	private final CopyOnWriteArrayList<SessionListener> listeners = new CopyOnWriteArrayList<>();

	private String currentWorldId;

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

	public WorldSessionTracker() {
		this(WarehouseRegistryImpl.worldIdentifiers());
	}

	/** 注入式构造（测试用） */
	public WorldSessionTracker(List<WorldIdentifier> identifiers) {
		this.identifiers = List.copyOf(identifiers);
	}

	public void addListener(SessionListener listener) {
		listeners.add(Objects.requireNonNull(listener, "listener"));
	}

	public void removeListener(SessionListener listener) {
		listeners.remove(listener);
	}

	/** 当前 worldId；会话未就绪（主菜单/已断开）返回 null */
	@Nullable
	public String currentWorldId() {
		return currentWorldId;
	}

	/** 每 tick 调用：解析当前 worldId 并处理会话切换 */
	public void tick() {
		update(resolveCurrentWorldId());
	}

	/** 应用一次解析结果（公开以便无客户端环境测试注入）；变化时广播 */
	public void update(@Nullable String resolved) {
		if (Objects.equals(resolved, currentWorldId)) return;
		String old = currentWorldId;
		currentWorldId = resolved;
		for (SessionListener listener : listeners) {
			listener.onSessionChanged(old, resolved);
		}
	}

	@Nullable
	private String resolveCurrentWorldId() {
		for (WorldIdentifier identifier : identifiers) {
			String id = identifier.currentWorldId();
			if (id != null && !id.isEmpty()) return id;
		}
		return null;
	}
}

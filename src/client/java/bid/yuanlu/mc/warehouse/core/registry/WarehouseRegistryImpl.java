package bid.yuanlu.mc.warehouse.core.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;
import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.item.SlotAllocator;
import bid.yuanlu.mc.warehouse.api.navigation.Navigator;
import bid.yuanlu.mc.warehouse.api.plugin.AgentPlanner;
import bid.yuanlu.mc.warehouse.api.plugin.SelectorCodec;
import bid.yuanlu.mc.warehouse.api.plugin.WarehouseRegistry;
import bid.yuanlu.mc.warehouse.api.world.WorldIdentifier;

/**
 * {@link WarehouseRegistry} 默认实现（PDD §9.2）：注册期限于客户端初始化阶段，此后冻结。
 * <p>
 * 内置实现与插件经同一机制注册；重复 id 快速失败。
 */
public final class WarehouseRegistryImpl implements WarehouseRegistry {

	private static final List<ContainerDetector> DETECTORS = new ArrayList<>();
	private static final List<Navigator> NAVIGATORS = new ArrayList<>();
	private static final List<WorldIdentifier> WORLD_IDENTIFIERS = new ArrayList<>();
	private static final List<ContainerInteraction> INTERACTIONS = new ArrayList<>();
	private static final List<SlotAllocator> ALLOCATORS = new ArrayList<>();
	private static final List<AgentPlanner> AGENT_PLANNERS = new ArrayList<>();

	private static volatile boolean frozen = false;

	/** 全局单例（客户端初始化时装配） */
	private static final WarehouseRegistryImpl INSTANCE = new WarehouseRegistryImpl();

	public static WarehouseRegistryImpl get() {
		return INSTANCE;
	}

	private WarehouseRegistryImpl() {
	}

	// ---- 注册 ----

	@Override
	public void registerDetector(ContainerDetector detector) {
		registerUnique(DETECTORS, detector, "detector");
	}

	@Override
	public void registerNavigator(Navigator navigator) {
		registerUnique(NAVIGATORS, navigator, "navigator");
	}

	@Override
	public void registerWorldIdentifier(WorldIdentifier identifier) {
		registerUnique(WORLD_IDENTIFIERS, identifier, "world identifier");
	}

	@Override
	public void registerInteraction(ContainerInteraction interaction) {
		registerUnique(INTERACTIONS, interaction, "interaction");
	}

	@Override
	public void registerSlotAllocator(SlotAllocator allocator) {
		registerUnique(ALLOCATORS, allocator, "slot allocator");
	}

	@Override
	public void registerAgentPlanner(AgentPlanner planner) {
		registerUnique(AGENT_PLANNERS, planner, "agent planner");
	}

	@Override
	public void registerItemSelectorCodec(SelectorCodec<? extends ItemSelector> codec) {
		assertNotFrozen();
		SelectorCodecs.registerItem(codec);
	}

	@Override
	public void registerQuantitySelectorCodec(SelectorCodec<? extends QuantitySelector> codec) {
		assertNotFrozen();
		SelectorCodecs.registerQuantity(codec);
	}

	private static <T> void registerUnique(List<T> list, T value, String kind) {
		Objects.requireNonNull(value, kind);
		String id = requireCapabilityId(value, kind);
		synchronized (list) {
			assertNotFrozen();
			for (T existing : list) {
				if (requireCapabilityId(existing, kind).equals(id)) {
					throw new IllegalArgumentException("Duplicate " + kind + " id: " + id);
				}
			}
			list.add(value);
		}
	}

	private static String requireCapabilityId(Object value, String kind) {
		if (value instanceof ContainerDetector d) return d.id();
		if (value instanceof Navigator n) return n.id();
		if (value instanceof WorldIdentifier w) return w.id();
		if (value instanceof ContainerInteraction i) return i.id();
		if (value instanceof SlotAllocator a) return a.id();
		if (value instanceof AgentPlanner p) return p.id();
		throw new IllegalArgumentException("Unknown " + kind + " capability: " + value.getClass());
	}

	/** 冻结注册表（全部内置+插件注册完成后调用）；此后注册一律拒绝 */
	public static void freeze() {
		frozen = true;
	}

	public static boolean isFrozen() {
		return frozen;
	}

	private static void assertNotFrozen() {
		if (frozen) throw new IllegalStateException("WarehouseRegistry is frozen");
	}

	// ---- 查询（运行期）----

	public static List<ContainerDetector> detectors() {
		synchronized (DETECTORS) {
			return List.copyOf(DETECTORS);
		}
	}

	public static List<Navigator> navigators() {
		synchronized (NAVIGATORS) {
			return List.copyOf(NAVIGATORS);
		}
	}

	public static List<WorldIdentifier> worldIdentifiers() {
		synchronized (WORLD_IDENTIFIERS) {
			return List.copyOf(WORLD_IDENTIFIERS);
		}
	}

	@Nullable
	public static ContainerDetector detector(@Nullable String id) {
		if (id == null) return null;
		for (ContainerDetector d : detectors()) {
			if (d.id().equals(id)) return d;
		}
		return null;
	}

	@Nullable
	public static Navigator navigator(@Nullable String id) {
		if (id == null) return null;
		for (Navigator n : navigators()) {
			if (n.id().equals(id)) return n;
		}
		return null;
	}

	@Nullable
	public static ContainerInteraction interaction(@Nullable String id) {
		if (id == null) return null;
		for (ContainerInteraction i : interactions()) {
			if (i.id().equals(id)) return i;
		}
		return null;
	}

	@Nullable
	public static SlotAllocator slotAllocator(@Nullable String id) {
		if (id == null) return null;
		for (SlotAllocator a : allocators()) {
			if (a.id().equals(id)) return a;
		}
		return null;
	}

	public static List<ContainerInteraction> interactions() {
		synchronized (INTERACTIONS) {
			return List.copyOf(INTERACTIONS);
		}
	}

	public static List<SlotAllocator> allocators() {
		synchronized (ALLOCATORS) {
			return List.copyOf(ALLOCATORS);
		}
	}

	public static List<AgentPlanner> agentPlanners() {
		synchronized (AGENT_PLANNERS) {
			return List.copyOf(AGENT_PLANNERS);
		}
	}

	// ---- 测试辅助 ----

	/** 仅测试用：清空注册并解冻（生产代码禁止调用） */
	public static void resetForTest() {
		synchronized (DETECTORS) {
			DETECTORS.clear();
			NAVIGATORS.clear();
			WORLD_IDENTIFIERS.clear();
			INTERACTIONS.clear();
			ALLOCATORS.clear();
			AGENT_PLANNERS.clear();
			frozen = false;
		}
		SelectorCodecs.resetForTest();
	}
}

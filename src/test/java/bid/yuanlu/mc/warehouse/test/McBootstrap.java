package bid.yuanlu.mc.warehouse.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Lifecycle;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;

import org.junit.jupiter.api.BeforeAll;

/**
 * 需要 MC 注册表 / 静态初始化的测试基类。
 * <p>
 * fabric-loader-junit 已引导 loader 并探测游戏版本，但不会构造 Minecraft 客户端；
 * 此处手动 Bootstrap 以初始化纯数据类 (Registry 等)。
 * <p>
 * MC 26.1 起 {@link Bootstrap#bootStrap()} 之后还需执行组件初始化器
 * （否则 {@code new ItemStack(...)} 抛 "Components not bound yet"）。
 * 初始化器会引用数据包侧内容（如 fire_resistant → {@code damage_type/is_fire} 标签，
 * 而 damage_type 本身是动态注册表），裸 Bootstrap 环境不存在——
 * 用「缺失标签解析为空集、缺失注册表解析为空表」的宽松 Provider 完成绑定；
 * 真实注册表的绑定状态不受影响（依赖标签的选择器测试按未加载断言）。
 */
public abstract class McBootstrap {

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		bindBuiltInComponents();
	}

	static void bindBuiltInComponents() {
		HolderLookup.Provider access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		for (DataComponentInitializers.PendingComponents<?> pending : BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
				.build(lenientTagProvider(access))) {
			pending.apply();
		}
	}

	private static final Object NOT_HANDLED = new Object();

	/** 宽松合成 Holder 共用的占位 owner */
	private static final HolderOwner LENIENT_OWNER = new HolderOwner() {
	};

	private interface Special {
		Object get() throws Throwable;
	}

	private static Object invoke(Object delegate, Method method, Object[] args, Special special) throws Throwable {
		Object handled = special.get();
		if (handled != NOT_HANDLED) return handled;
		try {
			return method.invoke(delegate, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	// ---- 宽松 Provider：缺失注册表 → 空表；缺失标签 → 空 Named ----

	@SuppressWarnings({"unchecked", "rawtypes"})
	static HolderLookup.Provider lenientTagProvider(HolderLookup.Provider delegate) {
		ClassLoader cl = McBootstrap.class.getClassLoader();
		return (HolderLookup.Provider) Proxy.newProxyInstance(cl, new Class<?>[]{HolderLookup.Provider.class},
				(proxy, method, args) -> invoke(delegate, method, args, () -> {
					if (method.getName().equals("lookupOrThrow")) {
						ResourceKey key = (ResourceKey) args[0];
						Optional inner = delegate.lookup(key);
						Object resolved = inner.isPresent()
								? lenientLookup((HolderLookup.RegistryLookup) inner.get())
								: emptyLookup(key);
						return resolved;
					}
					if (method.getName().equals("lookup")) {
						Optional inner = delegate.lookup((ResourceKey) args[0]);
						return inner.isEmpty() ? Optional.empty()
								: Optional.of(lenientLookup((HolderLookup.RegistryLookup) inner.get()));
					}
					// Provider 本身也是 HolderGetter：直接落在 Provider 上的查询转发到宽松 lookup
					if (args != null && args.length == 1
							&& (method.getName().equals("get") || method.getName().equals("getOrThrow"))) {
						if (args[0] instanceof TagKey<?> tag) {
							HolderLookup.RegistryLookup lookup = (HolderLookup.RegistryLookup) resolveLookup(delegate, tag.registry());
							Optional<? extends HolderSet.Named> named = lookup.get(tag);
							return method.getReturnType() == Optional.class ? named : named.orElseThrow();
						}
						if (args[0] instanceof ResourceKey<?> element) {
							return lenientElement(delegate, method, element);
						}
					}
					return NOT_HANDLED;
				}));
	}

	@SuppressWarnings("rawtypes")
	private static Object resolveLookup(HolderLookup.Provider delegate, ResourceKey key) throws Throwable {
		Optional inner = delegate.lookup(key);
		return inner.isPresent() ? lenientLookup((HolderLookup.RegistryLookup) inner.get()) : emptyLookup(key);
	}

	/** Provider 层的元素查询：缺失元素/注册表 → 合成 stand-alone 占位 Holder */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object lenientElement(HolderLookup.Provider delegate, Method method, ResourceKey element)
			throws Throwable {
		// ResourceKey.registry() 返回注册表的 Identifier，需还原为注册表自身的 ResourceKey
		ResourceKey registryKey = ResourceKey.createRegistryKey(element.registry());
		Optional inner = delegate.lookup(registryKey);
		if (inner.isPresent()) {
			Optional existing = ((HolderLookup.RegistryLookup) inner.get()).get(element);
			if (existing.isPresent()) return method.getReturnType() == Optional.class ? existing : existing.get();
			Object holder = net.minecraft.core.Holder.Reference.createStandAlone((HolderOwner) inner.get(), element);
			return method.getReturnType() == Optional.class ? Optional.of(holder) : holder;
		}
		Object holder = net.minecraft.core.Holder.Reference.createStandAlone(LENIENT_OWNER, element);
		return method.getReturnType() == Optional.class ? Optional.of(holder) : holder;
	}

	/** RegistryLookup 代理：get/getOrThrow(TagKey) 对未绑定标签返回空 Named（RegistryLookup 自身即 HolderOwner） */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static HolderLookup.RegistryLookup<?> lenientLookup(HolderLookup.RegistryLookup<?> inner) {
		ClassLoader cl = McBootstrap.class.getClassLoader();
		return (HolderLookup.RegistryLookup) Proxy.newProxyInstance(cl,
				new Class<?>[]{HolderLookup.RegistryLookup.class}, (proxy, method, args) -> invoke(inner, method, args, () -> {
					boolean tagQuery = args != null && args.length == 1 && args[0] instanceof TagKey
							&& (method.getName().equals("get") || method.getName().equals("getOrThrow"));
					if (!tagQuery) return NOT_HANDLED;
					Optional<? extends HolderSet.Named> existing = inner.get((TagKey) args[0]);
					if (existing.isPresent()) return existing.get();
					HolderSet.Named empty = HolderSet.emptyNamed((HolderOwner) inner, (TagKey) args[0]);
					boolean optionalReturn = method.getReturnType() == Optional.class;
					return optionalReturn ? Optional.of(empty) : empty;
				}));
	}

	/** 合成不存在的（动态）注册表的空 RegistryLookup：仅满足标签引用，一切元素查询为空 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static HolderLookup.RegistryLookup<?> emptyLookup(ResourceKey<? extends net.minecraft.core.Registry<?>> key) {
		ClassLoader cl = McBootstrap.class.getClassLoader();
		HolderOwner owner = new HolderOwner() {
		};
		return (HolderLookup.RegistryLookup) Proxy.newProxyInstance(cl,
				new Class<?>[]{HolderLookup.RegistryLookup.class}, (proxy, method, args) -> {
					try {
						return switch (method.getName()) {
							case "key" -> key;
							case "registryLifecycle" -> Lifecycle.stable();
							case "listTags", "listElements" -> Stream.empty();
							default -> {
								if (args != null && args.length == 1 && args[0] instanceof TagKey
										&& (method.getName().equals("get") || method.getName().equals("getOrThrow"))) {
									HolderSet.Named empty = HolderSet.emptyNamed(owner, (TagKey) args[0]);
									yield method.getReturnType() == Optional.class ? Optional.of(empty) : empty;
								}
								if (method.getName().equals("get")) yield Optional.empty();
								if (method.getName().equals("getOrThrow") && args != null && args.length == 1
										&& args[0] instanceof ResourceKey element) {
									yield net.minecraft.core.Holder.Reference.createStandAlone(owner, element);
								}
								yield defaultReturn(method);
							}
						};
					} catch (Throwable t) {
						throw t;
					}
				});
	}

	private static Object defaultReturn(Method method) {
		Class<?> r = method.getReturnType();
		if (r == boolean.class) return false;
		if (r == int.class) return 0;
		if (r == long.class) return 0L;
		if (r == float.class) return 0f;
		if (r == double.class) return 0d;
		if (Optional.class.isAssignableFrom(r)) return Optional.empty();
		if (Stream.class.isAssignableFrom(r)) return Stream.empty();
		if (List.class.isAssignableFrom(r) || java.util.Collection.class.isAssignableFrom(r)) return List.of();
		throw new UnsupportedOperationException("lenient empty registry lookup: " + method);
	}
}

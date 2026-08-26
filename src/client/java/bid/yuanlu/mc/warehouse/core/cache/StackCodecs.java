package bid.yuanlu.mc.warehouse.core.cache;

import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * ItemStack ↔ SNBT 字符串编解码：DISK 缓存落盘的载荷格式（cache/&lt;worldId&gt;/）。
 * <p>
 * 解码失败（未知物品/组件跨版本不兼容）由调用方按「缓存无效」处理，不影响正确性。
 */
public final class StackCodecs {

	private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_\\-.]+");

	private static volatile HolderLookup.Provider provider;

	private StackCodecs() {
	}

	/**
	 * 注册表 Provider。生产环境（客户端初始化后）直接取内置注册表；
	 * 测试等无客户端环境可用 {@link #setProvider} 注入已引导的实现。
	 */
	public static HolderLookup.Provider provider() {
		if (provider == null) {
			synchronized (StackCodecs.class) {
				if (provider == null) {
					provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
				}
			}
		}
		return provider;
	}

	/** 注入 Provider（测试/无客户端环境；须已完成组件绑定） */
	public static void setProvider(HolderLookup.Provider p) {
		provider = java.util.Objects.requireNonNull(p, "provider");
	}

	/** 序列化为 SNBT 文本；失败返回 null */
	@Nullable
	public static String encode(ItemStack stack) {
		try {
			DynamicOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider());
			return ItemStack.CODEC.encodeStart(ops, stack).result().map(net.minecraft.nbt.Tag::toString).orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	@Nullable
	public static ItemStack decode(@Nullable String snbt) {
		if (snbt == null || snbt.isEmpty()) return null;
		try {
			CompoundTag tag = TagParser.parseCompoundFully(snbt);
			DynamicOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider());
			return ItemStack.CODEC.parse(ops, tag).result().filter(s -> !s.isEmpty()).orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/** worldId → 目录名安全化 */
	public static String sanitizeWorldDir(String worldId) {
		String cleaned = worldId.replaceAll("[^A-Za-z0-9_\\-.]", "_");
		return SAFE_NAME.matcher(cleaned).matches() && !cleaned.isBlank() ? cleaned : "world_" + Integer.toHexString(worldId.hashCode());
	}
}

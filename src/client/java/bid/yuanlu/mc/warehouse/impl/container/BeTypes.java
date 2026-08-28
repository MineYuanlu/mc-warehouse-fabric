package bid.yuanlu.mc.warehouse.impl.container;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 方块实体类型注册表解析（PDD §8.5）。
 *
 * <p>26.2 起 vanilla 移除了 {@code BlockEntityType.CHEST} 等静态常量，改为纯注册表；
 * universal jar 策略下不得引用任何版本易变的静态字段，统一经注册表 ID 解析
 * （vanilla 注册键跨版本稳定）。</p>
 */
public final class BeTypes {

	private BeTypes() {
	}

	/** 按注册键解析方块实体类型；缺失即抛错（版本漂移早暴露，禁止静默降级）。 */
	public static BlockEntityType<?> of(String id) {
		BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE
				.getValue(Identifier.withDefaultNamespace(id));
		if (type == null) {
			throw new IllegalStateException("BlockEntityType not registered: minecraft:" + id
					+ " (vanilla API drift?)");
		}
		return type;
	}
}

package bid.yuanlu.mc.warehouse.api.container;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

/**
 * 容器检测器 SPI（PDD §8.1）：识别容器类型并产出带槽位能力的快照。
 * <p>
 * 身份识别必须组合判定：仅凭 Screen 类不可靠（箱子/木桶/陷阱箱共用同类 Screen），
 * 须叠加方块实体类型与标题/槽位数（PDD §8.1）。
 */
public interface ContainerDetector {

	String id();

	/** 打开前预判：目标方块（含方块实体类型）是否属于此容器类型 */
	boolean matchesBlock(BlockInWorld pos);

	/** 打开后校验：组合判定（方块实体类型 + Screen 标题 + 槽位数），ctx 携带 PRECHECK 捕获的方块快照 */
	boolean matches(AbstractContainerScreen<?> screen, ContainerOpenContext ctx);

	/** 扫描当前打开 Screen 的容器侧内容，返回快照（含槽位能力，§8.2） */
	ContainerSnapshot scan(AbstractContainerScreen<?> screen);

	/**
	 * 解析多格容器：给定一组坐标，返回合并后的 ContainerInfo；无法合并时返回 null。
	 * （如大箱子自动检测双格并关联，PDD §3.2）
	 */
	ContainerInfo resolveMultiBlock(BlockPos[] positions);

	/**
	 * 该类型容器的内容是否因玩家而异（如末影箱）——缓存键需附加 playerUUID（PDD §3.8）。
	 */
	default boolean playerScoped() {
		return false;
	}
}

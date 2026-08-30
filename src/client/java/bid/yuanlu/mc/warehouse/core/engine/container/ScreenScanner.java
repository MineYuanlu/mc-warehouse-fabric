package bid.yuanlu.mc.warehouse.core.engine.container;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.container.ContainerOpenContext;
import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 屏幕扫描共享工具（F2）：对真实打开的容器 Screen，用 Detector 注册链做
 * 身份匹配 + 全量扫描，产出 {@link ScanResult}。
 * <p>
 * 标记模式与玩家开箱刷新（PlayerOpenRefresher）共用，保证扫描口径唯一。
 * 快照只来自真实打开的 Screen（红线：操作依据只能来自对账后的快照）。
 */
public final class ScreenScanner {

	private static final Logger LOG = LoggerFactory.getLogger("yuanlu-warehouse/screen");

	/** 匹配成功的 Detector 及其扫描快照 */
	public record ScanResult(ContainerDetector detector, ContainerSnapshot snapshot) {
	}

	/** 对打开的 Screen 做身份校验 + 扫描；无匹配 Detector 返回 null */
	@Nullable
	public static ScanResult scan(AbstractContainerScreen<?> screen, BlockInWorld block) {
		String worldName = WorldSessionTracker.get() == null ? null : WorldSessionTracker.get().currentWorldName();
		String dimId = Minecraft.getInstance().level == null ? null
				: Minecraft.getInstance().level.dimension().identifier().toString();
		LOG.debug("scanning: menu={}, blockEntity={}, title={}",
				screen.getMenu().getType(), block.getEntity(), screen.getTitle().getString());
		ContainerOpenContext ctx = new ContainerOpenContext(
				new WorldDimPos(worldName, dimId, block.getPos().getX(), block.getPos().getY(), block.getPos().getZ()),
				block);
		for (ContainerDetector d : WarehouseRegistryImpl.detectors()) {
			try {
				if (d.matches(screen, ctx)) {
					ContainerSnapshot snap = d.scan(screen);
					return new ScanResult(d, snap);
				}
			} catch (Exception e) {
				LOG.warn("detector {} threw: {}", d.id(), e.toString());
			}
		}
		return null;
	}

	private ScreenScanner() {
	}
}

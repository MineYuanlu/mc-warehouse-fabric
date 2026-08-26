package bid.yuanlu.mc.warehouse.api.interaction;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 一次容器会话的句柄（PDD §8.3）：pos + 当前 Screen/Menu 引用 + 身份信息。
 * <p>
 * 由协议层（ContainerSession）创建与维护：requestOpen 前只有 pos，
 * 开屏确认后 {@link #bind} 绑定 Screen；一切回调按会话身份门控。
 */
public final class ContainerHandle {

	private final WorldDimPos pos;

	private volatile AbstractContainerScreen<?> screen;

	public ContainerHandle(WorldDimPos pos) {
		this.pos = java.util.Objects.requireNonNull(pos, "pos");
	}

	public WorldDimPos pos() {
		return pos;
	}

	/** 会话建立后的 Screen；未打开/已关闭返回 null */
	@Nullable
	public AbstractContainerScreen<?> screen() {
		return screen;
	}

	/** 当前菜单；等价于 screen().getMenu()，未绑定时为 null */
	@Nullable
	public AbstractContainerMenu menu() {
		AbstractContainerScreen<?> s = screen;
		return s != null ? s.getMenu() : null;
	}

	/** 会话是否已建立且 Screen 已绑定 */
	public boolean isOpen() {
		return screen != null;
	}

	/** 协议层在开屏确认后绑定 Screen；传 null 解绑 */
	public void bind(@Nullable AbstractContainerScreen<?> screen) {
		this.screen = screen;
	}
}

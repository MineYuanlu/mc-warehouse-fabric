package bid.yuanlu.mc.warehouse.ui.mc.mc261;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;

/**
 * Screen 薄壳挂载点（UI-PDD §4.2，LDLib2 ModularUIScreen 思想）：
 * 输入事件翻译成 UiEvent 投入根元素；渲染转发根元素提取；
 * 全部 UI 输入处理收敛在 UiRoot，本类不含业务。
 */
public final class Mc261ScreenHost extends Screen {

	private final Supplier<UiRoot> rootFactory;
	/** HUD 设置屏等：本屏打开期间 HUD 保持渲染（实时预览）。 */
	private final boolean hudPassthrough;
	@Nullable
	private UiRoot root;

	public Mc261ScreenHost(Component title, Supplier<UiRoot> rootFactory) {
		this(title, rootFactory, false);
	}

	public Mc261ScreenHost(Component title, Supplier<UiRoot> rootFactory, boolean hudPassthrough) {
		super(title);
		this.rootFactory = rootFactory;
		this.hudPassthrough = hudPassthrough;
	}

	public boolean hudPassthrough() {
		return hudPassthrough;
	}

	public @Nullable UiRoot root() {
		return root;
	}

	@Override
	protected void init() {
		if (root == null) {
			root = rootFactory.get();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// passthrough 屏（HUD 设置）：完全透明背景——默认分支会做模糊后处理 + 菜单
		// 背景贴图，把下层 HUD 糊掉，用户无法看清/定位拖拽目标
		if (!hudPassthrough) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	public void resize(int width, int height) {
		// 只更新尺寸，不重建 root：MC 默认 resize 会重跑 init 重建控件树，
		// 丢 hover/焦点/拖拽工作状态；尺寸变化由每帧 root.update 触发 relayout
		this.width = width;
		this.height = height;
	}

	@Override
	public void tick() {
		if (root != null) {
			root.tick();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (root == null) {
			return;
		}
		var draw = new Mc261Draw(graphics, a);
		root.update(draw, width, height, mouseX, mouseY);
		root.extract(draw);
		// HUD 设置屏：HUD 由本屏代渲染在内容之上（HUD 层在更早 stratum，必然被盖住）
		if (hudPassthrough) {
			Mc261HudHost.extractOverlay(graphics, a);
		}
	}

	// ---- 输入翻译 ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return root != null && root.mouseDown(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return root != null && root.mouseUp(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return root != null && root.mouseMoved(event.x(), event.y());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return root != null && root.mouseScroll(x, y, scrollY);
	}

	@Override
	public void mouseMoved(double x, double y) {
		if (root != null) {
			root.mouseMoved(x, y);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		return root != null && root.keyDown(event.key(), event.scancode(), event.modifiers());
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return root != null && root.keyUp(event.key(), event.scancode(), event.modifiers());
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return root != null && root.charTyped(event.codepoint());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		root = null;
	}
}

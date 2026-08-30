package bid.yuanlu.mc.warehouse.ui.app.widget;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import org.jetbrains.annotations.Nullable;

/**
 * 模态浮层（UI-PDD §9 规范 2）：满铺模态层（根级 grow 权重全屏）内含全屏 scrim
 * 吞输入 + 居中对话框面板。
 */
public final class Modal {

	@Nullable
	private static Runnable dismissAction = null;

	private Modal() {
	}

	/** 危险操作确认：[确认][取消]（Esc 关闭由根处理不了——scrim 吞键盘输入例外）。 */
	public static void confirm(UiRoot root, Component title, Component message, Runnable onConfirm) {
		show(root, title, message, onConfirm, Component.translatable("ui.wh.modal.confirm"),
				Component.translatable("ui.wh.modal.cancel"));
	}

	public static void show(UiRoot root, Component title, Component message, Runnable onConfirm,
			Component confirmLabel, Component cancelLabel) {
		close();
		var theme = Theme.active();
		var overlay = overlay(root);
		var dialog = centeredDialog(root);
		dialog.padding(10).layout(new Column(8)).id("modal-dialog");
		dialog.add(new LabelElement(title));
		dialog.add(new LabelElement(message).color(theme.textMuted()));
		var buttons = new PanelElement().padding(2).layout(new Row(6));
		buttons.add(new ButtonElement(confirmLabel).semantic(ButtonElement.Semantic.SUCCESS)
				.onClick(() -> {
					close();
					onConfirm.run();
				}));
		buttons.add(new ButtonElement(cancelLabel).onClick(Modal::close));
		dialog.add(buttons);
		overlay.add(dialog);
		dismissAction = () -> root.remove(overlay);
	}

	/**
	 * 全屏模态层：根级 grow 权重满铺、zIndex 置顶，内含吞点击的 scrim。
	 * 自定义对话框 add 进返回的层内（放 scrim 之后即在其上）；调用方自行持引用 remove。
	 */
	public static PanelElement overlay(UiRoot root) {
		var theme = Theme.active();
		var overlay = new PanelElement().filled(false).bordered(false).zIndex(100).grow(1).id("modal-overlay");
		var scrim = new PanelElement() {
			@Override
			protected void onTick() {
				size(root.width(), root.height());
			}
		}.colors(theme.overlayScrim(), theme.overlayScrim(), theme.overlayScrim(), theme.overlayScrim());
		scrim.id("modal-scrim");
		scrim.onClick(() -> {
		}); // 吞掉点击，防误触下层
		overlay.add(scrim);
		root.add(overlay);
		return overlay;
	}

	/** 每 tick 自居中的对话框面板（模态层无布局器，pos 生效）。 */
	public static PanelElement centeredDialog(UiRoot root) {
		return new PanelElement() {
			@Override
			protected void onTick() {
				pos(Math.max(0, (root.width() - width()) / 2), Math.max(0, (root.height() - height()) / 2));
			}
		};
	}

	public static void close() {
		Runnable r = dismissAction;
		dismissAction = null;
		if (r != null) {
			r.run();
		}
	}

	public static boolean open() {
		return dismissAction != null;
	}

}

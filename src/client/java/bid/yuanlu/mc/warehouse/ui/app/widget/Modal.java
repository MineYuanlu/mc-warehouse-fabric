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
 * 模态浮层（UI-PDD §9 规范 2）：全屏 scrim 吞输入 + 居中对话框面板（zIndex 置顶）。
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
		var scrim = new PanelElement() {
			@Override
			protected void onTick() {
				size(root.width(), root.height());
			}
		}.colors(theme.overlayScrim(), theme.overlayScrim(), theme.overlayScrim(), theme.overlayScrim());
		scrim.zIndex(100).size(root.width(), root.height()).id("modal-scrim");
		scrim.onClick(() -> {
		}); // 吞掉点击，防误触下层
		var dialog = new PanelElement().padding(10).layout(new Column(8)).id("modal-dialog");
		dialog.zIndex(101);
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
		root.add(scrim);
		root.add(dialog);
		dismissAction = () -> {
			root.remove(scrim);
			root.remove(dialog);
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

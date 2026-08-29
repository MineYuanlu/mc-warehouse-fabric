package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.app.hud.HudConfig;
import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.CheckboxElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * HUD 设置屏（UI-PDD §6.2）：左侧画布上拖拽区块芯片改角落/偏移，
 * 右侧逐区块开关/排序/偏移微调；[完成] 持久化并重建 HUD。
 */
public final class HudSettingsScreens {

	private HudSettingsScreens() {
	}

	/** 打开 HUD 设置屏。 */
	public static void open() {
		UiPlatform.openScreen(HudSettingsScreens::create);
	}

	public static UiRoot create() {
		HudConfig working = new HudConfig();
		working.copyFrom(bid.yuanlu.mc.warehouse.ui.app.hud.HudConfig.get());

		var root = new UiRoot();
		var panel = new PanelElement().padding(8).layout(new Column(6)).size(400, -1);
		panel.add(ScreenHeader.create(ScreenHeader.Page.HUD_SETTINGS));
		panel.add(new LabelElement(Component.translatable("ui.wh.hud.settings.title")));

		// 画布：可拖拽的区块芯片（拖拽 = 角落吸附 + 偏移）
		var canvas = new CanvasPanel(working).size(400, 120).clipContent(true);
		panel.add(canvas);

		// 每区块控制行
		for (HudConfig.Block block : HudConfig.Block.values()) {
			var cfg = working.get(block);
			var line = new PanelElement().padding(2).layout(new Row(4));
			line.add(new CheckboxElement(Component.translatable(block.labelKey), cfg.enabled)
					.bind(bindOf(cfg)));
			line.add(new LabelElement(Component.translatable("ui.wh.hud.settings.corner", cfg.corner)));
			line.add(new ButtonElement(Component.translatable("ui.wh.hud.settings.up"))
					.onClick(() -> cfg.order = Math.max(0, cfg.order - 1)));
			line.add(new ButtonElement(Component.translatable("ui.wh.hud.settings.down"))
					.onClick(() -> cfg.order = cfg.order + 1));
			line.add(new ButtonElement("-")
					.onClick(() -> cfg.offsetY = Math.max(0, cfg.offsetY - 1)));
			line.add(new ButtonElement("+")
					.onClick(() -> cfg.offsetY = cfg.offsetY + 1));
			panel.add(line);
		}

		var buttons = PanelElement.plain().padding(2).layout(new Row(6));
		buttons.add(new ButtonElement(Component.translatable("ui.wh.hud.settings.done"))
				.onClick(() -> {
					bid.yuanlu.mc.warehouse.ui.app.hud.HudConfig.save(working);
					UiPlatform.resetHud("hud");
					ScreenHeader.backToMain();
				}));
		buttons.add(new ButtonElement(Component.translatable("ui.wh.hud.settings.cancel"))
				.onClick(ScreenHeader::backToMain));
		panel.add(buttons);
		root.add(panel);
		return root;
	}

	private static bid.yuanlu.mc.warehouse.ui.core.bind.Value<Boolean> bindOf(HudConfig.BlockConfig cfg) {
		var v = bid.yuanlu.mc.warehouse.ui.core.bind.Value.of(cfg.enabled);
		v.listen(b -> cfg.enabled = b);
		return v;
	}

	/** 画布：网格背景 + 每区块一枚可拖拽芯片（拖到四角吸附，记录偏移）。 */
	private static final class CanvasPanel extends PanelElement {

		private final HudConfig working;

		CanvasPanel(HudConfig working) {
			this.working = working;
			// 无布局器：芯片由配置偏移手动定位（拖拽直接改 pos）
			for (HudConfig.Block block : HudConfig.Block.values()) {
				var chip = new LabelElement(Component.translatable(block.labelKey)).padding(2);
				chip.id(block.name());
				chip.pos(working.get(block).offsetX, working.get(block).offsetY);
				chip.on(UiEvent.Type.DRAG, e -> {
					var cfg = working.get(block);
					cfg.offsetX = Math.max(0, cfg.offsetX + (int) e.dx);
					cfg.offsetY = Math.max(0, cfg.offsetY + (int) e.dy);
					chip.pos(cfg.offsetX, cfg.offsetY);
				});
				add(chip);
			}
		}

		@Override
		protected void drawElement(UiDraw g) {
			super.drawElement(g);
			// 网格辅助线（20px 间距）
			var t = bid.yuanlu.mc.warehouse.ui.core.theme.Theme.active();
			for (int gx = 0; gx < width(); gx += 20) {
				g.fill(absX() + gx, absY(), absX() + gx + 1, absY() + height(), t.border() & 0x40FFFFFF);
			}
		}
	}
}

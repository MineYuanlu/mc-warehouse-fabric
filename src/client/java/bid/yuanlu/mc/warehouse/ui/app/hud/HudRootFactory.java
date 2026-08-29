package bid.yuanlu.mc.warehouse.ui.app.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.ui.app.presenter.HudPresenter;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * HUD 根工厂（UI-PDD §6）：区块 = 渐变小面板 + 绑定 Presenter 的文本行。
 * 注册到 UiPlatform（"hud"），配置变化经 UiPlatform.resetHud 重建。
 */
public final class HudRootFactory {

	private HudRootFactory() {
	}

	public static void register() {
		UiPlatform.registerHud("hud", HudRootFactory::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		root.setRootLayout(new HudLayout());
		var cfg = HudConfig.get();
		for (HudConfig.Block block : HudConfig.Block.values()) {
			if (!cfg.get(block).enabled) {
				continue;
			}
			root.add(createBlock(block, cfg.get(block).maxLines));
		}
		return root;
	}

	public static UiElement createBlock(HudConfig.Block block, int maxLines) {
		var presenter = HudPresenter.get();
		// Column 布局：面板尺寸随文本行自适应（外框=内容尺寸），行变化即 relayout
		var panel = new PanelElement().padding(4).id(block.name()).layout(new Column(2));
		List<LabelElement> labels = new ArrayList<>();
		float scale = HudConfig.get().get(block).scale;
		for (int i = 0; i < Math.max(1, maxLines); i++) {
			LabelElement label = new LabelElement(Component.empty()).padding(1).scale(scale);
			labels.add(label);
			panel.add(label);
		}
		// 绑定：区块文本变化 → 逐行填充（超出 maxLines 截断，空行隐藏）
		Runnable update = () -> {
			var lines = presenter.blockLines(map(block));
			for (int i = 0; i < labels.size(); i++) {
				var label = labels.get(i);
				if (i < lines.size() && !lines.get(i).getString().isEmpty()) {
					label.text(lines.get(i));
					label.visible(true);
				} else {
					label.text(Component.empty());
					label.visible(false);
				}
			}
			// 全空时隐藏整个区块（REPORT 等空数据区块不留空面板）
			boolean any = lines.stream().anyMatch(l -> !l.getString().isEmpty());
			panel.visible(any);
		};
		bindBlock(block, update);
		update.run();
		return panel;
	}

	private static void bindBlock(HudConfig.Block block, Runnable update) {
		var p = HudPresenter.get();
		switch (block) {
			case WAREHOUSE -> p.warehouseLine.listen(v -> update.run());
			case STATE -> p.stateLine.listen(v -> update.run());
			case PROGRESS -> p.progressLine.listen(v -> update.run());
			case SELECTION -> p.selectionLine.listen(v -> update.run());
			case MARK -> p.markLine.listen(v -> update.run());
			case REPORT -> {
				p.reportLine.listen(v -> update.run());
				p.reportVisible.listen(v -> update.run());
			}
		}
		// tick 拉取（refresh）不触发 Value 监听的情况：同值重写代价可接受，
		// 这里在 refresh 值未变时靠 Value.set 的去重跳过即可。
	}

	private static HudPresenter.HudBlockId map(HudConfig.Block block) {
		return HudPresenter.HudBlockId.valueOf(block.name());
	}
}

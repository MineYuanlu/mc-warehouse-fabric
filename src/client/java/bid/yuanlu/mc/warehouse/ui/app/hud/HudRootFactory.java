package bid.yuanlu.mc.warehouse.ui.app.hud;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

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
		// 每角一个共享外层面板（单一大 box），同角区块按 order 排入其中；
		// 区块面板本身纯布局不画背景，视觉上只有一块整体框
		for (HudConfig.Corner corner : HudConfig.Corner.values()) {
			var wrapper = new PanelElement().padding(4).layout(new Column(2))
					.id(WRAPPER_PREFIX + corner.name());
			var blocks = java.util.Arrays.stream(HudConfig.Block.values())
					.filter(b -> cfg.get(b).enabled && cfg.get(b).corner() == corner)
					.sorted(java.util.Comparator.comparingInt(b -> cfg.get(b).order))
					.toList();
			for (HudConfig.Block block : blocks) {
				wrapper.add(createBlock(block, cfg.get(block).maxLines, wrapper));
			}
			if (blocks.isEmpty()) {
				continue;
			}
			root.add(wrapper);
		}
		return root;
	}

	/** corner 外层面板的 id 前缀（HudLayout 识别用）。 */
	public static final String WRAPPER_PREFIX = "corner:";

	public static UiElement createBlock(HudConfig.Block block, int maxLines, @Nullable PanelElement cornerWrapper) {
		var presenter = HudPresenter.get();
		// Column 布局：尺寸随文本行自适应；plain() 不画背景（背景由 corner 外层统一画）
		var panel = PanelElement.plain().padding(4).id(block.name()).layout(new Column(2));
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
			// 角落内所有区块都空 → 外层 box 一并隐藏
			if (cornerWrapper != null) {
				cornerWrapper.visible(cornerWrapper.children().stream().anyMatch(UiElement::visible));
			}
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

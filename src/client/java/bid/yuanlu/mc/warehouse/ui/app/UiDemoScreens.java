package bid.yuanlu.mc.warehouse.ui.app;

import java.util.List;

import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;
import net.minecraft.network.chat.Component;

/**
 * M0 临时 demo 屏（UI-PDD §14 M0 验收用）：验证主题/布局/事件/绑定链路。
 * M3 起由业务屏取代。
 */
public final class UiDemoScreens {

	private UiDemoScreens() {
	}

	public static UiRoot create() {
		Value<Integer> clicks = Value.of(0);
		var root = new UiRoot();
		var panel = new PanelElement().padding(8).layout(new Column(6)).size(240, -1);
		panel.add(new LabelElement(Component.translatable("ui.wh.demo.title")).color(0xFFFFFFFF));
		LabelElement clickLabel = new LabelElement("").padding(2);
		clicks.listen(v -> clickLabel.text(Component.translatable("ui.wh.demo.clicks", v)));
		panel.add(clickLabel);
		var row = new PanelElement().padding(4).layout(new Row(4));
		for (int i = 0; i < 3; i++) {
			row.add(new PanelElement().colors(0xFF404A60 + i * 0x00101010, 0xFF20242E + i * 0x00101010, 0xFF606878, 0xFF707888)
					.size(40, 24));
		}
		panel.add(row);
		panel.add(new ButtonElement(Component.translatable("ui.wh.demo.poke"))
				.onClick(() -> clicks.set(clicks.get() + 1)));
		panel.add(new ButtonElement(Component.translatable("ui.wh.demo.close"))
				.onClick(UiPlatform::closeScreen)
				.tooltip(() -> List.of(Component.translatable("ui.wh.demo.tip"))));
		root.add(panel);
		return root;
	}
}

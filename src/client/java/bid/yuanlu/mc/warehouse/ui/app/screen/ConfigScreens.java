package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.ArrayList;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.core.WarehouseServices;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.app.widget.DropdownElement;
import bid.yuanlu.mc.warehouse.ui.app.widget.ScreenScaffold;
import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.CheckboxElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.NumberFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.ScrollElement;
import bid.yuanlu.mc.warehouse.ui.core.element.TextFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * 配置页（等价 /wh config show/set + /wh reload）：11 个配置项类型化控件
 * （bool=checkbox、int=NumberField、enum=下拉、double=文本框）。
 * 开关/枚举即改即存；数值/文本失焦（BLUR）才落盘（避免逐键写盘）——
 * 落盘路径与命令一致（ConfigIO.saveModConfig）。
 */
public final class ConfigScreens {

	private ConfigScreens() {
	}

	public static void open() {
		UiPlatform.openScreen(ConfigScreens::create);
	}

	private static void refresh() {
		UiPlatform.openScreen(ConfigScreens::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		var scaffold = new ScreenScaffold();
		scaffold.add(ScreenHeader.create(ScreenHeader.Page.CONFIG));
		scaffold.add(new LabelElement(Component.translatable("ui.wh.config.title")));

		var scroll = new ScrollElement(4).grow(1);
		scaffold.add(scroll);

		ModConfig c = WarehouseServices.modConfig();
		if (c == null) {
			scroll.add(new LabelElement(Component.translatable("ui.wh.select.need_world"))
					.color(Theme.active().textMuted()));
			root.add(scaffold);
			return root;
		}

		// debug（bool，即改即存）
		var debugValue = Value.of(c.debug);
		debugValue.listen(v -> {
			c.debug = v;
			save(c);
		});
		scroll.add(new CheckboxElement(Component.translatable("ui.wh.config.debug"), c.debug).bind(debugValue));

		// 数值项（int，失焦保存）
		scroll.add(intRow(c, "ui.wh.config.defaultInteractionSpeed", 1, 20,
				c.defaultInteractionSpeed, v -> c.defaultInteractionSpeed = v));
		scroll.add(intRow(c, "ui.wh.config.interactionJitterPercent", 0, 100,
				c.interactionJitterPercent, v -> c.interactionJitterPercent = v));
		scroll.add(intRow(c, "ui.wh.config.cacheTtlSeconds", 0, 86400,
				c.cacheTtlSeconds, v -> c.cacheTtlSeconds = v));
		scroll.add(intRow(c, "ui.wh.config.exploreFailMax", 0, 100,
				c.exploreFailMax, v -> c.exploreFailMax = v));
		scroll.add(intRow(c, "ui.wh.config.navRetryMax", 0, 100,
				c.navRetryMax, v -> c.navRetryMax = v));
		scroll.add(intRow(c, "ui.wh.config.timeouts.openTicks", 0, 1200,
				c.timeouts().openTicks, v -> c.timeouts().openTicks = v));
		scroll.add(intRow(c, "ui.wh.config.timeouts.confirmTicks", 0, 1200,
				c.timeouts().confirmTicks, v -> c.timeouts().confirmTicks = v));
		scroll.add(intRow(c, "ui.wh.config.timeouts.settleTicks", 0, 100,
				c.timeouts().settleTicks, v -> c.timeouts().settleTicks = v));

		// slotAllocator（枚举下拉，即改即存）
		var allocOptions = new ArrayList<DropdownElement.Option>();
		for (var a : WarehouseRegistryImpl.allocators()) {
			allocOptions.add(new DropdownElement.Option(a.id(), Component.literal(a.id())));
		}
		if (!allocOptions.isEmpty()) {
			int idx = 0;
			for (int i = 0; i < allocOptions.size(); i++) {
				if (allocOptions.get(i).id().equals(c.slotAllocator)) {
					idx = i;
					break;
				}
			}
			var row = PanelElement.plain().padding(2).layout(new Row(6));
			row.add(rowLabel(Component.translatable("ui.wh.config.slotAllocator")));
			row.add(new DropdownElement(allocOptions, idx, i -> {
				c.slotAllocator = allocOptions.get(i).id();
				save(c);
			}).size(170, -1));
			scroll.add(row);
		}

		// reachLimit（double，失焦保存）
		var reachField = new TextFieldElement(String.valueOf(c.reachLimit)).maxLength(12);
		reachField.size(80, -1);
		String[] initial = { reachField.text() };
		reachField.on(UiEvent.Type.BLUR, e -> {
			try {
				double v = Double.parseDouble(reachField.text().trim());
				if (v > 0 && !reachField.text().equals(initial[0])) {
					c.reachLimit = v;
					save(c);
					initial[0] = reachField.text();
				}
			} catch (NumberFormatException ignored) {
				// 非法输入不落盘，恢复显示当前值
				reachField.setText(initial[0]);
			}
		});
		var reachRow = PanelElement.plain().padding(2).layout(new Row(6));
		reachRow.add(rowLabel(Component.translatable("ui.wh.config.reachLimit")));
		reachRow.add(reachField);
		scroll.add(reachRow);

		// 操作行：重载（等价 /wh reload）+ 返回
		var actions = PanelElement.plain().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.main.reload"))
				.onClick(() -> {
					WarehouseManagerImpl.get().reload();
					refresh();
				}));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.back"))
				.onClick(ScreenHeader::backToMain));
		scaffold.add(actions);
		root.add(scaffold);
		return root;
	}

	private static PanelElement intRow(ModConfig c, String labelKey, int min, int max,
			int current, java.util.function.IntConsumer setter) {
		var row = PanelElement.plain().padding(2).layout(new Row(6));
		row.add(rowLabel(Component.translatable(labelKey)));
		var field = new NumberFieldElement(min, max, current);
		field.size(80, -1);
		String[] initial = { field.text() };
		field.on(UiEvent.Type.BLUR, e -> {
			if (field.valid() && !field.text().equals(initial[0])) {
				setter.accept(field.intValue());
				save(c);
				initial[0] = field.text();
			} else if (!field.valid()) {
				field.setText(initial[0]);
			}
		});
		row.add(field);
		return row;
	}

	/** 落盘路径与命令 config set 一致。 */
	private static void save(ModConfig c) {
		new ConfigIO(ConfigIO.defaultRoot()).saveModConfig(c);
	}

	private static LabelElement rowLabel(Component c) {
		return new LabelElement(c).padding(1).size(200, ButtonElement.VANILLA_HEIGHT);
	}
}

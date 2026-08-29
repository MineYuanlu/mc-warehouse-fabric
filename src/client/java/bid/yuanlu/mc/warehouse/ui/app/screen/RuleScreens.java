package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.command.WhCommands;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.TextFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * 规则编辑屏（UI-PDD §5.3 最小版，用户已确认）：规则 CRUD + 条目增删。
 * 条目添加复用 /wh rule add 的 tail 语法与解析/校验/落盘路径
 * （{@link WhCommands#addRuleEntryDirect}）；物品选择网格为后续里程碑。
 */
public final class RuleScreens {

	private RuleScreens() {
	}

	public static void open() {
		UiPlatform.openScreen(RuleScreens::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		var panel = new PanelElement().padding(10).layout(new Column(6)).size(420, -1);
		panel.add(new LabelElement(Component.translatable("ui.wh.rule.title")));
		panel.add(body(root));
		var actions = new PanelElement().padding(2).layout(new Row(4));
		actions.add(new ButtonElement(Component.translatable("ui.wh.modal.cancel"))
				.onClick(UiPlatform::closeScreen));
		panel.add(actions);
		root.add(panel);
		return root;
	}

	private static @Nullable Warehouse activeWarehouse() {
		try {
			return WarehouseManagerImpl.get().active();
		} catch (Exception e) {
			return null;
		}
	}

	private static PanelElement body(UiRoot root) {
		Warehouse wh = activeWarehouse();
		var body = new PanelElement().padding(2).layout(new Column(4));
		if (wh == null) {
			body.add(new LabelElement(Component.translatable("ui.wh.rule.no_active"))
					.color(Theme.active().textMuted()));
			return body;
		}

		// 新建规则
		var createRow = new PanelElement().padding(2).layout(new Row(4));
		var idField = new TextFieldElement("").maxLength(32);
		idField.size(160, -1);
		createRow.add(idField);
		createRow.add(new ButtonElement(Component.translatable("ui.wh.rule.create"))
				.onClick(() -> {
					String id = idField.text().trim();
					if (!id.matches("[A-Za-z0-9_.\\-]+") || wh.rules.containsKey(id)) {
						return; // 非法/重复：静默忽略（M4 最小版）
					}
					wh.rules.put(id, new ContainerRule(id));
					WarehouseManagerImpl.get().save(wh);
					UiPlatform.openScreen(RuleScreens::create);
				}));
		body.add(createRow);

		// 规则列表（选中 → 条目编辑）
		for (ContainerRule rule : wh.rules.values()) {
			long refs = wh.containers.stream().filter(c -> c.rules.contains(rule.id)).count();
			var row = new PanelElement().padding(2).layout(new Row(4));
			row.add(new LabelElement(Component.translatable("ui.wh.rule.entry",
					rule.id, rule.itemRules.size(), refs)).padding(1));
			var del = new ButtonElement(Component.translatable("ui.wh.main.remove"))
					.semantic(ButtonElement.Semantic.DANGER);
			if (refs > 0) {
				// 引用中禁删（等价 /wh rule delete 语义），tooltip 说明
				del.enabled(false).tooltip(() -> List.of(Component.translatable("ui.wh.rule.referenced", refs)));
			} else {
				del.onClick(() -> {
					wh.rules.remove(rule.id);
					WarehouseManagerImpl.get().save(wh);
					UiPlatform.openScreen(RuleScreens::create);
				});
			}
			row.add(del);
			body.add(row);
			body.add(entryEditor(rule));
		}
		return body;
	}

	/** 选中规则的条目编辑（展开式：非空规则显示条目列表 + 添加框）。 */
	private static PanelElement entryEditor(ContainerRule rule) {
		var box = new PanelElement().padding(4).layout(new Column(2));
		box.classes("rule-editor");
		var addRow = new PanelElement().padding(2).layout(new Row(4));
		var tailField = new TextFieldElement("").maxLength(256);
		tailField.size(220, -1);
		tailField.tooltip(() -> List.of(Component.translatable("ui.wh.rule.tail.tip")));
		addRow.add(tailField);
		var errorLabel = new LabelElement(Component.empty())
				.color(Theme.active().danger()).padding(1);
		addRow.add(new ButtonElement(Component.translatable("ui.wh.rule.entry.add"))
				.onClick(() -> {
					String tail = tailField.text().trim();
					if (tail.isEmpty()) {
						return;
					}
					Component err = WhCommands.addRuleEntryDirect(rule.id, tail);
					errorLabel.text(err == null ? Component.empty() : err);
					if (err == null) {
						UiPlatform.openScreen(RuleScreens::create);
					}
				}));
		box.add(addRow);
		box.add(errorLabel);

		int index = 1;
		for (var item : rule.itemRules) {
			var line = new PanelElement().padding(1).layout(new Row(4));
			String desc = item.selector.getClass().getSimpleName()
					+ (item.negative ? " !" : "")
					+ (item.isUnlimited() ? "" : " ×" + item.quantity);
			line.add(new LabelElement(Component.translatable("ui.wh.rule.entry.item",
					index, desc)).padding(1));
			int idx = index;
			line.add(new ButtonElement("×").onClick(() -> {
				Component err = WhCommands.removeRuleEntryDirect(rule.id, idx);
				if (err == null) {
					UiPlatform.openScreen(RuleScreens::create);
				}
			}));
			box.add(line);
			index++;
		}
		return box;
	}
}

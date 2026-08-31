package bid.yuanlu.mc.warehouse.ui.app.screen;

import java.util.List;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.command.WhCommands;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.ui.app.widget.Modal;
import bid.yuanlu.mc.warehouse.ui.app.widget.RuleEntryEditor;
import bid.yuanlu.mc.warehouse.ui.app.widget.ScreenScaffold;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.ScrollElement;
import bid.yuanlu.mc.warehouse.ui.core.element.TextFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * 规则编辑屏（UI-PDD §5.3 完整版）：左列规则 CRUD（引用数>0 禁删），右列条目列表
 * （双击编辑 / ↑↓ 重排 / × 删除；重排为超越命令的便捷项）+ 结构化条目编辑器
 * （类型/参数/取反/数量表单 + 背包选物 + 即时校验，见 {@link RuleEntryEditor}）；
 * 命令 tail 语法保留为 Modal 兜底入口。保存经
 * {@link WhCommands#upsertRuleEntryDirect}/{@link WhCommands#removeRuleEntryDirect}
 * 与命令同一 D2 校验与落盘路径。
 */
public final class RuleScreens {

	/** 会话态：选中规则与编辑器条目下标（0 = 新增形态，>0 = 编辑该条，1 起）。 */
	private static @Nullable String selectedRuleId;
	private static int editingIndex;

	private RuleScreens() {
	}

	public static void open() {
		editingIndex = 0;
		UiPlatform.openScreen(RuleScreens::create);
	}

	private static void refresh() {
		UiPlatform.openScreen(RuleScreens::create);
	}

	public static UiRoot create() {
		var root = new UiRoot();
		var scaffold = new ScreenScaffold();
		scaffold.add(ScreenHeader.create(ScreenHeader.Page.RULES));
		scaffold.add(new LabelElement(Component.translatable("ui.wh.rule.title")));
		var body = PanelElement.plain().layout(new Row(8)).grow(1);
		body.add(ruleListPanel());
		body.add(entryPanel());
		scaffold.add(body);
		root.add(scaffold);
		return root;
	}

	private static @Nullable Warehouse activeWarehouse() {
		try {
			return WarehouseManagerImpl.get().active();
		} catch (Exception e) {
			return null;
		}
	}

	// ---- 左列：规则列表 ----

	private static PanelElement ruleListPanel() {
		var theme = Theme.active();
		var panel = PanelElement.plain().padding(4).layout(new Column(4)).size(240, -1);
		Warehouse wh = activeWarehouse();
		if (wh == null) {
			panel.add(new LabelElement(Component.translatable("ui.wh.rule.no_active"))
					.color(theme.textMuted()));
			return panel;
		}

		var createRow = PanelElement.plain().padding(1).layout(new Row(4));
		var idField = new TextFieldElement("").maxLength(32);
		idField.size(130, -1);
		var createError = new LabelElement(Component.empty()).color(theme.danger()).padding(1);
		createRow.add(idField);
		createRow.add(new ButtonElement(Component.translatable("ui.wh.rule.create")).onClick(() -> {
			String id = idField.text().trim();
			if (!id.matches("[A-Za-z0-9_.\\-]+") || wh.rules.containsKey(id)) {
				createError.text(Component.translatable("ui.wh.rule.err.id"));
				return;
			}
			wh.rules.put(id, new ContainerRule(id));
			WarehouseManagerImpl.get().save(wh);
			selectedRuleId = id;
			editingIndex = 0;
			refresh();
		}));
		panel.add(createRow);
		panel.add(createError);

		if (selectedRuleId != null && !wh.rules.containsKey(selectedRuleId)) {
			selectedRuleId = null;
			editingIndex = 0;
		}

		var scroll = new ScrollElement(2).grow(1);
		for (ContainerRule rule : wh.rules.values()) {
			scroll.add(ruleRow(wh, rule));
		}
		panel.add(scroll);
		return panel;
	}

	private static ButtonElement ruleRow(Warehouse wh, ContainerRule rule) {
		long refs = wh.containers.stream().filter(c -> c.rules.contains(rule.id)).count();
		boolean selected = rule.id.equals(selectedRuleId);
		return new ButtonElement(Component.translatable("ui.wh.rule.entry",
				rule.id, rule.itemRules.size(), refs))
				.semantic(selected ? ButtonElement.Semantic.SUCCESS : ButtonElement.Semantic.ACCENT)
				.onClick(() -> {
					selectedRuleId = rule.id;
					editingIndex = 0;
					refresh();
				});
	}

	// ---- 右列：条目列表 + 结构化编辑器 ----

	private static PanelElement entryPanel() {
		var theme = Theme.active();
		var panel = PanelElement.plain().padding(4).layout(new Column(4)).grow(1);
		Warehouse wh = activeWarehouse();
		if (wh == null) {
			panel.add(new LabelElement(Component.translatable("ui.wh.rule.no_active"))
					.color(theme.textMuted()));
			return panel;
		}
		if (selectedRuleId == null || !wh.rules.containsKey(selectedRuleId)) {
			panel.add(new LabelElement(Component.translatable("ui.wh.rule.pick_hint"))
					.color(theme.textMuted()));
			return panel;
		}
		ContainerRule rule = wh.rules.get(selectedRuleId);

		// 规则头：id 概览 + 命令语法入口 + 删除
		long refs = wh.containers.stream().filter(c -> c.rules.contains(rule.id)).count();
		var head = PanelElement.plain().padding(1).layout(new Row(6));
		head.add(new LabelElement(Component.translatable("ui.wh.rule.entry",
				rule.id, rule.itemRules.size(), refs)).padding(1).grow(1));
		head.add(new ButtonElement(Component.translatable("ui.wh.rule.tail.add"))
				.onClick(() -> openRawModal(head, rule)));
		var del = new ButtonElement(Component.translatable("ui.wh.main.remove"))
				.semantic(ButtonElement.Semantic.DANGER);
		if (refs > 0) {
			del.enabled(false).tooltip(() -> List.of(Component.translatable("ui.wh.rule.referenced", refs)));
		} else {
			del.onClick(() -> {
				wh.rules.remove(rule.id);
				WarehouseManagerImpl.get().save(wh);
				selectedRuleId = null;
				editingIndex = 0;
				refresh();
			});
		}
		head.add(del);
		panel.add(head);

		var scroll = new ScrollElement(2).grow(1);
		if (rule.itemRules.isEmpty()) {
			scroll.add(new LabelElement(Component.translatable("ui.wh.rule.no_entries"))
					.color(theme.textMuted()).padding(2));
		}
		int index = 1;
		for (ItemRule entry : rule.itemRules) {
			scroll.add(entryRow(rule, index));
			index++;
		}
		panel.add(scroll);

		Integer editIndex = editingIndex > 0 ? editingIndex : null;
		panel.add(RuleEntryEditor.create(rule, editIndex, () -> {
			editingIndex = 0;
			refresh();
		}));
		return panel;
	}

	private static PanelElement entryRow(ContainerRule rule, int index) {
		ItemRule entry = rule.itemRules.get(index - 1);
		var row = PanelElement.plain().padding(1).layout(new Row(2));
		var summary = new LabelElement(Component.translatable("ui.wh.rule.entry.item",
				index, RuleEntryEditor.summarize(entry))).padding(1).grow(1);
		summary.tooltip(() -> List.of(
				Component.literal(RuleEntryEditor.entryJson(entry)),
				Component.translatable("ui.wh.rule.entry.dblclick")));
		summary.on(UiEvent.Type.DOUBLE_CLICK, e -> {
			editingIndex = index;
			refresh();
		});
		row.add(summary);

		var up = new ButtonElement("↑").tooltip(() -> List.of(Component.translatable("ui.wh.rule.move.up")));
		if (index > 1) {
			up.onClick(() -> moveEntry(rule, index, -1));
		} else {
			up.enabled(false);
		}
		var down = new ButtonElement("↓").tooltip(() -> List.of(Component.translatable("ui.wh.rule.move.down")));
		if (index < rule.itemRules.size()) {
			down.onClick(() -> moveEntry(rule, index, 1));
		} else {
			down.enabled(false);
		}
		row.add(up);
		row.add(down);
		row.add(new ButtonElement("×").semantic(ButtonElement.Semantic.DANGER).onClick(() -> {
			Component err = WhCommands.removeRuleEntryDirect(rule.id, index);
			if (err == null) {
				editingIndex = 0; // 删除会前移后续下标，编辑态一律复位
				refresh();
			}
		}));
		return row;
	}

	/** 条目重排（UI 便捷项，语义无变化故不走 D2 重校验），落盘与命令同 manager.save 路径。 */
	private static void moveEntry(ContainerRule rule, int index, int delta) {
		int to = index - 1 + delta;
		if (to < 0 || to >= rule.itemRules.size()) {
			return;
		}
		var wh = activeWarehouse();
		if (wh == null) {
			return;
		}
		java.util.Collections.swap(rule.itemRules, index - 1, to);
		WarehouseManagerImpl.get().save(wh);
		if (editingIndex == index) {
			editingIndex = index + delta;
		}
		refresh();
	}

	/** 命令 tail 语法兜底入口（Modal）：解析/校验/落盘复用 {@link WhCommands#addRuleEntryDirect}。 */
	private static void openRawModal(UiElement<?> anchor, ContainerRule rule) {
		if (!(anchor.root() instanceof UiRoot root)) {
			return;
		}
		var overlay = Modal.overlay(root);
		var dialog = Modal.centeredDialog(root);
		dialog.padding(10).layout(new Column(6)).id("rule-raw-dialog");
		dialog.add(new LabelElement(Component.translatable("ui.wh.rule.tail.title")));
		var field = new TextFieldElement("").maxLength(512).size(300, -1);
		field.tooltip(() -> List.of(Component.translatable("ui.wh.rule.tail.tip")));
		dialog.add(field);
		var err = new LabelElement(Component.empty()).color(Theme.active().danger()).padding(1);
		dialog.add(err);
		var buttons = PanelElement.plain().padding(2).layout(new Row(6));
		buttons.add(new ButtonElement(Component.translatable("ui.wh.rule.entry.add")).onClick(() -> {
			String tail = field.text().trim();
			if (tail.isEmpty()) {
				return;
			}
			Component e = WhCommands.addRuleEntryDirect(rule.id, tail);
			if (e == null) {
				overlay.removeFromParent();
				editingIndex = 0;
				refresh();
			} else {
				err.text(e);
			}
		}));
		buttons.add(new ButtonElement(Component.translatable("ui.wh.modal.cancel"))
				.onClick(overlay::removeFromParent));
		dialog.add(buttons);
		overlay.add(dialog);
	}
}

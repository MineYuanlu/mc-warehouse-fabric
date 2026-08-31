package bid.yuanlu.mc.warehouse.ui.app.widget;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.command.WhCommands;
import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import bid.yuanlu.mc.warehouse.ui.core.bind.Value;
import bid.yuanlu.mc.warehouse.ui.core.element.ButtonElement;
import bid.yuanlu.mc.warehouse.ui.core.element.CheckboxElement;
import bid.yuanlu.mc.warehouse.ui.core.element.LabelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.NumberFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.PanelElement;
import bid.yuanlu.mc.warehouse.ui.core.element.TextFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;
import bid.yuanlu.mc.warehouse.ui.core.layout.Column;
import bid.yuanlu.mc.warehouse.ui.core.layout.Row;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 规则条目结构化编辑器（UI-PDD §5.3 完整版）：选择器类型 + 参数 + 取反 + 数量表单，
 * 即时经 SelectorCodecs round-trip 校验（错误行内红色，UI-PDD §9 规范 3）；
 * 保存走 {@link WhCommands#upsertRuleEntryDirect}（与命令同一 D2 校验与落盘路径）。
 * 物品网格（背包选物）经 Modal 弹出，点击物品填充 id 值。
 * editIndex = null 为新增形态（追加），否则编辑替换该位条目（1 起下标）。
 */
public final class RuleEntryEditor {

	/** 数量"不限量"伪类型（不在 codec 注册表中）。 */
	private static final String QTY_NONE = "none";

	private RuleEntryEditor() {
	}

	/** 条目摘要（列表行展示）：{@code !name(fuzzy):stone ×64 count} 形态。 */
	public static String summarize(ItemRule entry) {
		StringBuilder sb = new StringBuilder();
		if (entry.negative) {
			sb.append('!');
		}
		JsonObject sj = SelectorCodecs.toJson(entry.selector);
		sb.append(sj.get("type").getAsString());
		if (sj.has("value") && !sj.get("value").isJsonNull()) {
			sb.append(':').append(sj.get("value").getAsString());
		}
		if (sj.has("fuzzy")) {
			sb.append(sj.get("fuzzy").getAsBoolean() ? "(fuzzy)" : "(exact)");
		}
		if (sj.has("selectors")) {
			sb.append('(').append(sj.getAsJsonArray("selectors").size()).append(')');
		}
		if (!entry.isUnlimited()) {
			JsonObject qj = SelectorCodecs.toJson(entry.quantity);
			sb.append(" ×");
			if (qj.has("value")) {
				sb.append(qj.get("value").getAsString());
			}
			sb.append(' ').append(qj.get("type").getAsString());
		}
		return sb.toString();
	}

	/** 条目完整 JSON（列表 tooltip）。 */
	public static String entryJson(ItemRule entry) {
		JsonObject json = new JsonObject();
		json.add("selector", SelectorCodecs.toJson(entry.selector));
		json.addProperty("negative", entry.negative);
		if (!entry.isUnlimited()) {
			json.add("quantity", SelectorCodecs.toJson(entry.quantity));
		}
		return json.toString();
	}

	/** 构建编辑面板；onSaved 在保存成功（已落盘）后回调（调用方负责刷新界面）。 */
	public static PanelElement create(ContainerRule rule, @Nullable Integer editIndex, Runnable onSaved) {
		var theme = Theme.active();

		// ---- 初始值（编辑态从既有条目装载）----
		String initType = "id";
		String initValue = "";
		boolean initFuzzy = true;
		boolean initNegate = false;
		String initQtyType = QTY_NONE;
		int initQtyValue = 1;
		if (editIndex != null) {
			ItemRule initial = rule.itemRules.get(editIndex - 1);
			JsonObject sj = SelectorCodecs.toJson(initial.selector);
			initType = sj.get("type").getAsString();
			initValue = sj.has("value") && !sj.get("value").isJsonNull()
					? sj.get("value").getAsString()
					: sj.toString();
			initFuzzy = sj.has("fuzzy") && sj.get("fuzzy").getAsBoolean();
			initNegate = initial.negative;
			if (!initial.isUnlimited()) {
				JsonObject qj = SelectorCodecs.toJson(initial.quantity);
				initQtyType = qj.get("type").getAsString();
				initQtyValue = qj.has("value") ? qj.get("value").getAsInt() : 1;
			}
		}

		// ---- 组件 ----
		var panel = new PanelElement().padding(4).layout(new Column(2)).classes("rule-entry-editor");
		var selTypes = SelectorCodecs.itemTypeNames();
		var qtyTypes = new java.util.ArrayList<>(List.of(QTY_NONE));
		qtyTypes.addAll(SelectorCodecs.quantityTypeNames());
		final String[] curSelType = {initType};
		final String[] curQtyType = {initQtyType};
		var valueField = new TextFieldElement(initValue).maxLength(2048).size(260, -1).grow(1);
		var fuzzyValue = Value.of(initFuzzy);
		var fuzzyBox = new CheckboxElement(Component.translatable("ui.wh.rule.fuzzy"), initFuzzy).bind(fuzzyValue);
		var negateValue = Value.of(initNegate);
		var negateBox = new CheckboxElement(Component.translatable("ui.wh.rule.negate"), initNegate).bind(negateValue);
		var qtyField = new NumberFieldElement(0, 65536, initQtyValue);
		qtyField.size(80, -1);
		var error = new LabelElement(Component.empty()).color(theme.danger()).padding(1);

		// ---- 即时校验（类型存 holder，selector 构造晚于本闭包）----
		Runnable revalidate = () -> error.text(validate(curSelType[0], valueField.text(),
				fuzzyValue.get(), curQtyType[0], qtyField));
		valueField.onChange(revalidate);
		fuzzyValue.listen(b -> revalidate.run());
		qtyField.onValue(v -> revalidate.run());

		var typeSelector = new CycleSelector<String>(selTypes, RuleEntryEditor::selectorTypeLabel,
				Math.max(0, selTypes.indexOf(initType)), t -> {
					curSelType[0] = t;
					fuzzyBox.visible(t.equals("name"));
					valueField.tooltip(t.equals("composite")
							? () -> List.of(Component.translatable("ui.wh.rule.value.composite.tip"))
							: null);
					revalidate.run();
				});
		var qtySelector = new CycleSelector<String>(qtyTypes, RuleEntryEditor::quantityTypeLabel,
				Math.max(0, qtyTypes.indexOf(initQtyType)), t -> {
					curQtyType[0] = t;
					qtyField.visible(!t.equals(QTY_NONE));
					revalidate.run();
				});
		fuzzyBox.visible(initType.equals("name"));
		qtyField.visible(!initQtyType.equals(QTY_NONE));
		if (initType.equals("composite")) {
			valueField.tooltip(() -> List.of(Component.translatable("ui.wh.rule.value.composite.tip")));
		}

		// ---- 布局 ----
		panel.add(new LabelElement(editIndex == null
				? Component.translatable("ui.wh.rule.editor.new")
				: Component.translatable("ui.wh.rule.editor.edit", editIndex)));

		var typeRow = new PanelElement().padding(1).layout(new Row(6));
		typeRow.add(rowLabel(Component.translatable("ui.wh.rule.type")));
		typeRow.add(typeSelector);
		panel.add(typeRow);

		var valueRow = new PanelElement().padding(1).layout(new Row(6));
		valueRow.add(rowLabel(Component.translatable("ui.wh.rule.value")));
		valueRow.add(valueField);
		valueRow.add(new ButtonElement(Component.translatable("ui.wh.rule.pick_inventory"))
				.onClick(() -> openItemGrid(valueField)));
		panel.add(valueRow);
		panel.add(fuzzyBox);
		panel.add(negateBox);

		var qtyRow = new PanelElement().padding(1).layout(new Row(6));
		qtyRow.add(rowLabel(Component.translatable("ui.wh.rule.quantity")));
		qtyRow.add(qtySelector);
		qtyRow.add(qtyField);
		panel.add(qtyRow);
		panel.add(error);

		panel.add(new ButtonElement(Component.translatable("ui.wh.rule.save"))
				.semantic(ButtonElement.Semantic.SUCCESS)
				.onClick(() -> {
					try {
						var sel = SelectorCodecs.itemFromJson(
								selectorJson(curSelType[0], valueField.text(), fuzzyValue.get()));
						QuantitySelector qty = curQtyType[0].equals(QTY_NONE) ? null
								: SelectorCodecs.quantityFromJson(quantityJson(curQtyType[0], qtyField.intValue()));
						Component err = WhCommands.upsertRuleEntryDirect(rule.id, editIndex,
								new ItemRule(sel, negateValue.get(), qty));
						if (err == null) {
							onSaved.run();
						} else {
							error.text(err);
						}
					} catch (Throwable t) {
						error.text(Component.translatable("ui.wh.rule.err.selector", String.valueOf(t.getMessage())));
					}
				}));
		revalidate.run();
		return panel;
	}

	/** 背包选物 Modal：点击物品把注册 id 填进目标文本框。 */
	private static void openItemGrid(TextFieldElement target) {
		if (!(target.root() instanceof UiRoot root)) {
			return;
		}
		var overlay = Modal.overlay(root);
		var dialog = Modal.centeredDialog(root);
		dialog.padding(10).layout(new Column(6)).id("item-grid-dialog");
		dialog.add(new LabelElement(Component.translatable("ui.wh.rule.grid.tip")));
		dialog.add(new ItemGridElement(stack -> {
			target.setText(ItemGridElement.registryId(stack));
			overlay.removeFromParent();
		}));
		dialog.add(new ButtonElement(Component.translatable("ui.wh.modal.cancel"))
				.onClick(overlay::removeFromParent));
		overlay.add(dialog);
	}

	private static @Nullable Component validate(String selType, String value, boolean fuzzy,
			String qtyType, NumberFieldElement qtyField) {
		if (value.isBlank()) {
			return Component.translatable("ui.wh.rule.err.empty");
		}
		try {
			SelectorCodecs.itemFromJson(selectorJson(selType, value, fuzzy));
		} catch (Throwable t) {
			return Component.translatable("ui.wh.rule.err.selector", String.valueOf(t.getMessage()));
		}
		if (!qtyType.equals(QTY_NONE)) {
			if (!qtyField.valid()) {
				return Component.translatable("ui.wh.rule.err.quantity", qtyField.text());
			}
			if (qtyType.equals("percent") && (qtyField.intValue() < 0 || qtyField.intValue() > 100)) {
				return Component.translatable("ui.wh.rule.err.percent");
			}
		}
		return null;
	}

	private static JsonObject selectorJson(String type, String value, boolean fuzzy) {
		JsonObject json;
		if (type.equals("composite")) {
			json = JsonParser.parseString(value.trim()).getAsJsonObject();
		} else {
			json = new JsonObject();
			json.addProperty("value", value.trim());
		}
		json.addProperty("type", type);
		if (type.equals("name")) {
			json.addProperty("fuzzy", fuzzy);
		}
		return json;
	}

	private static JsonObject quantityJson(String type, int value) {
		JsonObject json = new JsonObject();
		json.addProperty("type", type);
		json.addProperty("value", value);
		return json;
	}

	private static Component selectorTypeLabel(String t) {
		return switch (t) {
			case "id" -> Component.translatable("ui.wh.rule.sel.id");
			case "tag" -> Component.translatable("ui.wh.rule.sel.tag");
			case "name" -> Component.translatable("ui.wh.rule.sel.name");
			case "nbt" -> Component.translatable("ui.wh.rule.sel.nbt");
			case "composite" -> Component.translatable("ui.wh.rule.sel.composite");
			default -> Component.literal(t);
		};
	}

	private static Component quantityTypeLabel(String t) {
		return switch (t) {
			case QTY_NONE -> Component.translatable("ui.wh.rule.quantity.none");
			case "count" -> Component.translatable("ui.wh.rule.qty.count");
			case "group" -> Component.translatable("ui.wh.rule.qty.group");
			case "fill_slots" -> Component.translatable("ui.wh.rule.qty.fill_slots");
			case "percent" -> Component.translatable("ui.wh.rule.qty.percent");
			default -> Component.literal(t);
		};
	}

	private static LabelElement rowLabel(Component c) {
		return new LabelElement(c).padding(1).size(64, ButtonElement.VANILLA_HEIGHT);
	}
}

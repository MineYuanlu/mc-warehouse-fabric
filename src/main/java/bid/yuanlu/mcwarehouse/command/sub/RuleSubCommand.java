package bid.yuanlu.mcwarehouse.command.sub;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mcwarehouse.controller.RuleController;
import bid.yuanlu.mcwarehouse.controller.WarehouseController;
import bid.yuanlu.mcwarehouse.model.rule.ItemRule;
import bid.yuanlu.mcwarehouse.model.rule.ItemRules;
import bid.yuanlu.mcwarehouse.model.selector.IdSelector;
import bid.yuanlu.mcwarehouse.model.quantifier.CountSelector;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

public class RuleSubCommand {

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("rule")
			.then(literal("list")
				.executes(ctx -> {
					var wc = WarehouseController.getInstance();
					String active = wc.getActiveWarehouse();
					if (active == null) {
						ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
						return 0;
					}
					var rules = RuleController.getInstance().listRules(active);
					if (rules.isEmpty()) {
						ctx.getSource().sendFeedback(Component.literal("§eNo rule groups in warehouse \"" + active + "\""));
					} else {
						ctx.getSource().sendFeedback(Component.literal("§6=== Rule Groups in \"" + active + "\" ==="));
						for (ItemRules r : rules) {
							int count = r.rules != null ? r.rules.size() : 0;
							ctx.getSource().sendFeedback(Component.literal("§e- " + r.name + " (" + count + " rules)"));
						}
					}
					return 1;
				}))
			.then(literal("create")
				.then(argument("name", word())
					.executes(ctx -> {
						String name = getString(ctx, "name");
						var wc = WarehouseController.getInstance();
						String active = wc.getActiveWarehouse();
						if (active == null) {
							ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
							return 0;
						}
						if (RuleController.getInstance().createRule(active, name)) {
							ctx.getSource().sendFeedback(Component.literal("§aCreated rule group: " + name));
						} else {
							ctx.getSource().sendError(Component.literal("§cFailed to create rule group (may already exist)."));
						}
						return 1;
					})))
			.then(literal("delete")
				.then(argument("name", word())
					.executes(ctx -> {
						String name = getString(ctx, "name");
						var wc = WarehouseController.getInstance();
						String active = wc.getActiveWarehouse();
						if (active == null) {
							ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
							return 0;
						}
						if (RuleController.getInstance().deleteRule(active, name)) {
							ctx.getSource().sendFeedback(Component.literal("§aDeleted rule group: " + name));
						} else {
							ctx.getSource().sendError(Component.literal("§cRule group not found: " + name));
						}
						return 1;
					})))
			.then(literal("add")
				.then(argument("name", word())
					.then(argument("args", greedyString())
						.executes(ctx -> {
							String name = getString(ctx, "name");
							String args = getString(ctx, "args");
							var wc = WarehouseController.getInstance();
							String active = wc.getActiveWarehouse();
							if (active == null) {
								ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
								return 0;
							}
							ItemRule rule = parseItemRule(args);
							if (rule == null) {
								ctx.getSource().sendError(Component.literal("§cInvalid rule format. Usage: --id <item> [--count <n>] [--negate]"));
								return 0;
							}
							if (RuleController.getInstance().addRuleItem(active, name, rule)) {
								ctx.getSource().sendFeedback(Component.literal("§aAdded rule to group: " + name));
							} else {
								ctx.getSource().sendError(Component.literal("§cRule group not found: " + name));
							}
							return 1;
						}))))
			.then(literal("remove")
				.then(argument("name", word())
					.then(argument("index", integer(0))
						.executes(ctx -> {
							String name = getString(ctx, "name");
							int index = getInteger(ctx, "index");
							var wc = WarehouseController.getInstance();
							String active = wc.getActiveWarehouse();
							if (active == null) {
								ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
								return 0;
							}
							if (RuleController.getInstance().removeRuleItem(active, name, index)) {
								ctx.getSource().sendFeedback(Component.literal("§aRemoved rule at index " + index + " from " + name));
							} else {
								ctx.getSource().sendError(Component.literal("§cFailed to remove rule. Check group name and index."));
							}
							return 1;
						}))))
			.then(literal("edit")
				.then(argument("name", word())
					.then(argument("index", integer(0))
						.then(argument("args", greedyString())
							.executes(ctx -> {
								String name = getString(ctx, "name");
								int index = getInteger(ctx, "index");
								String args = getString(ctx, "args");
								var wc = WarehouseController.getInstance();
								String active = wc.getActiveWarehouse();
								if (active == null) {
									ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
									return 0;
								}
								ItemRule rule = parseItemRule(args);
								if (rule == null) {
									ctx.getSource().sendError(Component.literal("§cInvalid rule format."));
									return 0;
								}
								if (RuleController.getInstance().editRuleItem(active, name, index, rule)) {
									ctx.getSource().sendFeedback(Component.literal("§aEdited rule at index " + index + " in " + name));
								} else {
									ctx.getSource().sendError(Component.literal("§cFailed to edit rule. Check group name and index."));
								}
								return 1;
							})))))
			.then(literal("show")
				.then(argument("name", word())
					.executes(ctx -> {
						String name = getString(ctx, "name");
						var wc = WarehouseController.getInstance();
						String active = wc.getActiveWarehouse();
						if (active == null) {
							ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
							return 0;
						}
						ItemRules rules = RuleController.getInstance().getRule(active, name);
						if (rules == null) {
							ctx.getSource().sendError(Component.literal("§cRule group not found: " + name));
							return 0;
						}
						ctx.getSource().sendFeedback(Component.literal("§6=== Rule Group: " + name + " ==="));
						if (rules.rules == null || rules.rules.isEmpty()) {
							ctx.getSource().sendFeedback(Component.literal("§e(empty)"));
						} else {
							for (int i = 0; i < rules.rules.size(); i++) {
								ItemRule r = rules.rules.get(i);
								String desc = (r.negate ? "NOT " : "") + describeSelector(r) + " -> " + describeQuantifier(r);
								ctx.getSource().sendFeedback(Component.literal("§e" + i + ": " + desc));
							}
						}
						return 1;
					})));
	}

	private static ItemRule parseItemRule(String args) {
		ItemRule rule = new ItemRule();
		boolean hasSelector = false;
		boolean hasQuantifier = false;
		String[] parts = args.split(" ");
		for (int i = 0; i < parts.length; i++) {
			switch (parts[i]) {
				case "--id" -> {
					if (i + 1 < parts.length) {
						IdSelector sel = new IdSelector();
						sel.value = parts[++i];
						rule.selector = sel;
						hasSelector = true;
					}
				}
				case "--count" -> {
					if (i + 1 < parts.length) {
						CountSelector q = new CountSelector();
						q.value = Integer.parseInt(parts[++i]);
						rule.quantifier = q;
						hasQuantifier = true;
					}
				}
				case "--negate" -> rule.negate = true;
			}
		}
		if (!hasSelector) return null;
		if (!hasQuantifier) {
			CountSelector q = new CountSelector();
			q.value = 64;
			rule.quantifier = q;
		}
		return rule;
	}

	private static String describeSelector(ItemRule rule) {
		if (rule.selector instanceof IdSelector s) return "id:" + s.value;
		return "selector";
	}

	private static String describeQuantifier(ItemRule rule) {
		if (rule.quantifier instanceof CountSelector q) return "count:" + q.value;
		return "quantifier";
	}
}

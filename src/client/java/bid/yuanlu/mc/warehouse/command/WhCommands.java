package bid.yuanlu.mc.warehouse.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.container.CacheType;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.item.ItemSelector;
import bid.yuanlu.mc.warehouse.api.item.QuantitySelector;
import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.api.transport.TransportEngine;
import bid.yuanlu.mc.warehouse.api.transport.TransportState;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.WarehouseServices;
import bid.yuanlu.mc.warehouse.core.cache.CacheKey;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ConfigValidator;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.mark.MarkMode;
import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;

/**
 * {@code /wh} 命令树（PDD §10.1 一阶段全量；{@code /warehouse} 别名经 redirect）。
 * <p>
 * 全部输出走 i18n（{@code commands.wh.*}，§10.3）；与未来 UI 共用 api 层。
 * 自由参数尾巴（--type/--rule/--quantity 等）统一经 {@link CommandSupport#parseOpts} 解析。
 */
public final class WhCommands {

	private WhCommands() {
	}

	/** 注册回调捕获的节点引用（gametest 冒烟可复用 buildRoot） */
	public static volatile Object lastRootNode;

	/** 经 ClientCommandRegistrationCallback 接入（正式入口） */
	public static void register(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> d) {
		var root = buildRoot();
		var rootNode = root.build();
		lastRootNode = rootNode;
		d.register(root);
		d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warehouse")
				.redirect(rootNode));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> buildRoot() {
		var root = LiteralArgumentBuilder.<FabricClientCommandSource>literal("wh");
		registerBasic(root);
		registerControl(root);
		RuleGroup.register(root);
		ContainerGroup.register(root);
		SelectGroup.register(root);
		TransferGroup.register(root);
		ConfigGroup.register(root);
		return root;
	}

	// ============ builders ============

	static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	static RequiredArgumentBuilder<FabricClientCommandSource, String> word(String name) {
		return RequiredArgumentBuilder.argument(name, StringArgumentType.word());
	}

	static RequiredArgumentBuilder<FabricClientCommandSource, String> strArg(String name) {
		return RequiredArgumentBuilder.argument(name, StringArgumentType.string());
	}

	static RequiredArgumentBuilder<FabricClientCommandSource, String> greedy(String name) {
		return RequiredArgumentBuilder.argument(name, StringArgumentType.greedyString());
	}

	static SuggestionProvider<FabricClientCommandSource> warehouses() {
		return (ctx, b) -> {
			for (Warehouse w : WarehouseManagerImpl.get().list()) b.suggest(w.id);
			return b.buildFuture();
		};
	}

	static SuggestionProvider<FabricClientCommandSource> ruleIds() {
		return (ctx, b) -> {
			Warehouse wh = WarehouseManagerImpl.get().active();
			if (wh != null) for (String id : wh.rules.keySet()) b.suggest(id);
			return b.buildFuture();
		};
	}

	static SuggestionProvider<FabricClientCommandSource> iofs(List<String> values) {
		return (ctx, b) -> {
			for (String v : values) b.suggest(v);
			return b.buildFuture();
		};
	}

	static final List<String> IO_TYPES = List.of("INPUT", "OUTPUT", "TEMP", "IGNORE");
	static final List<String> MODES = List.of("WHITELIST", "BLACKLIST");
	static final List<String> CACHES = List.of("NONE", "MEMORY", "DISK");
	static final List<String> DIRS = List.of("north", "south", "east", "west", "up", "down");
	static final List<String> SELECTOR_TYPES = List.of("id:<value>", "tag:<value>", "name:<value>",
			"nbt:<text>", "composite:{...JSON}");
	static final List<String> CONFIG_KEYS = List.of(
			"debug", "defaultInteractionSpeed", "interactionJitterPercent", "cacheTtlSeconds",
			"slotAllocator", "reachLimit", "exploreFailMax", "navRetryMax",
			"timeouts.openTicks", "timeouts.confirmTicks", "timeouts.settleTicks");

	// ============ 基础组 ============

	private static void registerBasic(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(lit("help").executes(WhCommands::help));
		var list = lit("list").executes(WhCommands::listAll);
		list.then(word("name").suggests(warehouses()).executes(WhCommands::listDetail));
		root.then(list);
		root.then(lit("create").then(word("name").executes(WhCommands::create)));
		root.then(lit("remove").then(word("name").suggests(warehouses()).executes(WhCommands::remove)));
		root.then(lit("use").then(word("name").suggests(warehouses()).executes(WhCommands::use)));
		root.then(lit("status").executes(WhCommands::status));
		root.then(lit("show").executes(WhCommands::show));
		root.then(lit("anchor").then(lit("set")
				.executes(WhCommands::anchorHere)
				.then(word("ax").then(word("ay").then(word("az")
						.executes(WhCommands::anchorCoords))))));
		root.then(lit("reload").executes(WhCommands::reloadConfig));
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		ctx.getSource().sendFeedback(Component.translatable("commands.wh.help"));
		return 1;
	}

	private static int listAll(CommandContext<FabricClientCommandSource> ctx) {
		var src = ctx.getSource();
		WarehouseManagerImpl mgr = manager();
		List<Warehouse> all = mgr.list();
		if (all.isEmpty()) {
			CommandSupport.err(src, "commands.wh.list.empty");
			return 0;
		}
		String active = mgr.activeId();
		for (Warehouse w : all) {
			src.sendFeedback(Component.translatable("commands.wh.list.entry",
					w.id,
					active != null && active.equals(w.id)
							? Component.translatable("commands.wh.list.active").withStyle(ChatFormatting.GREEN)
							: Component.empty(),
					w.containers.size(),
					w.rules.size()));
		}
		return all.size();
	}

	private static int listDetail(CommandContext<FabricClientCommandSource> ctx) {
		var src = ctx.getSource();
		Warehouse w = manager().get(StringArgumentType.getString(ctx, "name"));
		if (w == null) {
			CommandSupport.err(src, "commands.wh.error.no_such_warehouse", "?");
			return 0;
		}
		printContainers(src, w.containers);
		return w.containers.size();
	}

	private static int create(CommandContext<FabricClientCommandSource> ctx) {
		try {
			Warehouse created = manager().create(StringArgumentType.getString(ctx, "name"));
			if (manager().activeId() == null) {
				manager().activate(created.id); // 第一个仓库自动激活
			}
			CommandSupport.fb(ctx, "commands.wh.create.done", ChatFormatting.GREEN, created.id);
		} catch (IllegalArgumentException e) {
			CommandSupport.err(ctx.getSource(), "commands.wh.error.generic", e.getMessage());
			return 0;
		}
		return 1;
	}

	private static int remove(CommandContext<FabricClientCommandSource> ctx) {
		WarehouseManagerImpl mgr = manager();
		String name = StringArgumentType.getString(ctx, "name");
		if (mgr.hasTransferOverlay()) {
			CommandSupport.err(ctx.getSource(), "commands.wh.transfer.blocked");
			return 0;
		}
		if (!mgr.exists(name)) {
			CommandSupport.err(ctx.getSource(), "commands.wh.error.no_such_warehouse", name);
			return 0;
		}
		if (mgr.activeId() != null && mgr.activeId().equals(name)) {
			mgr.activate(null);
		}
		mgr.delete(name);
		CommandSupport.fb(ctx, "commands.wh.remove.done", ChatFormatting.YELLOW, name);
		return 1;
	}

	private static int use(CommandContext<FabricClientCommandSource> ctx) {
		WarehouseManagerImpl mgr = manager();
		String name = StringArgumentType.getString(ctx, "name");
		if (mgr.get(name) == null) {
			CommandSupport.err(ctx.getSource(), "commands.wh.error.no_such_warehouse", name);
			return 0;
		}
		mgr.activate(name);
		CommandSupport.fb(ctx, "commands.wh.use.done", ChatFormatting.GREEN, name);
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		var src = ctx.getSource();
		Warehouse wh = manager().active();
		if (wh == null) {
			CommandSupport.err(src, "commands.wh.status.none");
			return 0;
		}
		long in = wh.containers.stream().filter(c -> c.ioType == IOType.INPUT).count();
		long out = wh.containers.stream().filter(c -> c.ioType == IOType.OUTPUT).count();
		long tmp = wh.containers.stream().filter(c -> c.ioType == IOType.TEMP).count();
		src.sendFeedback(Component.translatable("commands.wh.status.header", wh.id));
		src.sendFeedback(Component.translatable("commands.wh.status.containers",
				wh.containers.size(), in, out, tmp));
		src.sendFeedback(Component.translatable("commands.wh.status.rules", wh.rules.size()));
		TransportState st = engine().state();
		RunReport r = engine().lastReport();
		src.sendFeedback(Component.translatable("commands.wh.status.engine",
				Component.translatable(st == null ? "wh.state.idle" : "wh.state." + st.name())
						.withStyle(engine().isRunning()
								? ChatFormatting.GREEN
								: ChatFormatting.GRAY),
				r == null ? "-" : r.grade().name()));
		return 1;
	}

	private static int show(CommandContext<FabricClientCommandSource> ctx) {
		var src = ctx.getSource();
		Warehouse wh = manager().active();
		if (wh == null) {
			CommandSupport.err(src, "commands.wh.status.none");
			return 0;
		}
		src.sendFeedback(Component.translatable("commands.wh.show.header", wh.id)
				.withStyle(ChatFormatting.AQUA));
		printContainers(src, wh.containers);
		return wh.containers.size();
	}

	private static void printContainers(FabricClientCommandSource src, List<ContainerInfo> containers) {
		for (ContainerInfo c : containers) {
			StringBuilder sb = new StringBuilder();
			for (WorldDimPos p : c.pos) {
				if (!sb.isEmpty()) sb.append(" | ");
				sb.append(p.x()).append(' ').append(p.y()).append(' ').append(p.z());
			}
			src.sendFeedback(Component.translatable("commands.wh.show.entry",
					sb.toString(),
					c.label == null ? "" : c.label,
					c.ioType.name(),
					Component.translatable(
							"commands.wh.mode." + c.effectiveRuleMode().name()),
					String.join(",", c.rules.isEmpty() ? List.of("-") : c.rules)));
		}
	}

	private static int anchorHere(CommandContext<FabricClientCommandSource> ctx) {
		BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
		setAnchor(ctx.getSource(), pos);
		return 1;
	}

	private static int anchorCoords(CommandContext<FabricClientCommandSource> ctx) {
		BlockPos pos = CommandSupport.posOf(ctx, "ax", "ay", "az");
		if (pos == null) {
			CommandSupport.err(ctx.getSource(), "commands.wh.error.bad_pos");
			return 0;
		}
		setAnchor(ctx.getSource(), pos);
		return 1;
	}

	private static void setAnchor(FabricClientCommandSource src, BlockPos abs) {
		Warehouse wh = manager().active();
		if (wh == null) {
			CommandSupport.err(src, "commands.wh.status.none");
			return;
		}
		WorldDim dim = CommandSupport.currentDim(src);
		if (dim.worldId() == null || dim.dimId() == null) {
			CommandSupport.err(src, "commands.wh.error.not_in_world");
			return;
		}
		wh.setAnchor(dim, abs);
		manager().save(wh);
		src.sendFeedback(Component.translatable("commands.wh.anchor.set",
				dim.dimId(), abs.getX(), abs.getY(), abs.getZ()).withStyle(ChatFormatting.GREEN));
	}

	private static int reloadConfig(CommandContext<FabricClientCommandSource> ctx) {
		WarehouseManagerImpl mgr = manager();
		mgr.reload();
		ModConfig live = WarehouseServices.modConfig();
		if (live != null) {
			ModConfig fresh = new ConfigIO(ConfigIO.defaultRoot()).loadModConfig();
			copyConfig(fresh, live);
		}
		CommandSupport.fb(ctx, "commands.wh.reload.done", ChatFormatting.GREEN,
				mgr.list().size());
		return 1;
	}

	static void copyConfig(ModConfig from, ModConfig to) {
		to.debug = from.debug;
		to.defaultInteractionSpeed = from.defaultInteractionSpeed;
		to.interactionJitterPercent = from.interactionJitterPercent;
		to.cacheTtlSeconds = from.cacheTtlSeconds;
		to.slotAllocator = from.slotAllocator;
		to.reachLimit = from.reachLimit;
		to.exploreFailMax = from.exploreFailMax;
		to.navRetryMax = from.navRetryMax;
		to.timeouts = from.timeouts;
		to.worlds = from.worlds;
	}

	// ============ 引擎控制组 ============

	private static void registerControl(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		var start = lit("start").executes(ctx -> startRun(ctx, null));
		start.then(lit("--pathfinder")
				.then(word("id").suggests((c, b) -> {
					for (var n : WarehouseRegistryImpl.navigators()) b.suggest(n.id());
					return b.buildFuture();
				}).executes(ctx -> startRun(ctx, StringArgumentType.getString(ctx, "id")))));
		root.then(start);
		root.then(lit("stop").executes(WhCommands::stop));
		root.then(lit("continue").executes(WhCommands::continueRun));
		root.then(lit("restart").executes(WhCommands::restart));
		root.then(lit("abort").executes(WhCommands::abort));
	}

	private static int startRun(CommandContext<FabricClientCommandSource> ctx, String pathfinder) {
		var src = ctx.getSource();
		TransportEngine engine = engine();
		if (engine.isRunning()) {
			CommandSupport.err(src, "commands.wh.control.running");
			return 0;
		}
		Warehouse wh = manager().active();
		if (wh == null) {
			CommandSupport.err(src, "commands.wh.status.none");
			return 0;
		}
		if (wh.containers.isEmpty()) {
			CommandSupport.err(src, "commands.wh.start.empty");
			return 0;
		}
		engine.start(pathfinder);
		src.sendFeedback(Component.translatable("commands.wh.start.done", wh.id,
				pathfinder == null ? "-" : pathfinder).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int stop(CommandContext<FabricClientCommandSource> ctx) {
		TransportEngine engine = engine();
		if (!engine.isRunning()) {
			CommandSupport.err(ctx.getSource(), "commands.wh.control.not_running");
			return 0;
		}
		engine.stop();
		CommandSupport.fb(ctx, "commands.wh.stop.done", ChatFormatting.YELLOW);
		return 1;
	}

	private static int continueRun(CommandContext<FabricClientCommandSource> ctx) {
		TransportEngine engine = engine();
		if (engine.isRunning()) {
			CommandSupport.err(ctx.getSource(), "commands.wh.control.running");
			return 0;
		}
		if (engine.state() != TransportState.SUSPENDED) {
			CommandSupport.err(ctx.getSource(), "commands.wh.control.not_suspended");
			return 0;
		}
		engine.continueRun();
		CommandSupport.fb(ctx, "commands.wh.continue.done", ChatFormatting.GREEN);
		return 1;
	}

	private static int restart(CommandContext<FabricClientCommandSource> ctx) {
		TransportEngine engine = engine();
		if (engine.isRunning()) {
			CommandSupport.err(ctx.getSource(), "commands.wh.control.running");
			return 0;
		}
		engine.restart();
		CommandSupport.fb(ctx, "commands.wh.restart.done", ChatFormatting.GREEN);
		return 1;
	}

	private static int abort(CommandContext<FabricClientCommandSource> ctx) {
		TransportEngine engine = engine();
		if (engine.state() == null) {
			CommandSupport.err(ctx.getSource(), "commands.wh.control.not_running");
			return 0;
		}
		engine.abort();
		CommandSupport.fb(ctx, "commands.wh.abort.done", ChatFormatting.RED);
		return 1;
	}

	// ============ 规则组 ============

	static final class RuleGroup {

		static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
			var g = lit("rule");
			g.then(lit("list").executes(RuleGroup::listRules));
			g.then(lit("create").then(word("id").executes(RuleGroup::createRule)));
			g.then(lit("delete").then(word("id").suggests(ruleIds()).executes(RuleGroup::deleteRule)));
			g.then(lit("show").then(word("id").suggests(ruleIds()).executes(RuleGroup::showRule)));
			var add = word("id").suggests(ruleIds())
					.then(greedy("tail").executes(ctx -> addEntry(ctx)));
			g.then(lit("add").then(add));
			g.then(lit("remove").then(word("id").suggests(ruleIds())
					.then(basicInt("index").executes(RuleGroup::removeEntry))));
			root.then(g);
		}

		static RequiredArgumentBuilder<FabricClientCommandSource, Integer> basicInt(String name) {
			return RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer(1));
		}

		static int listRules(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActive(src);
			if (wh == null) return 0;
			if (wh.rules.isEmpty()) {
				src.sendFeedback(Component.translatable("commands.wh.rule.none")
						.withStyle(ChatFormatting.GRAY));
				return 0;
			}
			for (ContainerRule r : wh.rules.values()) {
				src.sendFeedback(Component.translatable("commands.wh.rule.entry",
						r.id, r.itemRules.size()));
			}
			return wh.rules.size();
		}

		static int createRule(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActive(src);
			if (wh == null) return 0;
			String id = StringArgumentType.getString(ctx, "id");
			if (!id.matches("[A-Za-z0-9_.\\-]+")) {
				CommandSupport.err(src, "commands.wh.rule.bad_id");
				return 0;
			}
			if (wh.rules.containsKey(id)) {
				CommandSupport.err(src, "commands.wh.rule.exists", id);
				return 0;
			}
			wh.rules.put(id, new ContainerRule(id));
			manager().save(wh);
			CommandSupport.fb(ctx, "commands.wh.rule.created", ChatFormatting.GREEN, id);
			return 1;
		}

		static int deleteRule(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActive(src);
			if (wh == null) return 0;
			String id = StringArgumentType.getString(ctx, "id");
			boolean referenced = wh.containers.stream().anyMatch(c -> c.rules.contains(id));
			if (referenced) {
				CommandSupport.err(src, "commands.wh.rule.referenced", id);
				return 0;
			}
			if (wh.rules.remove(id) == null) {
				CommandSupport.err(src, "commands.wh.rule.missing", id);
				return 0;
			}
			manager().save(wh);
			CommandSupport.fb(ctx, "commands.wh.rule.deleted", ChatFormatting.YELLOW, id);
			return 1;
		}

		static int showRule(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActive(src);
			if (wh == null) return 0;
			String id = StringArgumentType.getString(ctx, "id");
			ContainerRule r = wh.rules.get(id);
			if (r == null) {
				CommandSupport.err(src, "commands.wh.rule.missing", id);
				return 0;
			}
			int i = 1;
			for (ItemRule ir : r.itemRules) {
				src.sendFeedback(Component.translatable("commands.wh.rule.detail_entry", i++,
						ir.negative ? Component.translatable("commands.wh.rule.neg").getString() : "",
						SelectorCodecs.toJson(ir.selector).toString(),
						ir.quantity == null ? Component.translatable("commands.wh.rule.unlimited").getString()
								: SelectorCodecs.toJson(ir.quantity).toString()));
			}
			if (r.itemRules.isEmpty()) {
				src.sendFeedback(Component.translatable("commands.wh.rule.empty_rule", id)
						.withStyle(ChatFormatting.GRAY));
			}
			return r.itemRules.size();
		}

		static int addEntry(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActive(src);
			if (wh == null) return 0;
			String id = StringArgumentType.getString(ctx, "id");
			ContainerRule rule = wh.rules.get(id);
			if (rule == null) {
				CommandSupport.err(src, "commands.wh.rule.missing", id);
				return 0;
			}
			CommandSupport.RuleTail tail = CommandSupport.parseRuleTail(greedyArg(ctx));
			String selSpec = tail.selector();
			JsonObject selJson = parseTyped(selSpec);
			ItemSelector selector = null;
			String exMsg = "";
			if (selJson != null) {
				try {
					selector = SelectorCodecs.itemFromJson(selJson);
				} catch (Throwable e) {
					exMsg = String.valueOf(e); lastEx = exMsg;
				}
			}
			if (selector == null) {
				CommandSupport.err(src, "commands.wh.rule.bad_selector", selSpec + ex());
				return 0;
			}
			CommandSupport.Opts opts = tail.opts();
			QuantitySelector quant = null;
			if (opts.quantity() != null) {
				try {
					JsonObject qJson = parseTyped(opts.quantity());
					quant = qJson == null ? null : SelectorCodecs.quantityFromJson(qJson);
				} catch (Throwable e) {
					CommandSupport.err(src, "commands.wh.rule.bad_quantity",
							opts.quantity() + " (" + e + ")");
					return 0;
				}
				if (quant == null) {
					CommandSupport.err(src, "commands.wh.rule.bad_quantity", opts.quantity());
					return 0;
				}
			}
			ItemRule entry = new ItemRule(selector, opts.negate(), quant);
			rule.itemRules.add(entry);
			String d2err = ConfigValidator.validateRuleOnContainers(wh, rule);
			if (d2err != null) {
				rule.itemRules.remove(entry); // D2 严格拒载（PDD §3.7）
				CommandSupport.err(src, "commands.wh.error.generic", d2err);
				return 0;
			}
			manager().save(wh);
			CommandSupport.fb(ctx, "commands.wh.rule.entry_added", ChatFormatting.GREEN, id);
			return 1;
		}

		static int removeEntry(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActive(src);
			if (wh == null) return 0;
			String id = StringArgumentType.getString(ctx, "id");
			ContainerRule rule = wh.rules.get(id);
			int index = IntegerArgumentType.getInteger(ctx, "index");
			if (rule == null) {
				CommandSupport.err(src, "commands.wh.rule.missing", id);
				return 0;
			}
			if (index > rule.itemRules.size()) {
				CommandSupport.err(src, "commands.wh.rule.bad_index", index);
				return 0;
			}
			rule.itemRules.remove(index - 1); // 1-based
			manager().save(wh);
			CommandSupport.fb(ctx, "commands.wh.rule.entry_removed", ChatFormatting.YELLOW, id);
			return 1;
		}

		/** 解析 "<type>:<value>" 或裸 JSON 对象为 typed JsonObject */
		static JsonObject parseTyped(String spec) {
			if (spec == null || spec.isBlank()) return null;
			spec = spec.trim();
			if (spec.startsWith("{")) {
				try {
					return JsonParser.parseString(spec).getAsJsonObject();
				} catch (Exception e) {
					return null;
				}
			}
			int sep = spec.indexOf(':');
			if (sep <= 0 || sep == spec.length() - 1) return null;
			JsonObject o = new JsonObject();
			o.addProperty("type", spec.substring(0, sep));
			o.addProperty("value", spec.substring(sep + 1));
			return o;
		}

		private RuleGroup() {
		}
	}

	// ============ 容器组 ============

	static final class ContainerGroup {

		static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
			var g = lit("container");
			g.requires(s -> true); // 执行期守卫（需要激活仓库 + 世界）
			g.then(lit("list").executes(ContainerGroup::listContainers));

			// add [<x y z>] [--type T] [--rule R]：省略坐标=准星指向（§10.1）
			var azWithTail = word("caz");
			azWithTail.then(greedy("opts").executes(ctx -> containerAdd(ctx, true, false)));
			azWithTail.executes(ctx -> containerAdd(ctx, true, false));
			var ayChain = word("cay").then(azWithTail);
			word("cax").then(ayChain);
			g.then(lit("add").executes(ctx -> containerAdd(ctx, false, false)).then(word("cax").then(ayChain)));

			// mark <type> [--rule R] [--template T]
			var mWord = word("mtype").suggests(iofs(IO_TYPES));
			mWord.executes(ctx -> mark(ctx));
			mWord.then(greedy("opts").executes(ctx -> mark(ctx)));
			g.then(lit("mark").then(mWord));

			// remove [x y z]
			buildOptionalCoordPayload(g, "remove", ContainerGroup::containerRemove);

			// type [x y z] <type>
			var tWord = word("ntype").suggests(iofs(IO_TYPES));
			tWord.executes(ctx -> setType(ctx));
			var tz = word("txz");
			tz.then(tWord);
			var ty = word("tyz").then(tz);
			var tx = word("txz_x").then(ty);
			// 双形态：带/不带显式坐标
			var typeCmd = lit("type");
			typeCmd.executes(ctx -> { // 缺参由执行器校验
				CommandSupport.err(ctx.getSource(), "commands.wh.error.bad_pos");
				return 0;
			});
			typeCmd.then(tWord);
			typeCmd.then(tx);
			g.then(typeCmd);

			// mode [x y z] <mode>
			var modeWord = word("nmode").suggests(iofs(MODES));
			modeWord.executes(ctx -> setMode(ctx));
			var mz = word("mxz");
			mz.then(modeWord);
			var my = word("myz").then(mz);
			var mx = word("mxz_x").then(my);
			var modeCmd = lit("mode");
			modeCmd.then(modeWord);
			modeCmd.then(mx);
			g.then(modeCmd);

			// memory [x y z] [clear]
			var memZ = word("mzz_x"); // 名字仅作区分
			memZ.executes(ctx -> memoryInfo(ctx));
			memZ.then(lit("clear").executes(ctx -> memoryClear(ctx)));
			var memY = word("mzy").then(memZ);
			var memX = word("mzx").then(memY);
			var memCmd = lit("memory");
			memCmd.executes(ContainerGroup::memoryInfo);
			memCmd.then(lit("clear").executes(ContainerGroup::memoryClear));
			memCmd.then(memX);
			g.then(memCmd);

			// rules [x y z] <add|remove> <ruleId>
			var rz = word("rzx");
			var rw = word("rop").suggests(iofs(List.of("add", "remove")));
			var rid = word("rrule").suggests(ruleIds());
			rid.executes(ctx -> containerRules(ctx));
			rw.then(rid);
			rz.then(rw);
			var rulesCmd = lit("rules");
			rulesCmd.then(lit("add").then(word("arule").suggests(ruleIds())
					.executes(ctx -> containerRulesPick(ctx, true))));
			rulesCmd.then(lit("remove").then(word("rrule2").suggests(ruleIds())
					.executes(ctx -> containerRulesPick(ctx, false))));
			rulesCmd.then(word("rx").then(word("ry").then(rz)));
			g.then(rulesCmd);

			root.then(g);
		}

		static void buildOptionalCoordPayload(LiteralArgumentBuilder<FabricClientCommandSource> parent,
				String name, PosHandler payload) {
			var cmd = lit(name);
			cmd.executes(ctx -> payload.run(ctx, null));
			cmd.then(word("px_" + name).then(word("py_" + name).then(word("pz_" + name)
					.executes(ctx -> payload.run(ctx,
							CommandSupport.posOf(ctx, "px_" + name, "py_" + name, "pz_" + name))))));
			parent.then(cmd);
		}

		interface PosHandler {
			int run(CommandContext<FabricClientCommandSource> ctx, BlockPos posOrNull);
		}

		@FunctionalInterface
		interface PickResolver {
			int run(CommandContext<FabricClientCommandSource> ctx, WorldDim dim, BlockPos pos);
		}

		// ---- helpers ----

		static BlockPos resolveTarget(CommandContext<FabricClientCommandSource> ctx, boolean coordsGiven) {
			if (coordsGiven) {
				return CommandSupport.posOf(ctx, "cax", "cay", "caz");
			}
			return CommandSupport.lookTarget(ctx.getSource());
		}

		static BlockPos ctxLook(CommandContext<FabricClientCommandSource> ctx) {
			BlockPos p = CommandSupport.lookTarget(ctx.getSource());
			return p; // 可能为 null，调用方处理
		}

		static int listContainers(CommandContext<FabricClientCommandSource> ctx) {
			Warehouse wh = requireActiveInWorld(ctx.getSource());
			if (wh == null) return 0;
			if (wh.containers.isEmpty()) {
				ctx.getSource().sendFeedback(Component.translatable("commands.wh.container.empty_list")
						.withStyle(ChatFormatting.GRAY));
				return 0;
			}
			printContainers(ctx.getSource(), wh.containers);
			return wh.containers.size();
		}

		static int containerAdd(CommandContext<FabricClientCommandSource> ctx, boolean coordsGiven,
				boolean unused) {
			var src = ctx.getSource();
			Warehouse wh = requireActiveInWorld(src);
			if (wh == null) return 0;
			BlockPos abs = resolveTarget(ctx, coordsGiven);
			if (abs == null) {
				CommandSupport.err(src, "commands.wh.error.no_look_target");
				return 0;
			}
			WorldDim dim = CommandSupport.currentDim(src);
			WorldDimPos rel = CommandSupport.relativePos(manager(), dim, abs);
			if (rel == null) {
				CommandSupport.err(src, "commands.wh.mark.no_anchor");
				return 0;
			}
			if (wh.containerAt(dim, rel.toBlockPos()) != null) {
				CommandSupport.err(src, "commands.wh.container.already_registered");
				return 0;
			}
			CommandSupport.Opts opts = CommandSupport.parseOpts(optTail(ctx));
			IOType type = coordsGiven
					? (opts.type() != null ? opts.type() : IOType.INPUT)
					: IOType.INPUT;
			ContainerInfo info = new ContainerInfo(type);
			info.pos.add(rel);
			String ruleRef = opts.rule() != null ? opts.rule() : opts.template();
			if (ruleRef != null) {
				if (!wh.rules.containsKey(ruleRef)) {
					CommandSupport.err(src, "commands.wh.mark.rule_missing", ruleRef);
					return 0;
				}
				info.rules.add(ruleRef);
			}
			String d2err = validateContainer(wh, info);
			if (d2err != null) {
				CommandSupport.err(src, "commands.wh.error.generic", d2err);
				return 0;
			}
			wh.containers.add(info);
			manager().save(wh);
			src.sendFeedback(Component.translatable("commands.wh.container.added",
					info.ioType.name(), abs.getX(), abs.getY(), abs.getZ())
					.withStyle(ChatFormatting.GREEN));
			return 1;
		}

		static int containerRemove(CommandContext<FabricClientCommandSource> ctx, BlockPos coordArg) {
			return modifyAt(ctx, coordArg, (src, wh, dim, info) -> {
				wh.containers.remove(info);
				manager().save(wh);
				src.sendFeedback(Component.translatable("commands.wh.container.removed",
						fmtPos(info.canonicalPos())).withStyle(ChatFormatting.YELLOW));
				return 1;
			});
		}

		static int setType(CommandContext<FabricClientCommandSource> ctx) {
			return setTypeMode(ctx, true);
		}

		static int setMode(CommandContext<FabricClientCommandSource> ctx) {
			return setTypeMode(ctx, false);
		}

		private static int setTypeMode(CommandContext<FabricClientCommandSource> ctx, boolean isType) {
			var src = ctx.getSource();
			Warehouse wh = requireActiveInWorld(src);
			if (wh == null) return 0;
			// 定位：优先显式三坐标（任意前缀名命中），否则准星
			BlockPos abs = posFromAnyPrefix(ctx);
			if (abs == null) abs = CommandSupport.lookTarget(src);
			if (abs == null) {
				CommandSupport.err(src, "commands.wh.error.no_look_target");
				return 0;
			}
			ContainerInfo info = findAt(src, wh, abs);
			if (info == null) return 0;
			if (isType) {
				String val = firstArg(ctx, "ntype", "txz_x", "tyz", "txz");
				IOType io = null;
				try {
					io = IOType.valueOf(val.toUpperCase(Locale.ROOT));
				} catch (Exception ignored) {
				}
				if (io == null) {
					CommandSupport.err(src, "commands.wh.container.bad_type", val);
					return 0;
				}
				info.ioType = io;
				info.ruleMode = null; // 回落默认
				for (String rid : info.rules) {
					ContainerRule r = wh.rules.get(rid);
					if (r != null) {
						String e = ConfigValidator.validateRuleOnContainers(wh, r);
						if (e != null) {
							CommandSupport.err(src, "commands.wh.error.generic", e);
							return 0;
						}
					}
				}
				src.sendFeedback(Component.translatable("commands.wh.container.type_set",
						posOf(abs), io.name()).withStyle(ChatFormatting.GREEN));
			} else {
				String val = firstArg(ctx, "nmode", "mxz_x", "myz", "mxz");
				RuleMode mode = null;
				try {
					mode = RuleMode.valueOf(val.toUpperCase(Locale.ROOT));
				} catch (Exception ignored) {
				}
				if (mode == null) {
					CommandSupport.err(src, "commands.wh.container.bad_mode", val);
					return 0;
				}
				info.ruleMode = mode;
				src.sendFeedback(Component.translatable("commands.wh.container.mode_set",
						posOf(abs), mode.name()).withStyle(ChatFormatting.GREEN));
			}
			manager().save(wh);
			return 1;
		}

		/** 从全部已知坐标前缀中尝试解析三坐标（type/mode 复用形态混杂，兼容两种命名） */
		static BlockPos posFromAnyPrefix(CommandContext<FabricClientCommandSource> ctx) {
			for (String[] trio : new String[][] {
					{"txz_x", "tyz", "txz"}, {"mxz_x", "myz", "mxz"},
					{"px_containerRemove", "py_containerRemove", "pz_containerRemove"},
					{"mzx", "mzy", "mzz_x"}, {"rx", "ry", "rz"}}) {
				BlockPos p = CommandSupport.posOf(ctx, trio[0], trio[1], trio[2]);
				if (p != null) return p;
			}
			return null;
		}

		static String firstArg(CommandContext<FabricClientCommandSource> ctx, String... names) {
			for (String n : names) {
				try {
					return StringArgumentType.getString(ctx, n);
				} catch (IllegalArgumentException ignored) {
					// 尝试下一个
				}
			}
			return "";
		}

		static String posOf(BlockPos p) {
			return p.getX() + " " + p.getY() + " " + p.getZ();
		}

		static int memoryInfo(CommandContext<FabricClientCommandSource> ctx) {
			BlockPos abs = absFromMemoryPrefixes(ctx);
			return doMemoryInfo(ctx, abs != null ? abs : CommandSupport.lookTarget(ctx.getSource()), false);
		}

		static int memoryClear(CommandContext<FabricClientCommandSource> ctx) {
			BlockPos abs = absFromMemoryPrefixes(ctx);
			return doMemoryInfo(ctx, abs != null ? abs : CommandSupport.lookTarget(ctx.getSource()), true);
		}

		static int doMemoryInfo(CommandContext<FabricClientCommandSource> ctx, BlockPos abs, boolean clear) {
			var src = ctx.getSource();
			Warehouse wh = requireActiveInWorld(src);
			if (wh == null) return 0;
			ContainerInfo info = findAt(src, wh, abs);
			if (info == null) return 0;
			var store = WarehouseServices.cacheStore();
			if (store == null) {
				CommandSupport.err(src, "commands.wh.error.generic", "no cache store");
				return 0;
			}
			CacheKey key = CacheKey.of(info.canonicalPos(), playerUuid(src));
			if (clear) {
				store.invalidate(key);
				CommandSupport.fb(ctx, "commands.wh.container.memory.cleared", ChatFormatting.YELLOW,
						fmtPos(info.canonicalPos()));
				return 1;
			}
			boolean explored = store.isExplored(key);
			var mem = store.getValid(key);
			long itemCount = 0;
			if (mem != null && mem.snapshot() != null) {
				itemCount = mem.snapshot().slots().values().stream()
						.mapToInt(st -> st.getCount()).sum();
			}
			src.sendFeedback(Component.translatable(explored
					? "commands.wh.container.memory.explored"
					: "commands.wh.container.memory.unexplored",
					key.toString(), info.cacheType.name(),
					mem == null ? "-" : mem.cacheType().name(),
					itemCount, fmtPos(info.canonicalPos())));
			return 1;
		}

		static BlockPos absFromMemoryPrefixes(CommandContext<FabricClientCommandSource> ctx) {
			return CommandSupport.posOf(ctx, "mzx", "mzy", "mzz_x");
		}

		static int containerRulesPick(CommandContext<FabricClientCommandSource> ctx, boolean addOp) {
			// PDD: /wh container rules <pos> <add|remove> <ruleId> —— 此处采用子命令式等价实现
			var src = ctx.getSource();
			Warehouse wh = requireActiveInWorld(src);
			if (wh == null) return 0;
			BlockPos abs = CommandSupport.lookTarget(src);
			ContainerInfo info = findAt(src, wh, abs);
			if (info == null) return 0;
			String ruleId = addOp ? StringArgumentType.getString(ctx, "arule")
					: StringArgumentType.getString(ctx, "rrule2");
			return applyRuleToContainer(src, wh, info, ruleId, addOp);
		}

		static int containerRules(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActiveInWorld(src);
			if (wh == null) return 0;
			BlockPos abs = posFromAnyPrefix(ctx);
			if (abs == null) abs = CommandSupport.lookTarget(src);
			ContainerInfo info = findAt(src, wh, abs);
			if (info == null) return 0;
			String op = firstArg(ctx, "rop");
			boolean isAdd = op.equalsIgnoreCase("add");
			String ruleId = "";
			for (String k : new String[]{"rrule", "arule", "rrule2"}) {
				String v = tryStr(ctx, k);
				if (v != null) {
					ruleId = v;
					break;
				}
			}
			return applyRuleToContainer(src, wh, info, ruleId, isAdd);
		}

		static String tryStr(CommandContext<FabricClientCommandSource> ctx, String name) {
			try {
				return StringArgumentType.getString(ctx, name);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}

		static int applyRuleToContainer(FabricClientCommandSource src, Warehouse wh, ContainerInfo info,
				String ruleId, boolean addOp) {
			if (ruleId == null || ruleId.isBlank()) {
				CommandSupport.err(src, "commands.wh.rule.missing", "-");
				return 0;
			}
			if (addOp) {
				if (!wh.rules.containsKey(ruleId)) {
					CommandSupport.err(src, "commands.wh.rule.missing", ruleId);
					return 0;
				}
				if (!info.rules.contains(ruleId)) info.rules.add(ruleId);
				ContainerRule r = wh.rules.get(ruleId);
				if (r != null) {
					String e = ConfigValidator.validateRuleOnContainers(wh, r);
					if (e != null) {
						info.rules.remove(ruleId);
						CommandSupport.err(src, "commands.wh.error.generic", e);
						return 0;
					}
				}
			} else {
				info.rules.remove(ruleId);
			}
			manager().save(wh);
			src.sendFeedback(Component.translatable(
					addOp ? "commands.wh.container.rule_added" : "commands.wh.container.rule_removed",
					posOf(findAbsPreview(src, info)), ruleId).withStyle(ChatFormatting.GREEN));
			return 1;
		}

		@Deprecated(forRemoval = false)
		static BlockPos findAbsPreview(FabricClientCommandSource src, ContainerInfo info) {
			Warehouse wh = manager().active();
			if (wh == null) return BlockPos.ZERO;
			BlockPos anchor = wh.anchorOf(CommandSupport.currentDim(src));
			return anchor == null ? null : info.canonicalPos().plus(anchor).toBlockPos();
		}

		static int mark(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse wh = requireActiveInWorld(src);
			if (wh == null) return 0;
			if (markNeedsCtx(ctx) == null) return 0;
			String typeName = StringArgumentType.getString(ctx, "mtype");
			bid.yuanlu.mc.warehouse.api.container.IOType io;
			try {
				io = bid.yuanlu.mc.warehouse.api.container.IOType.valueOf(typeName.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				CommandSupport.err(src, "commands.wh.container.bad_type", typeName);
				return 0;
			}
			CommandSupport.Opts opts = CommandSupport.parseOpts(optTail(ctx));
			var session = MarkMode.get().toggle(io, opts.rule(), opts.template());
			if (session == null) {
				src.sendFeedback(Component.translatable("commands.wh.mark.exited")
						.withStyle(ChatFormatting.GRAY));
			} else {
				src.sendFeedback(Component.translatable("commands.wh.mark.entered",
						io.name()).withStyle(ChatFormatting.AQUA));
			}
			return 1;
		}

		static CommandContext<FabricClientCommandSource> markNeedsCtx(
				CommandContext<FabricClientCommandSource> ctx) {
			return ctx; // 预留扩展点
		}

		// ---- 共享 ----

		@FunctionalInterface
		interface ModifyFn {
			int run(FabricClientCommandSource src, Warehouse wh, WorldDim dim, ContainerInfo info);
		}

		static int modifyAt(CommandContext<FabricClientCommandSource> ctx, BlockPos coordArg, ModifyFn fn) {
			var src = ctx.getSource();
			Warehouse wh = requireActiveInWorld(src);
			if (wh == null) return 0;
			BlockPos abs = coordArg != null ? coordArg : CommandSupport.lookTarget(src);
			ContainerInfo info = findAt(src, wh, abs);
			if (info == null) return 0;
			return fn.run(src, wh, CommandSupport.currentDim(src), info);
		}

		static ContainerInfo findAt(FabricClientCommandSource src, Warehouse wh, BlockPos abs) {
			if (abs == null) {
				CommandSupport.err(src, "commands.wh.error.no_look_target");
				return null;
			}
			WorldDim dim = CommandSupport.currentDim(src);
			BlockPos rel = CommandSupport.relOf(wh, dim, abs);
			if (rel == null) {
				CommandSupport.err(src, "commands.wh.mark.no_anchor");
				return null;
			}
			ContainerInfo info = wh.containerAt(dim, rel);
			if (info == null) {
				CommandSupport.err(src, "commands.wh.container.not_registered", abs.toShortString());
				return null;
			}
			return info;
		}

		static String validateContainer(Warehouse wh, ContainerInfo info) {
			for (String rid : info.rules) {
				ContainerRule r = wh.rules.get(rid);
				if (r != null) {
					// 临时加入后校验再移除不是必需——validator 直接按容器本身判
					for (ItemRule ir : r.itemRules) {
						if (info.effectiveRuleMode() == RuleMode.WHITELIST && ir.isUnlimited()) continue;
					}
				}
			}
			// 统一走 D2 校验器口径
			Warehouse probe = wh;
			ContainerInfo original = null;
			if (!probe.containers.contains(info)) {
				original = info;
				probe.containers.add(info);
			}
			String err = null;
			try {
				for (String rid : List.copyOf(info.rules)) {
					ContainerRule r = probe.rules.get(rid);
					if (r == null) continue;
					String e = ConfigValidator.validateRuleOnContainers(probe, r);
					if (e != null) {
						err = e;
						break;
					}
				}
			} finally {
				if (original != null) probe.containers.remove(info);
			}
			return err;
		}

		private ContainerGroup() {
		}
	}

	// ============ 选区组 ============

	static final class SelectGroup {

		static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
			var g = lit("select");
			g.requires(s -> true);
			g.then(cornerTree("pos1", SelectGroup::setCorner1));
			g.then(cornerTree("pos2", SelectGroup::setCorner2));
			g.then(lit("expand")
					.then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>
							argument("count", IntegerArgumentType.integer(1)).suggests(iofs(List.of()))
							.then(word("dir").suggests(iofs(DIRS)).executes(SelectGroup::expand))));
			g.then(lit("show").executes(SelectGroup::showSel));
			g.then(lit("clear").executes(SelectGroup::clearSel));
			g.then(lit("set-type").then(word("type").suggests(iofs(IO_TYPES)).executes(SelectGroup::setType)));
			g.then(lit("set-rule").then(word("id").suggests(ruleIds()).executes(SelectGroup::setRule)));
			g.then(lit("set-cache").then(word("type").suggests(iofs(CACHES)).executes(SelectGroup::setCache)));
			g.then(lit("plan").executes(SelectGroup::planNotImplemented));
			root.then(g);
		}

		/** posN [x y z | --look]：缺省=玩家位置 */
		static LiteralArgumentBuilder<FabricClientCommandSource> cornerTree(String name,
				java.util.function.BiFunction<WorldDim, BlockPos, Integer> setter) {
			var b = lit(name).executes(ctx -> save(setter, ctx.getSource(),
					BlockPos.containing(ctx.getSource().getPosition())));
			b.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(name + "x",
					StringArgumentType.word())
					.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
							name + "y", StringArgumentType.word())
							.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
									name + "z", StringArgumentType.word())
									.executes(ctx -> {
										BlockPos p = CommandSupport.posOf(ctx,
												name + "x", name + "y", name + "z");
										return p == null
												? errBadPos(ctx.getSource())
												: save(setter, ctx.getSource(), p);
									}))));
			b.then(lit("--look").executes(ctx -> {
				BlockPos p = CommandSupport.lookTarget(ctx.getSource());
				return p == null ? errNoLook(ctx.getSource()) : save(setter, ctx.getSource(), p);
			}));
			return b;
		}

		static int setCorner1(WorldDim dim, BlockPos abs) {
			SelectionState.get().set1(new WorldDimPos(dim.worldId(), dim.dimId(),
					abs.getX(), abs.getY(), abs.getZ()));
			return 1;
		}

		static int setCorner2(WorldDim dim, BlockPos abs) {
			SelectionState.get().set2(new WorldDimPos(dim.worldId(), dim.dimId(),
					abs.getX(), abs.getY(), abs.getZ()));
			return 1;
		}

		private static int save(java.util.function.BiFunction<WorldDim, BlockPos, Integer> setter,
				FabricClientCommandSource src, BlockPos p) {
			WorldDim dim = CommandSupport.currentDim(src);
			if (dim.worldId() == null || dim.dimId() == null) {
				CommandSupport.err(src, "commands.wh.error.not_in_world");
				return 0;
			}
			int rc = setter.apply(dim, p);
			src.sendFeedback(Component.translatable("commands.wh.select.corner_set",
					p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GREEN));
			return rc;
		}

		private static int errNoLook(FabricClientCommandSource src) {
			CommandSupport.err(src, "commands.wh.error.no_look_target");
			return 0;
		}

		private static int errBadPos(FabricClientCommandSource src) {
			CommandSupport.err(src, "commands.wh.error.bad_pos");
			return 0;
		}

		static int expand(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			SelectionState sel = SelectionState.get();
			int count = IntegerArgumentType.getInteger(ctx, "count");
			var dir = CommandSupport.parseDir(StringArgumentType.getString(ctx, "dir"));
			if (dir == null) {
				CommandSupport.err(src, "commands.wh.select.bad_dir", "?");
				return 0;
			}
			if (sel.pos2() == null) {
				if (sel.pos1() == null) {
					CommandSupport.err(src, "commands.wh.select.no_corners");
					return 0;
				}
				sel.set2(sel.pos1());
			}
			WorldDimPos p2 = sel.pos2();
			sel.set2(new WorldDimPos(p2.world(), p2.dim(),
					p2.x() + dir.getStepX() * count,
					p2.y() + dir.getStepY() * count,
					p2.z() + dir.getStepZ() * count));
			CommandSupport.fb(ctx, "commands.wh.select.expanded", ChatFormatting.GREEN,
					sel.pos2().x(), sel.pos2().y(), sel.pos2().z());
			return 1;
		}

		static int showSel(CommandContext<FabricClientCommandSource> ctx) {
			SelectionState sel = SelectionState.get();
			if (!sel.hasBox()) {
				ctx.getSource().sendFeedback(Component.translatable("commands.wh.select.incomplete")
						.withStyle(ChatFormatting.GRAY));
				return 0;
			}
			ctx.getSource().sendFeedback(Component.translatable("commands.wh.select.box",
					sel.pos1().x(), sel.pos1().y(), sel.pos1().z(),
					sel.pos2().x(), sel.pos2().y(), sel.pos2().z(),
					sel.pos1().world(), sel.pos1().dim()));
			return 1;
		}

		static int clearSel(CommandContext<FabricClientCommandSource> ctx) {
			SelectionState.get().clear();
			CommandSupport.fb(ctx, "commands.wh.select.cleared", ChatFormatting.YELLOW);
			return 1;
		}

		static int setType(CommandContext<FabricClientCommandSource> ctx) {
			return applyBatch(ctx, true);
		}

		static int setCache(CommandContext<FabricClientCommandSource> ctx) {
			return applyBatch(ctx, true);
		}

		static int setRule(CommandContext<FabricClientCommandSource> ctx) {
			return applyBatch(ctx, false);
		}

		private static int applyBatch(CommandContext<FabricClientCommandSource> ctx, boolean isTypeOrCache) {
			var src = ctx.getSource();
			Warehouse wh = requireActive(src);
			if (wh == null) return 0;
			SelectionState sel = SelectionState.get();
			if (!sel.hasBox()) {
				CommandSupport.err(src, "commands.wh.select.incomplete");
				return 0;
			}
			int changed = 0;
			List<ContainerInfo> changedList = new ArrayList<>();
			if (isTypeOrCache && ctx.getInput().contains("set-cache")) {
				String v = StringArgumentType.getString(ctx, "type");
				CacheType ct = CacheType.valueOf(v.toUpperCase(Locale.ROOT));
				for (ContainerInfo c : wh.containers) {
					if (inBox(sel, c)) {
						c.cacheType = ct;
						changedList.add(c);
						changed++;
					}
				}
			} else if (isTypeOrCache) {
				String v = StringArgumentType.getString(ctx, "type");
				IOType io = IOType.valueOf(v.toUpperCase(Locale.ROOT));
				for (ContainerInfo c : wh.containers) {
					if (inBox(sel, c)) {
						c.ioType = io;
						c.ruleMode = null;
						changedList.add(c);
						changed++;
					}
				}
				// D2 重校验全部规则引用
				for (ContainerInfo ci : changedList) {
					for (String rid : ci.rules) {
						ContainerRule r = wh.rules.get(rid);
						if (r != null) {
							String e = ConfigValidator.validateRuleOnContainers(wh, r);
							if (e != null) {
								CommandSupport.err(src, "commands.wh.error.generic", e);
								return 0;
							}
						}
					}
				}
			} else {
				String ruleId = StringArgumentType.getString(ctx, "id");
				if (!wh.rules.containsKey(ruleId)) {
					CommandSupport.err(src, "commands.wh.rule.missing", ruleId);
					return 0;
				}
				for (ContainerInfo c : wh.containers) {
					if (inBox(sel, c)) {
						if (!c.rules.contains(ruleId)) c.rules.add(ruleId);
						changedList.add(c);
						changed++;
					}
				}
			}
			if (!changedList.isEmpty()) manager().save(wh);
			CommandSupport.fb(ctx, "commands.wh.select.applied", ChatFormatting.GREEN, changed);
			return changed;
		}

		static boolean inBox(SelectionState sel, ContainerInfo c) {
			if (sel.hasBox() && c.pos.isEmpty()) return false;
			for (WorldDimPos p : c.pos) {
				// 绝对化：canonical 相对坐标 → 世界绝对坐标对比选区
				Warehouse wh = manager().active();
				if (wh == null) return false;
				WorldDim dim = new WorldDim(p.world(), p.dim());
				BlockPos anchor = wh.anchorOf(dim);
				if (anchor == null) continue;
				BlockPos abs = p.plus(anchor).toBlockPos();
				if (sel.contains(abs.getX(), abs.getY(), abs.getZ())) return true;
			}
			return false;
		}

		static int planNotImplemented(CommandContext<FabricClientCommandSource> ctx) {
			ctx.getSource().sendFeedback(Component.translatable("commands.wh.select.plan.todo")
					.withStyle(ChatFormatting.GRAY));
			return 0;
		}

		private SelectGroup() {
		}
	}

	// ============ 跨仓库搬运组 ============

	static final class TransferGroup {

		static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
			var g = lit("transfer");
			g.then(lit("status").executes(TransferGroup::status));
			g.then(lit("stop").executes(TransferGroup::stop));
			g.then(word("src").suggests(warehouses())
					.then(word("dst").suggests(warehouses())
							.then(lit("start").executes(TransferGroup::start))));
			root.then(g);
		}

		static int start(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			WarehouseManagerImpl mgr = manager();
			if (mgr.hasTransferOverlay()) {
				CommandSupport.err(src, "commands.wh.transfer.blocked");
				return 0;
			}
			Warehouse s = mgr.get(StringArgumentType.getString(ctx, "src"));
			Warehouse d = mgr.get(StringArgumentType.getString(ctx, "dst"));
			if (s == null || d == null) {
				CommandSupport.err(src, "commands.wh.error.no_such_warehouse",
						s == null ? StringArgumentType.getString(ctx, "src")
								: StringArgumentType.getString(ctx, "dst"));
				return 0;
			}
			if (s.id.equals(d.id)) {
				CommandSupport.err(src, "commands.wh.transfer.same");
				return 0;
			}
			TransportEngine engine = engine();
			if (engine.isRunning()) {
				CommandSupport.err(src, "commands.wh.control.running");
				return 0;
			}
			String overlayId = TransferOverlay.start(mgr, engine, s, d);
			src.sendFeedback(Component.translatable("commands.wh.transfer.started",
					s.id, d.id, overlayId).withStyle(ChatFormatting.GREEN));
			return 1;
		}

		static int status(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			Warehouse ov = manager().getTransferOverlay();
			if (ov == null) {
				src.sendFeedback(Component.translatable("commands.wh.transfer.none")
						.withStyle(ChatFormatting.GRAY));
				return 0;
			}
			TransportEngine engine = engine();
			src.sendFeedback(Component.translatable("commands.wh.transfer.active", ov.id,
					Component.translatable(engine.state() == null ? "wh.state.idle"
							: "wh.state." + engine.state().name())));
			return 1;
		}

		static int stop(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			WarehouseManagerImpl mgr = manager();
			if (!mgr.hasTransferOverlay()) {
				CommandSupport.err(src, "commands.wh.transfer.none_running");
				return 0;
			}
			TransferOverlay.end(mgr, engine(), true);
			src.sendFeedback(Component.translatable("commands.wh.transfer.stopped")
					.withStyle(ChatFormatting.YELLOW));
			return 1;
		}

		private TransferGroup() {
		}
	}

	// ============ 配置组 ============

	static final class ConfigGroup {

		static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
			var g = lit("config");
			g.then(lit("show").executes(ConfigGroup::showCfg));
			g.then(lit("set").then(word("key").suggests(iofs(CONFIG_KEYS))
					.then(strArg("value").executes(ConfigGroup::setCfg))));
			root.then(g);
		}

		static int showCfg(CommandContext<FabricClientCommandSource> ctx) {
			ModConfig c = WarehouseServices.modConfig();
			if (c == null) {
				CommandSupport.err(ctx.getSource(), "commands.wh.error.generic", "no config");
				return 0;
			}
			var src = ctx.getSource();
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"debug", String.valueOf(c.debug)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"defaultInteractionSpeed", String.valueOf(c.defaultInteractionSpeed)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"interactionJitterPercent", String.valueOf(c.interactionJitterPercent)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"cacheTtlSeconds", String.valueOf(c.cacheTtlSeconds)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"slotAllocator", String.valueOf(c.slotAllocator)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"reachLimit", String.valueOf(c.reachLimit)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"exploreFailMax", String.valueOf(c.exploreFailMax)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"navRetryMax", String.valueOf(c.navRetryMax)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"timeouts.openTicks", String.valueOf(c.timeouts().openTicks)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"timeouts.confirmTicks", String.valueOf(c.timeouts().confirmTicks)));
			src.sendFeedback(Component.translatable("commands.wh.config.value",
					"timeouts.settleTicks", String.valueOf(c.timeouts().settleTicks)));
			return 11;
		}

		static int setCfg(CommandContext<FabricClientCommandSource> ctx) {
			var src = ctx.getSource();
			ModConfig c = WarehouseServices.modConfig();
			if (c == null) {
				CommandSupport.err(src, "commands.wh.error.generic", "no config");
				return 0;
			}
			String key = StringArgumentType.getString(ctx, "key");
			String value = StringArgumentType.getString(ctx, "value");
			try {
				switch (key) {
					case "debug" -> c.debug = Boolean.parseBoolean(value);
					case "defaultInteractionSpeed" -> c.defaultInteractionSpeed = Integer.parseInt(value);
					case "interactionJitterPercent" -> c.interactionJitterPercent =
							Integer.parseInt(value);
					case "cacheTtlSeconds" -> c.cacheTtlSeconds = Integer.parseInt(value);
					case "slotAllocator" -> c.slotAllocator = value;
					case "reachLimit" -> c.reachLimit = Double.parseDouble(value);
					case "exploreFailMax" -> c.exploreFailMax = Integer.parseInt(value);
					case "navRetryMax" -> c.navRetryMax = Integer.parseInt(value);
					case "timeouts.openTicks" -> c.timeouts().openTicks = Integer.parseInt(value);
					case "timeouts.confirmTicks" -> c.timeouts().confirmTicks = Integer.parseInt(value);
					case "timeouts.settleTicks" -> c.timeouts().settleTicks = Integer.parseInt(value);
					default -> {
						CommandSupport.err(src, "commands.wh.config.unknown_key", key);
						return 0;
					}
				}
			} catch (NumberFormatException e) {
				CommandSupport.err(src, "commands.wh.config.bad_value", key, value);
				return 0;
			}
			new ConfigIO(ConfigIO.defaultRoot()).saveModConfig(c);
			CommandSupport.fb(ctx, "commands.wh.config.value", ChatFormatting.GREEN, key, value);
			return 1;
		}

		private ConfigGroup() {
		}
	}

	// ============ 公共 ============

		/** 可选 greedy 尾参；不存在时 null */
	private static String lastEx = "";

	private static String ex() {
		return lastEx.isEmpty() ? "" : " (" + lastEx + ")";
	}

	/** 可选 greedy 尾参（规则 add 用） */
	static String greedyArg(CommandContext<FabricClientCommandSource> ctx) {
		try {
			return StringArgumentType.getString(ctx, "tail");
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static String optTail(CommandContext<FabricClientCommandSource> ctx) {
		try {
			return StringArgumentType.getString(ctx, "opts");
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static WarehouseManagerImpl manager() {
		return WarehouseManagerImpl.get();
	}

	/** 引擎（接口类型；未装配即未初始化的异常装配环境） */
	static TransportEngine engine() {
		TransportEngine e = WarehouseServices.transportEngine();
		if (e == null) throw new IllegalStateException("TransportEngine not initialized");
		return e;
	}

	static Warehouse requireActive(FabricClientCommandSource src) {
		Warehouse wh = manager().active();
		if (wh == null) {
			CommandSupport.err(src, "commands.wh.status.none");
			return null;
		}
		return wh;
	}

	static Warehouse requireActiveInWorld(FabricClientCommandSource src) {
		Warehouse wh = manager().active();
		if (wh == null) {
			CommandSupport.err(src, "commands.wh.status.none");
			return null;
		}
		WorldDim dim = CommandSupport.currentDim(src);
		if (dim.worldId() == null || dim.dimId() == null) {
			CommandSupport.err(src, "commands.wh.error.not_in_world");
			return null;
		}
		return wh;
	}

		static String fmtPos(WorldDimPos p) {
		return p.x() + " " + p.y() + " " + p.z();
	}

	static UUID playerUuid(FabricClientCommandSource src) {
		var p = src.getPlayer();
		return p == null ? null : p.getUUID();
	}
}

package bid.yuanlu.mc.warehouse.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 命令层公共支撑：坐标解析（~ 相对）、准星目标、可选参数解析、i18n 反馈。
 */
final class CommandSupport {

	private CommandSupport() {
	}

	// ---- 反馈 ----

	static void fb(CommandContext<FabricClientCommandSource> ctx, String key, ChatFormatting color,
			Object... args) {
		ctx.getSource().sendFeedback(Component.translatable(key, args).withStyle(color));
	}

	static void err(FabricClientCommandSource src, String key, Object... args) {
		src.sendError(Component.translatable(key, args).withStyle(ChatFormatting.RED));
	}

	// ---- 环境 ----

	static WarehouseManagerImpl manager(FabricClientCommandSource src) {
		return WarehouseManagerImpl.get();
	}

	static WorldDim currentDim(FabricClientCommandSource src) {
		WorldSessionTracker trk = WorldSessionTracker.get();
		String worldId = trk == null ? null : trk.currentWorldId();
		String dimId = src.getLevel() == null ? null
				: src.getLevel().dimension().identifier().toString();
		return new WorldDim(worldId, dimId);
	}

	// ---- 坐标 ----

	/** 解析 "~"/"~-3"/"-12" 形式的坐标 token；失败抛 NumberFormatException */
	static int coord(String token, double origin) {
		if (token.startsWith("~")) {
			String off = token.substring(1);
			double delta = off.isEmpty() ? 0 : Double.parseDouble(off);
			return (int) Math.floor(origin + delta);
		}
		return Integer.parseInt(token);
	}

	/** 从 ctx 读 xKey/yKey/zKey 三个 word 参数为绝对坐标；解析失败返回 null */
	@Nullable
	static BlockPos posOf(CommandContext<FabricClientCommandSource> ctx, String xKey, String yKey,
			String zKey) {
		try {
			var v = ctx.getSource().getPosition();
			return new BlockPos(
					coord(StringArgumentType.getString(ctx, xKey), v.x),
					coord(StringArgumentType.getString(ctx, yKey), v.y),
					coord(StringArgumentType.getString(ctx, zKey), v.z));
		} catch (Exception e) {
			return null;
		}
	}

	/** 准星指向的方块坐标（--look 语义，PDD §5.7/§10.1） */
	@Nullable
	static BlockPos lookTarget(FabricClientCommandSource src) {
		Minecraft mc = src.getClient();
		return mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
	}

	// ---- 容器定位 ----

	/** 绝对坐标 → 激活仓库中的 ContainerInfo（按 canonical 相对坐标比对） */
	static ContainerInfo containerAtAbs(WarehouseManagerImpl mgr, WorldDim dim, BlockPos abs) {
		Warehouse wh = mgr.active();
		if (wh == null) return null;
		BlockPos rel = relOf(wh, dim, abs);
		if (rel == null) return null;
		return wh.containerAt(dim, rel);
	}

	@Nullable
	static BlockPos relOf(Warehouse wh, WorldDim dim, BlockPos abs) {
		BlockPos anchor = wh.anchorOf(dim);
		return anchor == null ? null : abs.subtract(anchor);
	}

	/** 绝对坐标 → 相对坐标记录；无 anchor 时 null */
	@Nullable
	static WorldDimPos relativePos(WarehouseManagerImpl mgr, WorldDim dim, BlockPos abs) {
		Warehouse wh = mgr.active();
		if (wh == null) return null;
		BlockPos anchor = wh.anchorOf(dim);
		if (anchor == null) return null;
		return new WorldDimPos(dim.worldId(), dim.dimId(),
				abs.getX() - anchor.getX(), abs.getY() - anchor.getY(), abs.getZ() - anchor.getZ());
	}

	// ---- 可选参数 ----

	record Opts(IOType type, String rule, String template, boolean negate, String quantity) {

		static final Opts EMPTY = new Opts(null, null, null, false, null);
	}

	/** 解析 "--type T --rule R --template T --negate --quantity count:64" 空格分词的尾巴 */
	static CommandSupport.Opts parseOpts(@Nullable String tail) {
		if (tail == null || tail.isBlank()) return CommandSupport.Opts.EMPTY;
		IOType type = null;
		String rule = null;
		String template = null;
		boolean negate = false;
		String quantity = null;
		String[] t = tail.trim().split("\\s+");
		for (int i = 0; i < t.length; i++) {
			switch (t[i]) {
				case "--type" -> {
					if (i + 1 < t.length) type = parseIoSafe(t[++i]);
				}
				case "--rule" -> {
					if (i + 1 < t.length) rule = t[++i];
				}
				case "--template" -> {
					if (i + 1 < t.length) template = t[++i];
				}
				case "--negate" -> negate = true;
				case "--quantity" -> {
					if (i + 1 < t.length) quantity = t[++i];
				}
				default -> {
					// 未知 token 忽略
				}
			}
		}
		return new Opts(type, rule, template, negate, quantity);
	}

	static IOType parseIoSafe(String s) {
		try {
			return IOType.valueOf(s.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** 规则 add 尾巴解析结果：首个 token 为 selector（支持裸 JSON），余下走通用 Opts */
	record RuleTail(String selector, Opts opts) {
	}

	/** 解析 "id:minecraft:diamond --negate --quantity count:64" / "{...json} ..." */
	static RuleTail parseRuleTail(@Nullable String raw) {
		if (raw == null || raw.isBlank()) return new RuleTail("", Opts.EMPTY);
		List<String> t = new ArrayList<>(java.util.Arrays.asList(raw.trim().split("\s+")));
		StringBuilder sel = new StringBuilder(t.remove(0));
		long open = count(sel, '{'), close = count(sel, '}');
		while (open > close && !t.isEmpty()) {
			String n = t.remove(0);
			sel.append(' ').append(n);
			open += count(n, '{');
			close += count(n, '}');
		}
		return new RuleTail(sel.toString(), parseOpts(String.join(" ", t)));
	}

	private static long count(CharSequence cs, char c) {
		return cs.chars().filter(x -> x == c).count();
	}

	static Direction parseDir(String s) {
		return Direction.byName(s.toLowerCase(Locale.ROOT));
	}

	// ---- 可选坐标双形态注册 ----

	interface PosPayload extends BiConsumer<CommandContext<FabricClientCommandSource>, @Nullable BlockPos> {
	}

	/**
	 * 在 {@code lit} 下同时注册「无坐标（准星/玩家位置）」与「x y z 前缀」两种形态；
	 * payload 收到的 BlockPos 为 null 表示未提供坐标。
	 */
	static void withOptionalPos(LiteralArgumentBuilder<FabricClientCommandSource> lit,
			PosPayload payload) {
		lit.executes(ctx -> {
			payload.accept(ctx, null);
			return 1;
		});
		lit.then(arg("pos_x")
				.then(arg("pos_y")
						.then(arg("pos_z").executes(ctx -> {
							payload.accept(ctx, posOf(ctx, "pos_x", "pos_y", "pos_z"));
							return 1;
						}))));
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, String> arg(String name) {
		return RequiredArgumentBuilder.argument(name, StringArgumentType.word());
	}
}

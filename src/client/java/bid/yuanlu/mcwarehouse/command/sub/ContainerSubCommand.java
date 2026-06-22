package bid.yuanlu.mcwarehouse.command.sub;

import java.util.ArrayList;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import bid.yuanlu.mcwarehouse.controller.ContainerController;
import bid.yuanlu.mcwarehouse.controller.RuleController;
import bid.yuanlu.mcwarehouse.controller.SelectionController;
import bid.yuanlu.mcwarehouse.controller.WarehouseController;
import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.storage.WarehouseStorage;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class ContainerSubCommand {

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("container")
			.then(literal("add")
				.then(argument("type", word())
					.executes(ctx -> {
						String typeStr = getString(ctx, "type");
						ContainerType type;
						try {
							type = ContainerType.valueOf(typeStr.toUpperCase());
						} catch (IllegalArgumentException e) {
							ctx.getSource().sendError(Component.literal("§cInvalid type. Use: INPUT, OUTPUT, TEMP, IGNORE"));
							return 0;
						}
						var sel = SelectionController.getInstance();
						if (!sel.hasSelection()) {
							ctx.getSource().sendError(Component.literal("§cNo selection set."));
							return 0;
						}
						var wc = WarehouseController.getInstance();
						String active = wc.getActiveWarehouse();
						if (active == null) {
							ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
							return 0;
						}
						Warehouse wh = wc.getWarehouse(active);
						if (wh == null) {
							ctx.getSource().sendError(Component.literal("§cActive warehouse not found."));
							return 0;
						}
						var containers = sel.scanSelection(wh.anchor);
						int count = 0;
						for (var entry : containers.entrySet()) {
							entry.getValue().type = type;
							entry.getValue().ruleMode = ContainerInfo.defaultMode(type);
							if (wc.addContainer(active, entry.getValue())) {
								count++;
							}
						}
						ctx.getSource().sendFeedback(Component.literal("§aAdded " + count + " containers as " + type));
						return 1;
					})))
			.then(literal("remove")
				.executes(ctx -> {
					var sel = SelectionController.getInstance();
					if (!sel.hasSelection()) {
						ctx.getSource().sendError(Component.literal("§cNo selection set."));
						return 0;
					}
					var wc = WarehouseController.getInstance();
					String active = wc.getActiveWarehouse();
					if (active == null) {
						ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
						return 0;
					}
					Warehouse wh = wc.getWarehouse(active);
					if (wh == null || wh.containers == null || wh.containers.isEmpty()) {
						ctx.getSource().sendError(Component.literal("§cNo containers to remove."));
						return 0;
					}
					var selection = sel.getSelection();
					int removed = 0;
					var iter = wh.containers.iterator();
					while (iter.hasNext()) {
						ContainerInfo info = iter.next();
						BlockPos abs = CoordinateUtils.toAbsolute(info.relativePos, wh.anchor);
						if (isInsideSelection(abs, selection)) {
							iter.remove();
							removed++;
						}
					}
					if (removed > 0) {
						new WarehouseStorage().saveWarehouse(wh);
					}
					ctx.getSource().sendFeedback(Component.literal("§aRemoved " + removed + " containers."));
					return 1;
				}))
			.then(literal("list")
				.executes(ctx -> {
					var wc = WarehouseController.getInstance();
					String active = wc.getActiveWarehouse();
					if (active == null) {
						ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
						return 0;
					}
					Warehouse wh = wc.getWarehouse(active);
					if (wh == null || wh.containers == null || wh.containers.isEmpty()) {
						ctx.getSource().sendFeedback(Component.literal("§eNo containers in warehouse \"" + active + "\""));
						return 1;
					}
					ctx.getSource().sendFeedback(Component.literal("§6=== Containers in \"" + active + "\" ==="));
					for (int i = 0; i < wh.containers.size(); i++) {
						ContainerInfo info = wh.containers.get(i);
						BlockPos abs = CoordinateUtils.toAbsolute(info.relativePos, wh.anchor);
						ctx.getSource().sendFeedback(Component.literal("§e" + i + ": " + abs + " type=" + info.type + " mode=" + info.ruleMode));
					}
					return 1;
				}))
			.then(literal("type")
				.then(argument("type", word())
					.executes(ctx -> {
						String typeStr = getString(ctx, "type");
						ContainerType type;
						try {
							type = ContainerType.valueOf(typeStr.toUpperCase());
						} catch (IllegalArgumentException e) {
							ctx.getSource().sendError(Component.literal("§cInvalid type. Use: INPUT, OUTPUT, TEMP, IGNORE"));
							return 0;
						}
						var sel = SelectionController.getInstance();
						if (!sel.hasSelection()) {
							ctx.getSource().sendError(Component.literal("§cNo selection set."));
							return 0;
						}
						var wc = WarehouseController.getInstance();
						String active = wc.getActiveWarehouse();
						if (active == null) {
							ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
							return 0;
						}
						Warehouse wh = wc.getWarehouse(active);
						if (wh == null || wh.containers == null) {
							ctx.getSource().sendError(Component.literal("§cNo containers found."));
							return 0;
						}
						var selection = sel.getSelection();
						int changed = 0;
						for (ContainerInfo info : wh.containers) {
							BlockPos abs = CoordinateUtils.toAbsolute(info.relativePos, wh.anchor);
							if (isInsideSelection(abs, selection)) {
								info.type = type;
								info.ruleMode = ContainerInfo.defaultMode(type);
								changed++;
							}
						}
						if (changed > 0) {
							new WarehouseStorage().saveWarehouse(wh);
						}
						ctx.getSource().sendFeedback(Component.literal("§aChanged type of " + changed + " containers to " + type));
						return 1;
					})))
			.then(literal("mode")
				.then(argument("mode", word())
					.executes(ctx -> {
						String modeStr = getString(ctx, "mode");
						ContainerInfo.RuleMode mode;
						try {
							mode = ContainerInfo.RuleMode.valueOf(modeStr.toUpperCase());
						} catch (IllegalArgumentException e) {
							ctx.getSource().sendError(Component.literal("§cInvalid mode. Use: WHITELIST, BLACKLIST"));
							return 0;
						}
						var sel = SelectionController.getInstance();
						if (!sel.hasSelection()) {
							ctx.getSource().sendError(Component.literal("§cNo selection set."));
							return 0;
						}
						var wc = WarehouseController.getInstance();
						String active = wc.getActiveWarehouse();
						if (active == null) {
							ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
							return 0;
						}
						Warehouse wh = wc.getWarehouse(active);
						if (wh == null || wh.containers == null) {
							ctx.getSource().sendError(Component.literal("§cNo containers found."));
							return 0;
						}
						var selection = sel.getSelection();
						int changed = 0;
						for (ContainerInfo info : wh.containers) {
							BlockPos abs = CoordinateUtils.toAbsolute(info.relativePos, wh.anchor);
							if (isInsideSelection(abs, selection)) {
								info.ruleMode = mode;
								changed++;
							}
						}
						if (changed > 0) {
							new WarehouseStorage().saveWarehouse(wh);
						}
						ctx.getSource().sendFeedback(Component.literal("§aChanged mode of " + changed + " containers to " + mode));
						return 1;
					})))
			.then(literal("info")
				.executes(ctx -> {
					var player = Minecraft.getInstance().player;
					if (player == null) return 0;
					BlockPos lookingAt = getLookingAt(player);
					if (lookingAt == null) {
						ctx.getSource().sendError(Component.literal("§cNot looking at a block."));
						return 0;
					}
					var wc = WarehouseController.getInstance();
					String active = wc.getActiveWarehouse();
					if (active == null) {
						ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
						return 0;
					}
					Warehouse wh = wc.getWarehouse(active);
					if (wh == null) return 0;
					BlockPos rel = CoordinateUtils.toRelative(lookingAt, wh.anchor);
					ContainerInfo info = wc.getContainerInfo(active, rel);
					if (info == null) {
						ctx.getSource().sendError(Component.literal("§cNo container info at this position."));
						return 0;
					}
					ctx.getSource().sendFeedback(Component.literal("§6Container at " + lookingAt));
					ctx.getSource().sendFeedback(Component.literal("§eType: " + info.type));
					ctx.getSource().sendFeedback(Component.literal("§eMode: " + info.ruleMode));
					ctx.getSource().sendFeedback(Component.literal("§eRules: " + (info.rulesNames != null ? info.rulesNames : "none")));
					return 1;
				}))
			.then(literal("memory")
				.then(literal("show")
					.executes(ctx -> {
						var player = Minecraft.getInstance().player;
						if (player == null) return 0;
						BlockPos lookingAt = getLookingAt(player);
						if (lookingAt == null) {
							ctx.getSource().sendError(Component.literal("§cNot looking at a block."));
							return 0;
						}
						var mem = ContainerController.getInstance().getMemory(lookingAt);
						if (mem == null) {
							ctx.getSource().sendFeedback(Component.literal("§eNo memory for this container."));
						} else {
							ctx.getSource().sendFeedback(Component.literal("§6Memory snapshot for " + lookingAt + ":"));
							mem.slots.forEach((slot, stack) -> {
								if (!stack.isEmpty()) {
									ctx.getSource().sendFeedback(Component.literal("§eSlot " + slot + ": " + stack.getHoverName().getString() + " x" + stack.getCount()));
								}
							});
						}
						return 1;
					}))
				.then(literal("clear")
					.executes(ctx -> {
						ContainerController.getInstance().clearMemory();
						ctx.getSource().sendFeedback(Component.literal("§aContainer memory cleared."));
						return 1;
					})))
			.then(literal("rules")
				.then(literal("add")
					.then(argument("name", word())
						.executes(ctx -> {
							String ruleName = getString(ctx, "name");
							var player = Minecraft.getInstance().player;
							if (player == null) return 0;

							BlockPos lookingAt = getLookingAt(player);
							if (lookingAt == null) {
								ctx.getSource().sendError(Component.literal("§cNot looking at a block."));
								return 0;
							}

							var wc = WarehouseController.getInstance();
							String active = wc.getActiveWarehouse();
							if (active == null) {
								ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
								return 0;
							}

							var rc = RuleController.getInstance();
							if (rc.getRule(active, ruleName) == null) {
								ctx.getSource().sendError(Component.literal("§cRule not found: " + ruleName));
								return 0;
							}

							Warehouse wh = wc.getWarehouse(active);
							if (wh == null) return 0;

							BlockPos rel = CoordinateUtils.toRelative(lookingAt, wh.anchor);
							ContainerInfo info = wc.getContainerInfo(active, rel);
							if (info == null) {
								ctx.getSource().sendError(Component.literal("§cNo container at this position."));
								return 0;
							}

							if (info.rulesNames == null) {
								info.rulesNames = new ArrayList<>();
							}

							if (info.rulesNames.contains(ruleName)) {
								ctx.getSource().sendError(Component.literal("§cRule already bound to this container."));
								return 0;
							}

							info.rulesNames.add(ruleName);
							new WarehouseStorage().saveWarehouse(wh);
							ctx.getSource().sendFeedback(Component.literal("§aBound rule \"" + ruleName + "\" to container at " + lookingAt));
							return 1;
						})))
				.then(literal("remove")
					.then(argument("name", word())
						.executes(ctx -> {
							String ruleName = getString(ctx, "name");
							var player = Minecraft.getInstance().player;
							if (player == null) return 0;

							BlockPos lookingAt = getLookingAt(player);
							if (lookingAt == null) {
								ctx.getSource().sendError(Component.literal("§cNot looking at a block."));
								return 0;
							}

							var wc = WarehouseController.getInstance();
							String active = wc.getActiveWarehouse();
							if (active == null) {
								ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
								return 0;
							}

							Warehouse wh = wc.getWarehouse(active);
							if (wh == null) return 0;

							BlockPos rel = CoordinateUtils.toRelative(lookingAt, wh.anchor);
							ContainerInfo info = wc.getContainerInfo(active, rel);
							if (info == null || info.rulesNames == null) {
								ctx.getSource().sendError(Component.literal("§cNo rules bound to this container."));
								return 0;
							}

							if (!info.rulesNames.remove(ruleName)) {
								ctx.getSource().sendError(Component.literal("§cRule \"" + ruleName + "\" not bound to this container."));
								return 0;
							}

							new WarehouseStorage().saveWarehouse(wh);
							ctx.getSource().sendFeedback(Component.literal("§aRemoved rule \"" + ruleName + "\" from container at " + lookingAt));
							return 1;
						}))));
	}

	private static boolean isInsideSelection(BlockPos pos, SelectionController.Selection sel) {
		return pos.getX() >= sel.min().getX() && pos.getX() <= sel.max().getX()
			&& pos.getY() >= sel.min().getY() && pos.getY() <= sel.max().getY()
			&& pos.getZ() >= sel.min().getZ() && pos.getZ() <= sel.max().getZ();
	}

	private static BlockPos getLookingAt(net.minecraft.client.player.LocalPlayer player) {
		var hit = player.pick(5.0, 1.0f, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			return ((BlockHitResult) hit).getBlockPos();
		}
		return null;
	}
}

package bid.yuanlu.mcwarehouse.command.sub;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mcwarehouse.controller.WarehouseController;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class WarehouseSubCommand {

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("warehouse")
			.then(literal("create")
				.then(argument("name", word())
					.executes(ctx -> {
						String name = getString(ctx, "name");
						var player = Minecraft.getInstance().player;
						if (player == null) return 0;
						var wc = WarehouseController.getInstance();
						if (wc.createWarehouse(name, player.blockPosition())) {
							wc.activateWarehouse(name);
							ctx.getSource().sendFeedback(Component.literal("§aCreated and activated warehouse: " + name));
						} else {
							ctx.getSource().sendError(Component.literal("§cFailed to create warehouse (may already exist)."));
						}
						return 1;
					})))
			.then(literal("delete")
				.then(argument("name", word())
					.executes(ctx -> {
						String name = getString(ctx, "name");
						var wc = WarehouseController.getInstance();
						if (wc.deleteWarehouse(name)) {
							ctx.getSource().sendFeedback(Component.literal("§aDeleted warehouse: " + name));
						} else {
							ctx.getSource().sendError(Component.literal("§cWarehouse not found: " + name));
						}
						return 1;
					})))
			.then(literal("list")
				.executes(ctx -> {
					var wc = WarehouseController.getInstance();
					var list = wc.listWarehouses();
					if (list.isEmpty()) {
						ctx.getSource().sendFeedback(Component.literal("§eNo warehouses."));
					} else {
						ctx.getSource().sendFeedback(Component.literal("§6=== Warehouses ==="));
						String active = wc.getActiveWarehouse();
						for (String name : list) {
							boolean isActive = name.equals(active);
							ctx.getSource().sendFeedback(Component.literal((isActive ? "§a> " : "§7  ") + name));
						}
					}
					return 1;
				}))
			.then(literal("activate")
				.then(argument("name", word())
					.executes(ctx -> {
						String name = getString(ctx, "name");
						var wc = WarehouseController.getInstance();
						if (wc.activateWarehouse(name)) {
							ctx.getSource().sendFeedback(Component.literal("§aActivated warehouse: " + name));
						} else {
							ctx.getSource().sendError(Component.literal("§cWarehouse not found: " + name));
						}
						return 1;
					})))
			.then(literal("deactivate")
				.executes(ctx -> {
					WarehouseController.getInstance().deactivateWarehouse();
					ctx.getSource().sendFeedback(Component.literal("§aDeactivated warehouse."));
					return 1;
				}))
			.then(literal("show")
				.executes(ctx -> {
					var wc = WarehouseController.getInstance();
					String active = wc.getActiveWarehouse();
					if (active == null) {
						ctx.getSource().sendError(Component.literal("§cNo active warehouse."));
						return 0;
					}
					wc.showWarehouse(active);
					ctx.getSource().sendFeedback(Component.literal("§aShowing warehouse: " + active));
					return 1;
				}))
			.then(literal("hide")
				.executes(ctx -> {
					WarehouseController.getInstance().hideWarehouse();
					ctx.getSource().sendFeedback(Component.literal("§aHidden warehouse highlights."));
					return 1;
				}));
	}
}

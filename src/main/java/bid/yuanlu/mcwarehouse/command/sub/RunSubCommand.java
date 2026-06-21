package bid.yuanlu.mcwarehouse.command.sub;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mcwarehouse.controller.PathfindingController;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class RunSubCommand {

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("run")
			.executes(ctx -> {
				var controller = PathfindingController.getInstance();
				if (controller.isRunning()) {
					ctx.getSource().sendError(Component.literal("§cAlready running."));
					return 0;
				}
				if (controller.startRun("simple_walk")) {
					ctx.getSource().sendFeedback(Component.literal("§aStarted warehouse sorting."));
				} else {
					ctx.getSource().sendError(Component.literal("§cFailed to start. Ensure an active warehouse with containers exists."));
				}
				return 1;
			})
			.then(literal("--pathfinder")
				.then(argument("type", word())
					.executes(ctx -> {
						String type = getString(ctx, "type");
						var controller = PathfindingController.getInstance();
						if (controller.isRunning()) {
							ctx.getSource().sendError(Component.literal("§cAlready running."));
							return 0;
						}
						if (controller.startRun(type)) {
							ctx.getSource().sendFeedback(Component.literal("§aStarted with pathfinder: " + type));
						} else {
							ctx.getSource().sendError(Component.literal("§cFailed to start with pathfinder: " + type));
						}
						return 1;
					})));
	}
}

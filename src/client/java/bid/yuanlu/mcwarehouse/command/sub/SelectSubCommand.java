package bid.yuanlu.mcwarehouse.command.sub;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mcwarehouse.controller.SelectionController;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.minecraft.commands.arguments.StringArgument.word;
import static net.minecraft.commands.arguments.StringArgument.getString;
import static net.minecraft.commands.arguments.IntegerArgument.integer;
import static net.minecraft.commands.arguments.IntegerArgument.getInteger;

public class SelectSubCommand {

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("select")
			.then(literal("pos1")
				.executes(ctx -> {
					var player = Minecraft.getInstance().player;
					if (player == null) return 0;
					SelectionController.getInstance().setPos1(player.blockPosition());
					ctx.getSource().sendFeedback(Component.literal("§aPos1 set to " + player.blockPosition()));
					return 1;
				}))
			.then(literal("pos2")
				.executes(ctx -> {
					var player = Minecraft.getInstance().player;
					if (player == null) return 0;
					SelectionController.getInstance().setPos2(player.blockPosition());
					ctx.getSource().sendFeedback(Component.literal("§aPos2 set to " + player.blockPosition()));
					return 1;
				}))
			.then(literal("expand")
				.then(argument("direction", word())
					.then(argument("amount", integer(1))
						.executes(ctx -> {
							String dir = getString(ctx, "direction");
							int amount = getInteger(ctx, "amount");
							Direction direction = Direction.byName(dir);
							if (direction == null) {
								ctx.getSource().sendError(Component.literal("§cInvalid direction. Use: north, south, east, west, up, down"));
								return 0;
							}
							SelectionController.getInstance().expand(direction, amount);
							ctx.getSource().sendFeedback(Component.literal("§aSelection expanded " + dir + " by " + amount));
							return 1;
						}))))
			.then(literal("show")
				.executes(ctx -> {
					var sel = SelectionController.getInstance();
					if (!sel.hasSelection()) {
						ctx.getSource().sendError(Component.literal("§cNo selection set."));
						return 0;
					}
					var selection = sel.getSelection();
					ctx.getSource().sendFeedback(Component.literal("§aSelection: " + selection.min() + " to " + selection.max()));
					return 1;
				}));
	}
}

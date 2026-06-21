package bid.yuanlu.mcwarehouse.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mcwarehouse.command.sub.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

public class WarehouseCommand {

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var cmd = literal("warehouse");
			cmd.then(SelectSubCommand.build());
			cmd.then(ContainerSubCommand.build());
			cmd.then(RuleSubCommand.build());
			cmd.then(WarehouseSubCommand.build());
			cmd.then(RunSubCommand.build());
			cmd.then(ConfigSubCommand.build());
			cmd.then(help());
			dispatcher.register(cmd);
			var whCmd = literal("wh");
			whCmd.redirect(dispatcher.getRoot().getChild("warehouse"));
			dispatcher.register(whCmd);
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> help() {
		return literal("help")
			.executes(ctx -> {
				var src = ctx.getSource();
				src.sendFeedback(Component.literal("§6=== MC Warehouse Commands ==="));
				src.sendFeedback(Component.literal("§e/warehouse select pos1/pos2/expand/show"));
				src.sendFeedback(Component.literal("§e/warehouse container add/remove/list/type/mode/info/memory"));
				src.sendFeedback(Component.literal("§e/warehouse rule list/create/delete/add/remove/edit/show"));
				src.sendFeedback(Component.literal("§e/warehouse warehouse create/delete/list/activate/deactivate/show/hide"));
				src.sendFeedback(Component.literal("§e/warehouse run [--pathfinder <type>]"));
				src.sendFeedback(Component.literal("§e/warehouse config show/set/reload"));
				return 1;
			});
	}
}

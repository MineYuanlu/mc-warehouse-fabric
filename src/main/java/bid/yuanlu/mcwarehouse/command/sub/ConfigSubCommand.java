package bid.yuanlu.mcwarehouse.command.sub;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mcwarehouse.storage.WorldConfigStorage;
import bid.yuanlu.mcwarehouse.model.config.WorldConfig;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class ConfigSubCommand {

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("config")
			.then(literal("show")
				.executes(ctx -> {
					WorldConfig config = new WorldConfigStorage().load();
					String json = new GsonBuilder().setPrettyPrinting().create().toJson(config);
					ctx.getSource().sendFeedback(Component.literal("§6=== Config ==="));
					ctx.getSource().sendFeedback(Component.literal(json));
					return 1;
				}))
			.then(literal("set")
				.then(argument("key", word())
					.then(argument("value", word())
						.executes(ctx -> {
							String key = getString(ctx, "key");
							String value = getString(ctx, "value");
							WorldConfigStorage storage = new WorldConfigStorage();
							WorldConfig config = storage.load();
							switch (key) {
								case "interaction.speed" -> {
									try {
										int speed = Integer.parseInt(value);
										if (config.sp != null) {
											config.sp.values().forEach(e -> e.interaction.speed = speed);
										}
										if (config.mp != null) {
											config.mp.values().forEach(e -> e.interaction.speed = speed);
										}
										storage.save(config);
										ctx.getSource().sendFeedback(Component.literal("§aSet " + key + " = " + speed));
									} catch (NumberFormatException e) {
										ctx.getSource().sendError(Component.literal("§cInvalid number: " + value));
										return 0;
									}
								}
								default -> ctx.getSource().sendError(Component.literal("§cUnknown config key: " + key));
							}
							return 1;
						}))))
			.then(literal("reload")
				.executes(ctx -> {
					new WorldConfigStorage().load();
					ctx.getSource().sendFeedback(Component.literal("§aConfig reloaded from disk."));
					return 1;
				}));
	}
}

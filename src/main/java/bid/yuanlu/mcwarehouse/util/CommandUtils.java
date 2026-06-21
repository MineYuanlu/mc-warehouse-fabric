package bid.yuanlu.mcwarehouse.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class CommandUtils {

	public static void sendSuccess(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal(message), false);
	}

	public static void sendError(CommandSourceStack source, String message) {
		source.sendFailure(Component.literal(message));
	}

	public static void sendFeedback(CommandSourceStack source, String message) {
		source.sendSystemMessage(Component.literal(message));
	}

	private CommandUtils() {
	}
}

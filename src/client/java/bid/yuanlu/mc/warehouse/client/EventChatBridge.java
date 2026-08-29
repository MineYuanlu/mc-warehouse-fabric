package bid.yuanlu.mc.warehouse.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.transport.RunReport;
import bid.yuanlu.mc.warehouse.core.WarehouseServices;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.event.WarehouseEvents;

/**
 * 命令层的事件聊天栏桥（PDD §5.9）：ERROR 与 RUN_FINISHED 恒输出；
 * TRANSPORT_STATE / PROGRESS 仅 debug 配置时输出。全部在客户端主线程触发。
 */
final class EventChatBridge {

	private static boolean attached = false;

	private EventChatBridge() {
	}

	static synchronized void attach() {
		if (attached) return;
		attached = true;
		WarehouseEvents.ERROR.register((pos, key) -> {
			if (key == null) return;
			// 位置恒传：key 带 %s 而 pos 为 null 时，避免聊天栏残留字面 "%s"
			say(Component.translatable(key, pos == null ? Component.empty() : pos)
					.withStyle(net.minecraft.ChatFormatting.RED));
		});
		WarehouseEvents.RUN_FINISHED.register((RunReport report) -> {
			say(Component.translatable("commands.wh.report.header",
					Component.translatable("wh.grade." + report.grade().name()), report.rounds(),
					report.itemsMoved(),
					String.format("%.1fs", report.durationMs() / 1000.0),
					componentOfKey(report.detailKey())));
		});
		WarehouseEvents.TRANSPORT_STATE.register((state, detailKey) -> {
			ModConfig cfg = WarehouseServices.modConfig();
			if (cfg != null && cfg.debug && state != null) {
				say(Component.translatable("wh.state." + state.name())
						.withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		});
	}

	private static Component componentOfKey(@Nullable String key) {
		return key == null ? Component.empty()
				: Component.translatable(key);
	}

	private static void say(Component c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(c);
		}
	}
}

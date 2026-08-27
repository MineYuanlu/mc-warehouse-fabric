package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;

/**
 * 协议层入口（PDD §6/§8）：管理当前唯一活跃会话、注册 Mixin 钩子、引擎 tick 驱动。
 * <p>
 * 同一时刻只允许一个会话——新开屏请求会先关闭旧会话（防内容混淆，MVP 教训）。
 */
public final class ContainerProtocol {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/protocol");
	private static final ContainerProtocol INSTANCE = new ContainerProtocol();
	private static final AtomicLong CAPTURE_COUNTER = new AtomicLong();

	public static ContainerProtocol get() {
		return INSTANCE;
	}

	@Nullable
	private ContainerSession active;
	private boolean hooksRegistered;
	/** 最近一次开屏包捕获的 containerId（§6.1 步骤 4：会话经此做 syncId 门控） */
	private volatile int lastCapturedContainerId = -1;
	/** 关屏扫描回写（§5.4：玩家手动关闭也刷新缓存）；每次 open 覆盖 */
	@Nullable
	private volatile java.util.function.Consumer<bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot> closeScanSink;

	long captureCounter() {
		return CAPTURE_COUNTER.get();
	}

	int lastCapturedContainerId() {
		return lastCapturedContainerId;
	}

	public synchronized void ensureHooks() {
		if (hooksRegistered) return;
		OpenScreenCapture.register(id -> {
			lastCapturedContainerId = id;
			CAPTURE_COUNTER.incrementAndGet();
		});
		ScreenHooks.registerOnClose(screen -> {
			ContainerSession s = active;
			if (s != null && s.isBoundScreen(screen)) {
				if (!s.closingByUs) {
					LOGGER.debug("screen closed externally: {}", s.pos);
					s.failExternal();
				}
				// §5.4/§8.7：真实扫描回写缓存（对玩家手动关闭同样生效）
				var sink = closeScanSink;
				if (sink != null) {
					try {
						bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot snap = s.scanNow();
						if (snap != null) sink.accept(snap);
					} catch (Exception e) {
						LOGGER.warn("close-scan failed {}: {}", s.pos, e.toString());
					}
				}
				s.handle.bind(null);
			}
		});
		hooksRegistered = true;
	}

	/**
	 * 发起开箱（异步）。已有活跃会话时先取消之。
	 *
	 * @param onCloseScanSink 关屏（含玩家手动关闭）时对当前界面真实扫描的回写目标；
	 *                        null = 不回写
	 */
	public synchronized void open(WorldDimPos pos, ContainerDetector detector, ContainerInteraction interaction,
			ModConfig config, ContainerSession.Listener listener,
			@Nullable java.util.function.Consumer<bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot> onCloseScanSink) {
		ensureHooks();
		cancel("superseded");
		closeScanSink = onCloseScanSink;
		active = new ContainerSession(pos, detector, interaction, config, listener,
				System::currentTimeMillis);
	}

	/** 当前活跃会话；无则 null */
	@Nullable
	public ContainerSession active() {
		return active;
	}

	/** 引擎每 tick 调用 */
	public void tick() {
		ContainerSession s = active;
		if (s == null) return;
		switch (s.phase()) {
			case FAILED, DONE -> active = null;
			default -> s.tick();
		}
	}

	public synchronized void cancel(String reason) {
		ContainerSession s = active;
		if (s == null) return;
		LOGGER.debug("cancel session: {} ({})", s.pos, reason);
		s.close();
		active = null;
	}
}

package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.container.ContainerOpenContext;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerHandle;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;

/**
 * 容器会话（协议层，PDD §6）：
 * <ul>
 *   <li>开屏流程 CLOSE → PRECHECK → FACE → OPEN → WAIT_SCREEN（syncId 门控）→ VERIFY → READY</li>
 *   <li>执行流程：步骤队列逐击推进，每击对账（tick 轮询 stateId + 受监视槽位变化，D3）
 *       后才发下一步；settle 重扫后回调</li>
 *   <li>一切回调在客户端主线程；全程 tick 驱动单线程，无 sleep</li>
 * </ul>
 */
public final class ContainerSession {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/session");

	public enum Phase {
		CLOSING_RESIDUE, PRECHECK, FACING, OPENING, WAIT_SCREEN, VERIFYING,
		READY, EXECUTING, SETTLING, DONE, FAILED
	}

	public enum Failure {
		CONTAINER_GONE, REACH_FAILED, NOT_OPENED, UI_MISMATCH,
		UI_CLOSED_EXTERNAL, CLICK_TIMEOUT, CLICK_CORRECTED, PLAYER_GONE
	}

	/** 单个待执行原语：action 仅「发起」，watchSlot 为受监视的 Menu 槽位 */
	public record Step(String label, Runnable action, int watchSlot) {
	}

	public interface Listener {
		default void onReady(ContainerSession session) {
		}

		default void onStepsCompleted(ContainerSession session) {
		}

		default void onFailed(ContainerSession session, Failure failure) {
		}
	}

	final WorldDimPos pos;
	final ContainerDetector detector;
	final ContainerInteraction interaction;
	private final ModConfig config;
	private final Listener listener;
	private final LongSupplier clock;

	final ContainerHandle handle;
	@Nullable
	private ContainerOpenContext ctx;

	private Phase phase = Phase.CLOSING_RESIDUE;
	@Nullable
	private Failure failure;
	private int phaseTicks;
	private int speedGap;
	// WAIT_SCREEN 用：捕获基线（协议层捕获计数器快照）
	private long captureBaseline = -1;

	// 逐击对账状态（D3：不依赖客户端 stateId——26.1 上它不随服务端包更新；
	// 以「受监视槽位/光标内容变化 + 稳定窗口无回滚」为确认信号）
	private static final int ACK_STABILITY_TICKS = 2;

	private boolean ackPending;
	private final Map<Integer, int[]> ackWatchBefore = new HashMap<>(); // slot -> [count, itemHash]
	private int ackCarriedBefore;
	private int ackWaited;
	private boolean ackObservedChange;
	private int ackStableCount;
	@Nullable
	private Step currentStep;
	private final Deque<Step> steps = new ArrayDeque<>();

	ContainerSession(WorldDimPos pos, ContainerDetector detector, ContainerInteraction interaction,
			ModConfig config, Listener listener, LongSupplier clock) {
		this.pos = pos;
		this.detector = detector;
		this.interaction = interaction;
		this.config = config;
		this.listener = listener;
		this.clock = clock;
		this.handle = new ContainerHandle(pos);
	}

	// ---- 状态查询 ----

	public Phase phase() {
		return phase;
	}

	@Nullable
	public Failure failure() {
		return failure;
	}

	public ContainerHandle handle() {
		return handle;
	}

	public boolean isBoundScreen(AbstractContainerScreen<?> screen) {
		return handle.screen() == screen;
	}

	boolean closingByUs;

	// ---- 驱动 ----

	void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || Minecraft.getInstance().level == null) {
			fail(Failure.PLAYER_GONE);
			return;
		}
		// 绑定中的界面被外部关闭（按 E / 死亡 / 踢出）——绝不视为成功
		if (handle.isOpen()) {
			if (Minecraft.getInstance().screen != handle.screen()) {
				fail(Failure.UI_CLOSED_EXTERNAL);
				return;
			}
		} else if (phase == Phase.READY || phase == Phase.EXECUTING || phase == Phase.SETTLING) {
			fail(Failure.UI_CLOSED_EXTERNAL);
			return;
		}

		switch (phase) {
			case CLOSING_RESIDUE -> tickClosingResidue();
			case PRECHECK -> precheck(player);
			case FACING -> tickFacing(player);
			case OPENING -> open();
			case WAIT_SCREEN -> pollScreen();
			case VERIFYING -> verify();
			case EXECUTING -> stepLoop();
			case SETTLING -> settle();
			default -> {
			}
		}
	}

	private void tickClosingResidue() {
		Minecraft mc = Minecraft.getInstance();
		if (++phaseTicks >= 4 || mc.screen == null) {
			phase = Phase.PRECHECK;
			phaseTicks = 0;
			return;
		}
		if (phaseTicks == 1 && mc.screen instanceof AbstractContainerScreen<?> s && mc.player != null) {
			closingByUs = true;
			mc.player.closeContainer(); // 干净起点（§6.1 步骤 0）
		}
	}

	private void precheck(LocalPlayer player) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			fail(Failure.CONTAINER_GONE);
			return;
		}
		BlockPos blockPos = pos.toBlockPos();
		BlockInWorld biw = new BlockInWorld(level, blockPos, true);
		if (!detector.matchesBlock(biw)) {
			fail(Failure.CONTAINER_GONE);
			return;
		}
		double dist = player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(blockPos));
		if (dist > config.reachLimit) {
			fail(Failure.REACH_FAILED);
			return;
		}
		ctx = new ContainerOpenContext(pos, biw);
		phase = Phase.FACING;
		phaseTicks = 0;
	}

	private void tickFacing(LocalPlayer player) {
		if (phaseTicks++ == 0) {
			Vec3Like.face(player, pos.toBlockPos());
			return;
		}
		phase = Phase.OPENING;
		phaseTicks = 0;
		captureBaseline = ContainerProtocol.get().captureCounter();
	}

	private void open() {
		interaction.requestOpen(handle);
		phase = Phase.WAIT_SCREEN;
		phaseTicks = 0;
	}

	private void pollScreen() {
		Minecraft mc = Minecraft.getInstance();
		long captured = ContainerProtocol.get().captureCounter();
		if (captured == captureBaseline) {
			// 尚无新开屏包
			if (++phaseTicks > config.timeouts().openTicks) fail(Failure.NOT_OPENED);
			return;
		}
		if (mc.screen instanceof AbstractContainerScreen<?> screen) {
			int id = screen.getMenu().containerId;
			handle.bind(screen);
			phase = Phase.VERIFYING;
			phaseTicks = 0;
			LOGGER.debug("session open confirmed: containerId={} at {}", id, pos);
		} else if (++phaseTicks > config.timeouts().openTicks) {
			fail(Failure.NOT_OPENED);
		}
	}

	private void verify() {
		AbstractContainerScreen<?> screen = handle.screen();
		if (screen == null) {
			fail(Failure.NOT_OPENED);
			return;
		}
		boolean contentReady = screen.getMenu().getStateId() != 0 || phaseTicks >= 2;
		if (!contentReady) {
			phaseTicks++;
			if (phaseTicks > config.timeouts().openTicks) fail(Failure.NOT_OPENED);
			return;
		}
		if (!detector.matches(screen, ctx)) {
			fail(Failure.UI_MISMATCH);
			return;
		}
		phase = Phase.READY;
		listener.onReady(this);
	}

	// ---- 执行 ----

	/** READY 状态下入队步骤并开始执行 */
	public void execute(java.util.List<Step> toExecute) {
		if (phase != Phase.READY) throw new IllegalStateException("not READY: " + phase);
		steps.clear();
		steps.addAll(toExecute);
		speedGap = 0;
		phase = Phase.EXECUTING;
	}

	private void stepLoop() {
		if (!ackPending) {
			if (steps.isEmpty()) {
				phase = Phase.SETTLING;
				phaseTicks = 0;
				return;
			}
			if (speedGap > 0) {
				speedGap--;
				return;
			}
			issueNext();
			return;
		}
		evaluateAck();
	}

	private void issueNext() {
		currentStep = steps.poll();
		if (currentStep == null) return;
		AbstractContainerMenu menu = menuOrNull();
		if (menu == null) {
			fail(Failure.UI_CLOSED_EXTERNAL);
			return;
		}
		ackWatchBefore.clear();
		snapshotWatch(menu, currentStep.watchSlot());
		ItemStack carried = menu.getCarried();
		ackCarriedBefore = carried.isEmpty() ? -1 : System.identityHashCode(carried.getItem()) * 31 + carried.getCount();
		ackPending = true;
		ackWaited = 0;
		ackObservedChange = false;
		ackStableCount = 0;
		LOGGER.debug("step: {}", currentStep.label());
		currentStep.action().run();
	}

	private void evaluateAck() {
		AbstractContainerMenu menu = menuOrNull();
		if (menu == null) {
			fail(Failure.UI_CLOSED_EXTERNAL);
			return;
		}
		Step st = currentStep;
		if (st == null) {
			fail(Failure.CLICK_TIMEOUT);
			return;
		}
		int slot = st.watchSlot();
		ItemStack watch = slot >= 0 && slot < menu.slots.size() ? menu.slots.get(slot).getItem() : ItemStack.EMPTY;
		int[] nowWatch = fingerprint(watch);
		ItemStack carriedItem = menu.getCarried();
		int carriedNow = carriedItem.isEmpty() ? -1 : System.identityHashCode(carriedItem.getItem()) * 31 + carriedItem.getCount();

		int[] beforeWatch = ackWatchBefore.getOrDefault(slot, new int[]{-1, 0});
		boolean watchBackToBefore = java.util.Arrays.equals(nowWatch, beforeWatch);
		boolean carriedBackToBefore = carriedNow == ackCarriedBefore;

		if (Boolean.getBoolean("warehouse.debugAck")) {
			LOGGER.info("[ackDebug] waited={} obs={} stable={} watch={} carried={}",
					ackWaited, ackObservedChange, ackStableCount,
					watch.getCount() + "@" + System.identityHashCode(watch.getItem()),
					carriedItem.isEmpty() ? "empty" : carriedItem.getCount());
		}

		if (!ackObservedChange) {
			boolean watchDiff = !watchBackToBefore;
			boolean carriedDiff = !carriedBackToBefore;
			if (watchDiff || carriedDiff) {
				ackObservedChange = true;
				ackStableCount = 1;
			} else if (++ackWaited > config.timeouts().confirmTicks) {
				fail(Failure.CLICK_TIMEOUT);
			}
			return;
		}

		// 变化后若整体回到点击前状态 = 预测被服务端回滚（§6.2-b）
		if (watchBackToBefore && carriedBackToBefore) {
			fail(Failure.CLICK_CORRECTED);
			return;
		}
		if (++ackStableCount >= ACK_STABILITY_TICKS) {
			// 确认：变化已稳定（未被纠正）
			ackPending = false;
			currentStep = null;
			speedGap = Math.max(0, config.defaultInteractionSpeed);
			return;
		}
		if (++ackWaited > config.timeouts().confirmTicks) {
			fail(Failure.CLICK_TIMEOUT);
		}
	}

	private void snapshotWatch(AbstractContainerMenu menu, int watchSlot) {
		if (watchSlot >= 0 && watchSlot < menu.slots.size()) {
			ItemStack s = menu.slots.get(watchSlot).getItem();
			ackWatchBefore.put(watchSlot, fingerprint(s));
		}
	}

	private static int[] fingerprint(ItemStack stack) {
		return stack.isEmpty()
				? new int[]{-1, 0}
				: new int[]{stack.getCount(), System.identityHashCode(stack.getItem())};
	}

	private void settle() {
		if (++phaseTicks <= config.timeouts().settleTicks) return;
		phase = Phase.READY;
		listener.onStepsCompleted(this);
	}

	// ---- 快照与关闭 ----

	/** 当前界面的容器侧快照（detector.scan）；未绑定为 null。调用方负责写入内存缓存。 */
	@Nullable
	public bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot scanNow() {
		AbstractContainerScreen<?> screen = handle.screen();
		if (screen == null) return null;
		return detector.scan(screen);
	}

	/** 会话主动关闭（引擎发起，不算外部关闭） */
	public void close() {
		if (handle.screen() != null) {
			closingByUs = true;
			interaction.requestClose(handle);
		}
		handle.bind(null);
		phase = Phase.DONE;
	}

	void failExternal() {
		fail(Failure.UI_CLOSED_EXTERNAL);
	}

	private void fail(Failure f) {
		if (phase == Phase.FAILED || phase == Phase.DONE) return;
		failure = f;
		phase = Phase.FAILED;
		LOGGER.debug("session failed: {} ({})", pos, f);
		listener.onFailed(this, f);
	}

	@Nullable
	private AbstractContainerMenu menuOrNull() {
		AbstractContainerScreen<?> screen = handle.screen();
		if (screen == null || screen != Minecraft.getInstance().screen) return null;
		return screen.getMenu();
	}

	// Vec3 面向工具（独立小类避免 api 泄漏）
	private static final class Vec3Like {
		static void face(LocalPlayer player, BlockPos target) {
			double dx = target.getX() + 0.5 - player.getX();
			double dy = target.getY() + 0.5 - (player.getEyeY());
			double dz = target.getZ() + 0.5 - player.getZ();
			double horiz = Math.sqrt(dx * dx + dz * dz);
			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
			player.setYRot(yaw);
			player.setXRot(pitch);
			player.setYHeadRot(yaw);
		}
	}
}

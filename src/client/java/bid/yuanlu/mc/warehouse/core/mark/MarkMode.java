package bid.yuanlu.mc.warehouse.core.mark;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.engine.container.OpenScreenCapture;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.impl.container.BlockEntityDetector;

/**
 * 标记模式（PDD §5.7 v0.3）：右键注册/移除容器，自动采集内容。
 * <p>
 * 纯轮询感知右键（无输入拦截 Mixin，§5.7）：每 tick 记录准星指向的容器方块，
 * 开屏包事件到达后于下一帧绑定「最后指向的容器」，真实扫描当前 UI 后
 * 自动关屏并按类型入库；重复标记已注册容器 = 从仓库移除。
 * <p>
 * 快照只来自真实打开的 Screen 扫描（红线：操作依据只能来自对账后的快照）；
 * 缓存不在此伪造——引擎首次访问会真实开箱扫描建缓存。
 */
public final class MarkMode {

	private static final MarkMode INSTANCE = new MarkMode();

	public static MarkMode get() {
		return INSTANCE;
	}

	private MarkMode() {
	}

	/** 一次标记会话的参数 */
	public record Session(IOType type, @Nullable String ruleId, @Nullable String templateId) {
	}

	/** 待采集目标：最后指向的方块（绝对坐标，含 world/dim） */
	private record Target(WorldDimPos abs) {
	}

	private static final java.util.function.IntConsumer OPEN_LISTENER =
			containerId -> get().onScreenOpened();

	@Nullable
	private volatile Session session;

	@Nullable
	private volatile Target lastLookedAt;

	@Nullable
	private volatile Target pendingCapture;

	/**
	 * 切换式进入/退出（PDD §5.7：再次执行退出）。
	 *
	 * @return 新状态；null = 已退出
	 */
	@Nullable
	public Session toggle(IOType type, @Nullable String ruleId, @Nullable String templateId) {
		if (session != null) {
			exit();
			return null;
		}
		Session s = new Session(type, ruleId, templateId);
		session = s;
		lastLookedAt = null;
		pendingCapture = null;
		OpenScreenCapture.register(OPEN_LISTENER);
		return s;
	}

	public void exit() {
		session = null;
		lastLookedAt = null;
		pendingCapture = null;
		OpenScreenCapture.unregister(OPEN_LISTENER);
	}

	public boolean isActive() {
		return session != null;
	}

	/** 每 tick 轮询：记录准星指向 + 动作栏提示 + 待采集处理 */
	public void tick() {
		if (session == null) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;

		// 1. 待采集：等真实 Screen 出现（handleOpenScreen HEAD 时 screen 尚未挂上）
		Target pending = pendingCapture;
		Minecraft mc = Minecraft.getInstance();
		if (pending != null && mc.screen instanceof AbstractContainerScreen<?> screen) {
			pendingCapture = null;
			Session s = session;
			boolean close = true;
			try {
				complete(s, pending, screen);
			} catch (Exception e) {
				close = false; // 异常时保留界面便于用户观察现场
				say(player, "commands.wh.error.generic", ChatFormatting.RED, String.valueOf(e));
			}
			if (close && mc.screen == screen) {
				screen.onClose(); // 自动关屏（§5.7）
			}
			return;
		}

		// 2. 准星追踪 + 提示
		BlockPos pos = lookedContainerPos();
		if (pos == null) {
			lastLookedAt = null;
			player.sendOverlayMessage(Component.translatable("commands.wh.mark.hint")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		WorldDim dim = currentDim();
		if (dim.worldId() == null || dim.dimId() == null) return;
		lastLookedAt = new Target(new WorldDimPos(dim.worldId(), dim.dimId(),
				pos.getX(), pos.getY(), pos.getZ()));
		boolean registered = findContainer(lastLookedAt.abs()) != null;
		player.sendOverlayMessage(Component.translatable(
				registered ? "commands.wh.mark.pointing_registered" : "commands.wh.mark.pointing_new",
				pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY));
	}

	// ---- 内部 ----

	/** 开屏包事件（任何 containerId）：绑定最后指向的容器为待采集目标 */
	private void onScreenOpened() {
		if (session == null) return;
		Target target = lastLookedAt;
		lastLookedAt = null; // 防内容混淆：一次开屏只消费一个指向
		if (target == null) {
			say(Minecraft.getInstance().player, "commands.wh.mark.no_target", ChatFormatting.RED);
			return;
		}
		pendingCapture = target;
	}

	/** 准星指向且属于某 Detector 的容器方块坐标 */
	@Nullable
	private static BlockPos lookedContainerPos() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.hitResult instanceof BlockHitResult blockHit)) return null;
		var level = mc.level;
		if (level == null) return null;
		BlockPos pos = blockHit.getBlockPos();
		for (ContainerDetector d : WarehouseRegistryImpl.detectors()) {
			if (d instanceof BlockEntityDetector bed && bed.matchesBlock(new BlockInWorld(level, pos, false))) {
				return pos;
			}
		}
		return null;
	}

	private static WorldDim currentDim() {
		Minecraft mc = Minecraft.getInstance();
		WorldSessionTracker trk = WorldSessionTracker.get();
		String worldId = trk == null ? null : trk.currentWorldId();
		String dimId = mc.level == null ? null : mc.level.dimension().identifier().toString();
		return new WorldDim(worldId, dimId);
	}

	/** 按 canonical 相对坐标在激活仓库内查找容器 */
	static ContainerInfo findContainer(WorldDimPos abs) {
		WarehouseManagerImpl mgr = WarehouseManagerImpl.get();
		if (mgr == null || mgr.active() == null) return null;
		var wh = mgr.active();
		WorldDim dim = new WorldDim(abs.world(), abs.dim());
		WorldDimPos rel = wh.toRelative(dim, abs.toBlockPos());
		if (rel == null) return null;
		return wh.containerAt(dim, rel.toBlockPos());
	}

	private void complete(Session s, Target target, AbstractContainerScreen<?> screen) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		ContainerInfo existing = findContainer(target.abs());
		if (existing != null) {
			WarehouseManagerImpl mgr = WarehouseManagerImpl.get();
			var wh = mgr.active();
			wh.containers.remove(existing);
			mgr.save(wh);
			say(player, "commands.wh.mark.removed", ChatFormatting.YELLOW,
					fmt(target.abs()));
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ScreenScan scan = mc.level == null ? null
				: scanScreen(screen, new net.minecraft.world.level.block.state.pattern.BlockInWorld(
						mc.level, target.abs().toBlockPos(), false));
		if (scan == null) {
			say(player, "commands.wh.mark.no_detector", ChatFormatting.RED, fmt(target.abs()));
			return;
		}
		WarehouseManagerImpl mgr = WarehouseManagerImpl.get();
		var wh = mgr.active();
		WorldDim tdim = new WorldDim(target.abs().world(), target.abs().dim());
		ContainerInfo info = new ContainerInfo(s.type());
		// §3.2 双箱合并：大箱子的另一半一并入库（canonical = 被点击的半边）
		for (BlockPos part : multiblockParts(target.abs().toBlockPos())) {
			WorldDimPos partRel = wh.toRelative(tdim, part);
			if (partRel == null) {
				say(player, "commands.wh.mark.no_anchor", ChatFormatting.RED);
				return;
			}
			info.pos.add(partRel);
		}
		info.label = scan.snapshot().title();
		String ruleRef = s.ruleId() != null && !s.ruleId().isBlank() ? s.ruleId() : s.templateId();
		if (ruleRef != null && ruleRef.isBlank()) ruleRef = null;
		if (ruleRef != null) {
			if (!wh.rules.containsKey(ruleRef)) {
				say(player, "commands.wh.mark.rule_missing", ChatFormatting.RED, ruleRef);
				return;
			}
			info.rules.add(ruleRef);
		}
		wh.containers.add(info);
		mgr.save(wh);
		seedCache(info, scan.detector(), scan.snapshot()); // §5.7：标记完成即缓存种子
		say(player, "commands.wh.mark.added", ChatFormatting.GREEN,
				s.type(), fmt(target.abs()), scan.snapshot().slotCount());
	}

	/** §3.2 多格坐标：大箱子返回 [被点击半边, 相连半边]，其余单格返回 [自身] */
	private static java.util.List<BlockPos> multiblockParts(BlockPos abs) {
		Minecraft mc = Minecraft.getInstance();
		java.util.List<BlockPos> parts = new java.util.ArrayList<>();
		parts.add(abs);
		if (mc.level != null) {
			var state = mc.level.getBlockState(abs);
			if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
				BlockPos other = net.minecraft.world.level.block.ChestBlock
						.getConnectedBlockPos(abs, state);
				if (other != null && mc.level.getBlockState(other).getBlock()
						instanceof net.minecraft.world.level.block.ChestBlock) {
					parts.add(other);
				}
			}
		}
		return parts;
	}

	/** §5.7/§3.8：真实扫描即种子——按容器 cacheType 写入内存（DISK 同步落盘） */
	private static void seedCache(ContainerInfo info, ContainerDetector detector,
			bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot snapshot) {
		var store = bid.yuanlu.mc.warehouse.core.WarehouseServices.cacheStore();
		if (store == null || info.cacheType == bid.yuanlu.mc.warehouse.api.container.CacheType.NONE) return;
		var key = bid.yuanlu.mc.warehouse.core.cache.CacheKey.of(info.canonicalPos(),
				bid.yuanlu.mc.warehouse.core.cache.DetectorResolver.playerUuidIfScoped(detector));
		store.remember(key, info.cacheType, snapshot);
	}

	record ScreenScan(ContainerDetector detector,
			bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot snapshot) {
	}

	private static final org.slf4j.Logger LOG =
			org.slf4j.LoggerFactory.getLogger("yuanlu-warehouse/mark");

	@Nullable
	private static ScreenScan scanScreen(AbstractContainerScreen<?> screen,
			net.minecraft.world.level.block.state.pattern.BlockInWorld block) {
		LOG.info("[mark] scanning: menu={}, blockEntity={}, title={}",
				screen.getMenu().getType(), block.getEntity(), screen.getTitle().getString());
		for (ContainerDetector d : WarehouseRegistryImpl.detectors()) {
			try {
					if (d.matches(screen, new bid.yuanlu.mc.warehouse.api.container.ContainerOpenContext(
						new WorldDimPos(WorldSessionTracker.get().currentWorldId(),
								Minecraft.getInstance().level.dimension().identifier().toString(),
								block.getPos().getX(), block.getPos().getY(), block.getPos().getZ()),
						block))) {
					bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot snap = d.scan(screen);
					return new ScreenScan(d, snap);
				}
			} catch (Exception e) {
				LOG.warn("[mark] detector {} threw: {}", d.id(), e.toString());
			}
		}
		return null;
	}

	private static void say(@Nullable LocalPlayer player, String key, ChatFormatting color,
			Object... args) {
		if (player != null) {
			player.sendSystemMessage(Component.translatable(key, args).withStyle(color));
		}
	}

	private static String fmt(WorldDimPos p) {
		return p.x() + " " + p.y() + " " + p.z();
	}
}

package bid.yuanlu.mc.warehouse.impl.navigation;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import bid.yuanlu.mc.warehouse.api.navigation.Goal;
import bid.yuanlu.mc.warehouse.api.navigation.Navigator;
import bid.yuanlu.mc.warehouse.api.navigation.PathResult;
import bid.yuanlu.mc.warehouse.api.navigation.PathStatus;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 空寻路器（PDD §7.2 一阶段默认）：玩家自行前往。start 输出「请前往」提示
 * （引擎不消费 PathResult.messageKey，由导航器自发）；tick 检测玩家位置，
 * 同世界同维度且距目标方块中心 < acceptableDistance 才 ARRIVED——引擎的 reach
 * 预检仍是最终守门（REACH_FAILED）。
 * <p>
 * 能力边界 = 当前世界 + 当前维度：目标在别的世界/维度时返回 FAILED——
 * 能否前往各世界/维度是各 Navigator 自身的能力（§7.1），引擎不做整体限制，
 * FAILED 重试耗尽后由引擎跳过该容器。注册 id 为 {@value #ID}。
 */
public final class NoOpNavigator implements Navigator {

	public static final String ID = "noop";

	private @Nullable Goal goal;
	/** 引擎重试会对同一目标重复 start（经 cancel），「无法前往」提示按目标去重只报一次 */
	@Nullable
	private WorldDimPos lastTarget;
	private boolean unreachableReported;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public PathResult start(Goal goal) {
		if (!goal.target().equals(lastTarget)) {
			lastTarget = goal.target();
			unreachableReported = false;
		}
		this.goal = goal;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) {
			var t = goal.target();
			if (reachableHere(t)) {
				p.sendSystemMessage(Component.translatable("wh.nav.noop.goto",
						t.world() != null ? t.world() : "", t.dim(),
						String.format(Locale.ROOT, "%d %d %d", t.x(), t.y(), t.z())));
			} else if (!unreachableReported) {
				unreachableReported = true;
				p.sendSystemMessage(Component.translatable("wh.nav.noop.unreachable",
						t.world() != null ? t.world() : "", t.dim(),
						String.format(Locale.ROOT, "%d %d %d", t.x(), t.y(), t.z())));
			}
		}
		return PathResult.ok();
	}

	@Override
	public PathStatus tick() {
		Goal g = goal;
		LocalPlayer p = Minecraft.getInstance().player;
		if (g == null || p == null) return PathStatus.ARRIVED;
		if (!reachableHere(g.target())) {
			return PathStatus.FAILED; // 其他世界/维度：NoOp 能力外，重试与跳过归引擎协议
		}
		var t = g.target();
		double dx = p.getX() - (t.x() + 0.5);
		double dy = p.getY() - (t.y() + 0.5);
		double dz = p.getZ() - (t.z() + 0.5);
		return dx * dx + dy * dy + dz * dz < g.acceptableDistance() * g.acceptableDistance()
				? PathStatus.ARRIVED
				: PathStatus.MOVING;
	}

	@Override
	public void cancel() {
		goal = null;
	}

	/** 目标是否在当前世界与当前维度（NoOp 的能力边界；目标 world 缺失时只比维度） */
	private static boolean reachableHere(WorldDimPos t) {
		if (t.world() != null) {
			String cur = currentWorldName();
			if (cur == null || !cur.equals(t.world())) return false;
		}
		LocalPlayer p = Minecraft.getInstance().player;
		return p != null && p.level().dimension().identifier().toString().equals(t.dim());
	}

	@Nullable
	private static String currentWorldName() {
		try {
			return WorldSessionTracker.get().currentWorldName();
		} catch (IllegalStateException e) {
			return null;
		}
	}
}

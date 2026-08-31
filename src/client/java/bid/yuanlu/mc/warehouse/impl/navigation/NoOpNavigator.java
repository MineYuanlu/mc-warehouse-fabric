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

/**
 * 空寻路器（PDD §7.2 一阶段默认）：玩家自行前往。start 输出「请前往」提示
 * （引擎不消费 PathResult.messageKey，由导航器自发）；tick 检测玩家位置，
 * 同维度且距目标方块中心 < acceptableDistance 才 ARRIVED——引擎的 reach
 * 预检仍是最终守门（REACH_FAILED）。注册 id 为 {@value #ID}。
 */
public final class NoOpNavigator implements Navigator {

	public static final String ID = "noop";

	private @Nullable Goal goal;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public PathResult start(Goal goal) {
		this.goal = goal;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) {
			var t = goal.target();
			p.sendSystemMessage(Component.translatable("wh.nav.noop.goto",
					t.world() != null ? t.world() : "", t.dim(),
					String.format(Locale.ROOT, "%d %d %d", t.x(), t.y(), t.z())));
		}
		return PathResult.ok();
	}

	@Override
	public PathStatus tick() {
		Goal g = goal;
		LocalPlayer p = Minecraft.getInstance().player;
		if (g == null || p == null) return PathStatus.ARRIVED;
		var t = g.target();
		if (!p.level().dimension().identifier().toString().equals(t.dim())) {
			return PathStatus.MOVING;
		}
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
}

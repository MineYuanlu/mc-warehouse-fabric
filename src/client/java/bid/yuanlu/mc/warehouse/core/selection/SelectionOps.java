package bid.yuanlu.mc.warehouse.core.selection;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;

/**
 * 选区操作核心（UI-PDD D9 延伸）：inBox 判定与 expand 与命令/UI 共用同一实现，
 * 消除双实现漂移（原 WhCommands.SelectGroup.inBox 与 SelectionPanelScreens.inBox 合并于此）。
 */
public final class SelectionOps {

	private SelectionOps() {
	}

	/**
	 * 容器是否在选区内：canonical 相对坐标经 anchor 绝对化后做 AABB 判定
	 * （含 world 缺省补全）；无 box / 会话未就绪 / 无 anchor → false。
	 */
	public static boolean inBox(SelectionState sel, Warehouse wh, ContainerInfo c) {
		if (!sel.hasBox() || c.pos.isEmpty()) {
			return false;
		}
		String serverId;
		try {
			serverId = WorldSessionTracker.get().currentServerId();
		} catch (IllegalStateException e) {
			return false;
		}
		if (serverId == null) {
			return false;
		}
		for (WorldDimPos p : c.pos) {
			if (!p.hasWorld()) {
				continue;
			}
			WorldDim dim = new WorldDim(serverId, p.world(), p.dim());
			BlockPos anchor = wh.anchorOf(dim);
			if (anchor == null) {
				continue;
			}
			BlockPos abs = p.plus(anchor).toBlockPos();
			if (sel.contains(abs.getX(), abs.getY(), abs.getZ())) {
				return true;
			}
		}
		return false;
	}

	/** 选区内容器数（批量操作/信息行共用）。 */
	public static int countInBox(SelectionState sel, Warehouse wh) {
		int n = 0;
		for (ContainerInfo c : wh.containers) {
			if (inBox(sel, wh, c)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * 选区扩展（等价 {@code /wh select expand <count> <dir>}）：角 2 沿 dir 移动
	 * count 格（负数反向收缩）；只有角 1 时先令角 2 = 角 1。无任何角点 → false。
	 */
	public static boolean expand(SelectionState sel, Direction dir, int count) {
		if (sel.pos2() == null) {
			if (sel.pos1() == null) {
				return false;
			}
			sel.set2(sel.pos1());
		}
		WorldDimPos p2 = sel.pos2();
		sel.set2(new WorldDimPos(p2.world(), p2.dim(),
				p2.x() + dir.getStepX() * count,
				p2.y() + dir.getStepY() * count,
				p2.z() + dir.getStepZ() * count));
		return true;
	}

	/** 视线主轴方向（expand 快捷键用）：|分量| 最大的轴，正/负朝向按符号。 */
	public static Direction dominantAxis(double x, double y, double z) {
		double ax = Math.abs(x);
		double ay = Math.abs(y);
		double az = Math.abs(z);
		if (ay >= ax && ay >= az) {
			return y > 0 ? Direction.UP : Direction.DOWN;
		}
		if (ax >= az) {
			return x > 0 ? Direction.EAST : Direction.WEST;
		}
		return z > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** 相对/混合 token 解析失败时的兜底（UI 坐标输入用，见 util/RelativeCoords）。 */
	public static @Nullable WorldDimPos cornerAt(@Nullable WorldDim dim, BlockPos abs) {
		if (dim == null) {
			return null;
		}
		return new WorldDimPos(dim.worldName(), dim.dimId(), abs.getX(), abs.getY(), abs.getZ());
	}
}

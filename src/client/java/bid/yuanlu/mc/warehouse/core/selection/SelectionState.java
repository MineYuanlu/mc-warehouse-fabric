package bid.yuanlu.mc.warehouse.core.selection;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;

/**
 * 框选状态（PDD §5.7/§10.1 select 子树；UI-PDD D9：自 command/ 上移，
 * 命令层与 UI 层/快捷键共享同一状态）。单玩家客户端 → 单份即可。
 * 存储绝对坐标（含 world/dim），应用时校验同世界同维度。
 */
public final class SelectionState {

	private static final SelectionState INSTANCE = new SelectionState();

	public static SelectionState get() {
		return INSTANCE;
	}

	private SelectionState() {
	}

	@Nullable
	private WorldDimPos pos1;
	@Nullable
	private WorldDimPos pos2;

	public void set1(WorldDimPos p) {
		pos1 = p;
	}

	public void set2(WorldDimPos p) {
		pos2 = p;
	}

	@Nullable
	public WorldDimPos pos1() {
		return pos1;
	}

	@Nullable
	public WorldDimPos pos2() {
		return pos2;
	}

	public void clear() {
		pos1 = null;
		pos2 = null;
	}

	public boolean hasBox() {
		return pos1 != null && pos2 != null
				&& pos1.world() != null && pos1.dim() != null
				&& sameWorldDim(pos1, pos2);
	}

	static boolean sameWorldDim(WorldDimPos a, WorldDimPos b) {
		return a.world() != null && a.world().equals(b.world())
				&& a.dim() != null && a.dim().equals(b.dim());
	}

	public boolean contains(int x, int y, int z) {
		if (!hasBox()) return false;
		int minX = Math.min(pos1.x(), pos2.x()), maxX = Math.max(pos1.x(), pos2.x());
		int minY = Math.min(pos1.y(), pos2.y()), maxY = Math.max(pos1.y(), pos2.y());
		int minZ = Math.min(pos1.z(), pos2.z()), maxZ = Math.max(pos1.z(), pos2.z());
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}
}

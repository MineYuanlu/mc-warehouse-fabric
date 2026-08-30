package bid.yuanlu.mc.warehouse.ui.app.highlight;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.highlight.HighlightManager;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.ui.core.world.WorldHighlighter;
import bid.yuanlu.mc.warehouse.ui.mc.UiHooks;
import bid.yuanlu.mc.warehouse.ui.mc.UiPlatform;

/**
 * 高亮渲染器（UI-PDD §7）：每帧（经 UiHooks，renderLevel HEAD）把
 * HighlightManager 快照 + 选区盒提交给 WorldHighlighter（Gizmos）。
 * 选区盒 = 三轴三色棱线 + 角块描边 + 半透明面（Litematica renderSelectionBox 分层语义）。
 */
public final class HighlightRenderer {

	private static final HighlightRenderer INSTANCE = new HighlightRenderer();

	public static HighlightRenderer get() {
		return INSTANCE;
	}

	private HighlightRenderer() {
	}

	private static final int SEL_X = 0xFFFF5555;
	private static final int SEL_Y = 0xFF55FF55;
	private static final int SEL_Z = 0xFF5555FF;
	private static final int SEL_FILL = 0x30FFFFFF;
	private static final int SEL_CORNER = 0xFF00E5E5;
	private static final float SEL_LINE = 2.0F;
	private static final float SEL_SIDE = 1.5F;
	private static final float EXPAND = 0.001F;

	private volatile boolean selectionVisible = true;

	public void toggleSelectionVisible() {
		selectionVisible = !selectionVisible;
	}

	public boolean selectionVisible() {
		return selectionVisible;
	}

	/** 客户端入口调用：注册每帧提交钩子。 */
	public void register() {
		UiHooks.onPerFrameWorld(this::submitFrame);
	}

	private void submitFrame() {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		var frame = UiPlatform.worldHighlighter().beginFrame();
		try (frame) {
			for (var e : HighlightManager.get().snapshot()) {
				frame.box(expand(e.box()), e.type().strokeArgb, e.type().strokeArgb & 0x28FFFFFF,
						SEL_SIDE, false);
			}
			if (selectionVisible) {
				renderSelectionBox(frame);
			}
		}
	}

	private static AABB expand(AABB box) {
		return box.inflate(EXPAND);
	}

	/** 两角点选区盒：12 棱按 XYZ 轴分色 + 角块高亮 + 六面半透明。 */
	private void renderSelectionBox(WorldHighlighter.WorldFrame frame) {
		SelectionState sel = SelectionState.get();
		WorldDimPos p1 = sel.pos1();
		WorldDimPos p2 = sel.pos2();
		if (p1 == null && p2 == null) {
			return;
		}
		WorldDim dim = currentDim();
		if (dim == null) {
			return;
		}
		var a = vecOf(p1);
		var b = p2 != null ? vecOf(p2) : a;
		if (a == null || b == null) {
			return;
		}

		double minX = Math.min(a.x, b.x), maxX = Math.max(a.x, b.x) + 1;
		double minY = Math.min(a.y, b.y), maxY = Math.max(a.y, b.y) + 1;
		double minZ = Math.min(a.z, b.z), maxZ = Math.max(a.z, b.z) + 1;
		double ex = EXPAND;

		// 半透明六面（Gizmos.rect 四角定义）
		renderSides(frame, minX, minY, minZ, maxX, maxY, maxZ, ex);

		if (p2 == null) {
			// 单角点：只画该块描边
			frame.box(new AABB(a.x - ex, a.y - ex, a.z - ex, a.x + 1 + ex, a.y + 1 + ex, a.z + 1 + ex),
					SEL_CORNER, 0x00000000, SEL_LINE, true);
			return;
		}

		// 12 棱三轴三色
		// X 轴（沿 x 的 4 条）
		line(frame, minX, minY, minZ, maxX, minY, minZ, SEL_X, ex);
		line(frame, minX, maxY, minZ, maxX, maxY, minZ, SEL_X, ex);
		line(frame, minX, minY, maxZ, maxX, minY, maxZ, SEL_X, ex);
		line(frame, minX, maxY, maxZ, maxX, maxY, maxZ, SEL_X, ex);
		// Y 轴
		line(frame, minX, minY, minZ, minX, maxY, minZ, SEL_Y, ex);
		line(frame, maxX, minY, minZ, maxX, maxY, minZ, SEL_Y, ex);
		line(frame, minX, minY, maxZ, minX, maxY, maxZ, SEL_Y, ex);
		line(frame, maxX, minY, maxZ, maxX, maxY, maxZ, SEL_Y, ex);
		// Z 轴
		line(frame, minX, minY, minZ, minX, minY, maxZ, SEL_Z, ex);
		line(frame, maxX, minY, minZ, maxX, minY, maxZ, SEL_Z, ex);
		line(frame, minX, maxY, minZ, minX, maxY, maxZ, SEL_Z, ex);
		line(frame, maxX, maxY, minZ, maxX, maxY, maxZ, SEL_Z, ex);

		// 角块：pos1 红、pos2 蓝、重合时 accent 青
		if (sel.hasBox()) {
			frame.box(blockBox(a), SEL_CORNER, 0x00000000, SEL_LINE, true);
			frame.box(blockBox(b), SEL_CORNER, 0x00000000, SEL_LINE, true);
		} else {
			int c1 = p1 != null ? SEL_X : SEL_Z;
			frame.box(blockBox(p1 != null ? a : b), c1, 0x00000000, SEL_LINE, true);
		}
	}

	private static void renderSides(WorldHighlighter.WorldFrame frame,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double ex) {
		// 每面由四角逆时针定义（朝外）
		int fill = SEL_FILL;
		// 底面 (down)
		frame.quad(new Vec3(minX - ex, minY - ex, minZ - ex), new Vec3(maxX + ex, minY - ex, minZ - ex),
				new Vec3(maxX + ex, minY - ex, maxZ + ex), new Vec3(minX - ex, minY - ex, maxZ + ex), fill);
		// 顶面 (up)
		frame.quad(new Vec3(minX - ex, maxY + ex, minZ - ex), new Vec3(minX - ex, maxY + ex, maxZ + ex),
				new Vec3(maxX + ex, maxY + ex, maxZ + ex), new Vec3(maxX + ex, maxY + ex, minZ - ex), fill);
		// 北面 (north, z-)
		frame.quad(new Vec3(maxX + ex, minY - ex, minZ - ex), new Vec3(maxX + ex, maxY + ex, minZ - ex),
				new Vec3(minX - ex, maxY + ex, minZ - ex), new Vec3(minX - ex, minY - ex, minZ - ex), fill);
		// 南面 (south, z+)
		frame.quad(new Vec3(minX - ex, minY - ex, maxZ + ex), new Vec3(minX - ex, maxY + ex, maxZ + ex),
				new Vec3(maxX + ex, maxY + ex, maxZ + ex), new Vec3(maxX + ex, minY - ex, maxZ + ex), fill);
		// 西面 (west, x-)
		frame.quad(new Vec3(minX - ex, minY - ex, minZ - ex), new Vec3(minX - ex, maxY + ex, minZ - ex),
				new Vec3(minX - ex, maxY + ex, maxZ + ex), new Vec3(minX - ex, minY - ex, maxZ + ex), fill);
		// 东面 (east, x+)
		frame.quad(new Vec3(maxX + ex, minY - ex, maxZ + ex), new Vec3(maxX + ex, maxY + ex, maxZ + ex),
				new Vec3(maxX + ex, maxY + ex, minZ - ex), new Vec3(maxX + ex, minY - ex, minZ - ex), fill);
	}

	private static void line(WorldHighlighter.WorldFrame frame,
			double x1, double y1, double z1, double x2, double y2, double z2, int argb, double ex) {
		frame.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), argb, SEL_LINE);
	}

	private static AABB blockBox(Vec3 corner) {
		return new AABB(corner.x - 0.002, corner.y - 0.002, corner.z - 0.002,
				corner.x + 1.002, corner.y + 1.002, corner.z + 1.002);
	}

	private static @Nullable Vec3 vecOf(WorldDimPos p) {
		if (p == null) {
			return null;
		}
		WorldDim dim = currentDim();
		if (dim == null || !dim.dimId().equals(p.dim())) {
			return null;
		}
		// 选区存储的是绝对坐标（SelectionState 语义），直接用
		return new Vec3(p.x(), p.y(), p.z());
	}

	private static @Nullable WorldDim currentDim() {
		var mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) {
			return null;
		}
		var trk = bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker.get();
		String serverId = trk.currentServerId();
		String worldName = trk.currentWorldName();
		if (serverId == null || worldName == null) return null;
		return new WorldDim(serverId, worldName, mc.level.dimension().identifier().toString());
	}

}

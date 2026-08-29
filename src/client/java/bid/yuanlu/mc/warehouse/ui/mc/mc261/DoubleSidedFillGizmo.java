package bid.yuanlu.mc.warehouse.ui.mc.mc261;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 双面填充立方体 Gizmo：原版 {@code debug_filled_box} 管线开背面剔除，因此每面发
 * 正反两种绕向的 quad 各一次——单任视角下恰好一个绕向存活，6 面全可见；
 * 半透明填充使多 box 叠加处不透明度自然升高（用户要求的"叠加更白"）。
 * <p>
 * 注意不可用 {@link GizmoPrimitives#addTriangleFan}：多次调用的顶点会合并进同一个
 * TRIANGLE_FAN draw，GPU 以首顶点为公共锚点连三角形，除第一个面外全是穿越盒身的
 * 对角三角形（实机"只有远侧面渲染"的根因）。线框仍由调用方用普通 cuboid 画。
 */
record DoubleSidedFillGizmo(AABB box, int fillArgb) implements Gizmo {

	@Override
	public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
		int color = ARGB.multiplyAlpha(fillArgb, alphaMultiplier);
		double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
		double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
		// 每面两个相反绕向的 quad：剔除下必存活其一，任意视角双面可见
		face(primitives, new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1), color);
		face(primitives, new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), color);
		face(primitives, new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), color);
		face(primitives, new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), color);
		face(primitives, new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), color);
		face(primitives, new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), color);
	}

	private static void face(GizmoPrimitives p, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		p.addQuad(a, b, c, d, color);
		p.addQuad(a, d, c, b, color);
	}
}

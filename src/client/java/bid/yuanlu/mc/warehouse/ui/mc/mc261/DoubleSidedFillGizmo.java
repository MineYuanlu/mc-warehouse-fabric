package bid.yuanlu.mc.warehouse.ui.mc.mc261;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 双面填充立方体 Gizmo：原版 {@code debug_filled_box} 管线开背面剔除，半透明填充
 * 朝向不一致（用户实机：面向玩家的面不渲染）。本实现 6 面改走
 * {@link GizmoPrimitives#addTriangleFan}（{@code debug_triangle_fan} 管线自带
 * withCull(false) 双面），双面 + 半透明使 box 自身正反面/多 box 叠加处
 * 不透明度自然升高（用户要求的"叠加更白"）。线框仍由调用方用普通 cuboid 画。
 */
record DoubleSidedFillGizmo(AABB box, int fillArgb) implements Gizmo {

	@Override
	public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
		int color = ARGB.multiplyAlpha(fillArgb, alphaMultiplier);
		double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
		double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
		// 每面 4 点 triangle fan（a,b,c,d → 三角形 abc + acd，恰好覆盖该面）；
		// 双面渲染下绕序不影响可见性
		face(primitives, new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1), color);
		face(primitives, new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), color);
		face(primitives, new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), color);
		face(primitives, new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), color);
		face(primitives, new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), color);
		face(primitives, new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), color);
	}

	private static void face(GizmoPrimitives p, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		p.addTriangleFan(new Vec3[]{a, b, c, d}, color);
	}
}

package bid.yuanlu.mc.warehouse.ui.mc.mc261;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import bid.yuanlu.mc.warehouse.ui.core.world.WorldHighlighter;

/**
 * 世界高亮的 26.1 实现：Gizmos 直通（UI-PDD §4.3/§7，公开 API 无 mixin）。
 */
final class Mc261WorldHighlighter implements WorldHighlighter {

	@Override
	public WorldFrame beginFrame() {
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.levelRenderer == null) {
			return WorldFrame.NOOP;
		}
		var collection = mc.levelRenderer.collectPerFrameGizmos();
		return new WorldFrame() {
			@Override
			public void box(AABB box, int strokeArgb, int fillArgb, float lineWidth, boolean throughWalls) {
				// 填充走双面 Gizmo（debug_filled_box 管线单面剔除，面向玩家的面不渲染）；
				// 线框仍走原版 cuboid
				if (fillArgb != 0) {
					var fill = Gizmos.addGizmo(new DoubleSidedFillGizmo(box, fillArgb));
					if (throughWalls) {
						fill.setAlwaysOnTop();
					}
				}
				if (strokeArgb != 0) {
					var stroke = Gizmos.cuboid(box, GizmoStyle.stroke(strokeArgb, lineWidth));
					if (throughWalls) {
						stroke.setAlwaysOnTop();
					}
				}
			}

			@Override
			public void line(Vec3 a, Vec3 b, int argb, float width) {
				Gizmos.line(a, b, argb, width);
			}

			@Override
			public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
				Gizmos.rect(a, b, c, d, GizmoStyle.fill(argb));
			}

			@Override
			public void label(String text, Vec3 pos, int argb) {
				Gizmos.billboardText(text, pos, TextGizmo.Style.forColorAndCentered(argb));
			}

			@Override
			public void close() {
				collection.close();
			}
		};
	}
}

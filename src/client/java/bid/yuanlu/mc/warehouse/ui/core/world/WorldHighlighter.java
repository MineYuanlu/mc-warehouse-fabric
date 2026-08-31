package bid.yuanlu.mc.warehouse.ui.core.world;

import java.util.List;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 世界空间高亮端口（UI-PDD §4.3）——L3 提交、L2 直通 MC Gizmos API。
 * 帧语义：beginFrame() 返回本帧收集器，try-with-resources 用完即关。
 */
public interface WorldHighlighter {

	WorldFrame beginFrame();

	/** 单帧高亮收集窗口。 */
	interface WorldFrame extends AutoCloseable {

		WorldFrame NOOP = new WorldFrame() {
			@Override
			public void box(AABB box, int strokeArgb, int fillArgb, float lineWidth, boolean throughWalls) {
			}

			@Override
			public void line(Vec3 a, Vec3 b, int argb, float width) {
			}

			@Override
			public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
			}

			@Override
			public void label(String text, Vec3 pos, int argb) {
			}

			@Override
			public void close() {
			}
		};

		void box(AABB box, int strokeArgb, int fillArgb, float lineWidth, boolean throughWalls);

		void line(Vec3 a, Vec3 b, int argb, float width);

		/** 四角定义的填充面（朝向由顶点顺序决定）。 */
		void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb);

		void label(String text, Vec3 pos, int argb);

		@Override
		void close();
	}

	/** 便捷批量提交。 */
	static void submit(WorldHighlighter hl, List<HighlightBox> boxes) {
		if (boxes.isEmpty()) {
			return;
		}
		try (var frame = hl.beginFrame()) {
			for (var b : boxes) {
				frame.box(b.box(), b.strokeArgb(), b.fillArgb(), b.lineWidth(), b.throughWalls());
			}
		}
	}

	/** 高亮盒快照（tick 级缓存的不可变数据，渲染帧只读）。 */
	record HighlightBox(AABB box, int strokeArgb, int fillArgb, float lineWidth, boolean throughWalls) {
	}
}

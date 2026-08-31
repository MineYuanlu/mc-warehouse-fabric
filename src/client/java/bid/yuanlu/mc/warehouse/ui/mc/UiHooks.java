package bid.yuanlu.mc.warehouse.ui.mc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * L2 每帧钩子（UI-PDD §7）：mixin 在 LevelRenderer.renderLevel HEAD 触发——
 * 此时 per-frame Gizmos 收集窗口已打开、finalizeGizmoCollection 尚未执行，
 * 注册方提交的世界空间 Gizmo 同帧渲染。仅供渲染侧 L3 注册，不含业务。
 */
public final class UiHooks {

	private static final List<Runnable> PER_FRAME_WORLD = new CopyOnWriteArrayList<>();

	private UiHooks() {
	}

	public static void onPerFrameWorld(Runnable r) {
		PER_FRAME_WORLD.add(r);
	}

	public static void firePerFrameWorld() {
		for (var r : PER_FRAME_WORLD) {
			r.run();
		}
	}
}

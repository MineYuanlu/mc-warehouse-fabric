package bid.yuanlu.mcwarehouse.engine.highlight;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;

import bid.yuanlu.mcwarehouse.engine.highlight.HighlightManager.HighlightType;

public class ContainerOutlineRenderer {

	private static final Map<HighlightType, Integer> COLORS = Map.of(
			HighlightType.INPUT_OUTLINED, 0xFF4444,
			HighlightType.OUTPUT_OUTLINED, 0x44FF44,
			HighlightType.TEMP_OUTLINED, 0xFFFF44,
			HighlightType.IGNORE_OUTLINED, 0x888888,
			HighlightType.HAS_SPACE, 0x44AAFF,
			HighlightType.FULL, 0xFFAA44,
			HighlightType.UNKNOWN, 0xAAAAAA
	);

	public static int getColor(HighlightType type) {
		return COLORS.getOrDefault(type, 0xFFFFFF);
	}

	public static void renderGizmos() {
		Map<BlockPos, HighlightType> highlights = HighlightManager.getInstance().getHighlights();
		if (highlights.isEmpty()) return;

		for (var entry : highlights.entrySet()) {
			BlockPos pos = entry.getKey();
			int color = getColor(entry.getValue());
			Gizmos.cuboid(pos, GizmoStyle.stroke(color));
		}
	}
}

package bid.yuanlu.mcwarehouse.engine.highlight;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

import bid.yuanlu.mcwarehouse.engine.highlight.HighlightManager.HighlightType;

public class ContainerOutlineRenderer {

	private static final Map<HighlightType, Integer> COLORS = Map.of(
			HighlightType.INPUT_OUTLINED, 0xFF4444,
			HighlightType.OUTPUT_OUTLINED, 0x44FF44,
			HighlightType.RELAY_OUTLINED, 0xFFFF44,
			HighlightType.IGNORE_OUTLINED, 0x888888,
			HighlightType.HAS_SPACE, 0x44AAFF,
			HighlightType.FULL, 0xFFAA44,
			HighlightType.UNKNOWN, 0xAAAAAA
	);

	public static int getColor(HighlightType type) {
		return COLORS.getOrDefault(type, 0xFFFFFF);
	}

	public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
		// Stub — WorldRendererMixin will wire up rendering
	}
}

package bid.yuanlu.mcwarehouse.engine.highlight;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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

	private static final VoxelShape FULL_BLOCK = Shapes.block();

	public static int getColor(HighlightType type) {
		return COLORS.getOrDefault(type, 0xFFFFFF);
	}

	public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
		Map<BlockPos, HighlightType> highlights = HighlightManager.getInstance().getHighlights();
		if (highlights.isEmpty()) return;

		Vec3 camPos = camera.getPosition();

		for (var entry : highlights.entrySet()) {
			BlockPos pos = entry.getKey();
			HighlightType type = entry.getValue();
			int color = getColor(type);

			float r = ((color >> 16) & 0xFF) / 255.0F;
			float g = ((color >> 8) & 0xFF) / 255.0F;
			float b = (color & 0xFF) / 255.0F;

			poseStack.pushPose();
			poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
			LevelRenderer.renderLineBox(poseStack, bufferSource, FULL_BLOCK,
					0.0, 0.0, 0.0, r, g, b, 1.0F);
			poseStack.popPose();
		}
	}
}

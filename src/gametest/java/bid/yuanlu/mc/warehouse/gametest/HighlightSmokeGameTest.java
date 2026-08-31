package bid.yuanlu.mc.warehouse.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.highlight.HighlightManager;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.core.world.WorldSessionTracker;
import bid.yuanlu.mc.warehouse.ui.app.highlight.HighlightRenderer;

/**
 * 世界高亮 E2E 冒烟（UI-PDD §14 M2 验收）：进世界 → 设选区两角 →
 * 高亮渲染帧路径（LevelRendererMixin → Gizmos）截图验证 → 开关与快照断言。
 * CI grep {@code yuanlu-warehouse highlight smoke assertions passed} 判定成功。
 */
@SuppressWarnings("UnstableApiUsage")
public class HighlightSmokeGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(singleplayer);
			context.waitTicks(20);

			context.runOnClient(mc -> {
				assert mc.player != null && mc.level != null : "应已在世界中";
				var trk = WorldSessionTracker.get();
				String worldName = trk == null ? null : trk.currentWorldName();
				String dimId = mc.level.dimension().identifier().toString();
				var feet = mc.player.blockPosition();
				SelectionState.get().set1(new WorldDimPos(worldName, dimId,
						feet.getX() - 2, feet.getY(), feet.getZ() - 2));
				SelectionState.get().set2(new WorldDimPos(worldName, dimId,
						feet.getX() + 2, feet.getY() + 2, feet.getZ() + 2));
				HighlightManager.get().refresh();
				// 无激活仓库：容器快照为空但构建路径无异常
				assert HighlightManager.get().snapshot().isEmpty() : "无激活仓库时快照应为空";
			});
			context.waitTicks(10);
			// 选区盒渲染路径（每帧提交 Gizmos）
			context.takeScreenshot("yuanlu-warehouse-highlight");

			context.runOnClient(mc -> {
				var r = HighlightRenderer.get();
				boolean before = r.selectionVisible();
				r.toggleSelectionVisible();
				assert r.selectionVisible() != before : "选区显隐应翻转";
				r.toggleSelectionVisible();
			});

			LOGGER.info("yuanlu-warehouse highlight smoke assertions passed");

			// 绕开 close() 死锁（见 WarehouseClientGameTest 注释）
			singleplayer.getServer().runOnServer(server -> server.halt(false));
		}
	}
}

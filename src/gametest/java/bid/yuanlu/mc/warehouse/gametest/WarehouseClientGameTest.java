package bid.yuanlu.mc.warehouse.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * E2E 冒烟测试：真实启动 MC 客户端 → 创建单机世界 → 等 chunk 渲染 → 截图。
 * <p>
 * 验证发布链路（universal jar 在各 MC 版本上真实可加载、入口点与 mixin 应用）；
 * 业务断言见 {@link ContainerProtocolGameTest}。CI grep {@code yuanlu-warehouse E2E assertions passed} 判定成功。
 */
@SuppressWarnings("UnstableApiUsage")
public class WarehouseClientGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(singleplayer);
			context.waitTicks(20);

			context.takeScreenshot("yuanlu-warehouse-final");

			LOGGER.info("yuanlu-warehouse E2E assertions passed");

			// 绕开 close() 死锁：MC 26 的 IntegratedServer.halt 先 executeBlocking 等 server 处理停止
			// 任务，而 fabric client gametest 的 phaser 让 server 卡在 postRunTasks 等 render arrive。
			// 在 server 线程内主动 halt（executeBlocking 检测当前线程即 server，直接执行不阻塞），
			// 使 server 先脱离 phaser 协调，之后 close() 的 disconnect 才能顺利完成。
			singleplayer.getServer().runOnServer(server -> server.halt(false));
		}
	}
}

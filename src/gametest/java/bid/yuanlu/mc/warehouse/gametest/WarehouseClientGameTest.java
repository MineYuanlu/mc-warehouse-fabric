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
 * 业务断言随 mod 功能补充。CI grep {@code yuanlu-warehouse E2E assertions passed} 判定成功。
 */
@SuppressWarnings("UnstableApiUsage")
public class WarehouseClientGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			waitForChunksRender(singleplayer);
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

	/**
	 * 等待 chunk 渲染完成。跨 fabric-client-gametest 版本兼容：
	 * <ul>
	 * <li>5.1.x（fabric-api 0.153/0.155，MC 26.1）：{@code singleplayer.getClientLevel()}
	 *     返回 {@code TestClientLevelContext}（有 {@code waitForChunksRender()}）</li>
	 * <li>6.0.0（fabric-api 0.156+，MC 26.2）：该类型被移除，改走
	 *     {@code singleplayer.getConnection().waitForChunksRender()}</li>
	 * </ul>
	 * 用反射统一入口：先试 {@code getClientLevel().waitForChunksRender()}，
	 * 没有则退到 {@code getConnection().waitForChunksRender()}。
	 */
	private static void waitForChunksRender(TestSingleplayerContext singleplayer) {
		try {
			Object level = TestSingleplayerContext.class.getMethod("getClientLevel").invoke(singleplayer);
			level.getClass().getMethod("waitForChunksRender").invoke(level);
			return;
		} catch (NoSuchMethodException e) {
			// 新版 gametest API：getClientLevel 已移除
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("waitForChunksRender (getClientLevel path) failed", e);
		}

		try {
			Object connection = TestSingleplayerContext.class.getMethod("getConnection").invoke(singleplayer);
			connection.getClass().getMethod("waitForChunksRender").invoke(connection);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("waitForChunksRender (getConnection path) failed", e);
		}
	}
}

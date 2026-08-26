package bid.yuanlu.mc.warehouse.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * GameTest 共享工具。
 */
public final class GameTestUtil {

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
	public static void waitForChunksRender(TestSingleplayerContext singleplayer) {
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

	private GameTestUtil() {
	}
}

package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;

import org.junit.jupiter.api.Test;

/**
 * 框架冒烟测试：验证 fabric-loader-junit 在 CI 矩阵的各 MC 版本上
 * 能正确引导 loader 并探测游戏版本（版本参数由 -Pminecraft_version 注入）。
 */
class FrameworkSmokeTest extends McBootstrap {

	@Test
	void gameVersionDetected() {
		assertNotNull(SharedConstants.getCurrentVersion(), "loader-junit should detect the game version");
	}

	@Test
	void supportedMinecraftLine() {
		String id = SharedConstants.getCurrentVersion().id();
		assertTrue(id.compareTo("26.1") >= 0,
				"expected MC >= 26.1 (min supported), got " + id);
	}
}

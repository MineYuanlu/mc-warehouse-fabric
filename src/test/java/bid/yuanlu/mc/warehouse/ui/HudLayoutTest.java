package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.ui.app.hud.HudLayout;

/** HudLayout.snapDelta：轴向贴靠修正（四向边缘对齐、阈值）。 */
class HudLayoutTest {

	private static final int TH = HudLayout.SNAP_THRESHOLD;

	@Test
	void snapsGroupLeftToTargetLeft() {
		// 组在 x=20，容器左缘 24：差 4 ≤ 6 → 吸附到 24
		assertEquals(4, HudLayout.snapDelta(20, 50, 24, 176, TH));
	}

	@Test
	void snapsGroupRightToTargetRight() {
		// 组右缘 20+50=70，容器右缘 200：差 130 > 6 → 不吸附该向；
		// 但组左缘 20 距容器左缘 24 差 4 → 吸附 4
		assertEquals(4, HudLayout.snapDelta(20, 50, 24, 176, TH));
		// 组右缘 199 距容器右缘 200 差 1 → 吸附 1
		assertEquals(1, HudLayout.snapDelta(149, 50, 24, 176, TH));
	}

	@Test
	void snapsAdjacentOutsideEdges() {
		// 组左缘对容器右缘（HUD 摆容器右侧）：组在 201，容器右缘 200 → -1
		assertEquals(-1, HudLayout.snapDelta(201, 50, 24, 176, TH));
		// 组右缘对容器左缘（HUD 摆容器左侧）：组右缘 25，容器左缘 24 → -1
		assertEquals(-1, HudLayout.snapDelta(-25, 50, 24, 176, TH));
	}

	@Test
	void beyondThresholdNoSnap() {
		assertEquals(0, HudLayout.snapDelta(20, 50, 40, 176, TH)); // 差 20
		assertEquals(0, HudLayout.snapDelta(0, 50, 24, 176, 3)); // 自定义阈值 3，差 4
	}

	@Test
	void picksMinimalDeltaAmongCandidates() {
		// 组左缘距容器左缘 2、组右缘距容器右缘 2 → 任取最小（同为 2）
		assertEquals(2, HudLayout.snapDelta(22, 176, 24, 176, TH));
	}
}

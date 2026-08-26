package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.util.CoordinateUtils;
import net.minecraft.core.BlockPos;

/**
 * 坐标换算往返（PDD §13 JVM 层）。
 */
public class CoordinateUtilsTest {

	@Test
	void roundTrip() {
		BlockPos anchor = new BlockPos(100, 64, -200);
		BlockPos absolute = new BlockPos(123, 70, -150);
		BlockPos relative = CoordinateUtils.toRelative(absolute, anchor);
		assertEquals(new BlockPos(23, 6, 50), relative);
		assertEquals(absolute, CoordinateUtils.toAbsolute(relative, anchor));
	}
}

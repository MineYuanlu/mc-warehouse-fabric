package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;

import bid.yuanlu.mc.warehouse.core.selection.SelectionOps;
import bid.yuanlu.mc.warehouse.core.selection.SelectionState;
import bid.yuanlu.mc.warehouse.util.RelativeCoords;

/** SelectionOps（expand/主轴）与 RelativeCoords（~ 相对解析）。 */
class SelectionOpsTest {

	@Test
	void expandMovesCorner2Only() {
		var sel = SelectionState.get();
		sel.clear();
		sel.set1(new bid.yuanlu.mc.warehouse.api.world.WorldDimPos("w", "minecraft:overworld", 10, 10, 10));
		assertFalse(sel.hasBox());
		assertTrue(SelectionOps.expand(sel, Direction.EAST, 5));
		assertEquals(15, sel.pos2().x());
		assertEquals(10, sel.pos2().y());
		assertTrue(sel.hasBox());

		// 负数收缩
		assertTrue(SelectionOps.expand(sel, Direction.EAST, -3));
		assertEquals(12, sel.pos2().x());
		sel.clear();
	}

	@Test
	void expandWithoutAnyCornerFails() {
		var sel = SelectionState.get();
		sel.clear();
		assertFalse(SelectionOps.expand(sel, Direction.UP, 1));
	}

	@Test
	void dominantAxisPicksLargestComponent() {
		assertEquals(Direction.EAST, SelectionOps.dominantAxis(0.9, 0.1, 0.2));
		assertEquals(Direction.WEST, SelectionOps.dominantAxis(-0.9, 0.1, 0.2));
		assertEquals(Direction.UP, SelectionOps.dominantAxis(0.1, 0.95, 0.2));
		assertEquals(Direction.DOWN, SelectionOps.dominantAxis(0.1, -0.95, 0.2));
		assertEquals(Direction.SOUTH, SelectionOps.dominantAxis(0.1, 0.2, 0.9));
		assertEquals(Direction.NORTH, SelectionOps.dominantAxis(0.2, 0.1, -0.95));
	}

	@Test
	void relativeCoordParsing() {
		assertEquals(100, RelativeCoords.parse("~", 100.2));
		assertEquals(105, RelativeCoords.parse("~5", 100.0));
		assertEquals(97, RelativeCoords.parse("~-3", 100.0));
		assertEquals(-12, RelativeCoords.parse("-12", 100.0));
		assertEquals(64, RelativeCoords.parse("64", 0.0));
	}
}

package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.ui.core.element.EditBuffer;

/** EditBuffer：光标移动/插入/删除/超长拒绝（纯模型）。 */
class EditBufferTest {

	@Test
	void insertAtCursorAdvancesCursor() {
		var b = new EditBuffer();
		b.insert("ab", 10);
		assertEquals("ab", b.text());
		assertEquals(2, b.cursor());
		b.moveLeft();
		b.insert("X", 10);
		assertEquals("aXb", b.text());
		assertEquals(2, b.cursor());
	}

	@Test
	void insertRejectsWhenExceedsMaxLength() {
		var b = new EditBuffer();
		b.insert("abc", 3);
		assertFalse(b.insert("d", 3));
		assertEquals("abc", b.text());
		assertEquals(3, b.cursor());
		assertTrue(b.insert("d", 4));
	}

	@Test
	void cursorMovementAndClamp() {
		var b = new EditBuffer();
		b.setText("hello");
		assertEquals(5, b.cursor());
		b.setCursor(99);
		assertEquals(5, b.cursor());
		b.setCursor(-1);
		assertEquals(0, b.cursor());
		b.moveLeft();
		assertEquals(0, b.cursor()); // 钳在 0
		b.end();
		assertEquals(5, b.cursor());
		b.moveRight();
		assertEquals(5, b.cursor());
		b.home();
		assertEquals(0, b.cursor());
	}

	@Test
	void backspaceDeletesBeforeCursorDeleteAtCursor() {
		var b = new EditBuffer();
		b.setText("abc");
		assertTrue(b.backspace());
		assertEquals("ab", b.text());
		assertEquals(2, b.cursor());
		b.setCursor(0);
		assertFalse(b.backspace());
		b.setCursor(0);
		assertTrue(b.delete());
		assertEquals("b", b.text());
		assertEquals(0, b.cursor());
		b.setCursor(99);
		assertFalse(b.delete());
	}
}

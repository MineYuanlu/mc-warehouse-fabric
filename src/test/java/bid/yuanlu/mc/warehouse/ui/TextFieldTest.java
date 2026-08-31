package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import bid.yuanlu.mc.warehouse.ui.core.draw.ClipboardPort;
import bid.yuanlu.mc.warehouse.ui.core.element.NumberFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.TextFieldElement;
import bid.yuanlu.mc.warehouse.ui.core.element.UiRoot;

/** TextFieldElement / NumberFieldElement：光标编辑、过滤、粘贴、解析与范围校验。 */
class TextFieldTest {

	private final TestDraw g = new TestDraw();

	@AfterEach
	void resetClipboard() {
		ClipboardPort.install(null);
	}

	private TextFieldElement focusedField(String initial) {
		var root = new UiRoot();
		var f = new TextFieldElement(initial);
		root.add(f);
		root.update(g, 400, 300, -1, -1);
		f.requestFocus();
		return f;
	}

	@Test
	void typingInsertsAtCursorAndBackspaceDeletes() {
		var f = focusedField("");
		var root = (UiRoot) f.root();
		root.charTyped('a');
		root.charTyped('b');
		root.charTyped('c');
		assertEquals("abc", f.text());
		root.keyDown(GLFW.GLFW_KEY_LEFT, 0, 0);
		root.charTyped('X');
		assertEquals("abXc", f.text());
		root.keyDown(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
		assertEquals("abc", f.text());
		root.keyDown(GLFW.GLFW_KEY_HOME, 0, 0);
		root.keyDown(GLFW.GLFW_KEY_DELETE, 0, 0);
		assertEquals("bc", f.text());
	}

	@Test
	void pasteFiltersAndRespectsMaxLength() {
		ClipboardPort.install(new ClipboardPort.Provider() {
			@Override
			public String get() {
				return "hi\nthere";
			}

			@Override
			public void set(String value) {
			}
		});
		var f = focusedField("");
		f.maxLength(6);
		var root = (UiRoot) f.root();
		root.keyDown(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
		// 换行被过滤、超长截断
		assertEquals("hither", f.text());
	}

	@Test
	void filterRejectsChars() {
		var f = focusedField("");
		f.filter(c -> c.charAt(0) >= '0' && c.charAt(0) <= '9');
		var root = (UiRoot) f.root();
		root.charTyped('a');
		root.charTyped('7');
		assertEquals("7", f.text());
	}

	@Test
	void numberFieldValidatesAndClamps() {
		var root = new UiRoot();
		var n = new NumberFieldElement(0, 64, 10);
		root.add(n);
		root.update(g, 400, 300, -1, -1);
		n.requestFocus();
		assertTrue(n.valid());
		assertEquals(10, n.intValue());

		root.charTyped('0'); // 100 越界（10 → 在尾部插入 0 = 100）
		assertFalse(n.valid());
		assertEquals(64, n.intValue()); // intValue 为钳制兜底

		root.keyDown(GLFW.GLFW_KEY_BACKSPACE, 0, 0); // 10
		assertTrue(n.valid());
		root.keyDown(GLFW.GLFW_KEY_HOME, 0, 0);
		root.keyDown(GLFW.GLFW_KEY_DELETE, 0, 0); // 0
		assertTrue(n.valid());
		assertEquals(0, n.intValue());
	}

	@Test
	void numberFieldRelativeToken() {
		var root = new UiRoot();
		var n = new NumberFieldElement(-64, 64, 0, true);
		root.add(n);
		root.update(g, 400, 300, -1, -1);
		n.requestFocus();
		root.charTyped('~');
		root.charTyped('-');
		root.charTyped('3');
		assertTrue(n.relative());
		assertTrue(n.valid());
		assertEquals(-3, n.intValue());

		var plain = new NumberFieldElement(0, 64, 0);
		assertFalse(plain.relative());
	}

	@Test
	void numberFieldEmptyIsInvalid() {
		var root = new UiRoot();
		var n = new NumberFieldElement(0, 64, 5);
		root.add(n);
		root.update(g, 400, 300, -1, -1);
		n.requestFocus();
		root.keyDown(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
		assertFalse(n.valid());
	}
}

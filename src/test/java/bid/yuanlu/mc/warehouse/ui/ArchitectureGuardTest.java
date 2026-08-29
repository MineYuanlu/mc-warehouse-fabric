package bid.yuanlu.mc.warehouse.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 分层架构守护（UI-PDD §2 依赖方向规则 / §13）：
 * <ul>
 *   <li>L1（ui/core/）禁止 import net.minecraft.client.*（volatile 面）；</li>
 *   <li>L3（ui/app/）禁止 import net.minecraft.client.gui.*（必须经 L1/L2 门面）；</li>
 *   <li>RawGraphics 逃生舱在 L3 的使用点必须为 0。</li>
 * </ul>
 */
class ArchitectureGuardTest {

	private static final Pattern L1_FORBIDDEN = Pattern.compile("^\\s*import\\s+net\\.minecraft\\.client\\.");
	private static final Pattern L3_FORBIDDEN = Pattern.compile("^\\s*import\\s+net\\.minecraft\\.client\\.gui\\.");
	private static final Pattern RAW_GRAPHICS_USE = Pattern.compile("RawGraphics\\.");

	private static Path clientRoot() {
		// gradle test 工作目录 = 项目根
		return Path.of(System.getProperty("user.dir"), "src", "client", "java",
				"bid", "yuanlu", "mc", "warehouse");
	}

	private static List<Path> javaFiles(String sub) throws IOException {
		var dir = clientRoot().resolve(sub);
		assertTrue(Files.isDirectory(dir), "缺少目录 " + dir);
		try (Stream<Path> s = Files.walk(dir)) {
			return s.filter(p -> p.toString().endsWith(".java")).toList();
		}
	}

	private void assertNone(List<Path> files, Pattern forbidden, String layer, String rule) throws IOException {
		var violations = new ArrayList<String>();
		for (var f : files) {
			for (var line : Files.readAllLines(f)) {
				if (forbidden.matcher(line).find()) {
					violations.add(f.getFileName() + ": " + line.strip());
				}
			}
		}
		assertEquals(List.of(), violations, layer + " 违反依赖规则（" + rule + "）");
	}

	@Test
	void l1CoreHasNoClientImports() throws IOException {
		assertNone(javaFiles("ui/core"), L1_FORBIDDEN, "L1 ui/core",
				"禁止 net.minecraft.client.*——MC 通用类型（Component/ItemStack 等）允许");
	}

	@Test
	void l3AppHasNoClientGuiImports() throws IOException {
		assertNone(javaFiles("ui/app"), L3_FORBIDDEN, "L3 ui/app",
				"禁止 net.minecraft.client.gui.*——绘制/挂载经 L1 端口与 L2 门面");
	}

	@Test
	void l3AppDoesNotUseRawEscapeHatch() throws IOException {
		var files = javaFiles("ui/app");
		var violations = new ArrayList<String>();
		for (var f : files) {
			for (var line : Files.readAllLines(f)) {
				if (RAW_GRAPHICS_USE.matcher(line).find()) {
					violations.add(f.getFileName() + ": " + line.strip());
				}
			}
		}
		assertEquals(List.of(), violations, "L3 使用逃生舱属里程碑缺陷（UI-PDD §4.4）");
	}
}

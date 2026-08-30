package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.net.WorldIdFile;

/**
 * 存档级随机世界身份文件（PDD §4.2）：读/建、损坏重生成、IO 失败回退。
 */
public class WorldIdFileTest {

	@TempDir
	Path tempDir;

	@Test
	void createsOnFirstReadAndStableAfterwards() throws Exception {
		Path file = WorldIdFile.pathFor(tempDir);
		String first = WorldIdFile.readOrCreate(file);
		assertNotNull(first, "首次读取生成 id");
		assertTrue(!first.isBlank() && !first.contains(":") && !first.contains(" "),
				"十进制整数格式");

		assertEquals(first, WorldIdFile.readOrCreate(file), "第二次读取命中文件");
		assertEquals("id:" + first, Files.readString(file).trim(), "文件格式 id:<int>");
	}

	@Test
	void parsesExistingFile() throws Exception {
		Path file = WorldIdFile.pathFor(tempDir);
		Files.writeString(file, "id:-1979958895");
		assertEquals("-1979958895", WorldIdFile.readOrCreate(file), "解析既有 id（含负数）");
	}

	@Test
	void corruptFileRegenerates() throws Exception {
		Path file = WorldIdFile.pathFor(tempDir);
		Files.writeString(file, "garbage\nid:notanumber\n");
		assertNotNull(WorldIdFile.readOrCreate(file), "损坏文件重新生成");

		Integer parsed = null;
		for (String line : Files.readAllLines(file)) {
			if (line.startsWith("id:")) parsed = Integer.parseInt(line.substring(3).trim());
		}
		assertNotNull(parsed, "重新生成后文件含有效 id");
	}

	@Test
	void missingParentDirectoryIsCreated() {
		Path nested = tempDir.resolve("saves").resolve("New World");
		Path file = WorldIdFile.pathFor(nested);
		assertNotNull(WorldIdFile.readOrCreate(file), "父目录不存在时自动创建");
	}

	@Test
	void ioFailureReturnsNull() throws Exception {
		// 目标路径本身是目录 → 写入必失败 → 回退 null（worldId 缺省 ""）
		Path file = tempDir.resolve(WorldIdFile.FILE_NAME);
		Files.createDirectory(file);
		assertNull(WorldIdFile.readOrCreate(file), "路径为目录时 IO 失败回退 null");
	}

	@Test
	void pathForAppendsFileName() {
		assertEquals(WorldIdFile.FILE_NAME,
				WorldIdFile.pathFor(Path.of("/tmp/x")).getFileName().toString());
	}
}

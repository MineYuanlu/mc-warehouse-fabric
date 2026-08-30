package bid.yuanlu.mc.warehouse.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 存档级随机世界身份文件（PDD §4.2）：{@code <存档根>/yuanluworldid.txt}，
 * 单行 {@code id:<int>}（Xaero xaeromap.txt 同款格式）。
 * <p>
 * 服务端首次读取时生成并写回；此后存档目录改名/复制均保持同一 id。
 * 只防目录改名，不防"只搬 region"的新存档（新存档生成新 id，需玩家手动换绑）。
 * 纯 Path IO，便于单测；调用方（{@link ServerWorldIdSync}）按 server 实例缓存。
 */
public final class WorldIdFile {

	public static final String FILE_NAME = "yuanluworldid.txt";

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/world-id-file");

	private WorldIdFile() {
	}

	/** 存档 id 文件的标准路径：{@code server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME)} */
	public static Path pathFor(Path saveRoot) {
		return saveRoot.resolve(FILE_NAME);
	}

	/**
	 * 读取存档 id；缺失/损坏时生成新随机 id 并写回。
	 *
	 * @return 十进制 id 字符串；IO 失败（只读文件系统等）返回 null，由调用方回退 {@code ""}
	 */
	@Nullable
	public static String readOrCreate(Path file) {
		try {
			Integer id = read(file);
			if (id == null) {
				id = new Random().nextInt();
				write(file, id);
				LOGGER.info("world id file created: {} -> {}", file, id);
			}
			return Integer.toString(id);
		} catch (Exception e) {
			LOGGER.warn("world id file unavailable ({}): {}", file, e.toString());
			return null;
		}
	}

	/** 解析 {@code id:<int>}；文件缺失或无有效行返回 null（生成新 id 的信号） */
	@Nullable
	private static Integer read(Path file) throws IOException {
		if (!Files.isRegularFile(file)) return null;
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			String[] parts = line.trim().split(":", 2);
			if (parts.length == 2 && parts[0].equals("id")) {
				try {
					return Integer.parseInt(parts[1].trim());
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return null;
	}

	private static void write(Path file, int id) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, "id:" + id + "\n", StandardCharsets.UTF_8);
	}
}

package bid.yuanlu.mc.warehouse.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 临时文件 + 原子移动的落盘写（PDD §11.2 健壮性口径，配置与缓存共用）。
 */
public final class AtomicFiles {

	private AtomicFiles() {
	}

	public static void write(Path target, byte[] bytes) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		Files.write(tmp, bytes);
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}

package bid.yuanlu.mc.warehouse.util;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 临时文件 + 原子移动的落盘写（PDD §11.2 健壮性口径，配置与缓存共用）。
 * <p>
 * Windows 上目标文件可能被杀软/索引器等瞬时占用，{@code REPLACE_EXISTING} 的 move
 * 会抛 {@link AccessDeniedException}；对瞬时锁短暂重试（详见 MC-260962 同类问题）。
 */
public final class AtomicFiles {

	private static final int RETRIES = 5;
	private static final long RETRY_DELAY_MS = 50;

	private AtomicFiles() {
	}

	public static void write(Path target, byte[] bytes) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.write(tmp, bytes);
			moveWithRetry(tmp, target);
		} finally {
			// 成功时 tmp 已不存在，失败时清理残留
			Files.deleteIfExists(tmp);
		}
	}

	private static void moveWithRetry(Path from, Path target) throws IOException {
		AccessDeniedException last = null;
		for (int attempt = 0; attempt <= RETRIES; attempt++) {
			if (attempt > 0) {
				try {
					Thread.sleep(RETRY_DELAY_MS * attempt);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("move interrupted: " + target, e);
				}
			}
			try {
				Files.move(from, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				return;
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(from, target, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (AccessDeniedException e) {
				last = e;
			}
		}
		throw last;
	}
}

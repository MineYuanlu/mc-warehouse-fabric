package bid.yuanlu.mc.warehouse.ui.app.hud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.fabricmc.loader.api.FabricLoader;

/**
 * HUD 配置（UI-PDD §6.2）：每区块 {enabled, corner, offsetX, offsetY, order, maxLines}，
 * 持久化到 config/yuanlu-warehouse-hud.json。
 */
public final class HudConfig {

	public enum Block {
		WAREHOUSE("ui.wh.hud.block.warehouse"),
		STATE("ui.wh.hud.block.state"),
		PROGRESS("ui.wh.hud.block.progress"),
		SELECTION("ui.wh.hud.block.selection"),
		MARK("ui.wh.hud.block.mark"),
		REPORT("ui.wh.hud.block.report");

		public final String labelKey;

		Block(String labelKey) {
			this.labelKey = labelKey;
		}
	}

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}

	public static final class BlockConfig {
		public boolean enabled = true;
		public String corner = Corner.TOP_LEFT.name();
		public int offsetX = 4;
		public int offsetY = 4;
		public int order;
		public int maxLines = 8;

		public BlockConfig() {
		}

		public BlockConfig(boolean enabled, Corner corner, int offsetX, int offsetY, int order) {
			this.enabled = enabled;
			this.corner = corner.name();
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.order = order;
		}

		public Corner corner() {
			try {
				return Corner.valueOf(corner);
			} catch (IllegalArgumentException e) {
				return Corner.TOP_LEFT;
			}
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Logger LOG = LogUtils.getLogger();

	private final Map<Block, BlockConfig> blocks = new EnumMap<>(Block.class);

	public HudConfig() {
		int order = 0;
		for (Block b : Block.values()) {
			blocks.put(b, new BlockConfig(true, Corner.TOP_LEFT, 4, 4, order++));
		}
		// 默认无数据区块关闭
		blocks.get(Block.REPORT).enabled = true;
	}

	public BlockConfig get(Block b) {
		return blocks.get(b);
	}

	public void copyFrom(HudConfig other) {
		blocks.clear();
		for (var e : other.blocks.entrySet()) {
			var c = new BlockConfig();
			c.enabled = e.getValue().enabled;
			c.corner = e.getValue().corner;
			c.offsetX = e.getValue().offsetX;
			c.offsetY = e.getValue().offsetY;
			c.order = e.getValue().order;
			c.maxLines = e.getValue().maxLines;
			blocks.put(e.getKey(), c);
		}
	}

	// ---- 持久化 ----

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("yuanlu-warehouse-hud.json");
	}

	private static final class FileFormat {
		Map<String, BlockConfig> blocks = new java.util.LinkedHashMap<>();
	}

	@Nullable
	private static HudConfig loaded;

	public static HudConfig get() {
		if (loaded == null) {
			loaded = load();
		}
		return loaded;
	}

	public static void save(HudConfig config) {
		loaded = config;
		var fmt = new FileFormat();
		for (var e : config.blocks.entrySet()) {
			fmt.blocks.put(e.getKey().name(), e.getValue());
		}
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(fmt));
		} catch (IOException e) {
			LOG.warn("Failed to save hud config: {}", e.toString());
		}
	}

	private static HudConfig load() {
		HudConfig cfg = new HudConfig();
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return cfg;
		}
		try {
			FileFormat fmt = GSON.fromJson(Files.readString(f), FileFormat.class);
			if (fmt != null && fmt.blocks != null) {
				for (var e : fmt.blocks.entrySet()) {
					try {
						cfg.blocks.put(Block.valueOf(e.getKey()), e.getValue());
					} catch (IllegalArgumentException ignored) {
						// 未知区块（旧版本残留），忽略
					}
				}
			}
		} catch (Exception e) {
			LOG.warn("Failed to load hud config, using defaults: {}", e.toString());
		}
		return cfg;
	}
}

package bid.yuanlu.mcwarehouse.storage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bid.yuanlu.mcwarehouse.model.config.WorldConfig;
import net.minecraft.client.Minecraft;

public class WorldConfigStorage {

	private static final String CONFIG_FILE = "config" + File.separator + "worlds.json";

	private static WorldConfigStorage instance;

	private final Gson gson;
	private final File configFile;
	private WorldConfig cached;

	public WorldConfigStorage() {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.configFile = new File(
				Minecraft.getInstance().gameDirectory,
				"mc-warehouse" + File.separator + CONFIG_FILE
		);
	}

	public static WorldConfigStorage getInstance() {
		if (instance == null) {
			instance = new WorldConfigStorage();
		}
		return instance;
	}

	public WorldConfig load() {
		if (cached != null) return cached;
		if (!configFile.exists()) {
			cached = new WorldConfig();
			return cached;
		}
		try (FileReader reader = new FileReader(configFile)) {
			cached = gson.fromJson(reader, WorldConfig.class);
			if (cached == null) cached = new WorldConfig();
		} catch (IOException e) {
			cached = new WorldConfig();
		}
		return cached;
	}

	public void save(WorldConfig config) {
		File parent = configFile.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		try (FileWriter writer = new FileWriter(configFile)) {
			gson.toJson(config, writer);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save world config", e);
		}
		cached = config;
	}

	public int getInteractionSpeed() {
		WorldConfig config = load();
		if (config.sp != null) {
			for (WorldConfig.WorldEntry entry : config.sp.values()) {
				if (entry.interaction != null) {
					return entry.interaction.speed;
				}
			}
		}
		return 2;
	}
}

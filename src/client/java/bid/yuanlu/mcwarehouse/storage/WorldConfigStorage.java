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

	private final Gson gson;
	private final File configFile;

	public WorldConfigStorage() {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.configFile = new File(
				Minecraft.getInstance().gameDirectory,
				"mc-warehouse" + File.separator + CONFIG_FILE
		);
	}

	public WorldConfig load() {
		if (!configFile.exists()) {
			return new WorldConfig();
		}
		try (FileReader reader = new FileReader(configFile)) {
			return gson.fromJson(reader, WorldConfig.class);
		} catch (IOException e) {
			return new WorldConfig();
		}
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
	}
}

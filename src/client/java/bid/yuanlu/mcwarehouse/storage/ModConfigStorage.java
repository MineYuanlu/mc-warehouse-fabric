package bid.yuanlu.mcwarehouse.storage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bid.yuanlu.mcwarehouse.model.config.ModConfig;
import net.minecraft.client.Minecraft;

public class ModConfigStorage {

	private static final String CONFIG_FILE = "config" + File.separator + "mod.json";

	private final Gson gson;
	private final File configFile;
	private ModConfig cached;

	public ModConfigStorage() {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.configFile = new File(
				Minecraft.getInstance().gameDirectory,
				"mc-warehouse" + File.separator + CONFIG_FILE
		);
	}

	public ModConfig load() {
		if (cached != null) return cached;
		if (!configFile.exists()) {
			cached = new ModConfig();
			save(cached);
			return cached;
		}
		try (FileReader reader = new FileReader(configFile)) {
			cached = gson.fromJson(reader, ModConfig.class);
			if (cached == null) cached = new ModConfig();
		} catch (IOException e) {
			cached = new ModConfig();
		}
		return cached;
	}

	public void save(ModConfig config) {
		File parent = configFile.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		try (FileWriter writer = new FileWriter(configFile)) {
			gson.toJson(config, writer);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save mod config", e);
		}
		cached = config;
	}
}

package bid.yuanlu.mcwarehouse.storage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bid.yuanlu.mcwarehouse.model.config.PathfinderConfig;
import net.minecraft.client.Minecraft;

public class PathfinderDataStorage {

	private static final String PATHFINDER_DIR = "pathfinder";

	private final Gson gson;
	private final File rootDir;

	public PathfinderDataStorage() {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.rootDir = new File(Minecraft.getInstance().gameDirectory, "mc-warehouse" + File.separator + PATHFINDER_DIR);
	}

	@Nullable
	public PathfinderConfig load(String type, String name) {
		File file = getFile(type, name);
		if (!file.exists()) {
			return null;
		}
		try (FileReader reader = new FileReader(file)) {
			return gson.fromJson(reader, PathfinderConfig.class);
		} catch (IOException e) {
			return null;
		}
	}

	public void save(String type, String name, PathfinderConfig config) {
		File file = getFile(type, name);
		File parent = file.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		try (FileWriter writer = new FileWriter(file)) {
			gson.toJson(config, writer);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save pathfinder config", e);
		}
	}

	private File getFile(String type, String name) {
		return new File(rootDir, type + File.separator + name + ".json");
	}
}

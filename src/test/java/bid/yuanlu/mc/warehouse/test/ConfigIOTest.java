package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.container.IOType;
import bid.yuanlu.mc.warehouse.api.item.ContainerRule;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.api.warehouse.Warehouse;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.impl.quantity.GroupSelector;
import bid.yuanlu.mc.warehouse.impl.selector.CompositeSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;
import bid.yuanlu.mc.warehouse.impl.selector.TagSelector;

/**
 * 配置持久化（PDD §11）：多态 codec 往返、schemaVersion、冲突拒载、原子写、world 补全。
 */
public class ConfigIOTest extends McBootstrap {

	@TempDir
	Path tempDir;

	private ConfigIO io;

	@BeforeAll
	static void installCodecs() {
		TestCodecs.install();
	}

	@BeforeEach
	void setup() {
		io = new ConfigIO(tempDir.resolve("yuanlu-warehouse"));
	}

	private Warehouse sampleWarehouse() {
		Warehouse w = new Warehouse("main");
		w.setAnchor(new bid.yuanlu.mc.warehouse.api.world.WorldDim("singleplayer:New World", "minecraft:overworld"),
				new net.minecraft.core.BlockPos(0, 64, 0));

		ContainerInfo output = new ContainerInfo(IOType.OUTPUT);
		output.pos.add(new WorldDimPos(null, "minecraft:overworld", 10, -64, 20)); // 省略 world
		output.ruleMode = IOType.OUTPUT.defaultRuleMode();
		output.rules.add("ores");
		output.cacheType = bid.yuanlu.mc.warehouse.api.container.CacheType.DISK;
		output.priority = new bid.yuanlu.mc.warehouse.api.container.Priority(10, 5);
		output.label = "主存储箱-1";
		w.containers.add(output);

		ContainerRule ores = new ContainerRule("ores");
		ores.itemRules.add(new ItemRule(new TagSelector("minecraft:coal_ores"), false,
				new GroupSelector(1)));
		ores.itemRules.add(new ItemRule(
				new CompositeSelector(CompositeSelector.Op.OR, List.of(
						new IdSelector("minecraft:diamond"),
						CompositeSelector.not(new IdSelector("minecraft:dirt")))),
				true, new bid.yuanlu.mc.warehouse.impl.quantity.CountSelector(64)));
		w.rules.put("ores", ores);
		return w;
	}

	@Test
	void warehouseRoundTripPreservesModel() throws Exception {
		Warehouse w = sampleWarehouse();
		io.saveWarehouse(w);
		assertTrue(Files.isRegularFile(io.warehousesDir().resolve("main.json")));

		var result = io.loadAll();
		assertEquals(List.of(), result.errors(), () -> String.join("\n", result.errors()));
		assertEquals(1, result.warehouses().size());

		Warehouse loaded = result.warehouses().getFirst();
		assertEquals("main", loaded.id);
		assertEquals(new net.minecraft.core.BlockPos(0, 64, 0), loaded.anchorOf(
				new bid.yuanlu.mc.warehouse.api.world.WorldDim("singleplayer:New World", "minecraft:overworld")));

		ContainerInfo c = loaded.containers.getFirst();
		assertEquals(IOType.OUTPUT, c.ioType);
		var pos = c.pos.getFirst();
		assertTrue(pos.hasWorld(), "省略的 world 已按唯一 anchor 键补全");
		assertEquals("singleplayer:New World", pos.world());
		assertEquals(new WorldDimPos("singleplayer:New World", "minecraft:overworld", 10, -64, 20), pos,
				"存档保持相对坐标，仅补全 world");

		// 规则与选择器（多态）保真
		ContainerRule rule = loaded.rules.get("ores");
		assertEquals(2, rule.itemRules.size());
		ItemRule r0 = rule.itemRules.get(0);
		assertInstanceOf(TagSelector.class, r0.selector);
	}

	@Test
	void schemaVersionWrittenAndChecked() throws Exception {
		io.saveWarehouse(sampleWarehouse());
		Path file = io.warehousesDir().resolve("main.json");
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals(ConfigIO.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());

		root.remove("schemaVersion");
		Files.writeString(file, root.toString());
		var result = io.loadAll();
		assertEquals(0, result.warehouses().size());
		assertEquals(1, result.errors().size());
		assertTrue(result.errors().getFirst().contains("schemaVersion"));
	}

	@Test
	void corruptFileRejectedNotFatal() throws Exception {
		Files.createDirectories(io.warehousesDir());
		Files.writeString(io.warehousesDir().resolve("broken.json"), "{not valid json");
		var result = io.loadAll();
		assertTrue(result.warehouses().isEmpty());
		assertEquals(1, result.errors().size());
	}

	@Test
	void globalRuleIdConflictRejectsWarehouse() throws Exception {
		// 全局规则 ores
		ContainerRule global = new ContainerRule("ores");
		global.itemRules.add(new ItemRule(new IdSelector("minecraft:iron_ore"), true, null));
		io.saveGlobalRule(global);

		// 仓库内嵌同名规则 → 冲突拒载并列出冲突 id
		Warehouse w = sampleWarehouse(); // 内嵌 rules["ores"]
		io.saveWarehouse(w);

		var result = io.loadAll();
		assertTrue(result.warehouses().isEmpty());
		assertEquals(1, result.errors().size());
		assertTrue(result.errors().getFirst().contains("ores"),
				() -> "应列出冲突 id: " + result.errors().getFirst());
	}

	@Test
	void unlimitedQuantityOnOutputRejected() throws Exception {
		Warehouse w = new Warehouse("bad");
		w.setAnchor(new bid.yuanlu.mc.warehouse.api.world.WorldDim("sp:w", "minecraft:overworld"),
				net.minecraft.core.BlockPos.ZERO);
		ContainerInfo output = new ContainerInfo(IOType.OUTPUT);
		output.pos.add(new WorldDimPos("sp:w", "minecraft:overworld", 1, 0, 1));
		w.containers.add(output);
		ContainerRule r = new ContainerRule("all");
		r.itemRules.add(new ItemRule(new IdSelector("minecraft:dirt"), false, null)); // 不限量
		w.rules.put("all", r);
		output.rules.add("all");
		io.saveWarehouse(w);

		var result = io.loadAll();
		assertTrue(result.warehouses().isEmpty(), "D2：不限量×OUTPUT 必须拒载");
		assertTrue(result.errors().getFirst().contains("unlimited"),
				() -> result.errors().getFirst());
	}

	@Test
	void unknownReferencedRuleRejected() {
		Warehouse w = new Warehouse("w2");
		w.setAnchor(new bid.yuanlu.mc.warehouse.api.world.WorldDim("sp:w", "minecraft:overworld"),
				net.minecraft.core.BlockPos.ZERO);
		ContainerInfo c = new ContainerInfo(IOType.INPUT);
		c.pos.add(new WorldDimPos("sp:w", "minecraft:overworld", 1, 0, 1));
		c.rules.add("no_such_rule");
		w.containers.add(c);

		var errors = bid.yuanlu.mc.warehouse.core.config.ConfigValidator.validate(w, java.util.Set.of());
		assertEquals(1, errors.size());
		assertTrue(errors.getFirst().contains("no_such_rule"));
	}

	@Test
	void atomicWriteLeavesValidFileAndTmpCleaned() throws Exception {
		ModConfig config = new ModConfig();
		config.debug = true;
		io.saveModConfig(config);

		Path file = tempDir.resolve("yuanlu-warehouse").resolve("config.json");
		assertTrue(Files.isRegularFile(file));
		try (var stream = Files.list(file.getParent())) {
			assertEquals(0, stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count(),
					"原子移动后不应残留临时文件");
		}
		ModConfig loaded = io.loadModConfig();
		assertTrue(loaded.debug);
	}

	@Test
	void modConfigCorruptFallsBackToDefaults() throws Exception {
		Files.createDirectories(io.root());
		Files.writeString(io.root().resolve("config.json"), "{\"schemaVersion\":99}");
		ModConfig c = io.loadModConfig();
		assertFalse(c.debug);
		assertEquals(2, c.defaultInteractionSpeed);
	}

	@Test
	void sanitizeBlocksPathTraversal() {
		assertThrows(IllegalArgumentException.class, () -> io.saveWarehouse(new Warehouse("../evil")));
	}

	@Test
	void deleteWarehouseRemovesFile() {
		Warehouse w = sampleWarehouse();
		io.saveWarehouse(w);
		io.deleteWarehouse("main");
		assertTrue(io.loadAll().warehouses().isEmpty());
	}

}

package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bid.yuanlu.mc.warehouse.api.container.ContainerInfo;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;
import bid.yuanlu.mc.warehouse.core.config.ConfigIO;
import bid.yuanlu.mc.warehouse.core.warehouse.WarehouseManagerImpl;

/**
 * 仓库管理 CRUD + 激活态（PDD §3.1 v0.2：激活态仅内存持有）。
 */
public class WarehouseManagerTest extends McBootstrap {

	@TempDir
	Path tempDir;

	@BeforeAll
	static void installCodecs() {
		TestCodecs.install();
	}

	private WarehouseManagerImpl newManager() {
		return new WarehouseManagerImpl(new ConfigIO(tempDir.resolve("cfg-" + System.nanoTime())));
	}

	@Test
	void crudLifecycle() {
		var m = newManager();
		assertFalse(m.exists("a"));
		assertNull(m.get("a"));

		m.create("a");
		assertTrue(m.exists("a"));
		assertEquals(1, m.list().size());

		var created = m.get("a");
		created.setAnchor(new bid.yuanlu.mc.warehouse.api.world.WorldDim("sp:w", "minecraft:overworld"),
				net.minecraft.core.BlockPos.ZERO);
		var container = new ContainerInfo(bid.yuanlu.mc.warehouse.api.container.IOType.TEMP);
		container.pos.add(new bid.yuanlu.mc.warehouse.api.world.WorldDimPos("sp:w", "minecraft:overworld", 1, 0, 1));
		created.containers.add(container);
		m.save(created);

		assertThrows(IllegalArgumentException.class, () -> m.create("a"), "重复创建拒绝");
		assertThrows(IllegalArgumentException.class, () -> m.create("../evil"), "非法 id 拒绝");

		// 重载后变更仍在（持久化生效）
		m.reload();
		assertEquals(1, m.list().size());
		assertEquals(1, m.get("a").containers.size());
	}

	@Test
	void activationIsRuntimeOnly() {
		var m = newManager();
		m.create("a");
		m.create("b");

		assertNull(m.active(), "默认未激活");
		assertThrows(IllegalArgumentException.class, () -> m.activate("nope"));

		m.activate("a");
		assertEquals("a", m.active().id);
		assertEquals("a", m.activeId());

		m.activate(null);
		assertNull(m.active());

		m.activate("b");
		m.delete("b");
		assertNull(m.active(), "删除激活中的仓库应同时取消激活");

		// reload 后激活态不复活（未持久化）
		m.activate("a");
		m.reload();
		assertEquals("a", m.activeId());
	}

	@Test
	void reloadSurvivesRejectedFiles() throws Exception {
		var m = newManager();
		m.create("good");

		// 手工放一个会被拒载的仓库文件（不限量×OUTPUT，D2）
		Path bad = m.configIo().warehousesDir().resolve("bad.json");
		String body = """
				{
				  "schemaVersion": 1,
				  "id": "bad",
				  "anchors": {"sp:w": {"minecraft:overworld": {"x": 0, "y": 0, "z": 0}}},
				  "containers": [
				    {
				      "pos": [{"world": "sp:w", "dim": "minecraft:overworld", "x": 1, "y": 2, "z": 3}],
				      "ioType": "OUTPUT",
				      "rules": ["all"],
				      "cacheType": "NONE",
				      "priority": {"hard": 0, "soft": 0}
				    }
				  ],
				  "rules": {
				    "all": {"id": "all", "itemRules": [
				      {"selector": {"type": "id", "value": "minecraft:dirt"}, "negative": false}
				    ]}
				  }
				}
				""";
		Files.writeString(bad, body);

		m.reload();
		assertEquals(1, m.list().size());
		assertTrue(m.exists("good"));
		assertFalse(m.exists("bad"));
	}

	@Test
	void externalFileAppearsAfterReload() throws Exception {
		var io = new ConfigIO(tempDir.resolve("shared-cfg"));
		var writer = new WarehouseManagerImpl(io);
		writer.create("external");

		var reader = new WarehouseManagerImpl(io); // 独立实例共享目录
		assertTrue(reader.exists("external"));

		// 外部修改经 ConfigIO 写入后可被 manager.save 流程覆盖
		var w = reader.get("external");
		w.rules.put("r", ruleWithEntry());
		reader.save(w);
		reader.reload();
		assertEquals(1, reader.get("external").rules.get("r").itemRules.size());
	}

	private bid.yuanlu.mc.warehouse.api.item.ContainerRule ruleWithEntry() {
		var r = new bid.yuanlu.mc.warehouse.api.item.ContainerRule("r");
		r.itemRules.add(new ItemRule(new IdSelector("minecraft:iron_ingot"), false,
				new CountSelector(32)));
		return r;
	}
}

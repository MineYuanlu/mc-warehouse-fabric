package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.api.container.ContainerDetector;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.impl.container.BarrelDetector;
import bid.yuanlu.mc.warehouse.impl.container.BrewingStandDetector;
import bid.yuanlu.mc.warehouse.impl.container.ChestDetector;
import bid.yuanlu.mc.warehouse.impl.container.DispenserDropperDetector;
import bid.yuanlu.mc.warehouse.impl.container.EnderChestDetector;
import bid.yuanlu.mc.warehouse.impl.container.FurnaceDetector;
import bid.yuanlu.mc.warehouse.impl.container.HopperDetector;
import bid.yuanlu.mc.warehouse.impl.container.ShulkerBoxDetector;

/**
 * 内置检测器注册与槽位角色表（PDD §8.2/§8.5）。
 * 身份判定/扫描的端到端验证在 L8 gametest 里程碑覆盖。
 */
public class DetectorRegistryTest {

	@BeforeEach
	void reset() {
		WarehouseRegistryImpl.resetForTest();
	}

	@AfterEach
	void cleanup() {
		WarehouseRegistryImpl.resetForTest();
		TestCodecs.install();
	}

	@Test
	void builtinsRegisterWithUniqueIds() {
		var registry = WarehouseRegistryImpl.get();
		registry.registerDetector(new ChestDetector());
		registry.registerDetector(new BarrelDetector());
		registry.registerDetector(new ShulkerBoxDetector());
		registry.registerDetector(new EnderChestDetector());
		registry.registerDetector(new HopperDetector());
		registry.registerDetector(new DispenserDropperDetector());
		registry.registerDetector(FurnaceDetector.FURNACE);
		registry.registerDetector(FurnaceDetector.BLAST_FURNACE);
		registry.registerDetector(FurnaceDetector.SMOKER);
		registry.registerDetector(new BrewingStandDetector());

		assertEquals(10, WarehouseRegistryImpl.detectors().size());

		// 重复 id 快速失败
		assertThrows(IllegalArgumentException.class,
				() -> WarehouseRegistryImpl.get().registerDetector(new ChestDetector()));
	}

	@Test
	void onlyEnderChestIsPlayerScoped() {
		assertTrue(new EnderChestDetector().playerScoped());
		for (ContainerDetector d : new ContainerDetector[]{
				new ChestDetector(), new BarrelDetector(), new ShulkerBoxDetector(),
				new HopperDetector(), new DispenserDropperDetector(),
				FurnaceDetector.FURNACE, new BrewingStandDetector()}) {
			assertFalse(d.playerScoped(), d.id());
		}
	}

	@Test
	void furnaceRoleTable() throws Exception {
		Object detector = FurnaceDetector.FURNACE;
		Method roleFor = detector.getClass().getDeclaredMethod("roleFor", int.class, int.class);
		roleFor.setAccessible(true);

		Object input = roleFor.invoke(detector, 0, 3);
		Object fuel = roleFor.invoke(detector, 1, 3);
		Object output = roleFor.invoke(detector, 2, 3);

		assertEquals(bid.yuanlu.mc.warehouse.api.container.SlotRole.MACHINE_INPUT,
				((bid.yuanlu.mc.warehouse.api.container.SlotInfo) input).role());
		assertEquals(bid.yuanlu.mc.warehouse.api.container.SlotRole.MACHINE_FUEL,
				((bid.yuanlu.mc.warehouse.api.container.SlotInfo) fuel).role());
		assertEquals(bid.yuanlu.mc.warehouse.api.container.SlotRole.MACHINE_OUTPUT,
				((bid.yuanlu.mc.warehouse.api.container.SlotInfo) output).role());
		// §8.2：输出槽仅可取
		assertFalse(((bid.yuanlu.mc.warehouse.api.container.SlotInfo) output).canPutTo());
		assertTrue(((bid.yuanlu.mc.warehouse.api.container.SlotInfo) output).canTakeFrom());
	}

	@Test
	void brewingRoleTable() throws Exception {
		Object detector = new BrewingStandDetector();
		Method roleFor = detector.getClass().getDeclaredMethod("roleFor", int.class, int.class);
		roleFor.setAccessible(true);
		for (int i = 0; i < 3; i++) {
			assertEquals(bid.yuanlu.mc.warehouse.api.container.SlotRole.MACHINE_INPUT,
					((bid.yuanlu.mc.warehouse.api.container.SlotInfo) roleFor.invoke(detector, i, 5)).role(),
					"药水位 slot" + i);
		}
		assertEquals(bid.yuanlu.mc.warehouse.api.container.SlotRole.SPECIAL,
				((bid.yuanlu.mc.warehouse.api.container.SlotInfo) roleFor.invoke(detector, 3, 5)).role());
		assertEquals(bid.yuanlu.mc.warehouse.api.container.SlotRole.SPECIAL,
				((bid.yuanlu.mc.warehouse.api.container.SlotInfo) roleFor.invoke(detector, 4, 5)).role());
	}
}

package bid.yuanlu.mc.warehouse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import bid.yuanlu.mc.warehouse.core.registry.WarehouseRegistryImpl;
import bid.yuanlu.mc.warehouse.impl.allocator.FirstFitAllocator;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;
import bid.yuanlu.mc.warehouse.impl.selector.TagSelector;
import bid.yuanlu.mc.warehouse.impl.world.ServerPushedWorldIdentifier;

/**
 * 注册表语义（PDD §9.2）：重复 id 快速失败、冻结后拒绝注册、内置 codec 走同一入口。
 */
public class WarehouseRegistryTest {

	@BeforeEach
	void resetRegistry() {
		WarehouseRegistryImpl.resetForTest();
	}

	@AfterEach
	void cleanup() {
		WarehouseRegistryImpl.resetForTest();
	}

	private static bid.yuanlu.mc.warehouse.api.navigation.Navigator navigator(String id) {
		return new bid.yuanlu.mc.warehouse.api.navigation.Navigator() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public bid.yuanlu.mc.warehouse.api.navigation.PathResult start(bid.yuanlu.mc.warehouse.api.navigation.Goal goal) {
				return bid.yuanlu.mc.warehouse.api.navigation.PathResult.ok();
			}

			@Override
			public bid.yuanlu.mc.warehouse.api.navigation.PathStatus tick() {
				return bid.yuanlu.mc.warehouse.api.navigation.PathStatus.ARRIVED;
			}

			@Override
			public void cancel() {
			}
		};
	}

	@Test
	void duplicateCapabilityIdRejected() {
		var registry = WarehouseRegistryImpl.get();
		registry.registerNavigator(navigator("noop"));
		assertThrows(IllegalArgumentException.class, () -> registry.registerNavigator(navigator("noop")));
	}

	@Test
	void freezeRejectsRegistration() {
		var registry = WarehouseRegistryImpl.get();
		registry.registerSlotAllocator(new FirstFitAllocator());
		WarehouseRegistryImpl.freeze();
		assertTrue(WarehouseRegistryImpl.isFrozen());
		assertThrows(IllegalStateException.class, () -> registry.registerSlotAllocator(new FirstFitAllocator()));
		assertThrows(IllegalStateException.class, () -> registry.registerItemSelectorCodec(IdSelector.codec()));
	}

	@Test
	void builtinsRegisterViaSameEntrance() {
		var registry = WarehouseRegistryImpl.get();
		registry.registerWorldIdentifier(new ServerPushedWorldIdentifier());
		registry.registerSlotAllocator(new FirstFitAllocator());
		registry.registerItemSelectorCodec(IdSelector.codec());

		assertFalse(WarehouseRegistryImpl.worldIdentifiers().isEmpty());
		assertTrue(WarehouseRegistryImpl.slotAllocator("first_fit") instanceof FirstFitAllocator);
		assertEquals(1, WarehouseRegistryImpl.worldIdentifiers().stream()
				.filter(w -> w.id().equals("server_pushed")).count());
	}

	@Test
	void duplicateCodecTypeRejected() {
		var registry = WarehouseRegistryImpl.get();
		registry.registerQuantitySelectorCodec(CountSelector.codec());
		assertThrows(IllegalArgumentException.class,
				() -> SelectorCodecs.registerQuantity(CountSelector.codec()));
	}
}

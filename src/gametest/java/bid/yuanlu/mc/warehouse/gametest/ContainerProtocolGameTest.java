package bid.yuanlu.mc.warehouse.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;
import bid.yuanlu.mc.warehouse.core.config.ModConfig;
import bid.yuanlu.mc.warehouse.core.engine.container.ContainerProtocol;
import bid.yuanlu.mc.warehouse.core.engine.container.ContainerSession;
import bid.yuanlu.mc.warehouse.impl.container.ChestDetector;
import bid.yuanlu.mc.warehouse.impl.container.VanillaGuiInteraction;
import bid.yuanlu.mc.warehouse.util.McScreens;

/**
 * gametest①：容器打开协议（syncId 握手 / 身份校验）+ 点击对账 + 整堆取出（PDD §6/§13）。
 * <p>
 * 真实启动 MC → 单机世界 → 服务器侧放置并填充箱子 → 客户端协议层开箱 →
 * 断言扫描快照 → 执行 quickMove 步骤 → 断言物品经服务端对账后落入背包。
 */
@SuppressWarnings("UnstableApiUsage")
public class ContainerProtocolGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("yuanlu-warehouse/gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			GameTestUtil.waitForChunksRender(singleplayer);
			context.waitTicks(20);

			BlockPos chestPos = placeChestWithDiamonds(context, singleplayer);

			var ready = new AtomicBoolean(false);
			var stepsDone = new AtomicBoolean(false);
			var failure = new AtomicReference<ContainerSession.Failure>();
			var listener = new ContainerSession.Listener() {
				@Override
				public void onReady(ContainerSession s) {
					ready.set(true);
				}

				@Override
				public void onStepsCompleted(ContainerSession s) {
					stepsDone.set(true);
				}

				@Override
				public void onFailed(ContainerSession s, ContainerSession.Failure f) {
					failure.set(f);
				}
			};

			WorldDimPos pos = context.computeOnClient(mc -> new WorldDimPos(
					"gametest", mc.level.dimension().identifier().toString(),
					chestPos.getX(), chestPos.getY(), chestPos.getZ()));

			// 开箱流程：CLOSE→PRECHECK→FACE→OPEN→WAIT_SCREEN(syncId 门控)→VERIFY→READY
			context.runOnClient(mc -> ContainerProtocol.get()
					.open(pos, new ChestDetector(), new VanillaGuiInteraction(), new ModConfig(), listener, null));
			await(context, "ready", ready, failure, 200);

			// 扫描快照：slot0 = diamond×32（仅容器侧槽位）
			int scanned = context.computeOnClient(mc -> {
				ContainerSession s = ContainerProtocol.get().active();
				if (s == null || !s.handle().isOpen()) return -1;
				ContainerSnapshot snap = s.scanNow();
				if (snap == null) return -2;
				ItemStack st = snap.slots().get(0);
				return st == null ? -3 : st.getCount();
			});
			check(scanned == 32, "扫描快照应含 slot0=diamond×32，实际=" + scanned);

			// 执行整堆取出：QUICK_MOVE 槽位 0（逐击对账推进）
			context.runOnClient(mc -> {
				ContainerSession s = ContainerProtocol.get().active();
				s.execute(List.of(new ContainerSession.Step("quickMove#0",
						() -> new VanillaGuiInteraction().quickMoveToPlayer(s.handle(), 0), 0)));
			});
			await(context, "steps-done", stepsDone, failure, 200);

			// 对账后的结果：背包收到 32 钻石、箱子槽位已空（服务端权威状态）
			boolean gotItem = context.computeOnClient(mc -> {
				if (!(McScreens.current() instanceof AbstractContainerScreen<?> screen)) return false;
				boolean chestEmpty = !screen.getMenu().slots.isEmpty()
						&& screen.getMenu().slots.get(0).getItem().isEmpty();
				boolean inInventory = false;
				for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
					ItemStack st = mc.player.getInventory().getItem(i);
					if (st.is(Items.DIAMOND) && st.getCount() == 32) inInventory = true;
				}
				return chestEmpty && inInventory;
			});
			check(gotItem, "quickMove 应把 32 钻石移入玩家背包且箱子槽位清空");

			context.runOnClient(mc -> ContainerProtocol.get().cancel("test done"));
			context.waitTicks(10);

			// 异常路径：对空气坐标开箱 → PRECHECK CONTAINER_GONE
			var goneFailure = new AtomicReference<ContainerSession.Failure>();
			WorldDimPos airPos = new WorldDimPos("gametest", "minecraft:overworld",
					chestPos.getX() + 100, chestPos.getY(), chestPos.getZ());
			context.runOnClient(mc -> ContainerProtocol.get().open(airPos, new ChestDetector(),
					new VanillaGuiInteraction(), new ModConfig(), new ContainerSession.Listener() {
						@Override
						public void onFailed(ContainerSession s, ContainerSession.Failure f) {
							goneFailure.set(f);
						}
					}, null));
			for (int i = 0; i < 60 && goneFailure.get() == null; i++) {
				context.runOnClient(mc -> ContainerProtocol.get().tick());
				context.waitTick();
			}
			check(goneFailure.get() == ContainerSession.Failure.CONTAINER_GONE,
					"空气坐标应快速失败 CONTAINER_GONE，实际=" + goneFailure.get());

			LOGGER.info("yuanlu-warehouse L8 protocol gametest passed");
			try {
				context.takeScreenshot("yuanlu-warehouse-l8-protocol");
			} catch (Exception ignored) {
			}

			singleplayer.getServer().runOnServer(server -> server.halt(false));
		}
	}

	private static void check(boolean cond, String msg) {
		if (!cond) throw new AssertionError(msg);
	}

	private static BlockPos placeChestWithDiamonds(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		BlockPos chestPos = context.computeOnClient(mc -> {
			assert mc.player != null;
			return mc.player.blockPosition().offset(2, 0, 0);
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
			if (level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity container) {
				container.setItem(0, new ItemStack(Items.DIAMOND, 32));
			}
		});
		context.waitTicks(10);
		return chestPos;
	}

	private static void await(ClientGameTestContext context, String what, AtomicBoolean flag,
			AtomicReference<ContainerSession.Failure> failure, int maxTicks) {
		for (int i = 0; i < maxTicks; i++) {
			context.runOnClient(mc -> ContainerProtocol.get().tick());
			context.waitTick();
			if (flag.get()) return;
		}
		String detail = failure.get() != null ? ("failure=" + failure.get()) : "timeout";
		throw new AssertionError("await '" + what + "' failed: " + detail);
	}
}

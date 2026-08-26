package bid.yuanlu.mc.warehouse.core.engine.container;

import java.util.ArrayList;
import java.util.List;

import bid.yuanlu.mc.warehouse.api.interaction.ContainerHandle;
import bid.yuanlu.mc.warehouse.api.interaction.ContainerInteraction;

/**
 * 精确数量算法（PDD §6.2）：在协议层只实现一份，参数化于 {@link ContainerInteraction}
 * 原语——插件提供原语即免费获得双向精确搬运。
 * <p>
 * 全部算子经 Wurst7 MVP 真实运行验证；折半阈值 t = 初始 ≤4 ? 2 : 4。
 * 步骤按序执行，每步由会话逐击对账后才推进下一步。
 */
public final class ExactAmount {

	/**
	 * 存入方向：把背包槽位（menu 索引）上的 total 个物品存入配额 quota 个。
	 * 前置条件：total &gt; quota（否则走整堆 quickMove 快速路径，无需本算法）。
	 * <p>
	 * 不变式 R &lt; S 由前置条件与循环条件保证，不会过量存入。复杂度 O(log n) + O(R)。
	 */
	public static List<ContainerSession.Step> deposit(ContainerInteraction it, ContainerHandle h,
			int menuSlot, int total, int quota) {
		List<ContainerSession.Step> out = new ArrayList<>();
		int t = total <= 4 ? 2 : 4;
		int s = total;
		int moved = 0;
		boolean cursorEmpty = true;

		// ① 折半批量段：pickupHalf 拾起 ceil(S/2) 到光标，再对同槽 QUICK_MOVE 移入 floor(S/2)
		//   条件即 MVP 的 storeCount - moveCount >= allCount / 2（剩余配额能吃下整个半批）
		while (s > t && quota - moved >= s / 2) {
			if (!cursorEmpty) {
				out.add(new ContainerSession.Step("放回光标", () -> it.putBackHeld(h, menuSlot), menuSlot));
			}
			out.add(new ContainerSession.Step("拾起半组", () -> it.pickupHalf(h, menuSlot), menuSlot));
			out.add(new ContainerSession.Step("快速移入", () -> it.quickMoveToContainer(h, menuSlot), menuSlot));
			cursorEmpty = false;
			moved += s / 2;
			s -= s / 2;
		}

		// ② 单件补齐段：源槽自身充当暂存区——pickupAll 后 placeOne×R 再整批移入
		if (quota - moved > 0) {
			if (cursorEmpty) {
				out.add(new ContainerSession.Step("拾起整堆", () -> it.pickupAll(h, menuSlot), menuSlot));
				cursorEmpty = false;
			}
			for (; moved < quota; moved++) {
				out.add(new ContainerSession.Step("单件放入", () -> it.placeOne(h, menuSlot), menuSlot));
			}
			out.add(new ContainerSession.Step("批量移入", () -> it.quickMoveToContainer(h, menuSlot), menuSlot));
		}

		// ③ 归还段：光标剩余放回原槽
		if (!cursorEmpty) {
			out.add(new ContainerSession.Step("归还剩余", () -> it.putBackHeld(h, menuSlot), menuSlot));
		}
		return out;
	}

	/**
	 * 取出方向：从容器槽位 nowCount 个中取出 needCount 个到光标，
	 * 随后 {@code dragDistribute} 分发到目标背包槽位列表（menu 索引）。
	 * 前置条件：0 &lt; needCount &lt; nowCount（整槽取出走 quickMove 快速路径）。
	 */
	public static List<ContainerSession.Step> withdraw(ContainerInteraction it, ContainerHandle h,
			int menuSlot, int nowCount, int needCount, int[] distributeTargets) {
		if (distributeTargets == null || distributeTargets.length == 0) return List.of();

		List<ContainerSession.Step> out = new ArrayList<>();
		int halfNow = nowCount / 2 + (nowCount & 1); // ceil(now/2)
		int putBack;
		if (needCount <= halfNow) {
			out.add(new ContainerSession.Step("拾起 ceil(半组)", () -> it.pickupHalf(h, menuSlot), menuSlot));
			putBack = halfNow - needCount;
		} else {
			out.add(new ContainerSession.Step("拾起整堆", () -> it.pickupAll(h, menuSlot), menuSlot));
			putBack = nowCount - needCount;
		}
		for (int i = 0; i < putBack; i++) {
			out.add(new ContainerSession.Step("放回 1 个", () -> it.placeOne(h, menuSlot), menuSlot));
		}
		// 光标恰好持有 needCount → 多目标一次发包序列分发
		out.add(new ContainerSession.Step("dragDistribute×" + distributeTargets.length,
				() -> it.dragDistribute(h, distributeTargets), menuSlot));
		return out;
	}

	private ExactAmount() {
	}
}

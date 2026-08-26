package bid.yuanlu.mc.warehouse.core.engine.rule;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.item.ItemRule;

/**
 * 数量目标计算（PDD §3.6/§3.7）：delta = target - current 推导方向与数量。
 * <p>
 * 口径说明：current 只统计参与判定槽位（canTakeFrom || canPutTo）内的物品；
 * 不可参与的槽位（如机器纯输出格）内容不参与目标数学。
 */
public final class QuantityCalculator {

	public static final int UNLIMITED = Integer.MAX_VALUE;

	/** 该物品的目标总量；无命中规则时按 ruleMode 缺省（BLACKLIST=∞ / WHITELIST=0） */
	public static int targetAmount(RuleModeContext ctx, @Nullable ItemRule matched, int currentTotal, int maxStackSize) {
		if (matched == null) {
			return ctx.ruleMode() == RuleMode.BLACKLIST ? UNLIMITED : 0;
		}
		if (matched.isUnlimited()) return UNLIMITED;
		return Math.max(0, matched.quantity.computeTargetAmount(ctx.quantity(currentTotal, maxStackSize)));
	}

	/** 快照内按物品聚合的当前总量（isSameItemSameComponents 归组；保持首次出现顺序） */
	public static Map<ItemStack, Integer> aggregateByItem(ContainerSnapshot snapshot) {
		Map<ItemStack, Integer> out = new LinkedHashMap<>();
		snapshot.slots().forEach((slot, stack) -> {
			if (stack.isEmpty()) return;
			for (Map.Entry<ItemStack, Integer> e : out.entrySet()) {
				if (ItemStack.isSameItemSameComponents(e.getKey(), stack)) {
					e.setValue(e.getValue() + stack.getCount());
					return;
				}
			}
			out.put(stack.copyWithCount(1), stack.getCount());
		});
		return out;
	}

	private QuantityCalculator() {
	}
}

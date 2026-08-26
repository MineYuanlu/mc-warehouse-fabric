package bid.yuanlu.mc.warehouse.core.engine.rule;

import bid.yuanlu.mc.warehouse.api.container.ContainerSnapshot;
import bid.yuanlu.mc.warehouse.api.container.RuleMode;
import bid.yuanlu.mc.warehouse.api.item.QuantityContext;

/**
 * 规则计算的容器级上下文：槽位口径按「canTakeFrom || canPutTo」参与判定（PDD §3.6/§8.2）。
 *
 * @param ruleMode           容器生效的规则模式（IOType 默认或显式覆盖）
 * @param participatingSlots 参与判定的槽位总数
 * @param freeSlots          其中空槽数
 */
public record RuleModeContext(RuleMode ruleMode, int participatingSlots, int freeSlots) {

	public RuleModeContext {
		if (ruleMode == null) throw new IllegalArgumentException("ruleMode");
		if (participatingSlots < 0) throw new IllegalArgumentException("participatingSlots < 0");
		if (freeSlots < 0 || freeSlots > participatingSlots) throw new IllegalArgumentException("freeSlots out of range");
	}

	/** 从快照统计槽位口径（未知槽位回退 GENERIC，即双 true 参与） */
	public static RuleModeContext fromSnapshot(RuleMode mode, ContainerSnapshot snapshot) {
		int participating = 0;
		int free = 0;
		for (int slot = 0; slot < snapshot.slotCount(); slot++) {
			var info = snapshot.slotInfo(slot);
			if (!(info.canTakeFrom() || info.canPutTo())) continue;
			participating++;
			if (!snapshot.slots().containsKey(slot)) free++;
		}
		return new RuleModeContext(mode, participating, free);
	}

	/** 每物品的 QuantityContext（maxStackSize 因物品而异） */
	public QuantityContext quantity(int currentTotal, int maxStackSize) {
		return new QuantityContext(currentTotal, participatingSlots, freeSlots, maxStackSize);
	}
}

package bid.yuanlu.mc.warehouse.api.container;

import java.util.Objects;

/**
 * 单个槽位的能力描述，由 Detector.scan 产出（PDD §8.2）；默认全 GENERIC + 双 true。
 *
 * @param role        槽位角色
 * @param canTakeFrom 取出方向是否可用
 * @param canPutTo    放入方向是否可用
 */
public record SlotInfo(SlotRole role, boolean canTakeFrom, boolean canPutTo) {

	public static final SlotInfo GENERIC = new SlotInfo(SlotRole.GENERIC, true, true);

	public static SlotInfo of(SlotRole role, boolean canTakeFrom, boolean canPutTo) {
		return new SlotInfo(Objects.requireNonNull(role, "role"), canTakeFrom, canPutTo);
	}
}

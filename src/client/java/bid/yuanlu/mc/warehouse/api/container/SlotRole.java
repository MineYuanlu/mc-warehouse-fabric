package bid.yuanlu.mc.warehouse.api.container;

/**
 * 槽位角色（PDD §8.2）：机器类容器的槽位不是均质的。
 */
public enum SlotRole {
	GENERIC,
	MACHINE_INPUT,
	MACHINE_FUEL,
	MACHINE_OUTPUT,
	SPECIAL
}

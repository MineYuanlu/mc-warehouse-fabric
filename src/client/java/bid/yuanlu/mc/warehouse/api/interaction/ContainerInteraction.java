package bid.yuanlu.mc.warehouse.api.interaction;

/**
 * 交互方式 SPI（PDD §8.3）：打开/关闭/搬移动作的物理实现抽象。
 * <p>
 * 原版 GUI 点击只是默认实现——这是未来接入非 GUI 交互通道（如 AE 存储网络终端直连 API）的关键接口。
 * 精确数量算法在协议层只实现一份（PDD §6.2），参数化于本接口的原语——
 * 插件提供原语即可获得双向精确搬运能力。协议层的等待/对账/超时逻辑对一切实现复用不变。
 * <p>
 * 所有方法仅「发起」动作，成败由协议层的逐击对账判定（返回值表示是否已发起）。
 */
public interface ContainerInteraction {

	String id();

	/** 发起打开（异步；等待/校验由 §6.1 协议层负责，实现不关心时序） */
	void requestOpen(ContainerHandle handle);

	/** 发起关闭 */
	void requestClose(ContainerHandle handle);

	/** 整堆移出槽位 → 背包（QUICK_MOVE 快速路径） */
	boolean quickMoveToPlayer(ContainerHandle handle, int slot);

	/** 背包 → 整堆移入槽位（QUICK_MOVE 快速路径） */
	boolean quickMoveToContainer(ContainerHandle handle, int slot);

	// ---- v0.3 点击原语层：精确数量能力的物理基础，语义见 §6.2 ----

	/** 是否支持点击原语；false 时引擎降级为整堆粒度 */
	boolean supportsExactAmount();

	/** 左键拾起整堆到光标 */
	boolean pickupAll(ContainerHandle handle, int slot);

	/** 右键拾起半组（向上取整）到光标 */
	boolean pickupHalf(ContainerHandle handle, int slot);

	/** 右键从光标放 1 个入槽 */
	boolean placeOne(ContainerHandle handle, int slot);

	/** 左键把光标全部放回该槽 */
	boolean putBackHeld(ContainerHandle handle, int slot);

	/** QUICK_CRAFT 拖拽协议：把光标堆均分到多个槽位（一次发包序列） */
	boolean dragDistribute(ContainerHandle handle, int[] slots);
}

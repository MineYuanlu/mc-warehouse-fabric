package bid.yuanlu.mc.warehouse.api.transport;

import org.jetbrains.annotations.Nullable;

import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;

/**
 * 传输引擎（PDD §5/§12）：一阶段搬运的状态机控制面。
 * <p>
 * 命令层与未来 UI 只依赖本接口（与命令/UI 操作同一套 API 的解耦约定）；
 * 实例由客户端入口装配，经运行期服务句柄获取。
 * <p>
 * 一切方法仅客户端主线程调用；{@link #tick()} 每 tick 由外部驱动。
 */
public interface TransportEngine {

	/** 开始搬运（寻路器用配置默认） */
	void start();

	/**
	 * 开始搬运（PDD §10.1 {@code /wh start --pathfinder}）。
	 *
	 * @param pathfinderId 本次运行的一次性寻路器覆盖；null = 回配置默认
	 */
	void start(@Nullable String pathfinderId);

	/** 仅暂停（可 continue 无损恢复，不跳过容器） */
	void stop();

	/** 断点继续：出错容器标记 skip，继续当前轮次（仅 SUSPENDED 态有效） */
	void continueRun();

	/** 重头开始：重置状态与轮次标志，重新 ENTRY */
	void restart();

	/** 退出搬运（SUSPENDED 恢复选项③） */
	void abort();

	/** 每 tick 驱动（反模式红线：tick 单线程，无 worker/sleep） */
	void tick();

	boolean isRunning();

	@Nullable
	TransportState state();

	@Nullable
	RunReport lastReport();

	/** 最近一次结束/挂起的原因键（i18n） */
	@Nullable
	String lastDetailKey();

	/** 当前挂起/出错容器的坐标（诊断用）；无则 null */
	@Nullable
	WorldDimPos erroredPos();
}

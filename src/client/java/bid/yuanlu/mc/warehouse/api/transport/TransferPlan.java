package bid.yuanlu.mc.warehouse.api.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次搬运操作的计划（PDD §3.9）：非持久化数据模型，传输引擎运行时产生。
 * <p>
 * 阶段级语义（PDD §5.3）：每个阶段对应一个 TransferPlan，随容器逐个处理而累积 moves；
 * 执行以单容器会话为粒度滚动完成。用于事件上报、日志、调试预览与失败定位。
 */
public final class TransferPlan {

	private final PlanDirection direction;
	private final List<ItemMove> moves = new ArrayList<>();

	public TransferPlan(PlanDirection direction) {
		this.direction = java.util.Objects.requireNonNull(direction, "direction");
	}

	public PlanDirection direction() {
		return direction;
	}

	/** 只读视图 */
	public List<ItemMove> moves() {
		return Collections.unmodifiableList(moves);
	}

	public void append(ItemMove move) {
		moves.add(java.util.Objects.requireNonNull(move, "move"));
	}

	public int moveCount() {
		return moves.size();
	}

	/** 计划内移动的物品总数 */
	public int totalAmount() {
		return moves.stream().mapToInt(ItemMove::amount).sum();
	}

	public boolean isEmpty() {
		return moves.isEmpty();
	}

	@Override
	public String toString() {
		return "TransferPlan[" + direction + " x" + moves.size() + "]";
	}
}

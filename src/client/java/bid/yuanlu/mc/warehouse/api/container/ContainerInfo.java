package bid.yuanlu.mc.warehouse.api.container;

import java.util.ArrayList;
import java.util.List;

import bid.yuanlu.mc.warehouse.api.world.WorldDim;
import bid.yuanlu.mc.warehouse.api.world.WorldDimPos;

/**
 * 容器信息（PDD §3.2）。命令层可直接修改字段（改类型/关联规则等）。
 */
public class ContainerInfo {

	/** 多格坐标列表；pos[0] 为 canonical pos（缓存键与日志的主标识） */
	public final List<WorldDimPos> pos = new ArrayList<>();

	public IOType ioType = IOType.INPUT;

	/** null = 使用 {@link IOType#defaultRuleMode()} */
	public RuleMode ruleMode;

	/** 引用的 ContainerRule id（有序） */
	public final List<String> rules = new ArrayList<>();

	public CacheType cacheType = CacheType.MEMORY;

	public Priority priority = Priority.ZERO;

	/** 可选标签，用于高亮显示和日志 */
	public String label;

	public ContainerInfo() {
	}

	public ContainerInfo(IOType ioType) {
		this.ioType = ioType;
	}

	/** canonical pos（pos[0]）；空列表抛 IllegalStateException */
	public WorldDimPos canonicalPos() {
		if (pos.isEmpty()) throw new IllegalStateException("ContainerInfo has no pos");
		return pos.getFirst();
	}

	/** 生效的 ruleMode：显式设置优先，否则取 IOType 默认 */
	public RuleMode effectiveRuleMode() {
		return ruleMode != null ? ruleMode : ioType.defaultRuleMode();
	}
}

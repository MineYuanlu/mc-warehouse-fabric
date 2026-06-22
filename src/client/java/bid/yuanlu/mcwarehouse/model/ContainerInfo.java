package bid.yuanlu.mcwarehouse.model;

import java.util.List;

import net.minecraft.core.BlockPos;

public class ContainerInfo {

	public enum RuleMode {
		WHITELIST,
		BLACKLIST
	}

	public static RuleMode defaultMode(ContainerType type) {
		return switch (type) {
			case INPUT, TEMP -> RuleMode.BLACKLIST;
			case OUTPUT -> RuleMode.WHITELIST;
			case IGNORE -> RuleMode.BLACKLIST;
		};
	}

	public BlockPos relativePos;
	public ContainerType type;
	public RuleMode ruleMode;
	public List<String> rulesNames;
}

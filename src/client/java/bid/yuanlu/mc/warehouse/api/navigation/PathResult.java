package bid.yuanlu.mc.warehouse.api.navigation;

import org.jetbrains.annotations.Nullable;

/**
 * 寻路结果（PDD §7.1）。
 *
 * @param success    是否成功开始/完成
 * @param messageKey 给玩家的提示（i18n key）
 */
public record PathResult(boolean success, @Nullable String messageKey) {

	public static PathResult ok() {
		return new PathResult(true, null);
	}

	public static PathResult fail(String messageKey) {
		return new PathResult(false, messageKey);
	}
}

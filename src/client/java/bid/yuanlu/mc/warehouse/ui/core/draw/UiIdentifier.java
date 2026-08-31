package bid.yuanlu.mc.warehouse.ui.core.draw;

/**
 * L1 侧资源标识值类型（UI-PDD §4.1）——避免 L1 依赖 MC 资源类，L2 负责转换。
 */
public record UiIdentifier(String namespace, String path) {

	public static UiIdentifier of(String namespace, String path) {
		return new UiIdentifier(namespace, path);
	}

	public static UiIdentifier parse(String raw) {
		int i = raw.indexOf(':');
		if (i < 0) {
			return new UiIdentifier("minecraft", raw);
		}
		return new UiIdentifier(raw.substring(0, i), raw.substring(i + 1));
	}

	@Override
	public String toString() {
		return namespace + ":" + path;
	}
}

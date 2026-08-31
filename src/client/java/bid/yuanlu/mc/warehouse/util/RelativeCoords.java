package bid.yuanlu.mc.warehouse.util;

/**
 * "~"/"~-3"/"-12" 形式坐标 token 解析（命令与 UI 共用，等价原 CommandSupport.coord）：
 * {@code ~} 前缀 = 相对 origin，缺省偏移 0；无前缀按绝对整数解析。
 */
public final class RelativeCoords {

	/** 解析单轴 token；失败抛 NumberFormatException。 */
	public static int parse(String token, double origin) {
		if (token.startsWith("~")) {
			String off = token.substring(1);
			double delta = off.isEmpty() ? 0 : Double.parseDouble(off);
			return (int) Math.floor(origin + delta);
		}
		return Integer.parseInt(token);
	}

	private RelativeCoords() {
	}
}

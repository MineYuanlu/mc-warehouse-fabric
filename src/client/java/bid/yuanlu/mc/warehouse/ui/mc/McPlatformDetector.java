package bid.yuanlu.mc.warehouse.ui.mc;

/**
 * 版本 feature probe（UI-PDD §4.4）。以 client GUI 关键类的存在性判别平台，
 * MethodHandle/反射细节沿用 {@code util/McScreens} 的探测思想。
 */
public final class McPlatformDetector {

	private McPlatformDetector() {
	}

	/** MC 26.1：GuiGraphicsExtractor 提取式 GUI 管线。 */
	public static boolean hasMc261Gui() {
		return hasClass("net.minecraft.client.gui.GuiGraphicsExtractor");
	}

	private static boolean hasClass(String name) {
		try {
			Class.forName(name, false, McPlatformDetector.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}

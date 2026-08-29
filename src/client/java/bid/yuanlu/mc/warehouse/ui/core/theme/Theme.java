package bid.yuanlu.mc.warehouse.ui.core.theme;

/**
 * 主题 token（UI-PDD §3.5，用户决策：主题形式，首发 CreateDark）。
 * 颜色全部为 ARGB；元素绘制只读 token，不写死颜色。
 */
public record Theme(
		String id,
		int bgPanel, int bgPanelGradient,
		int border, int borderGradient,
		int textPrimary, int textMuted, int textAccent,
		int accent, int accentHover, int accentPressed,
		int success, int warning, int danger,
		int overlayScrim,
		int radius, int padding, int gap, int lineWidth) {

	/** Create 式程序化深色主题（BoxElement 思路：上下渐变面板 + 双色边框）。 */
	public static final Theme CREATE_DARK = new Theme(
			"create_dark",
			0xF0181A1E, 0xF0202227,
			0xFF303338, 0xFF3E4248,
			0xFFFFFFFF, 0xFF9AA0A6, 0xFF7FB2F0,
			0xFF3B6FD6, 0xFF4A82E8, 0xFF2E58A8,
			0xFF3FBF5A, 0xFFE0A83C, 0xFFE04B4B,
			0x90000000,
			6, 8, 6, 1);

	private static Theme active = CREATE_DARK;

	public static Theme active() {
		return active;
	}

	public static void set(Theme t) {
		active = t == null ? CREATE_DARK : t;
	}
}

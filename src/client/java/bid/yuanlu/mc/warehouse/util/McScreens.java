package bid.yuanlu.mc.warehouse.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 跨版本读取当前 Screen（26.1：{@code Minecraft.screen} 公有字段；
 * 26.2 起移入 {@code Minecraft.gui.screen()}——字段/方法均 public，经 MethodHandle
 * 静态探测缓存，零分配热路径）。
 */
public final class McScreens {

	private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

	@Nullable
	private static final Accessor ACCESSOR = probe();

	private McScreens() {
	}

	/** 当前打开的 Screen，无则 null；探测失败（未来版本再漂移）返回 null 并告警。 */
	@Nullable
	public static Screen current() {
		Accessor a = ACCESSOR;
		return a == null ? null : a.get(Minecraft.getInstance());
	}

	@Nullable
	private static Accessor probe() {
		MethodHandles.Lookup lk = MethodHandles.publicLookup();
		try {
			Field f = Minecraft.class.getField("screen");
			MethodHandle mh = lk.unreflectGetter(f);
			return mc -> {
				try {
					return (Screen) mh.invokeExact(mc);
				} catch (Throwable t) {
					return null;
				}
			};
		} catch (NoSuchFieldException | IllegalAccessException e) {
			// 26.2+：Minecraft.gui (public final) → Gui.screen()
			try {
				Field guiField = Minecraft.class.getField("gui");
				MethodHandle getGui = lk.unreflectGetter(guiField);
				MethodHandle getScreen = lk.unreflect(
						guiField.getType().getMethod("screen"));
				return mc -> {
					try {
						// invoke（非 invokeExact）：gui 静态类型为 Object，需 asType 自动转换
						Object gui = getGui.invoke(mc);
						return gui == null ? null : (Screen) getScreen.invoke(gui);
					} catch (Throwable t) {
						return null;
					}
				};
			} catch (ReflectiveOperationException | RuntimeException e2) {
				LOG.warn("McScreens: no known screen accessor on this MC version", e2);
				return null;
			}
		} catch (RuntimeException e) {
			LOG.warn("McScreens: no known screen accessor on this MC version", e);
			return null;
		}
	}

	private interface Accessor {
		@Nullable
		Screen get(Minecraft mc);
	}
}

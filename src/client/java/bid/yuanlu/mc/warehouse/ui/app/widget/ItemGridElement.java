package bid.yuanlu.mc.warehouse.ui.app.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import bid.yuanlu.mc.warehouse.ui.core.draw.UiDraw;
import bid.yuanlu.mc.warehouse.ui.core.event.UiEvent;
import bid.yuanlu.mc.warehouse.ui.core.element.UiElement;
import bid.yuanlu.mc.warehouse.ui.core.theme.Theme;

/**
 * 物品选择网格（规则编辑器的 id 填充源）：玩家背包 36 格快照，9 列原版槽位尺寸（18px）。
 * 点击非空槽回调 {@link #onPick}；悬停格 accent 描边 + tooltip（物品名 + 注册 id）。
 * 快照在构建时拍下（规则编辑不依赖实时背包，打开页面即刷新）。
 */
public final class ItemGridElement extends UiElement<ItemGridElement> {

	private static final int CELL = 18;
	private static final int COLS = 9;

	private final List<ItemStack> stacks = new ArrayList<>();
	private final Consumer<ItemStack> onPick;
	private int hoverCell = -1;

	public ItemGridElement(Consumer<ItemStack> onPick) {
		this.onPick = onPick;
		var player = Minecraft.getInstance().player;
		if (player != null) {
			stacks.addAll(player.getInventory().getNonEquipmentItems());
		}
		on(UiEvent.Type.CLICK, e -> {
			ItemStack s = stackAt(e.x, e.y);
			if (s != null) {
				onPick.accept(s);
				e.consume();
			}
		});
		on(UiEvent.Type.MOUSE_MOVE, e -> hoverCell = cellIndex(e.x, e.y));
		on(UiEvent.Type.LEAVE, e -> hoverCell = -1);
		// 悬停格 tooltip：物品名 + 注册 id（提取期只读 hoverCell，状态由 MOUSE_MOVE 事件更新）
		tooltip(() -> {
			if (hoverCell < 0 || hoverCell >= stacks.size()) {
				return List.of();
			}
			ItemStack s = stacks.get(hoverCell);
			if (s.isEmpty()) {
				return List.of();
			}
			return List.of(s.getHoverName(), Component.literal(registryId(s)));
		});
	}

	private int cellIndex(double absX, double absY) {
		int col = (int) ((absX - absX()) / CELL);
		int row = (int) ((absY - absY()) / CELL);
		if (col < 0 || col >= COLS || row < 0) {
			return -1;
		}
		int idx = row * COLS + col;
		return idx < stacks.size() ? idx : -1;
	}

	private ItemStack stackAt(double absX, double absY) {
		int idx = cellIndex(absX, absY);
		if (idx < 0) {
			return null;
		}
		ItemStack s = stacks.get(idx);
		return s.isEmpty() ? null : s;
	}

	/** 物品注册 id（如 minecraft:stone）；规则 id 选择器的填充值。 */
	public static String registryId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	@Override
	protected int onMeasureWidth(UiDraw g) {
		return COLS * CELL;
	}

	@Override
	protected int onMeasureHeight(UiDraw g) {
		int rows = (Math.max(1, stacks.size()) + COLS - 1) / COLS;
		return rows * CELL;
	}

	@Override
	protected void drawElement(UiDraw g) {
		var theme = Theme.active();
		for (int i = 0; i < stacks.size(); i++) {
			int cx = absX() + (i % COLS) * CELL;
			int cy = absY() + (i / COLS) * CELL;
			g.fill(cx, cy, cx + CELL, cy + CELL, 0x66000000);
			g.outline(cx, cy, CELL, CELL, theme.border());
			ItemStack s = stacks.get(i);
			if (s.isEmpty()) {
				continue;
			}
			g.itemIcon(s, cx + 1, cy + 1);
			g.itemDecorations(s, cx + 1, cy + 1);
		}
		if (hoverCell >= 0 && hoverCell < stacks.size()) {
			int cx = absX() + (hoverCell % COLS) * CELL;
			int cy = absY() + (hoverCell / COLS) * CELL;
			g.outline(cx, cy, CELL, CELL, theme.accent());
		}
	}
}

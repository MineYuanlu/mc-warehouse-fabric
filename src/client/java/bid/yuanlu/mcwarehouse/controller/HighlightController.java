package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import bid.yuanlu.mcwarehouse.engine.highlight.HighlightManager;
import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.storage.WarehouseStorage;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class HighlightController {

	private static final HighlightController INSTANCE = new HighlightController();

	private final WarehouseStorage storage;
	private boolean showing;
	private String showingName;
	private final List<BlockPos> highlighted;

	public static HighlightController getInstance() {
		return INSTANCE;
	}

	private HighlightController() {
		this.storage = new WarehouseStorage();
		this.showing = false;
		this.showingName = null;
		this.highlighted = new ArrayList<>();
	}

	public void showWarehouse(String name) {
		Warehouse w = storage.loadWarehouse(name);
		if (w == null) {
			return;
		}
		hideAll();
		showing = true;
		showingName = name;
		highlighted.clear();
		if (w.containers != null) {
			for (ContainerInfo info : w.containers) {
				highlighted.add(CoordinateUtils.toAbsolute(info.relativePos, w.anchor));
			}
		}
		HighlightManager.getInstance().setWarehouseHighlights(w);
	}

	public void hideAll() {
		showing = false;
		showingName = null;
		highlighted.clear();
		HighlightManager.getInstance().clearAll();
	}

	public boolean isShowing() {
		return showing;
	}
}

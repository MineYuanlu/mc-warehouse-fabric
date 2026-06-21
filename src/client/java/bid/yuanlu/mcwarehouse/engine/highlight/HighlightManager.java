package bid.yuanlu.mcwarehouse.engine.highlight;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.Warehouse;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class HighlightManager {

	private static final HighlightManager INSTANCE = new HighlightManager();

	private final Map<BlockPos, HighlightType> highlights = new HashMap<>();

	private HighlightManager() {
	}

	public static HighlightManager getInstance() {
		return INSTANCE;
	}

	public void addHighlight(BlockPos pos, HighlightType type) {
		this.highlights.put(pos, type);
	}

	public void setWarehouseHighlights(Warehouse warehouse) {
		this.highlights.clear();
		if (warehouse == null || warehouse.containers == null) return;

		for (ContainerInfo info : warehouse.containers) {
			BlockPos abs = CoordinateUtils.toAbsolute(info.relativePos, warehouse.anchor);
			HighlightType type = switch (info.type) {
				case INPUT -> HighlightType.INPUT_OUTLINED;
				case OUTPUT -> HighlightType.OUTPUT_OUTLINED;
				case RELAY -> HighlightType.RELAY_OUTLINED;
				case IGNORE -> HighlightType.IGNORE_OUTLINED;
			};
			this.highlights.put(abs, type);
		}
	}

	public void clearAll() {
		this.highlights.clear();
	}

	public void removeHighlight(BlockPos pos) {
		this.highlights.remove(pos);
	}

	public Map<BlockPos, HighlightType> getHighlights() {
		return this.highlights;
	}

	public enum HighlightType {
		INPUT_OUTLINED,
		OUTPUT_OUTLINED,
		RELAY_OUTLINED,
		IGNORE_OUTLINED,
		HAS_SPACE,
		FULL,
		UNKNOWN
	}
}

package bid.yuanlu.mcwarehouse.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;

import bid.yuanlu.mcwarehouse.model.ContainerInfo;
import bid.yuanlu.mcwarehouse.model.ContainerType;
import bid.yuanlu.mcwarehouse.util.CoordinateUtils;

public class SelectionController {

	private static final SelectionController INSTANCE = new SelectionController();

	private BlockPos pos1;
	private BlockPos pos2;

	public static SelectionController getInstance() {
		return INSTANCE;
	}

	public void setPos1(BlockPos pos) {
		this.pos1 = pos;
	}

	public void setPos2(BlockPos pos) {
		this.pos2 = pos;
	}

	public boolean hasSelection() {
		return pos1 != null && pos2 != null;
	}

	public Selection getSelection() {
		if (!hasSelection()) {
			return null;
		}
		int minX = Math.min(pos1.getX(), pos2.getX());
		int minY = Math.min(pos1.getY(), pos2.getY());
		int minZ = Math.min(pos1.getZ(), pos2.getZ());
		int maxX = Math.max(pos1.getX(), pos2.getX());
		int maxY = Math.max(pos1.getY(), pos2.getY());
		int maxZ = Math.max(pos1.getZ(), pos2.getZ());
		return new Selection(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
	}

	public void clear() {
		pos1 = null;
		pos2 = null;
	}

	public void expand(Direction direction, int amount) {
		if (!hasSelection()) {
			return;
		}
		BlockPos offset = BlockPos.ZERO.relative(direction, amount);
		if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
			switch (direction.getAxis()) {
				case X:
					if (pos1.getX() >= pos2.getX()) pos1 = pos1.offset(offset);
					else pos2 = pos2.offset(offset);
					break;
				case Y:
					if (pos1.getY() >= pos2.getY()) pos1 = pos1.offset(offset);
					else pos2 = pos2.offset(offset);
					break;
				case Z:
					if (pos1.getZ() >= pos2.getZ()) pos1 = pos1.offset(offset);
					else pos2 = pos2.offset(offset);
					break;
			}
		} else {
			switch (direction.getAxis()) {
				case X:
					if (pos1.getX() <= pos2.getX()) pos1 = pos1.offset(offset);
					else pos2 = pos2.offset(offset);
					break;
				case Y:
					if (pos1.getY() <= pos2.getY()) pos1 = pos1.offset(offset);
					else pos2 = pos2.offset(offset);
					break;
				case Z:
					if (pos1.getZ() <= pos2.getZ()) pos1 = pos1.offset(offset);
					else pos2 = pos2.offset(offset);
					break;
			}
		}
	}

	public Map<BlockPos, ContainerInfo> scanSelection(BlockPos anchor) {
		Map<BlockPos, ContainerInfo> result = new HashMap<>();
		if (!hasSelection()) {
			return result;
		}
		Selection sel = getSelection();
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return result;
		}
		for (BlockPos pos : BlockPos.betweenClosed(sel.min(), sel.max())) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof Container) {
				BlockPos immutable = pos.immutable();
				ContainerInfo info = new ContainerInfo();
				info.relativePos = CoordinateUtils.toRelative(immutable, anchor);
				info.type = ContainerType.INPUT;
				info.ruleMode = ContainerInfo.defaultMode(info.type);
				info.rulesNames = new ArrayList<>();
				result.put(immutable, info);
			}
		}
		return result;
	}

	public record Selection(BlockPos min, BlockPos max) {
	}
}

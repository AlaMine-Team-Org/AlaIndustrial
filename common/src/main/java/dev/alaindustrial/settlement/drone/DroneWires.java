package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.TownRoadBlock;
import dev.alaindustrial.core.energy.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wires beside a cell a drone changed quietly (owner's round 4 of 0.1.219, R4-10). A drone puts and takes blocks
 * without shape updates, so a fence of a template keeps the shape the template gives it; but then a town road or a
 * cable beside the cell never learns that a house inlet, a machine or a block of its line came or went: its arm toward
 * the cell is not drawn, and the line does not look for the new endpoint. This hands the change to every road and
 * cable touching the cell, as a placement with shape updates would, and to nothing else.
 */
final class DroneWires {

	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private DroneWires() {
	}

	/** The roads and cables round {@code pos} work their arms out again, and their line looks at its endpoints. */
	static void changed(ServerLevel level, BlockPos pos) {
		BlockState placed = level.getBlockState(pos);
		for (Direction dir : Direction.values()) {
			BlockPos at = pos.relative(dir);
			BlockState wire = level.getBlockState(at);
			if (!(wire.getBlock() instanceof TownRoadBlock || wire.getBlock() instanceof CableBlock)) {
				continue;
			}
			BlockState updated = wire.updateShape(level, level, at, dir.getOpposite(), pos, placed, level.getRandom());
			if (updated != wire) {
				level.setBlock(at, updated, FLAGS);
			}
			NetworkManager.onNeighbourChanged(level, at);
		}
	}
}

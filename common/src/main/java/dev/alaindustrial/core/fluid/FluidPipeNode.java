package dev.alaindustrial.core.fluid;

import dev.alaindustrial.core.item.PipeFaceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A segment of a fluid network, as the network core sees it (MOD-715, CORE-3): its live buffer, the mode
 * of each face and whether a face joins the segment to its neighbour. {@link FluidNetwork} and
 * {@link FluidNetworkManager} ask this interface and never a block or block-entity class.
 */
public interface FluidPipeNode {

	/** The fluid the segment is carrying right now; the line's throughput per hop (MOD-677). */
	FluidTank lineBuffer();

	/** The mode the player set on the face pointing in {@code side}. */
	PipeFaceMode faceMode(Direction side);

	/**
	 * Whether the face pointing in {@code side} joins this segment to what stands there — the pipe's own
	 * connection rule, the one its drawn arms follow.
	 */
	boolean connects(Direction side);

	/** The level the segment stands in, or {@code null} before it has one. */
	@Nullable
	Level getLevel();

	/** Where the segment stands. */
	BlockPos getBlockPos();
}

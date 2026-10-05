package dev.alaindustrial.core.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A segment of an item network, as the network core sees it (MOD-715, CORE-3): its grade, the mode of each
 * face and whether a face joins it to its neighbour. {@link ItemNetwork} and {@link ItemNetworkManager}
 * ask this interface and never a block or block-entity class.
 */
public interface ItemPipeNode {

	/** The segment's grade, or {@code null} when the block it stands in is not an item pipe (MOD-581). */
	@Nullable
	PipeTier tier();

	/** The mode the player set on the face pointing in {@code side}. */
	PipeFaceMode faceMode(Direction side);

	/** Whether the face pointing in {@code side} joins this segment to what stands there. */
	boolean connects(Direction side);

	/** The level the segment stands in, or {@code null} before it has one. */
	@Nullable
	Level getLevel();

	/** Where the segment stands. */
	BlockPos getBlockPos();
}

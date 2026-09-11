package dev.alaindustrial.block;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block a cable meets low, the way it meets a slab, but whose model stands back from the cell edge
 * (MOD-609). The cable drops its arm toward such a block exactly as toward a solar panel, and then
 * carries the dropped sleeve on past its own cell edge until it touches the housing.
 *
 * <p><b>Why the cable draws the rest of its own arm.</b> The gap has to be filled by something that
 * looks like the cable — a bare wire must stay bare, an insulated one insulated, and there are eight
 * grades. The neighbour cannot know which grade is plugged into it without a flag per grade and face,
 * and the cable cannot grow a longer arm in its blockstate without multiplying every grade's states by
 * sixteen (ADR-022). The cable's block-entity renderer already knows its grade and already draws
 * geometry on the cable, so it draws the continuation, using the arm's own texture and the block
 * shading the baked arm gets.
 *
 * <p>A non-empty answer also makes the neighbour count as low in {@link HalfBlockNeighbour}, so the
 * arm drops before it continues. Decided from the block state alone, like the rest of a cable's shape.
 */
public interface CableArmReach {

	/**
	 * How the dropped sleeve of a cable arm meeting this state continues past its cell edge to touch the
	 * model: bands stacked from the sleeve's bottom up, one after another, together covering its full
	 * height. Empty when this state is met at the edge like any other block.
	 */
	List<Band> cableArmReach(BlockState state);

	/**
	 * One slice of the continuation, in block pixels: from {@code bottom} to {@code top} high, carried
	 * {@code depth} past the cell edge — more than nothing and less than half a cell, which is what the
	 * renderer lays the arm's texture strips for.
	 *
	 * <p>Slices rather than one depth because a housing is rarely flat. Each slice ends on the surface
	 * in front of it and no further: a continuation run past a surface lays its sides in the plane of
	 * that surface's own sides, and two faces in one plane facing the same way flicker as the camera
	 * moves.
	 */
	record Band(float bottom, float top, float depth) {
	}
}

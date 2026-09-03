package dev.alaindustrial.block;

import dev.alaindustrial.core.item.PipeFaceRender;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The collision/outline geometry both pipes are made of. Item pipes and fluid pipes are deliberately
 * the same size — they read as siblings and are told apart by texture, not by profile — so the shape
 * lives here once instead of being copied into each block, where the two could quietly drift.
 *
 * <p><b>These boxes are the contract the JSON models must match.</b> MOD-195 is what happens when they
 * do not: the cable's low arms were implemented in Java for every grade but shipped as assets for one,
 * leaving zones that were drawn but not clickable and hitboxes with nothing drawn in them. No gate
 * catches that class of drift — a model changed here has to be changed in
 * {@code assets/alaindustrial/models/block/*_pipe_*.json} in the same commit.
 */
public final class PipeShapes {
	/** Pipes are slimmer than the 6px cable so a dense factory line stays readable. */
	public static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);

	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
	static {
		ARMS.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
		ARMS.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
		ARMS.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
		ARMS.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
		ARMS.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
		ARMS.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
	}

	/**
	 * Horizontal arms toward a half-block neighbour: the line turns down at the cell boundary and ends
	 * in a nozzle sitting level with the neighbour's own side face (MOD-540).
	 *
	 * <p>Three pieces, from the core outward: a bridge at line height, a short riser at the boundary,
	 * and a nozzle spanning Y=0..4 — the height of the block it joins. The nozzle is 6px wide and 4px
	 * deep against the pipe's 4px body, so it reads as a flange entering the neighbour rather than as
	 * more pipe — and its top face is the one surface of a dropped arm a player can actually see, which
	 * is where the extract/insert marking goes: the nozzle's mouth is buried in the neighbour.
	 *
	 * <p><b>Why not simply drop the sleeve.</b> The first cut ran one riser from Y=1 up to the core:
	 * geometrically it hugged both a 4px plate and an 8px slab, and in game it read as a leg standing
	 * on the floor — nine pixels of vertical pipe beside a four-pixel machine, with the mode head lost
	 * near its top. Matching the neighbour's own height is what makes the joint look like a joint.
	 * A second threshold for 8px neighbours was rejected with it: it would double the low values per
	 * face and give back the state count this encoding exists to save (ADR-022).
	 */
	private static final Map<Direction, VoxelShape> ARMS_LOW = new EnumMap<>(Direction.class);
	static {
		ARMS_LOW.put(Direction.NORTH, Shapes.or(
				Block.box(6, 6, 2, 10, 10, 6),
				Block.box(6, 4, 0, 10, 8, 3),
				Block.box(5, 0, 0, 11, 4, 4)));
		ARMS_LOW.put(Direction.SOUTH, Shapes.or(
				Block.box(6, 6, 10, 10, 10, 14),
				Block.box(6, 4, 13, 10, 8, 16),
				Block.box(5, 0, 12, 11, 4, 16)));
		ARMS_LOW.put(Direction.WEST, Shapes.or(
				Block.box(2, 6, 6, 6, 10, 10),
				Block.box(0, 4, 6, 3, 8, 10),
				Block.box(0, 0, 5, 4, 4, 11)));
		ARMS_LOW.put(Direction.EAST, Shapes.or(
				Block.box(10, 6, 6, 14, 10, 10),
				Block.box(13, 4, 6, 16, 8, 10),
				Block.box(12, 0, 5, 16, 4, 11)));
	}

	/**
	 * Every shape a pipe can have, one per geometry rather than one per blockstate (MOD-562). Both
	 * pipes share the table: they are deliberately the same size, and neither the routing mode a face
	 * shows nor the fluid pipe's {@code filled} flag is geometry.
	 */
	private static final FaceShapeTable TABLE = new FaceShapeTable(codes -> {
		VoxelShape result = CORE;
		// Direction.values() is the order both getShape loops walked (an EnumMap iterates by ordinal),
		// kept so the geometry is provably the shipped one — see FaceShapeTable.Assembler.
		for (Direction dir : Direction.values()) {
			int code = codes[dir.ordinal()];
			if (code != FaceShapeTable.NONE) {
				result = Shapes.or(result, arm(dir, code == FaceShapeTable.LOW));
			}
		}
		return result;
	});

	private PipeShapes() {
	}

	/**
	 * The collision/outline shape of a pipe whose six faces draw these values — a table read, not an
	 * assembly. Why that distinction is worth a class: {@link FaceShapeTable}.
	 */
	public static VoxelShape of(PipeFaceRender down, PipeFaceRender up, PipeFaceRender north,
			PipeFaceRender south, PipeFaceRender west, PipeFaceRender east) {
		return TABLE.get(code(down), code(up), code(north), code(south), code(west), code(east));
	}

	/** What one face contributes to the shape: nothing, its arm, or its dropped arm. */
	private static int code(PipeFaceRender render) {
		if (render == PipeFaceRender.DISABLED) {
			return FaceShapeTable.NONE;
		}
		return render.low() ? FaceShapeTable.LOW : FaceShapeTable.ARM;
	}

	/**
	 * The arm toward {@code dir}. {@code low} is honoured only on horizontal faces — the vertical ones
	 * have no low variant, and asking for one returns the plain arm rather than throwing, so a caller
	 * may pass the face's own flag without first testing the axis.
	 */
	public static VoxelShape arm(Direction dir, boolean low) {
		VoxelShape lowArm = low ? ARMS_LOW.get(dir) : null;
		return lowArm != null ? lowArm : ARMS.get(dir);
	}
}

package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The hover outline of an assembled Mirror Concentrator (MOD-803): one contour around all eight cells,
 * whichever cell the player aims at — the same answer the Distillation Column gives (MOD-778). The client
 * draws it through {@code ClientContentManifest.BLOCK_OUTLINES}.
 *
 * <p>An outline, never a shape: each cell's {@code getShape} stays its own, because a shape that left its
 * cell would catch the ray aimed at a neighbouring one. The contour is the union of the cells' selection
 * shapes, so it traces the housing, the mast and the collector bed and nothing around them. The mirror
 * wings are not in it, exactly as they are in no shape: they fold, and an outline that moved with them
 * would change under the crosshair.
 *
 * <p>Assembled once, at class init, one per pair (facing, cell under the crosshair) — thirty-two in all
 * (ADR-023); a frame only looks one up.
 */
public final class ConcentratorOutline {
	/** {@code [horizontal facing][hovered cell]} → the whole machine's contour relative to that cell. */
	private static final VoxelShape[][] CONTOURS = contours();

	private ConcentratorOutline() {
	}

	private static VoxelShape[][] contours() {
		VoxelShape[][] out = new VoxelShape[4][ConcentratorPart.CELLS.length];
		for (int data = 0; data < 4; data++) {
			Direction facing = Direction.from2DDataValue(data);
			for (int hovered = 0; hovered < ConcentratorPart.CELLS.length; hovered++) {
				Vec3i from = ConcentratorPart.CELLS[hovered].worldOffset(facing);
				VoxelShape machine = Shapes.empty();
				for (ConcentratorPart cell : ConcentratorPart.CELLS) {
					Vec3i at = cell.worldOffset(facing).subtract(from);
					machine = Shapes.or(machine, cell.shape(facing).move(at.getX(), at.getY(), at.getZ()));
				}
				out[data][hovered] = machine.optimize();
			}
		}
		return out;
	}

	/**
	 * The whole machine's contour relative to {@code pos}, or {@code null} — keep the block's own outline —
	 * when {@code state} is not a cell of an assembled machine (a lone panel, a loose section) or its core is
	 * no longer there to anchor the structure.
	 */
	public static @Nullable VoxelShape shape(BlockGetter level, BlockPos pos, BlockState state) {
		ConcentratorPart part = ConcentratorStructure.partOf(state);
		Direction facing = ConcentratorStructure.assembledFacing(state);
		if (part == null || facing == null) {
			return null;
		}
		BlockState core = level.getBlockState(pos.subtract(part.worldOffset(facing)));
		if (ConcentratorStructure.partOf(core) != ConcentratorPart.CORE
				|| ConcentratorStructure.assembledFacing(core) != facing) {
			return null;
		}
		return CONTOURS[facing.get2DDataValue()][part.ordinal() - ConcentratorPart.CORE.ordinal()];
	}
}

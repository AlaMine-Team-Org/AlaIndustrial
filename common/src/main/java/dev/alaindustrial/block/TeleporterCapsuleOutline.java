package dev.alaindustrial.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The hover outline of an assembled teleporter (MOD-803): one contour around the station and both capsule
 * cells, whichever of the three the player aims at — the answer the Distillation Column gives (MOD-778).
 * The client draws it through {@code ClientContentManifest.BLOCK_OUTLINES}.
 *
 * <p>An outline, never a shape: each block's {@code getShape} stays its own, so the crosshair still picks
 * the cell it is on, and a click on the glass still opens the door. The contour is the capsule's solid
 * silhouette — the station's ring, the glass barrel and the crown — so it does not draw the walls'
 * collision boxes as a cage of lines inside the glass.
 *
 * <p>Assembled once, at class init, one per pair (facing, block under the crosshair) — twelve in all
 * (ADR-023); a frame only looks one up.
 */
public final class TeleporterCapsuleOutline {
	/** The blocks of an assembled teleporter, bottom-up: station, middle cell, top cell. */
	private static final int STOREYS = 3;
	/** {@code [horizontal facing][storey under the crosshair]} → the silhouette relative to that storey. */
	private static final VoxelShape[][] CONTOURS = contours();

	private TeleporterCapsuleOutline() {
	}

	private static VoxelShape[][] contours() {
		Map<Direction, VoxelShape> turned = Shapes.rotateHorizontal(TeleporterCapsuleBlock.capsuleSilhouette());
		VoxelShape[][] out = new VoxelShape[4][STOREYS];
		for (int data = 0; data < 4; data++) {
			VoxelShape silhouette = turned.get(Direction.from2DDataValue(data));
			for (int storey = 0; storey < STOREYS; storey++) {
				out[data][storey] = silhouette.move(0, -storey, 0).optimize();
			}
		}
		return out;
	}

	/**
	 * The whole capsule's contour relative to {@code pos}, or {@code null} — keep the block's own outline —
	 * when {@code state} is not part of an assembled capsule.
	 */
	public static @Nullable VoxelShape shape(BlockGetter level, BlockPos pos, BlockState state) {
		BlockPos station;
		if (state.getBlock() instanceof TeleporterCapsuleBlock) {
			station = TeleporterCapsuleBlock.middlePos(state, pos).below();
		} else if (TeleporterBlock.isFormed(state)) {
			station = pos;
		} else {
			return null;
		}
		BlockState middle = level.getBlockState(station.above());
		BlockState top = level.getBlockState(station.above(2));
		if (!TeleporterBlock.isFormed(level.getBlockState(station))
				|| !(middle.getBlock() instanceof TeleporterCapsuleBlock)
				|| middle.getValue(TeleporterCapsuleBlock.PART) != TeleporterCapsuleBlock.Part.MIDDLE
				|| !(top.getBlock() instanceof TeleporterCapsuleBlock)
				|| top.getValue(TeleporterCapsuleBlock.PART) != TeleporterCapsuleBlock.Part.TOP) {
			return null;
		}
		Direction facing = middle.getValue(TeleporterCapsuleBlock.FACING);
		return CONTOURS[facing.get2DDataValue()][pos.getY() - station.getY()];
	}
}

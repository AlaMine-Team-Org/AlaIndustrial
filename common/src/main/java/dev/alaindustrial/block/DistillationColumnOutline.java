package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The hover outline of an assembled Distillation Column (MOD-778): one contour around every storey —
 * base, middle, top and, when one is installed, the Rectification Section — whichever storey the player
 * aims at. The client draws it through {@code ClientContentManifest.BLOCK_OUTLINES}.
 *
 * <p>An outline, never a shape: each storey's {@code getShape} stays its own block's, because a shape that
 * leaves its cell would catch the ray aimed at a neighbouring storey, and the player would break or click
 * a block they are not looking at.
 *
 * <p>The contours are assembled once, at class init, one per pair (storeys in the tower, storey under the
 * crosshair) — seven in all (ADR-023); a frame only looks one up.
 */
public final class DistillationColumnOutline {
	/** The storeys bottom-up: base, middle, top, section. */
	private static final VoxelShape[] STOREYS = {
			DistillationColumnBlock.SHAPE,
			DistillationColumnSegmentBlock.SHAPE,
			DistillationColumnSegmentBlock.SHAPE,
			RectificationSectionBlock.SHAPE};
	/** Index of the section in {@link #STOREYS}: the tower without it is everything below. */
	private static final int SECTION = STOREYS.length - 1;
	/** [section installed ? 1 : 0][storey under the crosshair, 0 = base] → the whole tower's contour. */
	private static final VoxelShape[][] CONTOURS = {contours(SECTION), contours(STOREYS.length)};

	private DistillationColumnOutline() {
	}

	private static VoxelShape[] contours(int storeys) {
		VoxelShape[] out = new VoxelShape[storeys];
		for (int hovered = 0; hovered < storeys; hovered++) {
			VoxelShape tower = Shapes.empty();
			for (int storey = 0; storey < storeys; storey++) {
				tower = Shapes.or(tower, STOREYS[storey].move(0, storey - hovered, 0));
			}
			out[hovered] = tower;
		}
		return out;
	}

	/**
	 * The whole tower's contour relative to {@code pos}, or {@code null} — keep the block's own outline —
	 * when {@code state} is not a storey of an assembled tower (a lone blank, a section with no tower).
	 */
	public static @Nullable VoxelShape shape(BlockGetter level, BlockPos pos, BlockState state) {
		BlockPos base = baseOf(level, pos, state);
		if (base == null || !assembled(level, base)) {
			return null;
		}
		boolean section = level.getBlockState(base.above(SECTION)).getBlock() instanceof RectificationSectionBlock;
		return CONTOURS[section ? 1 : 0][pos.getY() - base.getY()];
	}

	private static @Nullable BlockPos baseOf(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof DistillationColumnBlock) {
			return pos;
		}
		if (state.getBlock() instanceof DistillationColumnSegmentBlock segment) {
			return pos.below(segment.offsetToBase());
		}
		if (state.getBlock() instanceof RectificationSectionBlock) {
			return RectificationSectionBlock.basePos(level, pos);
		}
		return null;
	}

	/** A formed base with its middle and top segments in place. */
	private static boolean assembled(BlockGetter level, BlockPos base) {
		BlockState baseState = level.getBlockState(base);
		return baseState.getBlock() instanceof DistillationColumnBlock
				&& baseState.getValue(DistillationColumnBlock.FORMED)
				&& level.getBlockState(base.above()).getBlock() instanceof DistillationColumnMiddleBlock
				&& level.getBlockState(base.above(2)).getBlock() instanceof DistillationColumnTopBlock;
	}
}

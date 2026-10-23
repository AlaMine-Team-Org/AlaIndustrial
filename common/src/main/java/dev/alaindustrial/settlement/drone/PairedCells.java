package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildingPlan;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The two cells of a two-cell block — a door, a tall plant or flower, a bed (owner's round 2 of 0.1.219, R2-4): the
 * builder drone puts both at once, as a player's placement does, so a door never stands cut in half. The half that
 * pays for the item ({@link BuildMaterials#countOf}: the lower half, a bed's foot) is the one the drone flies to; the
 * other half comes with it and costs nothing, as the material list already counts it.
 */
public final class PairedCells {

	private PairedCells() {
	}

	/** Where the other half of a two-cell block standing as {@code state} at {@code pos} is, or empty. */
	public static Optional<BlockPos> partner(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return Optional.of(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
					? pos.above() : pos.below());
		}
		if (state.hasProperty(BlockStateProperties.BED_PART)
				&& state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			return Optional.of(state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? pos.relative(facing)
					: pos.relative(facing.getOpposite()));
		}
		return Optional.empty();
	}

	/**
	 * The plan cell holding the other half of cell {@code index}'s block, or empty: the plan wants the same block
	 * there, as the other half.
	 */
	public static OptionalInt partnerIndex(BuildingPlan plan, int index) {
		BlockState want = plan.expected(index);
		Optional<BlockPos> other = partner(plan.pos(index), want);
		if (other.isEmpty()) {
			return OptionalInt.empty();
		}
		int j = plan.indexOf(other.get());
		if (j < 0 || plan.debris(j) || !plan.expected(j).is(want.getBlock())
				|| !partner(plan.pos(j), plan.expected(j)).map(plan.pos(index)::equals).orElse(false)) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(j);
	}

	/** Whether cell {@code index}'s block is the half of a pair that the other half pays for. */
	public static boolean freeHalf(BuildingPlan plan, int index) {
		return partnerIndex(plan, index).isPresent() && BuildMaterials.countOf(plan.expected(index)) == 0;
	}

	/** Whether {@code world} at a partner cell may simply be built over: air, or a block a placement replaces. */
	public static boolean free(BlockState world) {
		return world.isAir() || world.canBeReplaced() && !world.hasBlockEntity();
	}
}

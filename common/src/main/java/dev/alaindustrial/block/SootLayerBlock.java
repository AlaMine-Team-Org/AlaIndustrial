package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The layer of soot a burnt-out oil fire leaves on a solid floor (MOD-638, placed by
 * {@link OilFireBlock#burnOut}). A thin ragged blot: five models, each in four turns, picked per
 * position by the blockstate — so a burnt pool is covered with blots that are alike but not identical.
 *
 * <p>Behaves like one layer of snow: nothing collides with it, it needs a sturdy top face under it and
 * vanishes with that floor, and it drops the {@code soot} item only to a shovel
 * ({@code requiresCorrectToolForDrops} + {@code #minecraft:mineable/shovel}). A fluid flowing in
 * washes it away without a drop (the loot table's {@code entity_properties} condition).
 */
public class SootLayerBlock extends Block {
	/**
	 * Outline for the crosshair only — there is no collision. 14×3×14 covers the thick middle of every
	 * blot in every turn; built once here, never inside {@link #getShape} (ADR-023).
	 */
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 3.0);

	public SootLayerBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !state.canSurvive(level, pos)
				? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.LAND || super.isPathfindable(state, type);
	}
}

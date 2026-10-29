package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Industrial light (MOD-795): a small four-pixel light that sits on any of the six faces and always glows
 * at the full light level. No menu, no power, no block entity — the light level is a constant in its
 * properties.
 *
 * <p>{@code FACING} is the clicked face, so the light lies against the surface it was placed on and its
 * glowing side points away from it: facing=up lies on a floor, facing=down hangs from a ceiling.
 *
 * <p><b>Logged with water or oil (MOD-865).</b> The light is a one-pixel panel, so it must not empty the
 * cell it sits in: placed into a pool, it takes the fluid into its cell instead of leaving an air pocket
 * that the surrounding water renders its side faces into. The wiring is the enriched uranium torch's
 * ({@link OilLoggedBlock}, MOD-250) — the same hole in an oil pool is closed by the same lines.
 */
public class IndustrialLightBlock extends DirectionalBlock implements OilLoggedBlock {

	/** Panel footprint, in pixels: four wide, one thick (owner, 2026-10-09: a third of the first 12 x 3). */
	private static final VoxelShape[] SHAPES = new VoxelShape[Direction.values().length];

	static {
		SHAPES[Direction.UP.ordinal()] = Block.box(6, 0, 6, 10, 1, 10);
		SHAPES[Direction.DOWN.ordinal()] = Block.box(6, 15, 6, 10, 16, 10);
		SHAPES[Direction.SOUTH.ordinal()] = Block.box(6, 6, 0, 10, 10, 1);
		SHAPES[Direction.NORTH.ordinal()] = Block.box(6, 6, 15, 10, 10, 16);
		SHAPES[Direction.EAST.ordinal()] = Block.box(0, 6, 6, 1, 10, 10);
		SHAPES[Direction.WEST.ordinal()] = Block.box(15, 6, 6, 16, 10, 10);
	}

	public IndustrialLightBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP)
				.setValue(BlockStateProperties.WATERLOGGED, false).setValue(OILLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, BlockStateProperties.WATERLOGGED, OILLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		BlockState facing = defaultBlockState().setValue(FACING, context.getClickedFace());
		return OilLoggedBlock.loggedForPlacement(facing, fluid);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return OilLoggedBlock.fluidState(state, super.getFluidState(state));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		OilLoggedBlock.scheduleFluidTick(state, level, ticks, pos);
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING).ordinal()];
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}

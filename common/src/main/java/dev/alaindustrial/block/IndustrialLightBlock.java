package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Industrial light (MOD-795): a small four-pixel light that sits on any of the six faces and always glows
 * at the full light level. No menu, no power, no block entity — the light level is a constant in its
 * properties.
 *
 * <p>{@code FACING} is the clicked face, so the light lies against the surface it was placed on and its
 * glowing side points away from it: facing=up lies on a floor, facing=down hangs from a ceiling.
 */
public class IndustrialLightBlock extends DirectionalBlock {

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
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
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

package dev.alaindustrial.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The rail beams (MOD-927), concrete and tempered iron plate: a full cube with rails on top, lying along X or
 * along Z. Both blocks share this class; only their properties and models differ.
 *
 * <p>It lies along the way the placer looks, never upright, so {@code RotatedPillarBlock} (which takes the
 * clicked face and allows Y) does not fit. A structure rotation by a quarter turn swaps the two axes.
 */
public class ConcreteRailBeamBlock extends Block {

	/** Which horizontal axis the rails run along. */
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	public ConcreteRailBeamBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AXIS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}
}

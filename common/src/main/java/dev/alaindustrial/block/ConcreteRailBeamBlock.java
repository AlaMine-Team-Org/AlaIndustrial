package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

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
		registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X)
				.setValue(QuarryFramePart.FORMED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AXIS, QuarryFramePart.FORMED);
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

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return QuarryFramePart.renderShape(state, super.getRenderShape(state));
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return QuarryFramePart.occlusion(state, super.getOcclusionShape(state));
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return QuarryFramePart.lightDampening(state, super.getLightDampening(state));
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return QuarryFramePart.skylight(state, super.propagatesSkylightDown(state));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return QuarryFramePart.shape(state, level, pos, super.getShape(state, level, pos, context));
	}

	/** A formed cell opens the frame's controller screen ({@link QuarryFramePart#openController}). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		return QuarryFramePart.openController(state, level, pos, player);
	}

	/** Metal underfoot once formed ({@link QuarryFramePart#sound}). */
	@Override
	protected SoundType getSoundType(BlockState state) {
		return QuarryFramePart.sound(state, super.getSoundType(state));
	}
}

package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Running Wheel (MOD-763): the wheel itself, placed in the middle of the frame. Loose it is a solid block that
 * faces the player like the drive (owner review 2: a loose wheel's axle is turned by how it was placed);
 * formed it stands where the occupant's head and body are, so it has no shape at all (D1) — the drive's
 * renderer draws the spinning wheel around that empty cell, and its own {@code facing} no longer counts.
 * Assembly takes a wheel in any facing.
 */
public class MobWheelRotorBlock extends MobWheelMemberBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** The loose wheel: the bounds of its model in any facing, short of a full cube; built once (ADR-023). */
	private static final VoxelShape LOOSE = Block.box(1, 1, 1, 15, 15, 15);

	public MobWheelRotorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(MobWheelStructure.FORMED, Boolean.FALSE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, MobWheelStructure.FORMED);
	}

	/** The same convention as the drive's loose state ({@link HorizontalMachineBlock}): its front faces the player. */
	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(MobWheelStructure.FORMED) ? Shapes.empty() : LOOSE;
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

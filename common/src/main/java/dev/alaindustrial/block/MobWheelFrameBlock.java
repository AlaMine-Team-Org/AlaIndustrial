package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mob Wheel Frame (MOD-763): a wooden post, eleven of which make the four corner columns of the wheel's
 * stand. No state of its own beyond {@link MobWheelStructure#FORMED}. Loose it is the slim post its model
 * shows; formed it is a full invisible wall of the wheel (D1).
 */
public class MobWheelFrameBlock extends MobWheelMemberBlock {
	public MobWheelFrameBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(MobWheelStructure.FORMED, Boolean.FALSE));
	}

	/** The loose post, matching its 4x4 column model; built once (ADR-023). */
	private static final VoxelShape POST = Block.box(6, 0, 6, 10, 16, 10);

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(MobWheelStructure.FORMED) ? Shapes.block() : POST;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MobWheelStructure.FORMED);
	}
}

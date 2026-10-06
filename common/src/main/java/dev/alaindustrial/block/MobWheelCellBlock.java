package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mob Wheel Cell (MOD-763): the invisible block assembly puts in every cell of the 3×3×3 box the player left
 * empty. It has no item and drops nothing; it exists so the wheel has walls, a running deck and a doorway
 * (see {@link MobWheelCellShape}) and so that losing any cell takes the structure apart like losing a part.
 */
public class MobWheelCellBlock extends MobWheelMemberBlock {
	public static final EnumProperty<MobWheelCellShape> SHAPE = EnumProperty.create("shape", MobWheelCellShape.class);

	public MobWheelCellBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SHAPE, MobWheelCellShape.SOLID));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(SHAPE).shape();
	}
}

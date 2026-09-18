package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * One plate of the lab plaque (MOD-513): a digit or a broken prefix letter engraved into slate.
 *
 * <p>Purely decorative, no block entity and no recipe. The engraving sits on the front face only, so
 * the block has a horizontal facing; extending {@link HorizontalDirectionalBlock} rather than adding
 * the property by hand is what makes a rotated lab template turn the plates with it.
 */
public class EngravedPlateBlock extends HorizontalDirectionalBlock {

	public static final MapCodec<EngravedPlateBlock> CODEC = simpleCodec(EngravedPlateBlock::new);

	public EngravedPlateBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}
}

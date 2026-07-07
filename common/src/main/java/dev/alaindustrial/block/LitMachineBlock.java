package dev.alaindustrial.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Processing machine that faces the player and shows an active ("on") model while working. Adds the
 * {@code lit} blockstate property on top of {@link HorizontalMachineBlock#FACING}, matching the
 * {@code facing=*,lit=*} model variants. The block entity flips {@code lit} via
 * {@link dev.alaindustrial.block.entity.MachineBlockEntity#updateLit(boolean)}.
 */
public abstract class LitMachineBlock extends HorizontalMachineBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	protected LitMachineBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT);
	}
}

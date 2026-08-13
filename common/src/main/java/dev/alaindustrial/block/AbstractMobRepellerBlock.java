package dev.alaindustrial.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Shared base of the Mob Repeller tier family (MOD-278): a full-cube guard-field machine with the
 * {@code lit} blockstate (field on/off — the block entity flips it via {@code updateLit}, the
 * manifest's {@code lightLevel} chain turns it into the soft glow) and the standard machine ticker.
 *
 * <p>Deliberately NOT {@link LitMachineBlock}: that base adds {@code FACING}, and a radial field
 * has no front — every face is equal, like the solar panel. The three tier blocks differ only in
 * which block entity they create (and their codec), mirroring the solar panel family layout.
 */
public abstract class AbstractMobRepellerBlock extends AbstractMachineBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	protected AbstractMobRepellerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}

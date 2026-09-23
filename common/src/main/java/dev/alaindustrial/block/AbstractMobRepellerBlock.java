package dev.alaindustrial.block;

import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
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
 *
 * <p>Pattern A sound (MOD-447): {@link #LIT} is {@link LitMachineBlock#LIT}, so the default {@code isWorking} applies.
 */
public abstract class AbstractMobRepellerBlock extends AbstractMachineBlock implements MachineHumProvider {
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
		// humMachineTicker: the plain machineTicker is null client-side, so the loop would never start.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.MOB_REPELLER_HUM;
	}
}

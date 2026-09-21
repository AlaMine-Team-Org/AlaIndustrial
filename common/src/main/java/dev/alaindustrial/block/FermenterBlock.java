package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fermenter block (MOD-146) — a full cube that faces the player and shows its "on" model while a
 * batch is brewing.
 *
 * <p>Voiced since MOD-447 (2026-09-18): the mod's hum loops are authored per machine through
 * {@link MachineHumProvider}, and this block now has its own — wet, airy compost bubbling, chosen
 * to stay clear of the polymerizer's thick tar-like boil.
 */
public class FermenterBlock extends LitMachineBlock implements MachineHumProvider {
	public FermenterBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FermenterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// humMachineTicker, not machineTicker: the plain ticker returns null client-side, which
		// is the entire difference between a voiced and a silent machine (MOD-447).
		return humMachineTicker(level);
	}

	// --- Loop hum (pattern A: lit-based, MOD-447) ---

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.FERMENTER_HUM;
	}
}

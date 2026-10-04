package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;

/**
 * Three-input LV machine that melts metals into alloys (MOD-064).
 *
 * <p>Audible while working since MOD-573: implements {@link MachineHumProvider} with its own induction
 * ring (pattern A, the vanilla {@code lit} blockstate). It used to stay silent "following the sawmill's
 * precedent" — but the sawmill was given a voice in MOD-447, which left this block as the last
 * processing machine nobody had reached rather than a deliberate quiet one.
 */
public class AlloySmelterBlock extends LitMachineBlock implements MachineHumProvider, HasMachineTooltip {
	public AlloySmelterBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AlloySmelterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker: drives the client loop off the vanilla lit blockstate (pattern A). MOD-573.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.ALLOY_SMELTER_HUM;
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return MachineTooltipSpec.processing(
				() -> MachineRates.euPerTick(ServerBalance.alloySmelterEuPerTick(),
						ServerBalance.globalMachineSpeedMultiplier()),
				() -> ServerBalance.scaledDuration(ServerBalance.alloySmelterDuration()),
				ServerBalance::machineBuffer);
	}
}

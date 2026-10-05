package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
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
 * The Polymerizer block (MOD-019) — a full cube that faces the player and shows its "on" model while an
 * operation runs. Audible while working since MOD-447: implements {@link MachineHumProvider} with the
 * polymerizer's own bubbling loop (pattern A, the vanilla {@code lit} blockstate).
 */
public class PolymerizerBlock extends LitMachineBlock implements MachineHumProvider, HasMachineTooltip {
	public PolymerizerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PolymerizerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker: drives the client loop off the vanilla lit blockstate (pattern A). MOD-447.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.POLYMERIZER_HUM;
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return MachineTooltipSpec.processing(ServerBalance::machineEuPerTickEffective,
				() -> ServerBalance.scaledDuration(ServerBalance.polymerizerDuration()),
				ServerBalance::machineBuffer);
	}
}

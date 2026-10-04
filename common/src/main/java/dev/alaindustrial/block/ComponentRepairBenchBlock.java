package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
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
import java.util.List;

/**
 * LV Component Repair Bench (MOD-384) — restores worn wind-mill rotors and water-mill wheels. Plain
 * {@link LitMachineBlock}: it faces the player and lights up while repairing, and all the behaviour
 * lives in {@link ComponentRepairBenchBlockEntity}.
 *
 * <p>Audible while working since MOD-447: implements {@link MachineHumProvider} with the bench's own
 * anvil-ring loop (pattern A, the vanilla {@code lit} blockstate).
 */
public class ComponentRepairBenchBlock extends LitMachineBlock implements MachineHumProvider, HasMachineTooltip {
	public ComponentRepairBenchBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ComponentRepairBenchBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker: drives the client loop off the vanilla lit blockstate (pattern A). MOD-447.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.COMPONENT_REPAIR_BENCH_HUM;
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		// Per-op cost is per GRADE (5000 / 10000 / 18000); the T1 figure stands in for all three.
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_input", ServerBalance::repairBenchEuPerTick),
						MachineTooltipSpec.stat("duration_ticks", () -> ServerBalance.scaledDuration(
								ServerBalance.repairBenchTier1EuCost()
										/ Math.max(1, ServerBalance.repairBenchEuPerTick())))),
				List.of(MachineTooltipSpec.stat("buffer", ServerBalance::machineBuffer),
						MachineTooltipSpec.stat("energy_per_op", ServerBalance::repairBenchTier1EuCost)));
	}
}

package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.core.environment.SolarSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;

/**
 * Moonlit Solar Panel — night-mirror of {@link SolarPanelBlock}. Passive LV generator driven by
 * {@link SolarSky#isMoonlitActive}. Half-block slab, hum while producing, EU from five faces (the
 * {@code UP} face is the working surface; see {@link AbstractSolarPanelBlock}).
 */
public class MoonlitSolarPanelBlock extends AbstractSolarPanelBlock implements HasMachineTooltip {
	public MoonlitSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MoonlitSolarPanelBlockEntity(pos, state);
	}

	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isMoonlitActive(level, pos);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_output_night_only", ServerBalance::moonlitEuPerTick),
						MachineTooltipSpec.stat("capacity", ServerBalance::solarBuffer)),
				List.of(MachineTooltipSpec.stat("solar_day", () -> 0),
						MachineTooltipSpec.stat("solar_night", ServerBalance::moonlitEuPerTick)));
	}
}

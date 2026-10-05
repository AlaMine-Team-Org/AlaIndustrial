package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
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
 * T1 solar panel — passive LV generator driven by {@link SolarSky#isDaylitActive}. Half-block slab,
 * hum while producing, EU from five faces (the {@code UP} face is the working surface; see
 * {@link AbstractSolarPanelBlock}).
 */
public class SolarPanelBlock extends AbstractSolarPanelBlock implements HasMachineTooltip {
	public SolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SolarPanelBlockEntity(pos, state);
	}

	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isDaylitActive(level, pos);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_output_day", ServerBalance::solarEuPerTick),
						MachineTooltipSpec.stat("capacity", ServerBalance::solarBuffer)),
				List.of(MachineTooltipSpec.stat("solar_day", ServerBalance::solarEuPerTick),
						MachineTooltipSpec.stat("solar_night", () -> 0),
						MachineTooltipSpec.text("tooltip.alaindustrial.solar_chip_hint",
								MachineTooltipSpec.Tone.DARK_GRAY)));
	}
}

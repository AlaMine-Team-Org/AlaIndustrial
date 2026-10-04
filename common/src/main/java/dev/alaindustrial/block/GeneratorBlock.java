package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.GeneratorBlockEntity;
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

public class GeneratorBlock extends LitMachineBlock implements MachineHumProvider, HasMachineTooltip {
	public GeneratorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GeneratorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.GENERATOR_HUM;
	}

	@Override
	public float humVolume() {
		return 0.4f;
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_output_fuel", ServerBalance::fuelEuPerTick),
						MachineTooltipSpec.stat("capacity", ServerBalance::generatorBuffer)),
				List.of());
	}
}

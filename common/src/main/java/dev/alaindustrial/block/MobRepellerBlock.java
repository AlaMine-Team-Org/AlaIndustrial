package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;

/** LV Mob Repeller (MOD-278) — see {@link AbstractMobRepellerBlock}. */
public class MobRepellerBlock extends AbstractMobRepellerBlock implements HasMachineTooltip {
	public MobRepellerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobRepellerBlockEntity(pos, state);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.stat("energy_input", ServerBalance::mobRepellerEuPerTick)),
				List.of(MachineTooltipSpec.stat("buffer", ServerBalance::mobRepellerBuffer),
						MachineTooltipSpec.plain("range", ServerBalance::mobRepellerRange)));
	}
}

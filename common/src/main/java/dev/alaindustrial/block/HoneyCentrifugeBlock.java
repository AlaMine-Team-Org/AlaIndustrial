package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.HoneyCentrifugeBlockEntity;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Honey Centrifuge block (MOD-924) — a full cube that faces the player and shows its "on" model, a
 * spinning drum on the front face, while an operation runs. Silent in this release: it has no hum loop
 * of its own yet, so it ticks with the plain machine ticker.
 */
public class HoneyCentrifugeBlock extends LitMachineBlock implements HasMachineTooltip {
	public HoneyCentrifugeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HoneyCentrifugeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/** Hover tooltip of this block's item (MOD-716, ADR-040). */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return MachineTooltipSpec.processing(ServerBalance::machineEuPerTickEffective,
				() -> ServerBalance.scaledDuration(ServerBalance.honeyCentrifugeDuration()),
				ServerBalance::machineBuffer);
	}
}

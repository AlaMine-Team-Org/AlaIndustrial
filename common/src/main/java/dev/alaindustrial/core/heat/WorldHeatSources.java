package dev.alaindustrial.core.heat;

import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Resolves the single block directly below a machine into the shared {@link HeatSource} model. */
public final class WorldHeatSources {
	private WorldHeatSources() {
	}

	public static HeatSource resolve(Level level, BlockPos machinePos) {
		BlockPos below = machinePos.below();
		BlockState state = level.getBlockState(below);
		if (state.is(ModContent.ELECTRIC_HEATER.get())
				&& level.getBlockEntity(below) instanceof ElectricHeaterBlockEntity heater
				&& heater.canSupplyHeatTick()) {
			return HeatSource.ELECTRIC_HEATER;
		}
		if (CampfireBlock.isLitCampfire(state)) {
			return HeatSource.CAMPFIRE;
		}
		if (state.is(Blocks.LAVA_CAULDRON)) {
			return HeatSource.LAVA_CAULDRON;
		}
		if (state.is(Blocks.MAGMA_BLOCK)) {
			return HeatSource.MAGMA;
		}
		if (level.getFluidState(below).is(FluidTags.LAVA)) {
			return HeatSource.LAVA;
		}
		return HeatSource.NONE;
	}

	/**
	 * Commits the powered-heater half of one useful Vulcanizer tick. Passive sources cost nothing.
	 * The resolver has already checked availability; a false return handles a same-tick competing draw.
	 */
	public static boolean consumeForProgress(Level level, BlockPos machinePos, HeatSource source) {
		return consumeForProgress(level, machinePos, source, 0);
	}

	/**
	 * As above, but the heated machine passes its own overclocker count (MOD-392) so the heater below
	 * charges the same multiple. The Electric Heater is not a {@code MenuProvider} and therefore has no
	 * upgrade panel of its own; without this it would keep billing one flat tick while the machine above
	 * finished the batch in fewer of them — making a sped-up vulcanizer pay LESS for heat overall.
	 */
	public static boolean consumeForProgress(Level level, BlockPos machinePos, HeatSource source,
			int overclockers) {
		if (source != HeatSource.ELECTRIC_HEATER) {
			return source.level() > 0;
		}
		return level.getBlockEntity(machinePos.below()) instanceof ElectricHeaterBlockEntity heater
				&& heater.consumeHeatTick(overclockers);
	}
}

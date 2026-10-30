package dev.alaindustrial.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Liquid honey (MOD-922) — the apiary's product made a fluid of the mod: a bucket, a pool, pipes and
 * tanks. It is the thickest liquid the mod has, and both halves of that are the owner's decision of
 * 2026-10-28: the pool spreads as slowly as overworld lava, and whatever wades into it is held back hard —
 * since 2026-10-30 four times slower than walking, a slow sink and a laboured climb out
 * ({@link FluidImmersion#HONEY}).
 *
 * <p>Harmless otherwise, so the rest comes from {@link DistillateFluid} unchanged: no burning, no
 * drowning, a three-cell reach (slope 2, drop-off 2) and never an infinite source — a spilled bucket
 * cannot be multiplied, which matters for a fluid the mod measures by the bottle.
 */
public abstract class HoneyFluid extends DistillateFluid {

	/** Overworld lava's spread rate — the owner asked for honey to flow "like lava". Water's is 5. */
	public static final int TICK_DELAY = 30;

	@Override
	public Fluid getFlowing() {
		return ModContent.FLOWING_HONEY.get();
	}

	@Override
	public Fluid getSource() {
		return ModContent.HONEY.get();
	}

	@Override
	public Item getBucket() {
		return ModContent.HONEY_BUCKET.get();
	}

	@Override
	public BlockState createLegacyBlock(FluidState fluidState) {
		return ModContent.HONEY_BLOCK.get().defaultBlockState()
				.setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == ModContent.HONEY.get() || other == ModContent.FLOWING_HONEY.get();
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return TICK_DELAY;
	}

	/** The flowing variant ({@code alaindustrial:flowing_honey}) — carries LEVEL, never a source. */
	public static class Flowing extends HoneyFluid {
		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getAmount(FluidState fluidState) {
			return fluidState.getValue(LEVEL);
		}

		@Override
		public boolean isSource(FluidState fluidState) {
			return false;
		}
	}

	/** The source variant ({@code alaindustrial:honey}) — always full, the bucket-visible fluid. */
	public static class Source extends HoneyFluid {
		@Override
		public int getAmount(FluidState fluidState) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState fluidState) {
			return true;
		}
	}
}

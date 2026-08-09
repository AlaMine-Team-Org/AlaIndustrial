package dev.alaindustrial.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Fuel oil (MOD-251) — the heavy residue of distillation: 200 mB out of every 1000 mB of crude.
 * Thick dark-brown, water-like physics (see {@link DistillateFluid}); burns at half diesel's rate
 * (6 EU/t) in the liquid fuel generator (MOD-261) — more hassle per bucket, less return, exactly
 * what a bottom fraction should feel like.
 */
public abstract class FuelOilFluid extends DistillateFluid {

	@Override
	public Fluid getFlowing() {
		return ModContent.FLOWING_FUEL_OIL.get();
	}

	@Override
	public Fluid getSource() {
		return ModContent.FUEL_OIL.get();
	}

	@Override
	public Item getBucket() {
		return ModContent.FUEL_OIL_BUCKET.get();
	}

	@Override
	public BlockState createLegacyBlock(FluidState fluidState) {
		return ModContent.FUEL_OIL_BLOCK.get().defaultBlockState()
				.setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == ModContent.FUEL_OIL.get() || other == ModContent.FLOWING_FUEL_OIL.get();
	}

	/** The flowing variant ({@code alaindustrial:flowing_fuel_oil}) — carries LEVEL, never a source. */
	public static class Flowing extends FuelOilFluid {
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

	/** The source variant ({@code alaindustrial:fuel_oil}) — always full, the bucket-visible fluid. */
	public static class Source extends FuelOilFluid {
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

package dev.alaindustrial.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Diesel (MOD-251) — the target fraction of the distillation column: 700 mB out of every 1000 mB of
 * crude. Golden-yellow, water-like physics (see {@link DistillateFluid}); its energy value
 * (12 EU/t, one full copper cable) is realised by the liquid fuel generator (MOD-261).
 */
public abstract class DieselFluid extends DistillateFluid {

	@Override
	public Fluid getFlowing() {
		return ModContent.FLOWING_DIESEL.get();
	}

	@Override
	public Fluid getSource() {
		return ModContent.DIESEL.get();
	}

	@Override
	public Item getBucket() {
		return ModContent.DIESEL_BUCKET.get();
	}

	@Override
	public BlockState createLegacyBlock(FluidState fluidState) {
		return ModContent.DIESEL_BLOCK.get().defaultBlockState()
				.setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == ModContent.DIESEL.get() || other == ModContent.FLOWING_DIESEL.get();
	}

	/** The flowing variant ({@code alaindustrial:flowing_diesel}) — carries LEVEL, never a source. */
	public static class Flowing extends DieselFluid {
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

	/** The source variant ({@code alaindustrial:diesel}) — always full, the bucket-visible fluid. */
	public static class Source extends DieselFluid {
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

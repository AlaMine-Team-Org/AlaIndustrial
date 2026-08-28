package dev.alaindustrial.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Biofuel (MOD-146) — what the fermenter brews out of organic waste and water. Olive-green and
 * thin, it is the mod's first fuel that is farmed rather than pumped.
 *
 * <p><b>Nothing burns it yet.</b> Its only consumer today is the distillation column, which cracks
 * it into nutrient solution for the sprinkler. It carries {@code c:combustion_fuels} so a fuel-
 * burning machine — this mod's planned semifluid generator, or another mod's — picks it up the day
 * one exists, but no class in this mod reads that tag at present. Saying otherwise here would be a
 * promise the game does not keep.
 *
 * <p>Physics come from {@link DistillateFluid} unchanged — it is a refined liquid poured between
 * tanks, so it behaves like the two oil fractions rather than like crude.
 */
public abstract class BiofuelFluid extends DistillateFluid {

	@Override
	public Fluid getFlowing() {
		return ModContent.FLOWING_BIOFUEL.get();
	}

	@Override
	public Fluid getSource() {
		return ModContent.BIOFUEL.get();
	}

	@Override
	public Item getBucket() {
		return ModContent.BIOFUEL_BUCKET.get();
	}

	@Override
	public BlockState createLegacyBlock(FluidState fluidState) {
		return ModContent.BIOFUEL_BLOCK.get().defaultBlockState()
				.setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == ModContent.BIOFUEL.get() || other == ModContent.FLOWING_BIOFUEL.get();
	}

	/** The flowing variant ({@code alaindustrial:flowing_biofuel}) — carries LEVEL, never a source. */
	public static class Flowing extends BiofuelFluid {
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

	/** The source variant ({@code alaindustrial:biofuel}) — always full, the bucket-visible fluid. */
	public static class Source extends BiofuelFluid {
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

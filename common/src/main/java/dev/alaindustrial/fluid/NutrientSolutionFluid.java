package dev.alaindustrial.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Nutrient solution (MOD-525) — biofuel run through the distillation column a second time, ending
 * the organic chain the fermenter starts. Bright green and the thinnest liquid the mod makes; the
 * sprinkler sprays it over farmland and into a crystal greenhouse.
 *
 * <p>It is the one mod fluid that is not a fuel: nothing burns it, and its only consumer is the
 * sprinkler. Physics still come from {@link DistillateFluid} — a machine product poured between
 * tanks, harmless to stand in.
 */
public abstract class NutrientSolutionFluid extends DistillateFluid {

	@Override
	public Fluid getFlowing() {
		return ModContent.FLOWING_NUTRIENT_SOLUTION.get();
	}

	@Override
	public Fluid getSource() {
		return ModContent.NUTRIENT_SOLUTION.get();
	}

	@Override
	public Item getBucket() {
		return ModContent.NUTRIENT_SOLUTION_BUCKET.get();
	}

	@Override
	public BlockState createLegacyBlock(FluidState fluidState) {
		return ModContent.NUTRIENT_SOLUTION_BLOCK.get().defaultBlockState()
				.setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == ModContent.NUTRIENT_SOLUTION.get()
				|| other == ModContent.FLOWING_NUTRIENT_SOLUTION.get();
	}

	/** The flowing variant ({@code alaindustrial:flowing_nutrient_solution}) — never a source. */
	public static class Flowing extends NutrientSolutionFluid {
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

	/** The source variant ({@code alaindustrial:nutrient_solution}) — the bucket-visible fluid. */
	public static class Source extends NutrientSolutionFluid {
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

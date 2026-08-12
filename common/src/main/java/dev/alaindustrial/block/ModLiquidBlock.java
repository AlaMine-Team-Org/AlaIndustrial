package dev.alaindustrial.block;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * A plain vanilla liquid block, constructible from {@code common} (MOD-403).
 *
 * <p>It adds no behaviour whatsoever, and that is the entire point. {@link LiquidBlock}'s constructor
 * is {@code protected}, so it can only be reached from a subclass or from its own package. The two
 * loader subprojects compile against a Minecraft jar whose access modifiers have been widened, so
 * {@code new LiquidBlock(...)} worked there — which is why the distillation fractions were built
 * inline in each loader's registry. {@code common} compiles against the un-widened jar, so moving
 * that construction into the shared manifest needs this three-line subclass.
 *
 * <p>Subclassing is deliberately preferred over widening access in {@code common}: the block class is
 * not part of the save format (a world stores the registry id and the blockstate, never the Java
 * type), so this is invisible in game, and it keeps {@code common} compiling against stock Minecraft
 * — which is what lets the same code run on both loaders in the first place.
 *
 * <p>Oil has its own {@link OilLiquidBlock} because it carries real behaviour (it burns). The
 * distillation fractions are water-like machine products with no world hazard, so they need nothing
 * beyond this.
 */
public class ModLiquidBlock extends LiquidBlock {

	public ModLiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
		super(fluid, properties);
	}
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.fluid.FluidTankVisuals;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/**
 * The fluid colour table names every fluid of the mod (MOD-837), on both loaders.
 *
 * <p>None of the mod's fluid models declares a tint, so the pipe core, the portable tank's icon and its fill
 * bar all take a fluid's colour from {@link FluidTankVisuals}. A fluid missing from its table falls to the
 * map colour of its block, and a fluid with no block to an id-hash hue: liquid experience, missing there,
 * showed red in a pipe while the tank beside it showed its green sprite.
 */
public final class FluidColourScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidColourScenarios::everyModFluidHasANamedColour,
						"fluid_colour_names_every_mod_fluid"));

		private Roster() {}
	}

	private FluidColourScenarios() {}

	/**
	 * @implements MOD-837-COL01 — every registered fluid of the mod, still and flowing, has a colour of its own
	 *     in the fluid colour table, so no pipe or tank icon of it falls to the arbitrary id-hash hue
	 */
	public static void everyModFluidHasANamedColour(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		int checked = 0;
		for (Fluid fluid : BuiltInRegistries.FLUID) {
			Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			checked++;
			if (FluidTankVisuals.namedColor(fluid).isEmpty()) {
				missing.add(id.toString());
			}
		}
		helper.assertTrue(checked > 0, "no fluid of the mod is registered");
		helper.assertTrue(missing.isEmpty(), "fluids with no colour in FluidTankVisuals: " + missing);
		helper.succeed();
	}
}

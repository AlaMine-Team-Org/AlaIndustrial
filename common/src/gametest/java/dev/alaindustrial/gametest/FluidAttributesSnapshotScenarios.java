package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/**
 * L2 characterization of the fluid attributes a loader's own fluid API reports for the mod's fluids
 * (MOD-708, batch 0). The comparison lives here; what is asked is loader API, so each lane that runs it
 * hands in a {@link Probe} and its reference. Today only the Fabric lane does
 * ({@code FluidAttributesSnapshotGameTest}: Transfer API {@code FluidVariantAttributes}); NeoForge's
 * {@code FluidType}s are pinned on its L1.5 lane by {@code NeoForgeFluidTypeSnapshotTest}, which reads
 * fields no gametest probe could.
 *
 * <p><b>Updated only by hand</b> from the capture this scenario logs on a mismatch, in a commit that names
 * the behaviour change (ADR-032).
 */
public final class FluidAttributesSnapshotScenarios {

	private FluidAttributesSnapshotScenarios() {}

	/** What a loader reports about one registered fluid, as {@code key=value} pairs. */
	@FunctionalInterface
	public interface Probe {
		String describe(Fluid fluid, GameTestHelper helper);
	}

	/** The eleven fluids, in registration order. */
	static final List<String> FLUIDS = List.of("oil", "flowing_oil", "diesel", "flowing_diesel",
			"fuel_oil", "flowing_fuel_oil", "biofuel", "flowing_biofuel", "nutrient_solution",
			"flowing_nutrient_solution", "steam");

	/**
	 * One line per fluid of {@link #FLUIDS} — {@code <id> <probe answer>} — compared with {@code expected}.
	 */
	public static void attributesMatchTheReference(GameTestHelper helper, Probe probe, List<String> expected) {
		List<String> actual = new ArrayList<>();
		for (String path : FLUIDS) {
			Identifier key = Industrialization.id(path);
			actual.add(BuiltInRegistries.FLUID.containsKey(key)
					? path + " " + probe.describe(BuiltInRegistries.FLUID.getValue(key), helper)
					: path + " unregistered");
		}
		if (!actual.equals(expected)) {
			Industrialization.LOGGER.info("MOD-708 fluid attributes differ ({} lines)\n{}", actual.size(),
					String.join("\n", actual));
			int index = 0;
			while (index < Math.min(actual.size(), expected.size()) && actual.get(index).equals(expected.get(index))) {
				index++;
			}
			helper.fail("MOD-708: fluid attributes differ at line " + (index + 1) + ": expected '"
					+ (index < expected.size() ? expected.get(index) : "<end>") + "', got '"
					+ (index < actual.size() ? actual.get(index) : "<end>") + "'. The full capture is in the log.");
			return;
		}
		helper.succeed();
	}
}

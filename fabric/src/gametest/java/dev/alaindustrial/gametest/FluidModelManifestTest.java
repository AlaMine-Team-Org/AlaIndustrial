package dev.alaindustrial.gametest;

import dev.alaindustrial.client.ClientContentManifest;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * L3 characterization of the client fluid models (MOD-708, batch 0): the shared
 * {@link ClientContentManifest#FLUID_MODELS} names the same six (texture, still fluid, flowing fluid)
 * triples, in the same order, that both clients register today. Deriving that list from the shared fluid
 * declarations must leave it unchanged.
 *
 * <p>On the Fabric client lane because the manifest is a client class: initialising it builds key
 * mappings and renderer providers, which neither server gametest lane nor the NeoForge L1.5 lane loads
 * (MOD-699 research). It takes no frame. {@link #check} is plain Java, so the comparison can also be run
 * against the manifest outside the game.
 *
 * <p><b>Updated only by hand</b>, in a commit that names the behaviour change (ADR-032).
 */
public class FluidModelManifestTest implements FabricClientGameTest {

	/** The reference: {@code <texture> <still> <flowing or ->}, in registration order. */
	public static final List<String> EXPECTED = List.of(
			"oil oil flowing_oil",
			"diesel diesel flowing_diesel",
			"fuel_oil fuel_oil flowing_fuel_oil",
			"biofuel biofuel flowing_biofuel",
			"nutrient_solution nutrient_solution flowing_nutrient_solution",
			"steam steam -");

	/** The lines of {@code models}, in order. */
	public static List<String> lines(List<ClientContentManifest.FluidModelDef> models) {
		List<String> lines = new ArrayList<>();
		for (ClientContentManifest.FluidModelDef def : models) {
			lines.add(def.texture() + " " + def.still() + " " + (def.flowing() == null ? "-" : def.flowing()));
		}
		return lines;
	}

	/** {@code null} when {@code models} matches {@link #EXPECTED}, else what differs. */
	public static String check(List<ClientContentManifest.FluidModelDef> models) {
		List<String> actual = lines(models);
		return actual.equals(EXPECTED) ? null
				: "MOD-708: client fluid models differ — expected " + EXPECTED + ", got " + actual;
	}

	/**
	 * @implements MOD-708-FLD03 — the client registers the same six fluid models, texture and fluids, in the
	 *     same order
	 */
	@Override
	public void runTest(ClientGameTestContext context) {
		String problem = context.computeOnClient(client -> check(ClientContentManifest.FLUID_MODELS));
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}

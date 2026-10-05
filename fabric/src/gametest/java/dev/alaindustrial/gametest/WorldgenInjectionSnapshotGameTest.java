package dev.alaindustrial.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry point for the worldgen-injection characterization (MOD-708, batch 9). The capture is
 * loader-neutral ({@link WorldgenInjectionSnapshotScenarios}); the reference is Fabric's:
 * <ul>
 *   <li><b>The ore selector.</b> Fabric takes the five overworld ores with
 *       {@code BiomeSelectors.foundInOverworld()}, which asks the overworld's biome source; the flat game-test
 *       world generates one biome, so {@code jungle} and {@code savanna} carry the oil and the lab (picked by
 *       tag) but NO ores. NeoForge picks them by {@code #minecraft:is_overworld} and puts them there.</li>
 *   <li><b>The order within a step.</b> Fabric sorts its biome modifications by phase, then order (0 for
 *       every {@code addFeature}), then id (javap, fabric-biome-api-v1 20.0.9 on 26.3 and 18.0.6 on 26.2:
 *       {@code BiomeModificationImpl.MODIFIER_ORDER_COMPARATOR}), so a step lists the features by id, not in
 *       call order. NeoForge lists them as its files do.</li>
 * </ul>
 * Both differences are pinned as they are; the shared injection table must not change either.
 */
public class WorldgenInjectionSnapshotGameTest {

	private static final String LAKES = "lakes=[oil_geyser, oil_lake_deep, oil_lake_surface, oil_lake_underground]";

	/** The reference, one line per probe biome; "generated" stands for every biome this world generates. */
	static final List<String> EXPECTED = List.of(
			"generated " + LAKES + " surface_structures=[abandoned_lab]"
					+ " underground_ores=[nickel_ore, silver_ore, sulfur_ore, tin_ore, uranium_ore]",
			"minecraft:jungle " + LAKES + " surface_structures=[abandoned_lab]",
			"minecraft:savanna " + LAKES + " surface_structures=[abandoned_lab] vegetal_decoration=[kok_sagyz]",
			"minecraft:nether_wastes underground_ores=[palladium_ore]",
			"minecraft:the_end -");

	/**
	 * @implements MOD-708-WGN01 — Fabric injects the mod's placed features as it did before the shared
	 *     injection table: the ores by the overworld's own biome source, everything else by its biome tag,
	 *     each step's features in id order
	 */
	@GameTest
	public void modFeaturesPerBiomeMatchTheReference(GameTestHelper helper) {
		WorldgenInjectionSnapshotScenarios.modFeaturesPerBiomeMatchTheReference(helper, EXPECTED);
	}
}

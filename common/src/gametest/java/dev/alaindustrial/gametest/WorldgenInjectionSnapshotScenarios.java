package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * L2 characterization of where the mod's placed features land (MOD-708, batch 9): for a handful of probe
 * biomes, the mod's features at every generation step, in the order the biome lists them. The capture is
 * loader-neutral (it reads the biome's generation settings); the reference is the loader's own, so each lane
 * that runs it hands one in. Today only Fabric does ({@code WorldgenInjectionSnapshotGameTest}); NeoForge's
 * declaration is data and is pinned on its L1.5 lane by {@code NeoForgeBiomeModifierSnapshotTest}.
 *
 * <p>{@code WorldgenInjectionScenarios} (MOD-702, both lanes) checks each feature's step and a carrier and a
 * bystander biome of its selector. This adds what differs between the loaders and what a rewrite of the
 * injection code could change unseen there: which biomes a selector reaches beyond those two, and the
 * order of the features within a step.
 *
 * <p><b>Updated only by hand</b> from the capture this scenario logs on a mismatch, in a commit that names
 * the behaviour change (ADR-032).
 */
public final class WorldgenInjectionSnapshotScenarios {

	private WorldgenInjectionSnapshotScenarios() {}

	/** Overworld biomes the flat game-test world does not generate, the Nether and the End. */
	static final List<ResourceKey<Biome>> PROBES =
			List.of(Biomes.JUNGLE, Biomes.SAVANNA, Biomes.NETHER_WASTES, Biomes.THE_END);

	/**
	 * One line per biome — first every overworld biome this world generates (labelled "generated", since
	 * which one it is depends on the game version), then {@link #PROBES} — compared with {@code expected}.
	 */
	public static void modFeaturesPerBiomeMatchTheReference(GameTestHelper helper, List<String> expected) {
		List<String> actual = new ArrayList<>();
		List<ResourceKey<Biome>> generated = generatedOverworldBiomes(helper);
		if (generated.isEmpty()) {
			actual.add("generated <none>");
		}
		for (ResourceKey<Biome> biome : generated) {
			actual.add(line(helper, "generated", biome));
		}
		for (ResourceKey<Biome> biome : PROBES) {
			actual.add(line(helper, biome.identifier().toString(), biome));
		}
		if (!actual.equals(expected)) {
			Industrialization.LOGGER.info("MOD-708 worldgen injection differs ({} lines)\n{}", actual.size(),
					String.join("\n", actual));
			int index = 0;
			while (index < Math.min(actual.size(), expected.size()) && actual.get(index).equals(expected.get(index))) {
				index++;
			}
			helper.fail("MOD-708: worldgen injection differs at line " + (index + 1) + ": expected '"
					+ (index < expected.size() ? expected.get(index) : "<end>") + "', got '"
					+ (index < actual.size() ? actual.get(index) : "<end>") + "'. The full capture is in the log.");
			return;
		}
		helper.succeed();
	}

	/** {@code <label> <step>=[<mod feature paths in list order>] …}, steps in generation order; "-" if none. */
	private static String line(GameTestHelper helper, String label, ResourceKey<Biome> biome) {
		List<HolderSet<PlacedFeature>> steps = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
				.getOrThrow(biome).value().getGenerationSettings().features();
		StringBuilder out = new StringBuilder(label);
		boolean any = false;
		for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
			if (step.ordinal() >= steps.size()) {
				break;
			}
			List<String> mod = new ArrayList<>();
			for (Holder<PlacedFeature> holder : steps.get(step.ordinal())) {
				holder.unwrapKey().map(ResourceKey::identifier)
						.filter(id -> Industrialization.MOD_ID.equals(id.getNamespace()))
						.map(Identifier::getPath).ifPresent(mod::add);
			}
			if (!mod.isEmpty()) {
				out.append(' ').append(step.getSerializedName()).append('=').append(mod);
				any = true;
			}
		}
		return any ? out.toString() : out.append(" -").toString();
	}

	/** The overworld biomes this world's overworld can generate, sorted by id (one in the flat test world). */
	private static List<ResourceKey<Biome>> generatedOverworldBiomes(GameTestHelper helper) {
		Registry<Biome> biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
		Set<ResourceKey<Biome>> found = new TreeSet<>(Comparator.comparing(key -> key.identifier().toString()));
		for (Holder<Biome> holder : helper.getLevel().getServer().overworld().getChunkSource().getGenerator()
				.getBiomeSource().possibleBiomes()) {
			holder.unwrapKey().filter(key -> biomes.getOrThrow(key).is(BiomeTags.IS_OVERWORLD)).ifPresent(found::add);
		}
		return List.copyOf(found);
	}
}

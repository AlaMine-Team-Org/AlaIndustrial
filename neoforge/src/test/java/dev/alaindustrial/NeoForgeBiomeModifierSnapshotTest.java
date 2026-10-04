package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * NeoForge characterization of where the mod's placed features land (MOD-708, batch 9). NeoForge's
 * declaration is data — the {@code neoforge/biome_modifier/*.json} files of this mod — so this pins the
 * files themselves, as the server loaded them, and what they do to a handful of probe biomes: the mod's
 * features at every generation step, in the order the biome lists them.
 *
 * <p>The files are compared by their parsed content, key by key, so a change of formatting alone does not
 * show here; a changed selector, step, feature, feature order, file name or an extra file does. The probe
 * biomes differ from Fabric's ({@code WorldgenInjectionSnapshotGameTest}) on purpose: every selector here is
 * a tag, so {@code jungle} carries the ores although no flat world generates it, and the order within a step
 * follows the files, not the feature ids. Both are pinned as they are on each loader.
 *
 * <p><b>Updated only by hand</b>, in a commit that names the behaviour change (ADR-032).
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class NeoForgeBiomeModifierSnapshotTest {

	/** Every biome modifier file of this mod, by name, its keys sorted, each value as compact JSON. */
	static final List<String> FILES = List.of(
			"abandoned_labs biomes=\"#alaindustrial:has_abandoned_labs\" features=[\"alaindustrial:abandoned_lab\"]"
					+ " step=\"surface_structures\" type=\"neoforge:add_features\"",
			"kok_sagyz biomes=\"#alaindustrial:has_kok_sagyz\" features=[\"alaindustrial:kok_sagyz\"]"
					+ " step=\"vegetal_decoration\" type=\"neoforge:add_features\"",
			"nether_ores biomes=\"#alaindustrial:has_palladium_ore\" features=[\"alaindustrial:palladium_ore\"]"
					+ " step=\"underground_ores\" type=\"neoforge:add_features\"",
			"oil_geysers biomes=\"#alaindustrial:has_oil_geysers\" features=[\"alaindustrial:oil_geyser\"]"
					+ " step=\"lakes\" type=\"neoforge:add_features\"",
			"oil_lakes_deep biomes=\"#alaindustrial:has_underground_oil_lakes\""
					+ " features=[\"alaindustrial:oil_lake_deep\"] step=\"lakes\" type=\"neoforge:add_features\"",
			"oil_lakes_surface biomes=\"#alaindustrial:has_surface_oil_lakes\""
					+ " features=[\"alaindustrial:oil_lake_surface\"] step=\"lakes\" type=\"neoforge:add_features\"",
			"oil_lakes_underground biomes=\"#alaindustrial:has_underground_oil_lakes\""
					+ " features=[\"alaindustrial:oil_lake_underground\"] step=\"lakes\""
					+ " type=\"neoforge:add_features\"",
			"ores biomes=\"#minecraft:is_overworld\" features=[\"alaindustrial:tin_ore\",\"alaindustrial:silver_ore\","
					+ "\"alaindustrial:nickel_ore\",\"alaindustrial:sulfur_ore\",\"alaindustrial:uranium_ore\"]"
					+ " step=\"underground_ores\" type=\"neoforge:add_features\"");

	private static final String LAKES =
			"lakes=[oil_geyser, oil_lake_deep, oil_lake_surface, oil_lake_underground]";
	private static final String ORES = "underground_ores=[tin_ore, silver_ore, nickel_ore, sulfur_ore, uranium_ore]";

	/** The reference, one line per probe biome. */
	static final List<String> BIOMES = List.of(
			"minecraft:plains " + LAKES + " surface_structures=[abandoned_lab] " + ORES,
			"minecraft:jungle " + LAKES + " surface_structures=[abandoned_lab] " + ORES,
			"minecraft:savanna " + LAKES + " surface_structures=[abandoned_lab] " + ORES
					+ " vegetal_decoration=[kok_sagyz]",
			"minecraft:nether_wastes underground_ores=[palladium_ore]",
			"minecraft:the_end -");

	private static final List<ResourceKey<Biome>> PROBES =
			List.of(Biomes.PLAINS, Biomes.JUNGLE, Biomes.SAVANNA, Biomes.NETHER_WASTES, Biomes.THE_END);

	/**
	 * @implements MOD-708-WGN02 — NeoForge loads the same eight biome modifier files as before the shared
	 *     injection table, and they inject the mod's placed features into the same biomes, steps and order
	 */
	@Test
	void biomeModifierFilesMatchTheReference(MinecraftServer server) throws IOException {
		List<String> actual = new ArrayList<>();
		for (Map.Entry<String, Resource> file : biomeModifierFiles(server).entrySet()) {
			JsonObject json = JsonParser.parseString(read(file.getValue())).getAsJsonObject();
			StringBuilder line = new StringBuilder(file.getKey());
			for (String key : new TreeSet<>(json.keySet())) {
				JsonElement value = json.get(key);
				line.append(' ').append(key).append('=').append(value);
			}
			actual.add(line.toString());
		}
		assertEquals(String.join("\n", FILES), String.join("\n", actual),
				"MOD-708: the mod's NeoForge biome modifier files changed");
	}

	@Test
	void modFeaturesPerBiomeMatchTheReference(MinecraftServer server) {
		List<String> actual = new ArrayList<>();
		for (ResourceKey<Biome> biome : PROBES) {
			actual.add(line(server, biome));
		}
		assertEquals(String.join("\n", BIOMES), String.join("\n", actual),
				"MOD-708: the mod's placed features land in other biomes, steps or order on NeoForge");
	}

	/** This mod's {@code neoforge/biome_modifier/*.json} as the server loaded them, keyed by file name. */
	static Map<String, Resource> biomeModifierFiles(MinecraftServer server) {
		Map<String, Resource> files = new TreeMap<>();
		for (Map.Entry<Identifier, Resource> entry : server.getResourceManager()
				.listResources("neoforge/biome_modifier", id -> id.getPath().endsWith(".json")).entrySet()) {
			Identifier id = entry.getKey();
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				String path = id.getPath();
				String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
				files.put(name, entry.getValue());
			}
		}
		return files;
	}

	/** The file's text as UTF-8; {@code Resource.readAllAsString} exists on 26.3 only, this works on both lines. */
	static String read(Resource resource) throws IOException {
		try (InputStream in = resource.open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** {@code <biome> <step>=[<mod feature paths in list order>] …}, steps in generation order; "-" if none. */
	private static String line(MinecraftServer server, ResourceKey<Biome> biome) {
		List<HolderSet<PlacedFeature>> steps = server.registryAccess().lookupOrThrow(Registries.BIOME)
				.getOrThrow(biome).value().getGenerationSettings().features();
		StringBuilder out = new StringBuilder(biome.identifier().toString());
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
}

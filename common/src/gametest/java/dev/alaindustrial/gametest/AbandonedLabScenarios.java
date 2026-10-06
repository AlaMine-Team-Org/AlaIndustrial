package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.worldgen.AbandonedLabPlacer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * L2 suite for the abandoned lab (MOD-513): every approved lab template reaches the game intact on
 * both loaders, carrying everything {@link AbandonedLabPlacer} relies on when it places one.
 *
 * <p>The placement on real terrain — the site, the depth, the camouflage — needs a generated world
 * with rock under it, which a gametest does not have; it is checked on real world generation and in
 * the dev client. What does not need real terrain is where the placement reaches: it runs here on the
 * flat synthetic terrain of a {@link WriteWindowProbe}, which notes every access outside the window
 * world generation allows (MOD-774).
 */
public final class AbandonedLabScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AbandonedLabScenarios::labTemplatesReachTheGameIntact,
								"lab_templates_reach_the_game_intact")
						.fabricId("AbandonedLabGameTest", "labTemplatesReachTheGameIntact").ticks(20, 100),
				RosterEntry.of(AbandonedLabScenarios::labIsInjectedIntoItsBiomes, "lab_is_injected_into_its_biomes")
						.fabricId("AbandonedLabGameTest", "labIsInjectedIntoItsBiomes").ticks(20, 100),
				RosterEntry.of(AbandonedLabScenarios::labStaysInsideItsWriteWindow,
						"lab_stays_inside_its_write_window").ticks(100, 100));

		private Roster() {}
	}

	private AbandonedLabScenarios() {}

	private static final List<Supplier<Block>> PLATES = List.of(
			ModContent.ENGRAVED_PLATE_0, ModContent.ENGRAVED_PLATE_1, ModContent.ENGRAVED_PLATE_2,
			ModContent.ENGRAVED_PLATE_3, ModContent.ENGRAVED_PLATE_4, ModContent.ENGRAVED_PLATE_5,
			ModContent.ENGRAVED_PLATE_6, ModContent.ENGRAVED_PLATE_7, ModContent.ENGRAVED_PLATE_8,
			ModContent.ENGRAVED_PLATE_9, ModContent.BROKEN_ENGRAVED_PLATE_W, ModContent.BROKEN_ENGRAVED_PLATE_K,
			ModContent.BROKEN_ENGRAVED_PLATE_P, ModContent.BROKEN_ENGRAVED_PLATE_B,
			ModContent.BROKEN_ENGRAVED_PLATE_D, ModContent.BROKEN_ENGRAVED_PLATE_R,
			ModContent.BROKEN_ENGRAVED_PLATE_M);

	/**
	 * @implements MOD-513-LAB — each of the seven templates loads, ends in one entry ladder whose column
	 *     gives the feature its pivot, holds a plaque of exactly four plates, and — every lab but the
	 *     cave — one trapped chest whose loot table is its own and actually exists, as does the common
	 *     table all six nest.
	 */
	public static void labTemplatesReachTheGameIntact(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		StructurePlaceSettings identity = new StructurePlaceSettings();
		requireLootTable(helper, server, "common");
		for (String lab : AbandonedLabPlacer.LABS) {
			Optional<StructureTemplate> found = server.getStructureTemplateManager()
					.get(Industrialization.id("abandoned_lab/" + lab));
			if (found.isEmpty()) {
				helper.fail("lab template '" + lab + "' did not load");
				return;
			}
			StructureTemplate template = found.get();
			AbandonedLabPlacer.Shaft shaft = AbandonedLabPlacer.Shaft.of(template);
			if (shaft == null || shaft.foot().getY() < 0 || shaft.top() <= shaft.foot().getY()) {
				helper.fail("lab '" + lab + "' has no entry ladder reaching its top: " + shaft);
				return;
			}
			int plates = 0;
			for (Supplier<Block> plate : PLATES) {
				plates += template.filterBlocks(BlockPos.ZERO, identity, plate.get()).size();
			}
			if (plates != 4) {
				helper.fail("lab '" + lab + "' has " + plates + " plaque plates, expected 4");
				return;
			}
			List<StructureTemplate.StructureBlockInfo> chests =
					template.filterBlocks(BlockPos.ZERO, identity, Blocks.TRAPPED_CHEST);
			int expectedChests = "cave".equals(lab) ? 0 : 1;
			if (chests.size() != expectedChests) {
				helper.fail("lab '" + lab + "' has " + chests.size() + " trapped chests, expected " + expectedChests);
				return;
			}
			if (expectedChests == 1) {
				String table = chests.getFirst().nbt() == null ? ""
						: chests.getFirst().nbt().getStringOr("LootTable", "");
				String expected = "alaindustrial:chests/abandoned_lab/" + lab;
				if (!expected.equals(table)) {
					helper.fail("lab '" + lab + "' chest loot table is '" + table + "', expected '" + expected + "'");
					return;
				}
				requireLootTable(helper, server, lab);
			}
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-513-LAB — the lab reaches the biomes of its tag, and only those, at the step the
	 *     entrance spec names. The two loaders inject it by different means — Fabric in code, NeoForge
	 *     through a datapack biome modifier — and ores once generated on one loader only for exactly
	 *     that reason; both are read back here through the same generation settings.
	 */
	public static void labIsInjectedIntoItsBiomes(GameTestHelper helper) {
		ResourceKey<PlacedFeature> lab = ResourceKey.create(Registries.PLACED_FEATURE,
				Industrialization.id("abandoned_lab"));
		for (ResourceKey<Biome> biome : List.of(Biomes.PLAINS, Biomes.SNOWY_PLAINS, Biomes.JUNGLE)) {
			if (!BiomeFeatureProbe.has(helper, biome, GenerationStep.Decoration.SURFACE_STRUCTURES, lab)) {
				helper.fail("the abandoned lab is not generated in " + biome.identifier()
						+ " (surface_structures step)");
				return;
			}
		}
		for (ResourceKey<Biome> biome : List.of(Biomes.OCEAN, Biomes.RIVER, Biomes.DEEP_DARK)) {
			if (BiomeFeatureProbe.has(helper, biome, GenerationStep.Decoration.SURFACE_STRUCTURES, lab)) {
				helper.fail("the abandoned lab is generated in " + biome.identifier() + ", which its tag leaves out");
				return;
			}
		}
		helper.succeed();
	}

	/** Ground level of the probe's synthetic terrain: room for the deepest lab above the world's floor. */
	private static final int PROBE_GROUND_Y = 120;

	/** Placements tried: enough for every lab in every rotation at every probed origin. */
	private static final int PROBE_ATTEMPTS = 280;

	/**
	 * Where in its chunk the placement starts: the four corners push the lab against each side of the
	 * window, the middle leaves it room.
	 */
	private static final int[][] PROBE_ORIGINS = {{0, 0}, {15, 0}, {0, 15}, {15, 15}, {8, 8}};

	/** Biomes whose camouflage reaches furthest from the hatch (canopy, driftwood, bamboo). */
	private static final List<ResourceKey<Biome>> PROBE_BIOMES = List.of(Biomes.PLAINS, Biomes.JUNGLE,
			Biomes.SNOWY_BEACH, Biomes.BAMBOO_JUNGLE);

	/**
	 * @implements MOD-774 — every read and write the placement makes, the lab template's own included,
	 *     stays inside the ±1-chunk window of the chunk being decorated, for every lab in every rotation,
	 *     wherever in its chunk the placement starts. World generation logs "unsafe terrain read" past
	 *     that window, and a chunk there may not have its terrain yet.
	 */
	public static void labStaysInsideItsWriteWindow(GameTestHelper helper) {
		ServerLevel real = helper.getLevel();
		StructureTemplateManager templates = real.getServer().getStructureTemplateManager();
		HolderLookup.RegistryLookup<Biome> biomes = real.registryAccess().lookupOrThrow(Registries.BIOME);
		ChunkPos chunk = new ChunkPos(4000, -4000);
		Map<String, Integer> outside = new TreeMap<>();
		Set<String> covered = new TreeSet<>();
		for (int seed = 0; seed < PROBE_ATTEMPTS; seed++) {
			int[] start = PROBE_ORIGINS[seed % PROBE_ORIGINS.length];
			BlockPos origin = new BlockPos(chunk.getMinBlockX() + start[0], PROBE_GROUND_Y,
					chunk.getMinBlockZ() + start[1]);
			WriteWindowProbe probe = new WriteWindowProbe(real, chunk, PROBE_GROUND_Y,
					biomes.getOrThrow(PROBE_BIOMES.get(seed % PROBE_BIOMES.size())));
			// The placement's first two draws are the lab and its rotation; replay them to know what ran.
			RandomSource peek = RandomSource.create(seed);
			String lab = AbandonedLabPlacer.LABS.get(peek.nextInt(AbandonedLabPlacer.LABS.size()));
			Rotation rotation = Rotation.getRandom(peek);
			if (!AbandonedLabPlacer.place(probe.level, RandomSource.create(seed), origin, templates)) {
				helper.fail("lab '" + lab + "' (" + rotation + ") was not placed on flat solid ground at " + origin);
				return;
			}
			covered.add(lab + "/" + rotation);
			probe.outside().forEach((who, count) -> outside.merge(who, count, Integer::sum));
		}
		if (!outside.isEmpty()) {
			helper.fail("the lab's placement reaches past the ±1-chunk window of its chunk: " + outside);
			return;
		}
		int expected = AbandonedLabPlacer.LABS.size() * Rotation.values().length;
		if (covered.size() != expected) {
			helper.fail("only " + covered.size() + " of " + expected + " lab/rotation pairs were tried: " + covered);
			return;
		}
		helper.succeed();
	}

	private static void requireLootTable(GameTestHelper helper, MinecraftServer server, String name) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
				Industrialization.id("chests/abandoned_lab/" + name));
		if (server.reloadableRegistries().getLootTable(key) == LootTable.EMPTY) {
			helper.fail("loot table " + key.identifier() + " is missing");
		}
	}
}

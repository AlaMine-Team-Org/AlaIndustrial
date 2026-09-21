package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.worldgen.AbandonedLabFeature;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * L2 suite for the abandoned lab (MOD-513): every approved lab template reaches the game intact on
 * both loaders, carrying everything {@link AbandonedLabFeature} relies on when it places one.
 *
 * <p>The placement itself — a site on real terrain, the depth, the camouflage — needs a generated
 * world with rock under it, which a gametest does not have; it is checked on real world generation
 * and in the dev client.
 */
public final class AbandonedLabScenarios {

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
		for (String lab : AbandonedLabFeature.LABS) {
			Optional<StructureTemplate> found = server.getStructureTemplateManager()
					.get(Industrialization.id("abandoned_lab/" + lab));
			if (found.isEmpty()) {
				helper.fail("lab template '" + lab + "' did not load");
				return;
			}
			StructureTemplate template = found.get();
			AbandonedLabFeature.Shaft shaft = AbandonedLabFeature.Shaft.of(template);
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
			if (!surfaceStructuresOf(helper, biome).stream().anyMatch(feature -> feature.is(lab))) {
				helper.fail("the abandoned lab is not generated in " + biome.identifier()
						+ " (surface_structures step)");
				return;
			}
		}
		for (ResourceKey<Biome> biome : List.of(Biomes.OCEAN, Biomes.RIVER, Biomes.DEEP_DARK)) {
			if (surfaceStructuresOf(helper, biome).stream().anyMatch(feature -> feature.is(lab))) {
				helper.fail("the abandoned lab is generated in " + biome.identifier() + ", which its tag leaves out");
				return;
			}
		}
		helper.succeed();
	}

	/** The biome's features at the surface-structures step; a biome whose list stops short of it has none. */
	private static HolderSet<PlacedFeature> surfaceStructuresOf(GameTestHelper helper, ResourceKey<Biome> biome) {
		List<HolderSet<PlacedFeature>> steps = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
				.getOrThrow(biome).value().getGenerationSettings().features();
		int step = GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal();
		return step < steps.size() ? steps.get(step) : HolderSet.direct();
	}

	private static void requireLootTable(GameTestHelper helper, MinecraftServer server, String name) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
				Industrialization.id("chests/abandoned_lab/" + name));
		if (server.reloadableRegistries().getLootTable(key) == LootTable.EMPTY) {
			helper.fail("loot table " + key.identifier() + " is missing");
		}
	}
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.compat.TownTerrain;
import dev.alaindustrial.worldgen.town.AlaTownLabPiece;
import dev.alaindustrial.worldgen.town.AlaTownStructure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The ALA town on real overworld terrain (MOD-830): on twenty world seeds, both built-in towns are laid out by
 * their structure against the vanilla overworld generator — its noise, its biomes — at the first chunk of a fixed
 * grid where the town comes out (a seed with no town of either style on the grid is skipped, and the next one
 * taken), and every town found must hold what the flat-ground scenarios hold: its biome,
 * every template of its style (the jigsaw assembly drops a piece whose box would cut another, so uneven ground is
 * where a lost piece would show), the lab clear of every other piece under the hall, and buildings whose floors
 * the terrain adjustment can reach — sampled on a {@value #FLOOR_GRID} × {@value #FLOOR_GRID} grid, denser than the
 * structure's own check, so a slope between its samples shows too.
 *
 * <p>Only laid out, never placed: placing needs generated chunks, and what a real chunk does with the pieces —
 * carvers, aquifers, the terrain adjustment itself — is the dev client's and the RCON measurement's to see.
 */
public final class AlaTownTerrainScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AlaTownTerrainScenarios::townsHoldOnRealTerrain, "ala_town_holds_on_real_terrain")
						.ticks(100));

		private Roster() {}
	}

	private AlaTownTerrainScenarios() {}

	/** Seeds with a town on the grid that the properties are checked on. */
	private static final int SEEDS = 20;

	/** Seeds tried before the run gives up finding {@link #SEEDS} with a town: a grid this short says nothing. */
	private static final int MAX_SEEDS = 2 * SEEDS;

	/** Candidate chunks per seed and town: a grid of this many columns and rows, {@link #GRID_STEP} apart. */
	private static final int GRID = 8;
	private static final int GRID_STEP = 24;

	/** Samples per side of the grid over a building's floor. */
	private static final int FLOOR_GRID = 5;

	/** Share of a building's sampled floor the terrain adjustment must be able to reach. */
	private static final double MIN_REACHED = 0.9;

	/** Height at which a candidate chunk's biome is read before the town is laid out there (sea level). */
	private static final int BIOME_PROBE_Y = 63;

	/** @implements MOD-830-TERRAIN — see the class comment. */
	public static void townsHoldOnRealTerrain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess access = level.registryAccess();
		Holder<NoiseGeneratorSettings> noise = access.lookupOrThrow(Registries.NOISE_SETTINGS)
				.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(
				access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
						.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, noise);
		int seedsWithTown = 0;
		Set<String> stylesFound = new TreeSet<>();
		for (long seed = 1; seed <= MAX_SEEDS && seedsWithTown < SEEDS; seed++) {
			boolean found = false;
			RandomState randomState = TownTerrain.randomState(access, noise, seed);
			Climate.Sampler sampler = TownTerrain.climate(randomState);
			for (String style : List.of("plains", "desert")) {
				Holder.Reference<Structure> structure = AlaTownSite.structure(level,
						Industrialization.id("ala_town_" + style));
				StructureStart start = firstTown(level, structure, generator, biomes, sampler, randomState, seed);
				if (start == null) {
					continue;
				}
				found = true;
				stylesFound.add(style);
				String broken = check(level, structure.value(), style, start, generator, biomes, sampler, randomState);
				if (broken != null) {
					helper.fail("seed " + seed + ", " + style + " town at " + start.getChunkPos() + ": " + broken);
					return;
				}
			}
			seedsWithTown += found ? 1 : 0;
		}
		if (seedsWithTown < SEEDS) {
			helper.fail("only " + seedsWithTown + " of " + MAX_SEEDS + " seeds have a town on the grid");
			return;
		}
		if (stylesFound.size() != 2) {
			helper.fail("the grid found towns of " + stylesFound + " only");
			return;
		}
		helper.succeed();
	}

	private static StructureStart firstTown(ServerLevel level, Holder<Structure> structure,
			NoiseBasedChunkGenerator generator, BiomeSource biomes, Climate.Sampler sampler, RandomState randomState,
			long seed) {
		for (int i = 0; i < GRID; i++) {
			for (int j = 0; j < GRID; j++) {
				ChunkPos chunk = new ChunkPos(i * GRID_STEP, j * GRID_STEP);
				if (!structure.value().biomes().contains(TownTerrain.noiseBiome(biomes, sampler,
						QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(BIOME_PROBE_Y),
						QuartPos.fromBlock(chunk.getMiddleBlockZ())))) {
					continue; // laying a town out costs noise columns; a chunk of another biome is skipped first
				}
				StructureStart start = TownTerrain.generate(level, structure, generator, biomes, sampler, randomState,
						seed, chunk);
				if (start.isValid()) {
					return start;
				}
			}
		}
		return null;
	}

	/** What a found town gets wrong, or {@code null}. */
	private static String check(ServerLevel level, Structure structure, String style, StructureStart start,
			NoiseBasedChunkGenerator generator, BiomeSource biomes, Climate.Sampler sampler, RandomState randomState) {
		List<PoolElementStructurePiece> pieces = AlaTownSite.poolPieces(start);
		BoundingBox hall = pieces.getFirst().getBoundingBox();
		if (!structure.biomes().contains(TownTerrain.noiseBiome(biomes, sampler,
				QuartPos.fromBlock(hall.getCenter().getX()), QuartPos.fromBlock(hall.minY()),
				QuartPos.fromBlock(hall.getCenter().getZ())))) {
			return "the town hall stands outside the town's biomes";
		}
		List<Identifier> placed = new ArrayList<>();
		pieces.forEach(piece -> placed.add(AlaTownSite.template(piece)));
		List<Identifier> expected = AlaTownSite.styleTemplates(level, style);
		if (placed.size() != expected.size() || !new HashSet<>(placed).equals(new HashSet<>(expected))) {
			return placed.size() + " pieces laid out of " + expected.size();
		}
		List<AlaTownLabPiece> labs = AlaTownSite.labs(start);
		if (labs.size() != 1 || !hall.isInside(labs.getFirst().hatch())) {
			return "no lab under the town hall";
		}
		for (PoolElementStructurePiece piece : pieces) {
			if (piece.getBoundingBox().intersects(labs.getFirst().labBox())) {
				return "the lab cuts " + AlaTownSite.template(piece);
			}
			double reached = AlaTownSite.rigid(piece)
					? reached(level, piece.getBoundingBox(), generator, randomState) : 1;
			if (reached < MIN_REACHED) {
				return AlaTownSite.template(piece) + " has " + Math.round((1 - reached) * 100)
						+ "% of its floor out of the terrain adjustment's reach";
			}
		}
		return null;
	}

	/** Share of the grid over the floor where the ground is within {@link AlaTownStructure#BEARD_REACH} of it. */
	private static double reached(ServerLevel level, BoundingBox box, NoiseBasedChunkGenerator generator,
			RandomState randomState) {
		int reached = 0;
		for (int i = 0; i < FLOOR_GRID; i++) {
			for (int j = 0; j < FLOOR_GRID; j++) {
				int x = box.minX() + (box.maxX() - box.minX()) * i / (FLOOR_GRID - 1);
				int z = box.minZ() + (box.maxZ() - box.minZ()) * j / (FLOOR_GRID - 1);
				int ground = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
						randomState);
				reached += Math.abs(ground - box.minY()) <= AlaTownStructure.BEARD_REACH ? 1 : 0;
			}
		}
		return reached / (double) (FLOOR_GRID * FLOOR_GRID);
	}
}

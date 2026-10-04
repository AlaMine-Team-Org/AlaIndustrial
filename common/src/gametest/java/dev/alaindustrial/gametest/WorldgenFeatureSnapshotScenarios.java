package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.compat.FeaturePlacement;
import dev.alaindustrial.gametest.compat.SiteBiome;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * L2 characterization of the mod's own worldgen features (MOD-704, batch 0), on both loaders.
 *
 * <p><b>What it holds.</b> Each shipped configured feature — the three oil lake tiers, the oil geyser and
 * the abandoned lab — is run by id from the server's registry (the shipped JSON configuration), with a
 * fixed seed, at a fixed origin, inside a site this scenario builds itself: a 3 × 3 chunk window far from
 * every test structure, painted plains, solid stone under a grass-and-dirt surface at {@link #ROCK_TOP}
 * and air for {@link #AIR_ABOVE} blocks above it. A gametest world has no rock of its own, and a lab
 * needs dozens of blocks of it. What the feature changed against that baseline — its answer, the bounding box of the
 * changed cells relative to the origin, a hash of their layout and the count of every block state it
 * wrote — is compared line for line with {@link WorldgenFeatureSnapshot}.
 *
 * <p>The feature bodies are about to be split into a placer that is the same on both Minecraft lines and a
 * thin per-line {@code Feature} adapter (MOD-704, batches 4–6). Those batches must leave every line of this
 * snapshot as it is. The literals are captured on each line; they agree when the two lines place the
 * same blocks.
 *
 * <p><b>Updated only by the explicit command</b> in {@link WorldgenFeatureSnapshot}'s javadoc (ADR-032).
 */
public final class WorldgenFeatureSnapshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(WorldgenFeatureSnapshotScenarios::worldgenFeaturesMatchTheSnapshot,
								"worldgen_feature_snapshot_matches")
						.ticks(100, 200));

		private Roster() {}
	}

	private WorldgenFeatureSnapshotScenarios() {}

	static final String WRITE_TO_PROPERTY = "alaindustrial.worldgenFeatureSnapshot.writeTo";

	/**
	 * The ground surface of a site: grass on top, three layers of dirt, stone down to one above the world
	 * floor (the flat gametest world keeps its own bottom layer). High enough for the geyser's shaft from
	 * its configured dome depth and for the lab's deepest shaft.
	 */
	private static final int ROCK_TOP = 0;

	/** Dirt layers under the grass — the lab only opens its hatch in a ground block of its camouflage. */
	private static final int DIRT_DEPTH = 3;

	/** Air cleared above the rock, enough for the geyser's spout and the lab's entrance and camouflage. */
	private static final int AIR_ABOVE = 16;

	/** First chunk of the first site — far from the gametest grid, which sits around the world origin. */
	private static final int FIRST_SITE_CHUNK = 4000;

	/** Chunks between two sites, so no feature can reach its neighbour's window. */
	private static final int SITE_SPACING_CHUNKS = 8;

	/**
	 * One feature run: the feature id, its seed, and how far below the rock top its origin sits (a lake is
	 * buried, the geyser and the lab are given the surface, as their placed features give them).
	 */
	private record Site(String feature, long seed, int originBelowTop) {
	}

	private static final List<Site> SITES = List.of(
			new Site("oil_lake_small", 7041L, 8),
			new Site("oil_lake_medium", 7042L, 10),
			new Site("oil_lake_large", 7043L, 12),
			new Site("oil_geyser", 7044L, 0),
			new Site("abandoned_lab", 7045L, -1));

	/** Every site placed and fingerprinted, matched against the reviewed reference. */
	public static void worldgenFeaturesMatchTheSnapshot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < SITES.size(); i++) {
			lines.addAll(fingerprint(level, SITES.get(i), FIRST_SITE_CHUNK + i * SITE_SPACING_CHUNKS));
		}
		GoldenLines.check(helper, "WorldgenFeatureSnapshot", WRITE_TO_PROPERTY,
				"*:worldgen_feature_snapshot_matches", WorldgenFeatureSnapshot.LINES, lines);
	}

	/** Build the site in chunk ({@code chunk}, {@code chunk}) ± 1, run the feature, describe what it wrote. */
	private static List<String> fingerprint(ServerLevel level, Site site, int chunk) {
		int minX = (chunk - 1) * 16;
		int minZ = (chunk - 1) * 16;
		int maxX = (chunk + 1) * 16 + 15;
		int maxZ = (chunk + 1) * 16 + 15;
		int minY = level.getMinY() + 1;
		int maxY = ROCK_TOP + AIR_ABOVE;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					level.setBlock(cursor.set(x, y, z), baseline(y), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
		// A live chunk stops updating the _WG heightmaps once generation is over, and the features read
		// them (see OilScenarios.placeDeposit): prime them over the rebuilt window.
		EnumSet<Heightmap.Types> worldgen =
				EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG);
		// The lab dresses its entrance after the biome; the two lines' gametest worlds put different biomes
		// out here, so the site is painted plains first and the snapshot is the same on both lines.
		Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		for (int cx = chunk - 1; cx <= chunk + 1; cx++) {
			for (int cz = chunk - 1; cz <= chunk + 1; cz++) {
				Heightmap.primeHeightmaps(level.getChunk(cx, cz), worldgen);
				SiteBiome.fill(level, level.getChunk(cx, cz), plains);
			}
		}

		BlockPos origin = new BlockPos(chunk * 16 + 8, ROCK_TOP - site.originBelowTop(), chunk * 16 + 8);
		boolean placed = FeaturePlacement.place(level, Industrialization.id(site.feature()),
				RandomSource.create(site.seed()), origin);

		Map<String, Integer> counts = new TreeMap<>();
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
				Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		long layout = 17L;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					BlockState state = level.getBlockState(cursor.set(x, y, z));
					if (state == baseline(y)) {
						continue;
					}
					String id = stateId(state);
					counts.merge(id, 1, Integer::sum);
					int rx = x - origin.getX();
					int ry = y - origin.getY();
					int rz = z - origin.getZ();
					box[0] = Math.min(box[0], rx);
					box[1] = Math.min(box[1], ry);
					box[2] = Math.min(box[2], rz);
					box[3] = Math.max(box[3], rx);
					box[4] = Math.max(box[4], ry);
					box[5] = Math.max(box[5], rz);
					layout = layout * 31L + (((rx * 31L + ry) * 31L + rz) * 31L + id.hashCode());
				}
			}
		}
		List<String> lines = new ArrayList<>();
		String name = site.feature();
		if (counts.isEmpty()) {
			lines.add(name + " placed=" + placed + " changed=0");
			return lines;
		}
		lines.add(name + " placed=" + placed + " box=" + box[0] + "," + box[1] + "," + box[2] + ".." + box[3] + ","
				+ box[4] + "," + box[5] + " layout=" + Long.toHexString(layout));
		counts.forEach((id, count) -> lines.add(name + " " + id + " " + count));
		return lines;
	}

	private static BlockState baseline(int y) {
		if (y > ROCK_TOP) {
			return Blocks.AIR.defaultBlockState();
		}
		if (y == ROCK_TOP) {
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}
		return y >= ROCK_TOP - DIRT_DEPTH ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/**
	 * The block id ({@code minecraft:} dropped) and its property VALUES in the state's own order, e.g.
	 * {@code oxidized_copper_trapdoor[east,top,false,false,false]} — short enough for a golden line.
	 */
	private static String stateId(BlockState state) {
		String full = state.toString().replace("Block{", "").replace("}", "").replace("minecraft:", "");
		int open = full.indexOf('[');
		if (open < 0) {
			return full;
		}
		StringBuilder out = new StringBuilder(full.substring(0, open + 1));
		String[] properties = full.substring(open + 1, full.length() - 1).split(",");
		for (int i = 0; i < properties.length; i++) {
			out.append(i == 0 ? "" : ",").append(properties[i].substring(properties[i].indexOf('=') + 1));
		}
		return out.append(']').toString();
	}
}

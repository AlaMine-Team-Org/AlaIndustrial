package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The passage between the ALA town's buildings (R61 of MOD-824, MOD-849): in both built-in towns, laid out by the
 * structure itself on flat ground of its biome, no two buildings stand closer than {@value #MIN_GAP} blocks — the
 * player and the shift workers walk between any two of them. Playtest 004 found the desert workshop and warehouse
 * wall to wall.
 *
 * <p>A building is a piece that follows its own Y (a template of the jigsaw assembly), whole: its box is what the
 * template carves. The gap is measured between the boxes along the axis where it is wider, so a building beside
 * another and one diagonal to it are held to the same {@value #MIN_GAP} blocks. The same rule guards the layouts
 * the templates are written from ({@code tools/ala_town_rules.py}); this one sees the data as the game lays it out.
 */
public final class AlaTownSpacingScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AlaTownSpacingScenarios::buildingsKeepAPassage, "ala_town_buildings_keep_a_passage")
						.ticks(20, 100));

		private Roster() {}
	}

	private AlaTownSpacingScenarios() {}

	/** The narrowest passage between two buildings of a town, in blocks (R61). */
	static final int MIN_GAP = 2;

	/** Buildings of a built-in town: the hall, four houses, the workshop, the pump station, the warehouse, the post. */
	private static final int BUILDINGS = 9;

	private record Town(Identifier id, ResourceKey<Biome> biome, ChunkPos chunk) {}

	/** Each town at two sites far from the other suites', so the layout is drawn twice with its own turn. */
	private static final List<Town> TOWNS = List.of(
			new Town(Industrialization.id("ala_town_plains"), Biomes.PLAINS, new ChunkPos(5300, 5300)),
			new Town(Industrialization.id("ala_town_plains"), Biomes.PLAINS, new ChunkPos(5303, 5309)),
			new Town(Industrialization.id("ala_town_desert"), Biomes.DESERT, new ChunkPos(5300, 5312)),
			new Town(Industrialization.id("ala_town_desert"), Biomes.DESERT, new ChunkPos(5307, 5321)));

	/**
	 * @implements MOD-849-GAP — every two buildings of both built-in towns, as the structure lays them out, keep at
	 *     least {@value #MIN_GAP} blocks between them; the check is first shown to fail on planted boxes wall to
	 *     wall, one block apart and overlapping, and to pass two blocks apart.
	 */
	public static void buildingsKeepAPassage(GameTestHelper helper) {
		String blind = blindSpot();
		if (blind != null) {
			helper.fail("the passage check is blind: " + blind);
			return;
		}
		ServerLevel level = helper.getLevel();
		for (Town town : TOWNS) {
			StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, town.id()),
					AlaTownSite.generator(level, town.biome()), town.chunk(), AlaTownScenarios.SEED);
			if (!start.isValid()) {
				helper.fail(town.id() + " at " + town.chunk() + ": no town on flat ground of its own biome");
				return;
			}
			List<PoolElementStructurePiece> buildings = new ArrayList<>();
			AlaTownSite.poolPieces(start).stream().filter(AlaTownSite::rigid).forEach(buildings::add);
			if (buildings.size() != BUILDINGS) {
				helper.fail(town.id() + " at " + town.chunk() + ": " + buildings.size()
						+ " buildings laid out, expected " + BUILDINGS);
				return;
			}
			String crowded = crowded(buildings);
			if (crowded != null) {
				helper.fail(town.id() + " at " + town.chunk() + " (turned "
						+ buildings.getFirst().getRotation().name() + "): " + crowded);
				return;
			}
		}
		helper.succeed();
	}

	/** The first two buildings closer than {@link #MIN_GAP}, or {@code null}. */
	private static String crowded(List<PoolElementStructurePiece> buildings) {
		for (int i = 0; i < buildings.size(); i++) {
			for (int j = i + 1; j < buildings.size(); j++) {
				PoolElementStructurePiece a = buildings.get(i);
				PoolElementStructurePiece b = buildings.get(j);
				int gap = gap(a.getBoundingBox(), b.getBoundingBox());
				if (gap < MIN_GAP) {
					return AlaTownSite.template(a).getPath() + " and " + AlaTownSite.template(b).getPath()
							+ (gap < 0 ? " overlap" : " leave a passage of " + gap + ", expected at least " + MIN_GAP);
				}
			}
		}
		return null;
	}

	/**
	 * Free blocks between two boxes along the axis where there are more of them; negative when the boxes overlap on
	 * both axes. Height does not count: buildings stand at their own terrain height.
	 */
	static int gap(BoundingBox a, BoundingBox b) {
		int alongX = Math.max(b.minX() - a.maxX(), a.minX() - b.maxX()) - 1;
		int alongZ = Math.max(b.minZ() - a.maxZ(), a.minZ() - b.maxZ()) - 1;
		return Math.max(alongX, alongZ);
	}

	/** What a planted pair shows the check getting wrong, or {@code null}: it must be able to fail. */
	private static String blindSpot() {
		BoundingBox workshop = new BoundingBox(16, 0, 26, 24, 5, 32);
		if (gap(workshop, new BoundingBox(25, 3, 26, 36, 9, 33)) >= MIN_GAP) {
			return "two buildings wall to wall pass";
		}
		if (gap(workshop, new BoundingBox(26, 0, 26, 37, 5, 33)) >= MIN_GAP) {
			return "one block between two buildings passes";
		}
		if (gap(workshop, new BoundingBox(20, 0, 30, 30, 5, 36)) >= MIN_GAP) {
			return "overlapping buildings pass";
		}
		if (gap(workshop, new BoundingBox(26, 0, 34, 30, 5, 40)) >= MIN_GAP) {
			return "one block between two buildings on each axis passes";
		}
		if (gap(workshop, new BoundingBox(27, 0, 26, 38, 5, 33)) < MIN_GAP
				|| gap(workshop, new BoundingBox(10, 0, 35, 14, 5, 40)) < MIN_GAP) {
			return "two blocks between two buildings fail";
		}
		return null;
	}
}

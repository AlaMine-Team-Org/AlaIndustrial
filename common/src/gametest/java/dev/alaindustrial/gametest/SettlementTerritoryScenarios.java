package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.parse;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.SettlementTerritory;
import dev.alaindustrial.settlement.SettlementTier;
import dev.alaindustrial.worldgen.town.TownFootprint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * L2 for the territory of an ALA town (MOD-848, epic decisions R59): the territory of a town a structure built holds
 * every piece of it — each building, the ground between them and the lab under the town hall — with the margin round
 * them, on both built-in styles, the desert town along its pipe included; a town of an older save, whose record knows
 * no footprint, takes it from its structure through its core; and the towns' file reads with and without footprints.
 *
 * <p>A town no structure built — a core on a rig, of a command, of the demo stand — keeps the square of its tier round
 * its core: {@code SettlementCoreScenarios} checks its edges as before.
 *
 * <p>The towns are laid out and placed as {@link AlaTownScenarios} does it ({@link AlaTownSite}), far from the other
 * suites' towns, so their territories touch nobody else's.
 */
public final class SettlementTerritoryScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SettlementTerritoryScenarios::plainsTownHoldsEveryPiece,
						"ala_town_territory_holds_every_piece_plains").ticks(200, 200),
				RosterEntry.of(SettlementTerritoryScenarios::desertTownHoldsEveryPiece,
						"ala_town_territory_holds_every_piece_desert").ticks(200, 200),
				RosterEntry.of(SettlementTerritoryScenarios::olderTownLearnsItsFootprint,
						"ala_town_territory_older_town_learns_footprint").ticks(200, 200),
				RosterEntry.of(SettlementTerritoryScenarios::townsReadWithAndWithoutAFootprint,
						"settlement_core_footprint_save_reads_with_and_without").ticks(20));

		private Roster() {}
	}

	private SettlementTerritoryScenarios() {}

	private static final Identifier PLAINS = Industrialization.id("ala_town_plains");
	private static final Identifier DESERT = Industrialization.id("ala_town_desert");

	/**
	 * @implements MOD-848-TOWN — the plains town: its record carries the footprint of all its pieces and its
	 *     territory holds every one of them with the margin round them, see {@link #holdsEveryPiece}.
	 */
	public static void plainsTownHoldsEveryPiece(GameTestHelper helper) {
		holdsEveryPiece(helper, PLAINS, Biomes.PLAINS, new ChunkPos(5200, 5200), false);
	}

	/**
	 * @implements MOD-848-TOWN — the desert town along its pipe, whose pump house and guard post stand past the LV
	 *     square round its core (the playtest's finding): every piece is in its territory all the same.
	 */
	public static void desertTownHoldsEveryPiece(GameTestHelper helper) {
		holdsEveryPiece(helper, DESERT, Biomes.DESERT, new ChunkPos(5200, 5212), true);
	}

	/**
	 * A town laid out and placed on flat ground: once the structure has registered it, its record carries the
	 * footprint of all the pieces of the start, its territory is that footprint grown by the margin and the LV square
	 * round the core — the rule — and every corner of every piece, the lab's included, is the town's by the rule every
	 * reader goes through ({@link Settlement#territoryContains}); the core's screen measures the same rectangle.
	 *
	 * @param pastTheSquare the control that makes the town's case: some piece of it stands outside the LV square
	 *                      round the core, so a territory of the square alone would lose it
	 */
	private static void holdsEveryPiece(GameTestHelper helper, Identifier id, ResourceKey<Biome> biome,
			ChunkPos chunk, boolean pastTheSquare) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = AlaTownSite.generator(level, biome);
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, id), generator, chunk,
				AlaTownScenarios.SEED);
		if (!start.isValid()) {
			helper.fail(id + ": no town on flat ground of its biome");
			return;
		}
		AlaTownSite.place(level, start, generator, AlaTownScenarios.SEED);
		BlockPos core = hallCore(level, start);
		if (core == null) {
			helper.fail(id + ": no settlement core in the town hall");
			return;
		}
		helper.runAfterDelay(3, () -> {
			Optional<Settlement> found = SettlementRegistry.get(level.getServer()).findAt(level.dimension(), core);
			if (found.isEmpty()) {
				helper.fail(id + ": the core at " + core + " has no settlement");
				return;
			}
			Settlement town = found.get();
			String wrong = checkHoldsEveryPiece(level, town, start, pastTheSquare);
			if (wrong == null) {
				wrong = checkScreenMeasuresTheTerritory(level, town, core);
			}
			if (wrong != null) {
				helper.fail(id + ": " + wrong);
				return;
			}
			helper.succeed();
		});
	}

	/** What is wrong with {@code town}'s territory over the pieces of {@code start}, or {@code null}. */
	private static String checkHoldsEveryPiece(ServerLevel level, Settlement town, StructureStart start,
			boolean pastTheSquare) {
		Optional<SettlementTerritory.Area> footprint = TownFootprint.of(start.getPieces());
		if (footprint.isEmpty() || !town.footprint().equals(footprint)) {
			return "the town's footprint is " + town.footprint() + ", its pieces cover " + footprint;
		}
		SettlementTerritory.Area square = SettlementTerritory.Area.square(town.corePos().getX(), town.corePos().getZ(),
				SettlementTier.LV.radius());
		boolean outsideTheSquare = false;
		for (StructurePiece piece : start.getPieces()) {
			BoundingBox box = piece.getBoundingBox();
			for (BlockPos corner : corners(box)) {
				if (!town.territoryContains(level.dimension(), corner)) {
					return name(piece) + " has its corner " + corner + " outside the territory " + town.territory();
				}
				outsideTheSquare |= !square.contains(corner.getX(), corner.getZ());
			}
			// The margin: a step past the piece, on each side, is still the town's.
			for (BlockPos corner : corners(box.inflatedBy(SettlementConfig.settlementTerritoryMargin))) {
				if (!town.territoryContains(level.dimension(), corner)) {
					return "the margin round " + name(piece) + " ends before " + corner;
				}
			}
		}
		if (pastTheSquare && !outsideTheSquare) {
			return "control: no piece of the town stands past the LV square round its core, so this case proves "
					+ "nothing";
		}
		// And no more than that: the rule's rectangle round the grown footprint and the square.
		SettlementTerritory.Area expected = footprint.get().grow(SettlementConfig.settlementTerritoryMargin)
				.envelope(square);
		if (!town.territory().equals(expected)) {
			return "territory " + town.territory() + ", expected the footprint " + footprint.get() + " grown by "
					+ SettlementConfig.settlementTerritoryMargin + " round the square " + square + ": " + expected;
		}
		return null;
	}

	/** The core's screen measures the territory the shell shows (MOD-848: width and depth instead of a radius). */
	private static String checkScreenMeasuresTheTerritory(ServerLevel level, Settlement town, BlockPos core) {
		if (!(level.getBlockEntity(core) instanceof SettlementCoreBlockEntity be)) {
			return "no settlement core block entity at " + core;
		}
		// Its first tick takes the core's place in its town; a screen opens on a core that has ticked.
		be.serverTick(level, core, level.getBlockState(core));
		ContainerData data = be.viewerData(UUID.nameUUIDFromBytes(new byte[] {8, 4, 8}));
		int width = data.get(SettlementCoreBlockEntity.Channel.TERRITORY_X.ordinal());
		int depth = data.get(SettlementCoreBlockEntity.Channel.TERRITORY_Z.ordinal());
		if (width != town.territory().sizeX() || depth != town.territory().sizeZ()) {
			return "the core's screen shows " + width + " x " + depth + ", the territory is "
					+ town.territory().sizeX() + " x " + town.territory().sizeZ();
		}
		return null;
	}

	/**
	 * @implements MOD-848-OLDER — a town registered before towns knew their footprint (a record with the town's style
	 *     and no footprint) takes it from its structure on its core's next tick, found through the chunks' structure
	 *     data; a styled town whose core stands outside every piece of a town keeps its square.
	 */
	public static void olderTownLearnsItsFootprint(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = AlaTownSite.generator(level, Biomes.DESERT);
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, DESERT), generator,
				new ChunkPos(5200, 5224), AlaTownScenarios.SEED);
		if (!start.isValid()) {
			helper.fail("no desert town on flat ground of its biome");
			return;
		}
		AlaTownSite.place(level, start, generator, AlaTownScenarios.SEED);
		recordInChunks(level, start);
		BlockPos hallCore = hallCore(level, start);
		if (hallCore == null) {
			helper.fail("no settlement core in the town hall");
			return;
		}
		BoundingBox hall = AlaTownSite.poolPieces(start).getFirst().getBoundingBox();
		BlockPos older = hallCore.above(2);
		if (!hall.isInside(older)) {
			helper.fail("control: " + older + " is not inside the town hall " + hall);
			return;
		}
		BlockPos lone = new BlockPos(hall.minX() - 500, hallCore.getY(), hall.minZ());
		helper.runAfterDelay(3, () -> {
			SettlementRegistry registry = SettlementRegistry.get(level.getServer());
			String style = Industrialization.id("desert").toString();
			// What a save written before MOD-848 holds: the structure gave its town a style, and nothing else.
			int olderId = registry.place(level.dimension(), older, 0).settlementId();
			registry.setStyle(olderId, style);
			int loneId = registry.place(level.dimension(), lone, 0).settlementId();
			registry.setStyle(loneId, style);
			if (registry.find(olderId).orElseThrow().footprint().isPresent()) {
				helper.fail("control: the older town has a footprint before its core ticked");
				return;
			}
			drive(level, older);
			drive(level, lone);
			Optional<SettlementTerritory.Area> expected = TownFootprint.of(start.getPieces());
			Settlement learnt = registry.find(olderId).orElseThrow();
			if (!learnt.footprint().equals(expected)) {
				helper.fail("the older town took " + learnt.footprint() + ", its structure covers " + expected);
				return;
			}
			for (StructurePiece piece : start.getPieces()) {
				for (BlockPos corner : corners(piece.getBoundingBox())) {
					if (!learnt.territoryContains(level.dimension(), corner)) {
						helper.fail("the older town still leaves " + name(piece) + " outside at " + corner);
						return;
					}
				}
			}
			Settlement alone = registry.find(loneId).orElseThrow();
			if (alone.footprint().isPresent() || !alone.territory().equals(SettlementTerritory.Area.square(
					lone.getX(), lone.getZ(), SettlementTier.LV.radius()))) {
				helper.fail("a styled town standing in no structure must keep its square, has " + alone.territory());
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-848-SAVE — a towns' file written before footprints reads its town as before, a square round the
	 *     core, and writes it back without the field; a footprint comes back from a save as it went in; a footprint
	 *     with crossed bounds costs the town nothing but its footprint.
	 * @covers R-PER-01
	 */
	public static void townsReadWithAndWithoutAFootprint(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		String town = "{id:7,dimension:\"minecraft:overworld\",core:[I;10,64,-20],style:\"alaindustrial:desert\","
				+ "active:1b";
		SettlementRegistry old = SettlementRegistry.CODEC.parse(ops, parse("{settlements:[" + town + "}],next_id:8}"))
				.getOrThrow();
		Settlement read = old.find(7).orElseThrow();
		SettlementTerritory.Area square = SettlementTerritory.Area.square(10, -20, SettlementTier.LV.radius());
		helper.assertTrue(read.footprint().isEmpty() && read.territory().equals(square),
				"a town saved before footprints must read as the square " + square + ", read " + read);
		helper.assertTrue(!firstTown(SettlementRegistry.CODEC.encodeStart(ops, old).getOrThrow()).contains("footprint"),
				"a town without a footprint is written with a footprint field");

		SettlementTerritory.Area footprint = new SettlementTerritory.Area(4, -45, 80, -2);
		old.setFootprint(7, footprint);
		SettlementRegistry again = SettlementRegistry.CODEC.parse(ops,
				SettlementRegistry.CODEC.encodeStart(ops, old).getOrThrow()).getOrThrow();
		Settlement back = again.find(7).orElseThrow();
		helper.assertTrue(back.equals(old.find(7).orElseThrow()) && back.footprint().equals(Optional.of(footprint)),
				"the footprint changed through a save: " + back);
		helper.assertTrue(back.territory().equals(footprint.grow(SettlementConfig.settlementTerritoryMargin)
				.envelope(square)), "a town with a footprint reads the rule's territory, read " + back.territory());

		SettlementRegistry broken = SettlementRegistry.CODEC.parse(ops, parse("{settlements:[" + town
				+ ",footprint:{min_x:9,min_z:0,max_x:1,max_z:0}}],next_id:8}")).getOrThrow();
		helper.assertTrue(broken.find(7).map(t -> t.footprint().isEmpty() && t.style().endsWith("desert"))
				.orElse(false), "a crossed footprint must leave the town, without it: " + broken.all());
		helper.succeed();
	}

	/** The first town of an encoded registry. */
	private static CompoundTag firstTown(Tag encoded) {
		ListTag towns = ((CompoundTag) encoded).getListOrEmpty("settlements");
		return towns.getCompoundOrEmpty(0);
	}

	/** The settlement core standing in the town hall, the first pool piece; {@code null} without one. */
	private static BlockPos hallCore(ServerLevel level, StructureStart start) {
		BoundingBox hall = AlaTownSite.poolPieces(start).getFirst().getBoundingBox();
		for (BlockPos pos : BlockPos.betweenClosed(hall.minX(), hall.minY(), hall.minZ(), hall.maxX(), hall.maxY(),
				hall.maxZ())) {
			if (level.getBlockState(pos).is(ModContent.SETTLEMENT_CORE.get())) {
				return pos.immutable();
			}
		}
		return null;
	}

	/**
	 * Records {@code start} in the chunks the way world generation does — the start in its own chunk, a reference in
	 * every chunk it covers — so the structure manager finds the town at a position, as it would in a generated world.
	 */
	private static void recordInChunks(ServerLevel level, StructureStart start) {
		ChunkPos home = start.getChunkPos();
		level.getChunk(home.x(), home.z()).setStartForStructure(start.getStructure(), start);
		BoundingBox box = start.getBoundingBox();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				level.getChunk(cx, cz).addReferenceForStructure(start.getStructure(), home.pack());
			}
		}
	}

	/** Puts a core at {@code pos} — data-less, as a template places it — and drives it one tick. */
	private static void drive(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, ModContent.SETTLEMENT_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof SettlementCoreBlockEntity core) {
			core.serverTick(level, pos, level.getBlockState(pos));
		}
	}

	/** The four corner columns of {@code box}, at its floor. */
	private static List<BlockPos> corners(BoundingBox box) {
		List<BlockPos> out = new ArrayList<>();
		for (int x : new int[] {box.minX(), box.maxX()}) {
			for (int z : new int[] {box.minZ(), box.maxZ()}) {
				out.add(new BlockPos(x, box.minY(), z));
			}
		}
		return out;
	}

	private static String name(StructurePiece piece) {
		if (piece instanceof PoolElementStructurePiece pool && AlaTownSite.template(pool) != null) {
			return AlaTownSite.template(pool).toString();
		}
		return piece.getClass().getSimpleName() + " " + piece.getBoundingBox();
	}
}

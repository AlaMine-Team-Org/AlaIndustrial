package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.EngravedPlateBlock;
import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import dev.alaindustrial.worldgen.AbandonedLabPlacer;
import dev.alaindustrial.worldgen.OilLakeFilter;
import dev.alaindustrial.worldgen.town.AlaTownLabPiece;
import dev.alaindustrial.worldgen.town.AlaTownStructure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * L2 suite for the ALA town (MOD-830): the town is a structure from data on both loaders, it lays out its
 * templates as the approved plan with the lab under the town hall, stands on its ground, survives a save, and a
 * third style added as data alone appears in the world.
 *
 * <p>Laid out by the structure's own {@code generate} against a flat generator and placed chunk by chunk, the way
 * {@code /place structure} does ({@link AlaTownSite}). How towns spread over a real world — their number and
 * distance — needs a generated world and is not measured here.
 */
public final class AlaTownScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AlaTownScenarios::townsAreDataOfBothLoaders, "ala_town_is_data_of_both_loaders")
						.ticks(20, 100),
				RosterEntry.of(AlaTownScenarios::plainsTownPlacesWithItsLab, "ala_town_plains_places_with_its_lab")
						.ticks(200, 200),
				RosterEntry.of(AlaTownScenarios::desertTownPlacesWithItsLab, "ala_town_desert_places_with_its_lab")
						.ticks(200, 200),
				RosterEntry.of(AlaTownScenarios::thirdStyleFromDataPlaces, "ala_town_third_style_from_data_places")
						.ticks(100, 100),
				RosterEntry.of(AlaTownScenarios::layoutMatchesTheSnapshot, "ala_town_layout_snapshot_matches")
						.ticks(100, 100));

		private Roster() {}
	}

	private AlaTownScenarios() {}

	/** The world seed the towns of this suite are laid out with; the plaques read the level's own seed. */
	static final long SEED = 830L;

	private static final Identifier PLAINS = Industrialization.id("ala_town_plains");
	private static final Identifier DESERT = Industrialization.id("ala_town_desert");

	/** The third style: a datapack of the gametest lane, no code (`data/ala_town_test/` of both loaders). */
	private static final Identifier THIRD = Identifier.fromNamespaceAndPath("ala_town_test", "ala_town_ruin");

	/**
	 * Plaques a built-in town carries at least: the four houses' (one fell with its house's corner). The plains town
	 * hall has none — the main building without a number is the owner's riddle (MOD-845).
	 */
	private static final int PLAQUES = 4;

	/** Share of a building's floor that must stand on solid ground. */
	private static final double MIN_SUPPORT = 0.95;

	/**
	 * @implements MOD-830-DATA — both towns are structures of the mod's type, each with its style found in the
	 *     datapack registry {@code settlement_style} the way world generation sees it (the server's registries),
	 *     biome tags that keep them in plains and desert only, a placement set spread wider than the villages',
	 *     and a place in the tag that keeps oil lakes out of their area.
	 */
	public static void townsAreDataOfBothLoaders(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (Identifier id : List.of(PLAINS, DESERT)) {
			Structure structure = AlaTownSite.structure(level, id).value();
			if (!(structure instanceof AlaTownStructure town)) {
				helper.fail(id + " is " + structure.getClass().getSimpleName() + ", not an ALA town");
				return;
			}
			if (level.registryAccess().lookupOrThrow(ModDataRegistries.SETTLEMENT_STYLE).get(town.style()).isEmpty()) {
				helper.fail(id + ": its style " + town.style().identifier()
						+ " is not in the settlement_style registry");
				return;
			}
		}
		if (!biomes(level, PLAINS, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS) || biomes(level, PLAINS, Biomes.DESERT)
				|| !biomes(level, DESERT, Biomes.DESERT) || biomes(level, DESERT, Biomes.PLAINS)
				|| biomes(level, PLAINS, Biomes.OCEAN) || biomes(level, DESERT, Biomes.JUNGLE)) {
			helper.fail("the towns' biome tags are not plains and desert only");
			return;
		}
		StructureSet towns = set(level, "ala_towns");
		StructureSet villages = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("villages")))
				.value();
		if (!(towns.placement() instanceof RandomSpreadStructurePlacement spread)
				|| !(villages.placement() instanceof RandomSpreadStructurePlacement village)
				|| spread.spacing() <= village.spacing() || spread.separation() <= village.separation()) {
			helper.fail("ala_towns is not a random spread wider than the villages'");
			return;
		}
		Set<Identifier> listed = new TreeSet<>();
		towns.structures().forEach(entry -> entry.structure().unwrapKey().ifPresent(k -> listed.add(k.identifier())));
		if (!listed.equals(new TreeSet<>(List.of(PLAINS, DESERT)))) {
			helper.fail("ala_towns lists " + listed);
			return;
		}
		for (Identifier id : List.of(PLAINS, DESERT)) {
			if (!AlaTownSite.structure(level, id).is(OilLakeFilter.NO_OIL_LAKES_IN_AREA)) {
				helper.fail(id + " is not in #alaindustrial:no_oil_lakes_in_area");
				return;
			}
		}
		helper.succeed();
	}

	/** @implements MOD-830-PLACE — the plains town, see {@link #placesWithItsLab}. */
	public static void plainsTownPlacesWithItsLab(GameTestHelper helper) {
		placesWithItsLab(helper, PLAINS, "plains", Biomes.PLAINS, new ChunkPos(5000, 5000));
	}

	/** @implements MOD-830-PLACE — the desert town, see {@link #placesWithItsLab}. */
	public static void desertTownPlacesWithItsLab(GameTestHelper helper) {
		placesWithItsLab(helper, DESERT, "desert", Biomes.DESERT, new ChunkPos(5000, 5012));
	}

	/**
	 * A town on flat ground: every template of its style is laid out once, the buildings' floors lie on the
	 * surface and stand on solid ground, the lab is under the town hall and cuts no other piece, the hatch in the
	 * hall leads down a ladder into the lab, the lab's plaque and the houses' plaques carry their numbers, the
	 * numbers reach the record service, the hall's settlement core has a settlement of the town's style, the hall
	 * holds one whole reception capsule facing its core that serves that settlement (MOD-833, R55: dark core,
	 * nobody owns it yet, so nobody breaks it), and the town reads back from a save as it was.
	 */
	private static void placesWithItsLab(GameTestHelper helper, Identifier id, String style, ResourceKey<Biome> biome,
			ChunkPos chunk) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = AlaTownSite.generator(level, biome);
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, id), generator, chunk, SEED);
		String layout = checkLayout(level, start, style);
		if (layout != null) {
			helper.fail(id + ": " + layout);
			return;
		}
		AlaTownSite.place(level, start, generator, SEED);
		AlaTownLabPiece lab = AlaTownSite.labs(start).getFirst();
		String placed = checkPlaced(level, start, lab);
		if (placed != null) {
			helper.fail(id + ": " + placed);
			return;
		}
		String saved = checkSaveRoundTrip(level, start, chunk);
		if (saved != null) {
			helper.fail(id + ": " + saved);
			return;
		}
		BoundingBox hall = AlaTownSite.poolPieces(start).getFirst().getBoundingBox();
		List<BlockPos> cores = cores(level, hall);
		if (cores.size() != 1) {
			helper.fail(id + ": " + cores.size() + " settlement cores in the town hall, expected 1");
			return;
		}
		List<BlockPos> capsules = capsules(level, hall);
		String capsuleShape = capsules.size() == 1 ? checkCapsule(level, capsules.getFirst(), cores.getFirst())
				: capsules.size() + " reception capsules in the town hall, expected 1";
		if (capsuleShape != null) {
			helper.fail(id + ": " + capsuleShape);
			return;
		}
		List<RecordNumber> former = formerResidents(level, start);
		if (former.size() < PLAQUES) {
			helper.fail(id + ": only " + former.size() + " plaques carry their former resident's number, expected "
					+ PLAQUES);
			return;
		}
		helper.runAfterDelay(3, () -> {
			RecordNumberLedger ledger = RecordNumberLedger.get(level.getServer());
			for (RecordNumber number : former) {
				if (ledger.statusOf(number) != RecordNumber.Status.CLOSED) {
					helper.fail(id + ": former resident " + number + " is " + ledger.statusOf(number) + ", not closed");
					return;
				}
			}
			if (!ledger.isKnownLabNumber(lab.labNumber(level.getSeed()))) {
				helper.fail(id + ": the town lab's number " + lab.labNumber(level.getSeed())
						+ " did not reach the record service");
				return;
			}
			String settlementStyle = SettlementRegistry.get(level.getServer())
					.findAt(level.dimension(), cores.getFirst()).map(Settlement::style).orElse(null);
			if (!Industrialization.id(style).toString().equals(settlementStyle)) {
				helper.fail(id + ": the core at " + cores.getFirst() + " has settlement style " + settlementStyle);
				return;
			}
			String capsuleWork = checkCapsuleServesTheTown(helper, capsules.getFirst(), cores.getFirst());
			if (capsuleWork != null) {
				helper.fail(id + ": " + capsuleWork);
				return;
			}
			helper.succeed();
		});
	}

	/** What is wrong with the laid-out town, or {@code null}. */
	private static String checkLayout(ServerLevel level, StructureStart start, String style) {
		if (!start.isValid()) {
			return "no town on flat ground of its own biome";
		}
		List<Identifier> placed = new ArrayList<>();
		AlaTownSite.poolPieces(start).forEach(piece -> placed.add(AlaTownSite.template(piece)));
		List<Identifier> expected = AlaTownSite.styleTemplates(level, style);
		if (placed.size() != expected.size() || !new HashSet<>(placed).equals(new HashSet<>(expected))) {
			Set<Identifier> missing = new TreeSet<>(expected);
			placed.forEach(missing::remove);
			return placed.size() + " pieces laid out of " + expected.size() + "; missing " + missing;
		}
		List<AlaTownLabPiece> labs = AlaTownSite.labs(start);
		if (labs.size() != 1) {
			return labs.size() + " lab pieces, expected 1";
		}
		AlaTownLabPiece lab = labs.getFirst();
		for (PoolElementStructurePiece piece : AlaTownSite.poolPieces(start)) {
			if (piece.getBoundingBox().intersects(lab.labBox())) {
				return "the lab cuts " + AlaTownSite.template(piece);
			}
			if (AlaTownSite.rigid(piece) && piece.getBoundingBox().minY() != AlaTownSite.SURFACE_Y) {
				return AlaTownSite.template(piece) + " has its floor at " + piece.getBoundingBox().minY()
						+ ", not on the surface " + AlaTownSite.SURFACE_Y;
			}
			if (AlaTownSite.template(piece).getPath().endsWith("/town_hall")
					&& !piece.getBoundingBox().isInside(lab.hatch())) {
				return "the hatch " + lab.hatch() + " is not inside the town hall";
			}
		}
		return null;
	}

	/** What is wrong with the town once placed, or {@code null}. */
	private static String checkPlaced(ServerLevel level, StructureStart start, AlaTownLabPiece lab) {
		for (PoolElementStructurePiece piece : AlaTownSite.poolPieces(start)) {
			if (AlaTownSite.rigid(piece)) {
				BoundingBox box = piece.getBoundingBox();
				int solid = 0;
				for (int x = box.minX(); x <= box.maxX(); x++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						BlockPos under = new BlockPos(x, box.minY() - 1, z);
						solid += level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) ? 1 : 0;
					}
				}
				double support = solid / (double) (box.getXSpan() * box.getZSpan());
				if (support < MIN_SUPPORT) {
					return AlaTownSite.template(piece) + " stands on solid ground under " + Math.round(support * 100)
							+ "% of its floor";
				}
			}
		}
		if (!level.getBlockState(lab.hatch()).is(Blocks.COPPER_TRAPDOOR.weathering().oxidized())) {
			return "no copper hatch at " + lab.hatch() + ": " + level.getBlockState(lab.hatch());
		}
		for (int y = lab.hatch().getY() - 1; y > lab.labBox().maxY(); y--) {
			if (!level.getBlockState(lab.hatch().atY(y)).is(Blocks.LADDER)) {
				return "the shaft under the hatch breaks at Y " + y + ": " + level.getBlockState(lab.hatch().atY(y));
			}
		}
		List<Integer> plaque = plaqueDigits(level, lab.labBox());
		int number = lab.labNumber(level.getSeed());
		if (!plaque.equals(List.of(number / 100, number / 10 % 10, number % 10))) {
			return "the lab's plaque reads " + plaque + ", expected " + number;
		}
		return null;
	}

	/** A saved town reads back with the same pieces in the same places — the lab piece's type included. */
	private static String checkSaveRoundTrip(ServerLevel level, StructureStart start, ChunkPos chunk) {
		StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
		StructureStart back = StructureStart.loadStaticStart(context, start.createTag(context, chunk), SEED);
		if (back == null || back.getPieces().size() != start.getPieces().size()) {
			return "the town did not read back from its save";
		}
		for (int i = 0; i < start.getPieces().size(); i++) {
			if (!back.getPieces().get(i).getBoundingBox().equals(start.getPieces().get(i).getBoundingBox())) {
				return "piece " + i + " read back at " + back.getPieces().get(i).getBoundingBox();
			}
		}
		AlaTownLabPiece lab = AlaTownSite.labs(back).getFirst();
		boolean same = lab.hatch().equals(AlaTownSite.labs(start).getFirst().hatch());
		return same ? null : "the lab's hatch moved on reload";
	}

	/**
	 * The former residents of the town's plaques as the world shows them: in every building, the plaque's digit
	 * plates read back and matched against the number of its first plate — all three digits on a whole plaque,
	 * the last ones on a plaque that fell with a collapsed corner.
	 */
	private static List<RecordNumber> formerResidents(ServerLevel level, StructureStart start) {
		List<RecordNumber> out = new ArrayList<>();
		for (PoolElementStructurePiece piece : AlaTownSite.poolPieces(start)) {
			List<BlockPos> plates = plates(level, piece.getBoundingBox());
			if (!AlaTownSite.rigid(piece) || plates.isEmpty()) {
				continue;
			}
			AbandonedLabPlacer.sortForReader(plates,
					level.getBlockState(plates.getFirst()).getValue(HorizontalDirectionalBlock.FACING));
			BlockPos anchor = plates.getFirst();
			RecordNumber number = RecordNumberLedger.formerResidentAt(level.getSeed(), anchor.getX(), anchor.getY(),
					anchor.getZ());
			List<Integer> shown = digitsOf(level, plates);
			List<Integer> all = List.of(number.digits() / 100, number.digits() / 10 % 10, number.digits() % 10);
			if (!shown.isEmpty() && shown.equals(all.subList(all.size() - shown.size(), all.size()))) {
				out.add(number);
			}
		}
		return out;
	}

	/** The digits of the one plaque in {@code box}, in reading order; empty without a four-plate plaque. */
	private static List<Integer> plaqueDigits(ServerLevel level, BoundingBox box) {
		List<BlockPos> plates = plates(level, box);
		if (plates.size() != 4) {
			return List.of();
		}
		AbandonedLabPlacer.sortForReader(plates,
				level.getBlockState(plates.getFirst()).getValue(HorizontalDirectionalBlock.FACING));
		return digitsOf(level, plates);
	}

	/** The digits the digit plates among {@code plates} show, in their order; broken letters are skipped. */
	private static List<Integer> digitsOf(ServerLevel level, List<BlockPos> plates) {
		List<Integer> digits = new ArrayList<>();
		for (BlockPos plate : plates) {
			Block block = level.getBlockState(plate).getBlock();
			for (int d = 0; d < AbandonedLabPlacer.DIGITS.size(); d++) {
				if (AbandonedLabPlacer.DIGITS.get(d).get() == block) {
					digits.add(d);
				}
			}
		}
		return digits;
	}

	/**
	 * What is wrong with the hall's capsule at {@code lower}, or {@code null}: the flask stands on it — its middle
	 * and its upper cell (MOD-850) — and its open front looks at the core, three cells away.
	 */
	private static String checkCapsule(ServerLevel level, BlockPos lower, BlockPos core) {
		for (ReceptionCapsuleBlock.Part part : List.of(ReceptionCapsuleBlock.Part.MIDDLE,
				ReceptionCapsuleBlock.Part.UPPER)) {
			BlockState cell = level.getBlockState(lower.above(part.aboveLower()));
			if (!cell.is(ModContent.RECEPTION_CAPSULE.get()) || cell.getValue(ReceptionCapsuleBlock.PART) != part) {
				return "no " + part.getSerializedName() + " flask cell over the reception capsule at " + lower + ": "
						+ cell;
			}
		}
		Direction facing = level.getBlockState(lower).getValue(ReceptionCapsuleBlock.FACING);
		if (!lower.relative(facing, 3).equals(core)) {
			return "the reception capsule at " + lower + " faces " + facing + ", not its core at " + core;
		}
		return null;
	}

	/**
	 * What is wrong with the work of the capsule the structure placed, or {@code null}: on its first tick it takes
	 * the hall's settlement, refuses only because the core is dark, and — with no placer recorded and nobody owning
	 * the town yet — no player may break it, not even with a pickaxe (R36: the town's owner breaks it).
	 */
	private static String checkCapsuleServesTheTown(GameTestHelper helper, BlockPos lower, BlockPos core) {
		ServerLevel level = helper.getLevel();
		if (!(level.getBlockEntity(lower) instanceof ReceptionCapsuleBlockEntity capsule)) {
			return "the reception capsule at " + lower + " has no block entity";
		}
		AlaGameTestHelper.drive(capsule, helper, 1);
		int town = SettlementRegistry.get(level.getServer()).findAt(level.dimension(), core).map(Settlement::id)
				.orElse(0);
		if (town == 0 || capsule.settlementId() != town) {
			return "the reception capsule serves settlement " + capsule.settlementId() + ", the hall's is " + town;
		}
		if (capsule.verdict() != ReceptionCapsuleRules.Verdict.DARK) {
			return "the reception capsule of a dark town says " + capsule.verdict() + ", expected DARK";
		}
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
		if (capsule.mayBeBrokenBy(player)) {
			return "a player may break the capsule of a town nobody owns";
		}
		return null;
	}

	/** The lower cells of the reception capsules standing in {@code box}. */
	private static List<BlockPos> capsules(ServerLevel level, BoundingBox box) {
		List<BlockPos> capsules = new ArrayList<>();
		BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(pos -> {
			BlockState state = level.getBlockState(pos);
			if (state.is(ModContent.RECEPTION_CAPSULE.get())
					&& state.getValue(ReceptionCapsuleBlock.PART) == ReceptionCapsuleBlock.Part.LOWER) {
				capsules.add(pos.immutable());
			}
		});
		return capsules;
	}

	/** The settlement cores standing in {@code box}. */
	private static List<BlockPos> cores(ServerLevel level, BoundingBox box) {
		List<BlockPos> cores = new ArrayList<>();
		BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(pos -> {
			if (level.getBlockState(pos).is(ModContent.SETTLEMENT_CORE.get())) {
				cores.add(pos.immutable());
			}
		});
		return cores;
	}

	private static List<BlockPos> plates(ServerLevel level, BoundingBox box) {
		List<BlockPos> plates = new ArrayList<>();
		BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(pos -> {
			if (level.getBlockState(pos).getBlock() instanceof EngravedPlateBlock) {
				plates.add(pos.immutable());
			}
		});
		return plates;
	}

	/**
	 * @implements MOD-830-DATAPACK — a third style that only a datapack adds (the gametest lane's
	 *     {@code ala_town_test} namespace: a structure of the mod's type, its style and a pool of its own, no line
	 *     of code) is laid out and placed in the world.
	 */
	public static void thirdStyleFromDataPlaces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder.Reference<Structure> structure = AlaTownSite.structure(level, THIRD);
		if (!(structure.value() instanceof AlaTownStructure town)
				|| level.registryAccess().lookupOrThrow(ModDataRegistries.SETTLEMENT_STYLE).get(town.style())
						.isEmpty()) {
			helper.fail(THIRD + " is not an ALA town with a style in the registry");
			return;
		}
		ChunkGenerator generator = AlaTownSite.generator(level, Biomes.PLAINS);
		ChunkPos chunk = new ChunkPos(5000, 5024);
		StructureStart start = AlaTownSite.generate(level, structure, generator, chunk, SEED);
		if (!start.isValid() || AlaTownSite.poolPieces(start).isEmpty()) {
			helper.fail(THIRD + " was not laid out on flat ground of its biome");
			return;
		}
		AlaTownSite.place(level, start, generator, SEED);
		PoolElementStructurePiece first = AlaTownSite.poolPieces(start).getFirst();
		if (plates(level, first.getBoundingBox()).size() != 4) {
			helper.fail(THIRD + ": the placed piece " + AlaTownSite.template(first) + " has no plaque in the world");
			return;
		}
		helper.succeed();
	}

	static final String WRITE_TO_PROPERTY = "alaindustrial.alaTownLayoutSnapshot.writeTo";

	/**
	 * @implements MOD-830-LAYOUT — the layout of both built-in towns, laid out on flat ground with a fixed seed:
	 *     every piece's template, its place relative to the town hall and its turn, and what was drawn for the
	 *     lab, compared line by line with {@link AlaTownLayoutSnapshot}, which only its explicit command rewrites
	 *     (ADR-032).
	 */
	public static void layoutMatchesTheSnapshot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> lines = new ArrayList<>();
		lines.addAll(layoutLines(level, PLAINS, "plains", Biomes.PLAINS, new ChunkPos(5100, 5100)));
		lines.addAll(layoutLines(level, DESERT, "desert", Biomes.DESERT, new ChunkPos(5100, 5112)));
		GoldenLines.check(helper, "AlaTownLayoutSnapshot", WRITE_TO_PROPERTY, "*:ala_town_layout_snapshot_matches",
				AlaTownLayoutSnapshot.LINES, lines);
	}

	private static List<String> layoutLines(ServerLevel level, Identifier id, String style, ResourceKey<Biome> biome,
			ChunkPos chunk) {
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, id),
				AlaTownSite.generator(level, biome), chunk, SEED);
		List<String> lines = new ArrayList<>();
		if (!start.isValid()) {
			lines.add(style + " no town");
			return lines;
		}
		List<PoolElementStructurePiece> pieces = AlaTownSite.poolPieces(start);
		BlockPos origin = pieces.getFirst().getPosition();
		String prefix = "ala_town/" + style + "/";
		for (PoolElementStructurePiece piece : pieces) {
			BlockPos at = piece.getPosition().subtract(origin);
			lines.add(style + " " + AlaTownSite.template(piece).getPath().replace(prefix, "") + " " + at.getX() + ","
					+ at.getY() + "," + at.getZ() + " " + piece.getRotation().name());
		}
		for (AlaTownLabPiece lab : AlaTownSite.labs(start)) {
			BlockPos hatch = lab.hatch().subtract(origin);
			lines.add(style + " " + lab.describe() + " hatch=" + hatch.getX() + "," + hatch.getY() + ","
					+ hatch.getZ());
		}
		return lines;
	}

	@SafeVarargs
	private static boolean biomes(ServerLevel level, Identifier structure, ResourceKey<Biome>... biomes) {
		var set = AlaTownSite.structure(level, structure).value().biomes();
		for (ResourceKey<Biome> biome : biomes) {
			if (!set.contains(level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome))) {
				return false;
			}
		}
		return true;
	}

	private static StructureSet set(ServerLevel level, String path) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Industrialization.id(path))).value();
	}
}

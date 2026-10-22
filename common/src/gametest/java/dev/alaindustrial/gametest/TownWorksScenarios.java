package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.parse;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.BuildingStateRules;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.TownWorksService;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * L2 suite for the buildings and the work queue of an ALA town (MOD-857, epic decisions R79, R86, R95): a generated
 * town knows all its buildings; a town of an older save learns them from its structure on its core's first tick; a
 * town hall's state follows the world — a ruin, cleared, restored with a line in the hall's log, back in repair when a
 * block of its roof is broken, intact again when it is put back; the tab's controls are the owner's alone; and the
 * towns' file reads with and without the new field.
 */
public final class TownWorksScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownWorksScenarios::townKnowsItsBuildings, "town_works_town_knows_its_buildings")
						.ticks(200, 200),
				RosterEntry.of(TownWorksScenarios::olderTownLearnsItsBuildings,
						"town_works_older_town_learns_its_buildings").ticks(200, 200),
				RosterEntry.of(TownWorksScenarios::hallStateFollowsTheWorld, "town_works_hall_state_follows_the_world")
						.ticks(40, 40),
				RosterEntry.of(TownWorksScenarios::worksReadWithAndWithoutTheField,
						"town_works_save_reads_with_and_without").ticks(20));

		private Roster() {}
	}

	private TownWorksScenarios() {}

	private static final Identifier PLAINS = Industrialization.id("ala_town_plains");
	private static final Identifier DESERT = Industrialization.id("ala_town_desert");
	private static final Identifier PLAINS_HALL = Industrialization.id("ala_town/plains/town_hall");
	private static final Identifier PLAINS_HALL_INTACT = Industrialization.id("ala_town/plains/intact/town_hall");
	/** The plains town hall's core in its template (docs/structures/ala_town.md). */
	private static final BlockPos HALL_CORE = new BlockPos(6, 1, 5);
	/** A roof cell of the intact plains town hall (layer y = 9), part of the «roof» key point. */
	private static final BlockPos HALL_ROOF = new BlockPos(6, 9, 5);

	/**
	 * @implements MOD-857-REGISTRY — a generated town's record knows every building of its structure: the template, the
	 *     intact template where there is one (the town hall), where the structure put it and how it turned it.
	 */
	public static void townKnowsItsBuildings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = AlaTownSite.generator(level, Biomes.PLAINS);
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, PLAINS), generator,
				new ChunkPos(5400, 5400), AlaTownScenarios.SEED);
		if (!start.isValid()) {
			helper.fail("no plains town on flat ground of its biome");
			return;
		}
		AlaTownSite.place(level, start, generator, AlaTownScenarios.SEED);
		BlockPos core = hallCore(level, start);
		helper.runAfterDelay(3, () -> {
			Optional<Settlement> town = SettlementRegistry.get(level.getServer()).findAt(level.dimension(), core);
			if (town.isEmpty()) {
				helper.fail("no settlement at the town hall's core " + core);
				return;
			}
			TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.get().id());
			String problem = checkBuildings(start, works);
			if (problem != null) {
				helper.fail(problem);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-857-OLDER — a town registered before towns knew their buildings learns them from its structure
	 *     on its core's next tick, found through the chunks' structure data.
	 */
	public static void olderTownLearnsItsBuildings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = AlaTownSite.generator(level, Biomes.DESERT);
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, DESERT), generator,
				new ChunkPos(5400, 5412), AlaTownScenarios.SEED);
		if (!start.isValid()) {
			helper.fail("no desert town on flat ground of its biome");
			return;
		}
		AlaTownSite.place(level, start, generator, AlaTownScenarios.SEED);
		recordInChunks(level, start);
		BlockPos core = hallCore(level, start);
		helper.runAfterDelay(3, () -> {
			SettlementRegistry registry = SettlementRegistry.get(level.getServer());
			int id = registry.findAt(level.dimension(), core).map(Settlement::id).orElse(0);
			if (id <= 0) {
				helper.fail("no settlement at the town hall's core " + core);
				return;
			}
			// What a save written before MOD-857 holds: the town with its style and footprint, and no buildings.
			registry.works().forget(id);
			if (registry.works().town(id).buildingsKnown()) {
				helper.fail("control: the town still knows its buildings after forgetting them");
				return;
			}
			if (!(level.getBlockEntity(core) instanceof SettlementCoreBlockEntity be)) {
				helper.fail("no settlement core block entity at " + core);
				return;
			}
			be.serverTick(level, core, level.getBlockState(core));
			String problem = checkBuildings(start, registry.works().town(id));
			if (problem != null) {
				helper.fail("after the core's tick: " + problem);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements MOD-857-STATE — the town hall's state is derived from the world against its intact template: a ruin,
	 *     clearing while its clearing job is open, intact once it matches (one line in the hall's log), back in repair
	 *     when a block of its roof is broken, intact again without a second line when it is put back.
	 * @implements MOD-857-OWNER — the tab offers the controls to the town's owner only, all but the hall's schematic
	 *     switch, which is visual and goes to everyone.
	 */
	public static void hallStateFollowsTheWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		// A cell of the rig grid (TownDroneScenarios#nextOrigin): its chunks are shared with the other
		// halls, and no other case stands a hall or a town there.
		BlockPos origin = TownDroneScenarios.nextOrigin();
		placeTemplate(level, PLAINS_HALL, origin);
		BlockPos core = origin.offset(HALL_CORE);
		int id = registry.place(level.dimension(), core, 0).settlementId();
		registry.works().forget(id);
		TownWorks works = registry.works().town(id);
		works.learnBuildings(List.of(TownBuilding.found(PLAINS_HALL, Optional.of(PLAINS_HALL_INTACT),
				"settlement.alaindustrial.building.town_hall", origin, Rotation.NONE)));

		BuildingStateRules.Verdict ruin = measure(helper, level, id);
		TownBuilding measured = works.buildings().getFirst();
		helper.assertTrue(ruin.state() == BuildingState.RUIN && measured.percent() < 90 && measured.percent() > 0
				&& measured.missingKeys().contains("settlement.alaindustrial.building.key.roof"),
				"the ruin of the town hall measured " + measured);

		WorkJob clear = works.queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		helper.assertTrue(works.buildings().getFirst().state() == BuildingState.CLEARING,
				"a raised clearing job must show the hall as being cleared, it reads "
						+ works.buildings().getFirst().state());
		Settlement town = registry.find(id).orElseThrow();
		checkControls(helper, level, town, works);
		works.queue().cancel(clear.id());
		helper.assertTrue(works.buildings().getFirst().state() == BuildingState.RUIN,
				"a cancelled clearing job must leave a ruin, it reads " + works.buildings().getFirst().state());

		int logBefore = registry.workers().log().size();
		placeTemplate(level, PLAINS_HALL_INTACT, origin);
		BuildingStateRules.Verdict restored = measure(helper, level, id);
		helper.assertTrue(restored.state() == BuildingState.INTACT && restored.justRestored()
				&& works.buildings().getFirst().percent() == 100, "the intact hall measured " + restored + " "
				+ works.buildings().getFirst());
		List<ShiftWorkerBook.LogLine> added = registry.workers().log().subList(logBefore,
				registry.workers().log().size());
		helper.assertTrue(added.size() == 1 && added.getFirst().settlement() == id && added.getFirst().building()
				.map(line -> line.template().equals(PLAINS_HALL.toString())).orElse(false),
				"the restoration must write one hall log line about the town hall, wrote " + added);
		helper.assertTrue(TownWorksService.logLineText(added.getFirst())
				.map(text -> text.getContents() instanceof TranslatableContents contents
						&& contents.getKey().equals(TownWorksService.LOG_LINE_KEY)).orElse(false),
				"the restoration line must read as the building restored");

		BlockPos roof = origin.offset(HALL_ROOF);
		helper.assertTrue(!level.getBlockState(roof).isAir(), "control: the intact hall has no roof at " + roof);
		Block roofBlock = level.getBlockState(roof).getBlock();
		level.setBlock(roof, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		BuildingStateRules.Verdict broken = measure(helper, level, id);
		helper.assertTrue(broken.state() == BuildingState.REPAIR && works.buildings().getFirst().missingKeys()
				.contains("settlement.alaindustrial.building.key.roof"),
				"a broken roof must take the hall back to repair, measured " + works.buildings().getFirst());
		level.setBlock(roof, roofBlock.defaultBlockState(), Block.UPDATE_CLIENTS);
		BuildingStateRules.Verdict mended = measure(helper, level, id);
		helper.assertTrue(mended.state() == BuildingState.INTACT && !mended.justRestored()
				&& registry.workers().log().size() == logBefore + 1,
				"the mended hall must be intact again with no new log line, measured " + mended);
		helper.succeed();
	}

	/**
	 * @implements MOD-857-SAVE — a towns' file written before the buildings reads with no works and writes no field;
	 *     buildings and jobs come back from a save as they went in; a job of a type this build does not know is dropped
	 *     without losing the town.
	 * @covers R-PER-01
	 */
	public static void worksReadWithAndWithoutTheField(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		String towns = "{settlements:[{id:7,dimension:\"minecraft:overworld\",core:[I;10,64,-20],"
				+ "style:\"alaindustrial:plains\",active:1b}],next_id:8";
		SettlementRegistry old = SettlementRegistry.CODEC.parse(ops, parse(towns + "}")).getOrThrow();
		helper.assertTrue(old.find(7).isPresent() && old.works().isEmpty() && !old.works().town(7).buildingsKnown(),
				"a towns' save without works read as " + old.all());
		Tag rewritten = SettlementRegistry.CODEC.encodeStart(ops, old).getOrThrow();
		helper.assertTrue(rewritten instanceof CompoundTag tag && !tag.contains("works"),
				"towns without works are written with a works field: " + rewritten);

		TownWorks works = old.works().town(7);
		works.learnBuildings(List.of(TownBuilding.found(PLAINS_HALL, Optional.of(PLAINS_HALL_INTACT),
				"settlement.alaindustrial.building.town_hall", new BlockPos(4, 64, -25), Rotation.CLOCKWISE_90)));
		WorkJob job = works.queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		works.queue().takeNext("drone:1,2,3", Set.of(WorkJobType.BUILD));
		works.queue().progress(job.id(), "drone:1,2,3", 41);
		WorkJob repair = works.queue().raise(WorkJobType.CLEAR, 0, true).orElseThrow();
		SettlementRegistry again = SettlementRegistry.CODEC.parse(ops,
				SettlementRegistry.CODEC.encodeStart(ops, old).getOrThrow()).getOrThrow();
		TownWorks back = again.works().town(7);
		helper.assertTrue(back.buildingsKnown() && back.buildings().equals(works.buildings()),
				"the buildings changed through a save: " + back.buildings());
		helper.assertTrue(back.queue().jobs().equals(works.queue().jobs())
				&& back.queue().nextId() == works.queue().nextId()
				&& back.queue().job(job.id()).map(j -> j.status() == WorkJobStatus.TAKEN && j.progress() == 41)
						.orElse(false)
				&& back.queue().job(repair.id()).map(WorkJob::repair).orElse(false),
				"the queue changed through a save: " + back.queue().jobs());

		SettlementRegistry future = SettlementRegistry.CODEC.parse(ops, parse(towns + ",works:[{settlement:7,"
				+ "jobs:[{id:3,type:\"lay_road\",building:0},{id:4,type:\"clear\",building:0}],next_job:5}]}"))
				.getOrThrow();
		List<WorkJob> jobs = future.works().town(7).queue().jobs();
		helper.assertTrue(future.find(7).isPresent() && jobs.size() == 1 && jobs.getFirst().id() == 4
				&& future.works().town(7).queue().nextId() == 5,
				"an unknown job type must be dropped alone, the queue read " + jobs);
		helper.succeed();
	}

	/** What is wrong with {@code works} as the buildings of {@code start}, or {@code null}. */
	private static String checkBuildings(StructureStart start, TownWorks works) {
		if (!works.buildingsKnown()) {
			return "the town does not know its buildings";
		}
		List<PoolElementStructurePiece> buildings = new ArrayList<>();
		for (PoolElementStructurePiece piece : AlaTownSite.poolPieces(start)) {
			if (AlaTownSite.rigid(piece)) {
				buildings.add(piece);
			}
		}
		if (works.buildings().size() != buildings.size()) {
			return works.buildings().size() + " buildings known, the structure has " + buildings.size()
					+ " rigid pieces: " + works.buildings();
		}
		for (int i = 0; i < buildings.size(); i++) {
			PoolElementStructurePiece piece = buildings.get(i);
			TownBuilding known = works.buildings().get(i);
			Identifier template = AlaTownSite.template(piece);
			if (!known.template().equals(template) || !known.origin().equals(piece.getPosition())
					|| known.rotation() != piece.getRotation()) {
				return "building " + i + " is " + known + ", the piece is " + template + " at " + piece.getPosition()
						+ " turned " + piece.getRotation();
			}
			boolean hall = template.getPath().endsWith("/town_hall");
			if (known.intact().isPresent() != hall || known.name().isEmpty()) {
				return "building " + template + " has intact " + known.intact() + " and name '" + known.name() + "'";
			}
		}
		if (!works.buildings().getFirst().template().getPath().endsWith("/town_hall")) {
			return "the town hall is not the first building: " + works.buildings().getFirst();
		}
		return null;
	}

	/** The tab's rows give the controls to the owner and to nobody else (R95). */
	private static void checkControls(GameTestHelper helper, ServerLevel level, Settlement town, TownWorks works) {
		List<ConstructionRow> owner = ConstructionRows.collect(new ConstructionRows.Context(level, town, works, true));
		List<ConstructionRow> visitor = ConstructionRows.collect(new ConstructionRows.Context(level, town, works,
				false));
		helper.assertTrue(owner.size() == visitor.size(), "owner and visitor must read the same rows");
		int schematic = ConstructionRow.Action.SCHEMATIC.bit();
		helper.assertTrue(visitor.stream().allMatch(row -> (row.actions() & ~schematic) == 0),
				"a visitor got controls: " + visitor);
		helper.assertTrue(visitor.stream().anyMatch(row -> row.section().equals(ConstructionRows.SECTION_BUILDINGS)
				&& row.allows(ConstructionRow.Action.SCHEMATIC)),
				"the unfinished hall's schematic switch is visual and must reach a visitor too: " + visitor);
		boolean buildOffered = owner.stream().anyMatch(row -> row.section().equals(ConstructionRows.SECTION_BUILDINGS)
				&& row.allows(ConstructionRow.Action.RAISE_BUILD));
		boolean cancelOffered = owner.stream().anyMatch(row -> row.section().equals(ConstructionRows.SECTION_QUEUE)
				&& row.allows(ConstructionRow.Action.CANCEL));
		helper.assertTrue(buildOffered && cancelOffered, "the owner must be offered to raise and to cancel: " + owner);
	}

	/** A full pass over the hand-placed town hall, or a failed test. */
	private static BuildingStateRules.Verdict measure(GameTestHelper helper, ServerLevel level, int id) {
		Optional<BuildingStateRules.Verdict> verdict = TownWorksScanner.measureNow(level, id, 0);
		Optional<BuildingPlan> plan = TownWorksScanner.plan(level, id, 0);
		if (verdict.isEmpty() || plan.isEmpty() || plan.get().size() == 0) {
			throw helper.assertionException("the town hall could not be measured: plan " + plan);
		}
		return verdict.get();
	}

	/** Places template {@code id} at {@code origin} as a town shows it: jigsaws turned, no structure blocks. */
	private static void placeTemplate(ServerLevel level, Identifier id, BlockPos origin) {
		StructureTemplate template = LineStructures.templates(level).getOrCreate(id);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE)
				.addProcessor(JigsawReplacementProcessor.INSTANCE).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
		template.placeInWorld(level, origin, origin, settings, RandomSource.create(857L),
				Block.UPDATE_CLIENTS);
	}

	/** The settlement core standing in the town hall, the first pool piece. */
	private static BlockPos hallCore(ServerLevel level, StructureStart start) {
		BoundingBox hall = AlaTownSite.poolPieces(start).getFirst().getBoundingBox();
		for (BlockPos pos : BlockPos.betweenClosed(hall.minX(), hall.minY(), hall.minZ(), hall.maxX(), hall.maxY(),
				hall.maxZ())) {
			if (level.getBlockState(pos).is(ModContent.SETTLEMENT_CORE.get())) {
				return pos.immutable();
			}
		}
		return hall.getCenter();
	}

	/** Records {@code start} in the chunks the way world generation does, so the structure manager finds it. */
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
}

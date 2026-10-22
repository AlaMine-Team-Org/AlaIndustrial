package dev.alaindustrial.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.MaterialSource;
import dev.alaindustrial.settlement.works.StageRows;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownBuildingDef;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJournal;
import dev.alaindustrial.settlement.works.WorkJournalCodec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * L2 suite for the building materials of an ALA town (MOD-860, epic decision R84): the material list of each style's
 * town hall against its intact template — counts sane, the core and the capsule left out, a door counted once, every
 * block of the template staged without a crash; the stages of a ruin with a builder that places a stage only when its
 * buffer is stocked and takes each item at placement; the «Stages» rows of the core's tab, read-only for everybody;
 * the journal's save form; and the datapack's stage override field.
 */
public final class TownBuildMaterialsScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownBuildMaterialsScenarios::plainsHallBill, "town_build_materials_plains_hall_bill")
						.ticks(40),
				RosterEntry.of(TownBuildMaterialsScenarios::desertHallBill, "town_build_materials_desert_hall_bill")
						.ticks(40),
				RosterEntry.of(TownBuildMaterialsScenarios::journalAndOverridesSave,
						"town_build_materials_journal_and_overrides_save").ticks(20));

		private Roster() {}
	}

	private TownBuildMaterialsScenarios() {}

	private static final String NOT_BUILT_CORE = "alaindustrial:settlement_core";
	private static final String NOT_BUILT_CAPSULE = "alaindustrial:reception_capsule";

	/**
	 * @implements MOD-860-BILL — the plains town hall's material list from its intact template, its stages, and a
	 *     builder that goes stage by stage on a stocked buffer.
	 */
	public static void plainsHallBill(GameTestHelper helper) {
		hallBill(helper, "plains", TownDroneScenarios.nextOrigin(), Rotation.NONE, "minecraft:bricks", 2);
	}

	/**
	 * @implements MOD-860-BILL — the desert town hall's material list, the building turned, so the rotation path of
	 *     the plan and the stages is the one a generated town takes.
	 */
	public static void desertHallBill(GameTestHelper helper) {
		hallBill(helper, "desert", TownDroneScenarios.nextOrigin(), Rotation.CLOCKWISE_90,
				"minecraft:smooth_sandstone", 0);
	}

	private static void hallBill(GameTestHelper helper, String style, BlockPos origin, Rotation rotation,
			String mainBlock, int doors) {
		ServerLevel level = helper.getLevel();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Identifier ruin = Industrialization.id("ala_town/" + style + "/town_hall");
		Identifier intact = Industrialization.id("ala_town/" + style + "/intact/town_hall");
		place(level, ruin, origin, rotation);
		// The core of both halls stands at (6, 1, 5) of the template; turned with the building.
		BlockPos core = StructureTemplate.calculateRelativePosition(new StructurePlaceSettings().setRotation(rotation),
				new BlockPos(6, 1, 5)).offset(origin);
		int id = registry.place(level.dimension(), core, 0).settlementId();
		registry.works().forget(id);
		TownWorks works = registry.works().town(id);
		works.learnBuildings(List.of(TownBuilding.found(ruin, Optional.of(intact),
				"settlement.alaindustrial.building.town_hall", origin, rotation)));

		Optional<BuildMaterials.StagedPlan> found = BuildMaterials.forBuilding(level, id, 0);
		if (found.isEmpty()) {
			throw helper.assertionException("no staged plan for the " + style + " town hall");
		}
		BuildMaterials.StagedPlan staged = found.get();
		BuildingPlan plan = staged.plan();

		// The bill: every counted cell as if nothing stood — the intact template's materials.
		BuildStageRules.Progress bill = staged.bill();
		Map<String, Integer> items = new HashMap<>();
		bill.needed().forEach(need -> items.put(need.item(), need.count()));
		int total = items.values().stream().mapToInt(Integer::intValue).sum();
		helper.assertTrue(total > 300 && total < plan.size(), style + " hall bill of " + total + " items for "
				+ plan.size() + " cells: " + items);
		helper.assertTrue(items.getOrDefault(mainBlock, 0) > 50,
				style + " hall bill lacks " + mainBlock + ": " + items);
		helper.assertTrue(!items.containsKey(NOT_BUILT_CORE) && !items.containsKey(NOT_BUILT_CAPSULE)
				&& !items.containsKey("minecraft:structure_block") && !items.containsKey("minecraft:jigsaw"),
				style + " hall bill names a block no builder places: " + items);
		helper.assertTrue(items.getOrDefault("minecraft:dark_oak_door", 0) == doors,
				style + " hall bill counts " + items.get("minecraft:dark_oak_door") + " doors, expected " + doors);
		int withoutItem = bill.stages().stream().mapToInt(BuildStageRules.StageProgress::withoutItem).sum();
		helper.assertTrue(withoutItem == 0, style + " hall has " + withoutItem + " blocks with no item");
		helper.assertTrue(bill.count() == BuildStage.values().length,
				style + " hall must use every stage, it uses " + bill.stages().stream()
						.map(BuildStageRules.StageProgress::stage).toList());
		checkStages(helper, style, staged);

		// The ruin: some stage is missing cells, what it needs is within the bill.
		BuildStageRules.Progress ruinProgress = staged.progress(level).orElseThrow(
				() -> helper.assertionException("the " + style + " hall lies in an unloaded chunk"));
		BuildStageRules.StageProgress current = ruinProgress.current().orElseThrow(
				() -> helper.assertionException("the " + style + " ruin already matches every stage"));
		helper.assertTrue(current.neededItems() > 0 || current.withoutItem() > 0,
				"the current stage of the " + style + " ruin needs nothing: " + current);

		// The tab: a stage row for the hall, read-only for owner and visitor alike.
		Settlement town = registry.find(id).orElseThrow();
		for (boolean manage : new boolean[] {true, false}) {
			List<ConstructionRow> rows = ConstructionRows.collect(new ConstructionRows.Context(level, town, works,
					manage));
			List<ConstructionRow> stageRows = rows.stream().filter(row -> row.section().equals(StageRows.SECTION))
					.toList();
			helper.assertTrue(stageRows.size() == 1 && stageRows.getFirst().actions() == 0
					&& stageRows.getFirst().detail().isPresent(), "the stages section for manage=" + manage + " reads "
					+ stageRows);
		}

		// A builder on a buffer: waits while the stage is short, places it once stocked, taking each item at placement.
		Buffer buffer = new Buffer();
		BuildStageRules.Decision waiting = BuildStageRules.decide(ruinProgress, buffer);
		helper.assertTrue(current.neededItems() == 0 || waiting.verdict() == BuildStageRules.Verdict.WAIT
				&& !waiting.missing().isEmpty() && BuildMaterials.reason(waiting.missing()).args().size() > 0,
				"an empty buffer must make the " + style + " builder wait, it decided " + waiting);
		current.needed().forEach(need -> buffer.put(need.item(), need.count()));
		helper.assertTrue(BuildStageRules.decide(ruinProgress, buffer).verdict() == BuildStageRules.Verdict.BUILD,
				"a stocked buffer must let the " + style + " builder go on");
		BuildStage working = current.stage();
		int placed = 0;
		for (int i = 0; i < plan.size(); i++) {
			if (staged.stage(i).orElse(null) != working) {
				continue;
			}
			BlockPos pos = plan.pos(i);
			BuildStageRules.Cell cell = staged.cell(i, level.getBlockState(pos)).orElseThrow();
			if (cell.standing()) {
				continue;
			}
			BuildStageRules.Progress now = staged.progress(level).orElseThrow();
			helper.assertTrue(BuildStageRules.mayPlace(now, buffer, cell), "cell " + pos + " of " + working
					+ " may not be placed on a stocked buffer");
			int before = buffer.count(cell.item());
			BlockState previous = level.getBlockState(pos);
			level.setBlock(pos, plan.expected(i), Block.UPDATE_CLIENTS);
			if (!BuildStageRules.consumeOnPlace(buffer, cell)) {
				level.setBlock(pos, previous, Block.UPDATE_CLIENTS);
				throw helper.assertionException("the buffer ran out at " + pos + " for " + cell);
			}
			helper.assertTrue(buffer.count(cell.item()) == before - cell.count(),
					"placing " + cell + " took " + (before - buffer.count(cell.item())) + " items");
			placed++;
		}
		BuildStageRules.Progress after = staged.progress(level).orElseThrow();
		helper.assertTrue(after.stages().stream().filter(stage -> stage.stage() == working)
				.allMatch(BuildStageRules.StageProgress::done), "the " + working + " stage of the " + style
				+ " hall must stand after " + placed + " placements");
		helper.assertTrue(buffer.items.values().stream().allMatch(count -> count == 0),
				"the stage took exactly what it needed, left over: " + buffer.items);

		// The whole intact hall stands: nothing missing anywhere.
		place(level, intact, origin, rotation);
		BuildStageRules.Progress done = staged.progress(level).orElseThrow();
		helper.assertTrue(done.done() && done.needed().isEmpty() && done.currentNumber() == done.count(),
				"the intact " + style + " hall still misses " + done.current());
		helper.succeed();
	}

	/** Every counted cell of the plan gets a stage by the class's rules; the core and capsule get none. */
	private static void checkStages(GameTestHelper helper, String style, BuildMaterials.StagedPlan staged) {
		BuildingPlan plan = staged.plan();
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int i = 0; i < plan.size(); i++) {
			if (staged.counted(i)) {
				minY = Math.min(minY, plan.pos(i).getY());
				maxY = Math.max(maxY, plan.pos(i).getY());
			}
		}
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			String id = BuiltInRegistries.BLOCK.getKey(want.getBlock()).toString();
			Optional<BuildStage> stage = staged.stage(i);
			if (id.equals(NOT_BUILT_CORE) || id.equals(NOT_BUILT_CAPSULE) || plan.debris(i)) {
				helper.assertTrue(stage.isEmpty(), style + " cell " + plan.pos(i) + " " + id + " must not be counted");
				continue;
			}
			helper.assertTrue(stage.isPresent(), style + " cell " + plan.pos(i) + " " + id + " has no stage");
			BuildStage expected = id.endsWith("_door") || id.endsWith("_trapdoor") || id.endsWith("glass")
					|| id.endsWith("glass_pane") ? BuildStage.OPENINGS
					: id.endsWith("torch") || id.endsWith("carpet") || id.equals("minecraft:chest")
							? BuildStage.INTERIOR : null;
			if (expected == null && plan.pos(i).getY() == minY) {
				expected = BuildStage.FOUNDATION;
			}
			if (expected == null && plan.pos(i).getY() == maxY) {
				expected = BuildStage.ROOF;
			}
			if (expected != null) {
				helper.assertTrue(stage.get() == expected, style + " cell " + plan.pos(i) + " " + id + " is in "
						+ stage.get() + ", expected " + expected);
			}
			helper.assertTrue(staged.count(i) >= 0 && staged.count(i) <= 2, style + " cell " + id + " takes "
					+ staged.count(i));
		}
	}

	/**
	 * @implements MOD-860-JOURNAL — the dispatcher's journal comes back from its save as it went in, a line of a kind
	 *     this build does not know is dropped alone; a town building definition reads with and without its stage
	 *     overrides.
	 * @covers R-PER-01
	 */
	public static void journalAndOverridesSave(GameTestHelper helper) {
		WorkJournal journal = new WorkJournal();
		journal.cleared(100, "block.minecraft.cobweb");
		journal.placed(200, "block.minecraft.bricks");
		journal.placed(210, "block.minecraft.bricks");
		journal.missing(300, "block.minecraft.glass", 6);
		journal.stageDone(400, BuildStage.WALLS);
		Tag saved = WorkJournalCodec.CODEC.encodeStart(NbtOps.INSTANCE, journal).getOrThrow();
		WorkJournal back = WorkJournalCodec.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(back.entries().equals(journal.entries()), "the journal changed through a save: "
				+ back.entries() + " vs " + journal.entries());

		WorkJournal future = WorkJournalCodec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"[{\"kind\":\"repaired\",\"time\":5},{\"kind\":\"placed\",\"subject\":\"block.minecraft.stone\"}]"))
				.getOrThrow();
		helper.assertTrue(future.entries().size() == 1 && future.entries().getFirst().equals(
				new WorkJournal.Entry(0, WorkJournal.Kind.PLACED, "block.minecraft.stone", 0)),
				"an unknown kind must be dropped alone, read " + future.entries());
		helper.assertTrue(WorkJournalCodec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[]")).getOrThrow()
				.isEmpty(), "an empty journal must read empty");

		String base = "{\"template\":\"alaindustrial:x\",\"name\":\"n\"";
		TownBuildingDef old = TownBuildingDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(base + "}"))
				.getOrThrow();
		helper.assertTrue(old.stages().isEmpty(), "a definition without stages read " + old.stages());
		TownBuildingDef withStages = TownBuildingDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(base
				+ ",\"stages\":[{\"stage\":\"roof\",\"from\":[0,4,0],\"to\":[12,4,10]},{\"stage\":\"cellar\"}]}"))
				.getOrThrow();
		helper.assertTrue(withStages.stages().size() == 2
				&& withStages.stages().get(0).buildStage().equals(Optional.of(BuildStage.ROOF))
				&& withStages.stages().get(1).buildStage().isEmpty()
				&& withStages.stages().get(0).covers(new BlockPos(3, 4, 3), Blocks.SPRUCE_PLANKS.defaultBlockState())
				&& !withStages.stages().get(0).covers(new BlockPos(3, 5, 3), Blocks.SPRUCE_PLANKS.defaultBlockState()),
				"stage overrides read " + withStages.stages());
		helper.succeed();
	}

	/** A buffer as a map, all-or-nothing takes — what the dispatcher's buffer will be (MOD-858). */
	private static final class Buffer implements MaterialSource {
		final Map<String, Integer> items = new HashMap<>();

		void put(String item, int count) {
			items.merge(item, count, Integer::sum);
		}

		@Override
		public int count(String item) {
			return items.getOrDefault(item, 0);
		}

		@Override
		public boolean consume(String item, int count) {
			int have = count(item);
			if (have < count) {
				return false;
			}
			items.put(item, have - count);
			return true;
		}
	}

	/** Places template {@code id} at {@code origin} as a town shows it: jigsaws turned, no structure blocks. */
	private static void place(ServerLevel level, Identifier id, BlockPos origin, Rotation rotation) {
		StructureTemplate template = LineStructures.templates(level).getOrCreate(id);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation)
				.addProcessor(JigsawReplacementProcessor.INSTANCE).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
		template.placeInWorld(level, origin, origin, settings, RandomSource.create(860L), Block.UPDATE_CLIENTS);
	}
}

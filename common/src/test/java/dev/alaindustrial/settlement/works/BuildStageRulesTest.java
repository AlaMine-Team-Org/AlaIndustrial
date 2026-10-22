package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.BuildStageRules.Cell;
import dev.alaindustrial.settlement.works.BuildStageRules.Decision;
import dev.alaindustrial.settlement.works.BuildStageRules.Need;
import dev.alaindustrial.settlement.works.BuildStageRules.Progress;
import dev.alaindustrial.settlement.works.BuildStageRules.StageProgress;
import dev.alaindustrial.settlement.works.BuildStageRules.Verdict;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * L1 for building in stages (MOD-860, epic decision R84): «stage N of M» from the plan and the world, what the current
 * stage needs and what the buffer lacks for it, a stage that starts only when stocked and resumes by itself, an item
 * that leaves the buffer at placement and only then, and the reason a waiting job shows.
 */
class BuildStageRulesTest {

	private static final String BRICKS = "minecraft:bricks";
	private static final String GLASS = "minecraft:glass";
	private static final String PLANKS = "minecraft:spruce_planks";
	private static final String DOOR = "minecraft:dark_oak_door";

	/** A buffer as a map; {@code consume} is all or nothing. */
	private static final class Buffer implements MaterialSource {
		final Map<String, Integer> items = new HashMap<>();

		Buffer put(String item, int count) {
			items.merge(item, count, Integer::sum);
			return this;
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

	private static List<Cell> cells(BuildStage stage, String item, int count, int standing, int missing) {
		List<Cell> out = new ArrayList<>();
		for (int i = 0; i < standing; i++) {
			out.add(new Cell(stage, item, count, true));
		}
		for (int i = 0; i < missing; i++) {
			out.add(new Cell(stage, item, count, false));
		}
		return out;
	}

	/** A small hall: a laid foundation, walls half up, windows, a door (two halves) and a torch with no item. */
	private static List<Cell> hall() {
		List<Cell> out = new ArrayList<>();
		out.addAll(cells(BuildStage.FOUNDATION, PLANKS, 1, 10, 0));
		out.addAll(cells(BuildStage.WALLS, BRICKS, 1, 8, 12));
		out.addAll(cells(BuildStage.OPENINGS, GLASS, 1, 0, 6));
		out.add(new Cell(BuildStage.OPENINGS, DOOR, 1, false));
		out.add(new Cell(BuildStage.OPENINGS, DOOR, 0, false));
		out.add(new Cell(BuildStage.INTERIOR, BuildStageRules.NO_ITEM, 1, false));
		return out;
	}

	@Test
	void stagesCountOnlyThoseWithCellsInWorkOrder() {
		Progress progress = BuildStageRules.progress(hall());
		assertEquals(4, progress.count(), "the hall has no roof cell, so four stages");
		assertEquals(List.of(BuildStage.FOUNDATION, BuildStage.WALLS, BuildStage.OPENINGS, BuildStage.INTERIOR),
				progress.stages().stream().map(StageProgress::stage).toList());
		assertEquals(2, progress.currentNumber(), "the foundation stands, the walls are the second stage");
		assertEquals(Optional.of(BuildStage.WALLS), progress.current().map(StageProgress::stage));
		assertFalse(progress.done());
	}

	@Test
	void aStageCountsItsCellsAndWhatItsMissingCellsTake() {
		StageProgress walls = BuildStageRules.progress(hall()).stages().get(1);
		assertEquals(20, walls.total());
		assertEquals(8, walls.standing());
		assertEquals(List.of(new Need(BRICKS, 12)), walls.needed());
		assertEquals(12, walls.neededItems());
		assertFalse(walls.done());

		StageProgress openings = BuildStageRules.progress(hall()).stages().get(2);
		assertEquals(List.of(new Need(GLASS, 6), new Need(DOOR, 1)), openings.needed(),
				"most first; the door's upper half is paid by its lower half");
		assertEquals(0, openings.withoutItem(), "a half paid by its partner is not a block with no item");

		StageProgress interior = BuildStageRules.progress(hall()).stages().get(3);
		assertEquals(1, interior.withoutItem());
		assertTrue(interior.needed().isEmpty());
	}

	@Test
	void theMaterialListIsEveryMissingCellAcrossStages() {
		assertEquals(List.of(new Need(BRICKS, 12), new Need(GLASS, 6), new Need(DOOR, 1)),
				BuildStageRules.progress(hall()).needed());
	}

	@Test
	void aBrokenBlockInAnEarlierStageTakesTheBuildingBack() {
		List<Cell> cells = new ArrayList<>(cells(BuildStage.FOUNDATION, PLANKS, 1, 9, 1));
		cells.addAll(cells(BuildStage.WALLS, BRICKS, 1, 20, 0));
		cells.addAll(cells(BuildStage.ROOF, PLANKS, 1, 0, 5));
		Progress progress = BuildStageRules.progress(cells);
		assertEquals(1, progress.currentNumber());
		assertEquals(Optional.of(BuildStage.FOUNDATION), progress.current().map(StageProgress::stage));
	}

	@Test
	void aBuildingWithEverythingStandingIsDoneAtMOfM() {
		List<Cell> cells = new ArrayList<>(cells(BuildStage.FOUNDATION, PLANKS, 1, 3, 0));
		cells.addAll(cells(BuildStage.ROOF, PLANKS, 1, 2, 0));
		Progress progress = BuildStageRules.progress(cells);
		assertTrue(progress.done());
		assertEquals(2, progress.currentNumber());
		assertEquals(Verdict.DONE, BuildStageRules.decide(progress, new Buffer()).verdict());
		assertTrue(BuildStageRules.progress(List.of()).done(), "a building with no cells has nothing to do");
	}

	@Test
	void aStageStartsOnlyWhenTheBufferHoldsAllItStillNeeds() {
		Progress progress = BuildStageRules.progress(hall());
		Buffer buffer = new Buffer().put(BRICKS, 11).put(GLASS, 64);
		Decision waiting = BuildStageRules.decide(progress, buffer);
		assertEquals(Verdict.WAIT, waiting.verdict());
		assertEquals(Optional.of(BuildStage.WALLS), waiting.stage());
		assertEquals(List.of(new Need(BRICKS, 1)), waiting.missing(), "only what the current stage lacks");
		Cell wall = new Cell(BuildStage.WALLS, BRICKS, 1, false);
		assertFalse(BuildStageRules.mayPlace(progress, buffer, wall));

		buffer.put(BRICKS, 1);
		Decision going = BuildStageRules.decide(progress, buffer);
		assertEquals(Verdict.BUILD, going.verdict(), "the job resumes by itself once the items are there");
		assertTrue(going.missing().isEmpty());
		assertTrue(BuildStageRules.mayPlace(progress, buffer, wall));
		assertFalse(BuildStageRules.mayPlace(progress, buffer, new Cell(BuildStage.OPENINGS, GLASS, 1, false)),
				"a cell of a later stage waits for the current one");
		assertFalse(BuildStageRules.mayPlace(progress, buffer, new Cell(BuildStage.WALLS, BRICKS, 1, true)),
				"a standing cell is not placed again");
	}

	@Test
	void anItemLeavesTheBufferAtPlacementAllOrNothing() {
		Buffer buffer = new Buffer().put(BRICKS, 1);
		Cell wall = new Cell(BuildStage.WALLS, BRICKS, 1, false);
		assertTrue(BuildStageRules.consumeOnPlace(buffer, wall));
		assertEquals(0, buffer.count(BRICKS));
		assertFalse(BuildStageRules.consumeOnPlace(buffer, wall), "nothing left: the block may not stand");
		assertEquals(0, buffer.count(BRICKS));

		Buffer slabs = new Buffer().put("minecraft:stone_slab", 1);
		assertFalse(BuildStageRules.consumeOnPlace(slabs, new Cell(BuildStage.ROOF, "minecraft:stone_slab", 2, false)),
				"a double slab takes two");
		assertEquals(1, slabs.count("minecraft:stone_slab"), "a failed take keeps what the buffer has");

		Buffer empty = new Buffer();
		assertTrue(BuildStageRules.consumeOnPlace(empty, new Cell(BuildStage.OPENINGS, DOOR, 0, false)),
				"the paid half costs nothing");
		assertTrue(BuildStageRules.consumeOnPlace(empty, new Cell(BuildStage.INTERIOR, "", 3, false)),
				"a block with no item costs nothing");
	}

	@Test
	void aCellWithNoItemCostsNothingWhateverItsCount() {
		Cell cell = new Cell(BuildStage.WALLS, BuildStageRules.NO_ITEM, 5, false);
		assertEquals(0, cell.count());
		assertFalse(cell.costs());
		assertEquals(0, new Cell(BuildStage.WALLS, BRICKS, -3, false).count());
	}

	@Test
	void aBufferThatReportsNegativeCountsLacksTheWholeNeed() {
		MaterialSource broken = new MaterialSource() {
			@Override
			public int count(String item) {
				return -5;
			}

			@Override
			public boolean consume(String item, int count) {
				return false;
			}
		};
		StageProgress walls = BuildStageRules.progress(hall()).stages().get(1);
		assertEquals(List.of(new Need(BRICKS, 12)), BuildStageRules.shortage(walls, broken));
	}

	@Test
	void theReasonNamesTheMissingItemsCountedAndCapsTheList() {
		BlockReason reason = BuildStageRules.reason(List.of(new Need(GLASS, 6), new Need(BRICKS, 2)),
				item -> "block." + item.replace(':', '.'));
		assertEquals(BlockReason.NO_MATERIALS, reason.key());
		assertTrue(reason.joined());
		assertEquals(List.of("6*block.minecraft.glass", "2*block.minecraft.bricks"), reason.args());

		List<Need> many = new ArrayList<>();
		for (int i = 0; i < BuildStageRules.MAX_LISTED + 2; i++) {
			many.add(new Need("minecraft:item_" + i, 1));
		}
		List<String> args = BuildStageRules.reason(many, item -> item).args();
		assertEquals(BuildStageRules.MAX_LISTED + 1, args.size());
		assertEquals(BuildStageRules.MORE, args.getLast());
		assertEquals(List.of(), BuildStageRules.reason(List.of(), item -> item).args());
	}

	@Test
	void aCountedArgumentComesApartAndNothingElseDoes() {
		String arg = ConstructionRow.Text.counted(6, "block.minecraft.glass");
		assertEquals("6*block.minecraft.glass", arg);
		assertEquals(Optional.of(new ConstructionRow.Text.Counted(6, "block.minecraft.glass")),
				ConstructionRow.Text.counted(arg));
		for (String plain : List.of("12", "block.minecraft.glass", "*glass", "6*", "a6*glass", "", "…",
				"99999999999*glass")) {
			assertEquals(Optional.empty(), ConstructionRow.Text.counted(plain), plain);
		}
	}

	@Test
	void everyStageHasItsKeyAndComesBackFromItsName() {
		for (BuildStage stage : BuildStage.values()) {
			assertEquals("settlement.alaindustrial.works.stage." + stage.serializedName(), stage.key());
			assertEquals(Optional.of(stage), BuildStage.byName(stage.serializedName()));
		}
		assertEquals(Optional.empty(), BuildStage.byName("basement"));
		assertEquals("openings", BuildStage.OPENINGS.serializedName());
	}
}

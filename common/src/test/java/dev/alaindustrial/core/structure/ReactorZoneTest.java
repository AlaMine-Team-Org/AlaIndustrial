package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.structure.ReactorZone.Column;
import dev.alaindustrial.core.structure.ReactorZone.Coolant;
import dev.alaindustrial.core.structure.ReactorZone.Stack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** L1 coverage for {@link ReactorZone} (MOD-620): stacks, the two marks, and the fuel arithmetic behind them. */
class ReactorZoneTest {

	/** 144 000 EU a rod, the shipped default: 144 EU a point of wear. */
	private static final long ROD = 144_000;

	/** A zone wide enough for every test that does not probe the span limit. */
	private static final int WIDE = ReactorZone.MAX_SPAN;

	private static Column column(int x, int y, int z, int[] damage, int spent) {
		return new Column(x, y, z, damage, spent, 0, 4000, 0, 4000);
	}

	private static List<Stack> stacks(List<Column> columns, int originX, int originZ) {
		return ReactorZone.stacks(columns, originX, originZ, WIDE, WIDE, ROD);
	}

	@Test
	void aStackAddsUpItsColumns() {
		Column low = new Column(5, 1, 7, new int[] {0, 500}, 0, 4000, 4000, 1000, 4000);
		Column high = new Column(5, 2, 7, new int[] {250}, 1, 0, 4000, 0, 4000);
		List<Stack> stacks = stacks(List.of(low, high), 4, 6);
		assertEquals(1, stacks.size());
		Stack stack = stacks.get(0);
		assertEquals(1, stack.x(), "offset from the zone's west edge");
		assertEquals(1, stack.z(), "offset from the zone's north edge");
		assertEquals(2, stack.columns());
		assertEquals(3, stack.fuelledRods());
		assertEquals(1, stack.spentRods());
		assertEquals((0 + 500 + 250 + 1000) / 4, stack.averageWearPermille(), "a spent casing is fully worn");
		assertEquals(1000, stack.worstWearPermille());
		assertEquals((1000 + 500 + 750) * 144L, stack.remainingEu());
		assertEquals(2, stack.neighbours(), "the two columns sit on each other: one pair, counted from both ends");
		assertEquals(50, stack.waterPercent());
		assertEquals(13, stack.steamPercent(), "1000 of 8000 mB is 12.5 %, rounded to the nearest");
	}

	/**
	 * Tanks read like the «Console» tab's coolant bar (review, MOD-620): a column a pipe has parked a couple of mB short
	 * of full is full, where a truncating divide left it at 99 % on this tab and 100 % on the other.
	 */
	@Test
	void aNearlyFullStackReadsFullLikeTheConsole() {
		Column nearlyFull = new Column(0, 1, 0, new int[] {0}, 0, 3998, 4000, 0, 4000);
		assertEquals(100, stacks(List.of(nearlyFull), 0, 0).get(0).waterPercent());
		assertEquals(1, ReactorZone.percent(5, 1000), "half a percent rounds up");
		assertEquals(0, ReactorZone.percent(4, 1000));
		assertEquals(0, ReactorZone.percent(0, 0), "no tanks, no share");
	}

	private static Coolant coolantOf(Column... columns) {
		return stacks(List.of(columns), 0, 0).get(0).coolant();
	}

	/** Water and steam in millibuckets, summed over the stack: the numbers the «Coolant» tab prints (MOD-621). */
	@Test
	void aStackCarriesItsTanksInMillibuckets() {
		Coolant coolant = coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 3000, 4000, 0, 4000),
				new Column(0, 2, 0, new int[] {0}, 0, 1000, 4000, 3900, 4000));
		assertEquals(4000, coolant.water());
		assertEquals(8000, coolant.waterCapacity());
		assertEquals(3900, coolant.steam());
		assertEquals(8000, coolant.steamCapacity());
		assertFalse(coolant.dry());
		assertFalse(coolant.blocked(), "3900 of 8000 mB is under the line");
	}

	/** The line is the «Console» tab's: from 90 % of the room a vessel's exhaust counts as blocked. */
	@Test
	void steamFromTheLineOnBlocksTheExhaust() {
		assertTrue(coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 1000, 4000, 3600, 4000)).blocked(), "exactly 90 %");
		assertFalse(coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 1000, 4000, 3599, 4000)).blocked(), "a millibucket under");
	}

	/** Dry means no water at all: a trickle still boils, and the tab must not send a player to fix it. */
	@Test
	void onlyAnEmptyVesselIsDry() {
		assertTrue(coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 0, 4000, 0, 4000)).dry());
		assertFalse(coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 1, 4000, 0, 4000)).dry());
	}

	/**
	 * A vessel the reaction boiled empty lately is working, not dry (review, MOD-621). The controller drains columns in its
	 * own list order and a pipe refills them on its own tick, so a stack with a line that keeps up can hold 0 mB at the
	 * moment of the snapshot; sending the player to re-pipe it would be wrong.
	 */
	@Test
	void aVesselBoiledEmptyLatelyIsNotDry() {
		assertFalse(coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 0, 4000, 0, 4000, true)).dry());
		assertTrue(coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 0, 4000, 0, 4000, false)).dry());
		assertFalse(coolantOf(new Column(0, 1, 0, new int[] {0}, 0, 0, 4000, 0, 4000, false),
				new Column(0, 2, 0, new int[] {0}, 0, 0, 4000, 0, 4000, true)).dry(), "one boiling column keeps its vessel");
	}

	/**
	 * A gap in a stack's height makes two vessels, and each is judged on its own: summed, the full lower one would hide
	 * the empty, choked one above it. Touching, the columns are one vessel and share the lower one's water.
	 */
	@Test
	void aGapInAStackMakesTwoVesselsAndEachIsJudged() {
		Column lowFull = new Column(0, 1, 0, new int[] {0}, 0, 4000, 4000, 0, 4000);
		Coolant apart = coolantOf(new Column(0, 3, 0, new int[] {0}, 0, 0, 4000, 4000, 4000), lowFull);
		assertTrue(apart.dry(), "the upper vessel holds no water");
		assertTrue(apart.blocked(), "the upper vessel is full of steam");
		Coolant touching = coolantOf(lowFull, new Column(0, 2, 0, new int[] {0}, 0, 0, 4000, 0, 4000));
		assertFalse(touching.dry(), "one vessel: the water below is the column's above too");
	}

	/**
	 * Far out in a world the neighbours are counted the same (regression): with an overflowing position key, a column
	 * at the NeoForge gametest world's coordinates counted itself as its own neighbour above.
	 */
	@Test
	void neighboursAreTheSameMillionsOfBlocksOut() {
		int x = 8_452_547;
		int z = 14_850_362;
		for (int y : new int[] {-59, 64, 300}) {
			List<Stack> above = stacks(List.of(
					column(x, y, z, new int[] {0}, 0),
					column(x, y + 1, z, new int[] {}, 1)), x, z);
			assertEquals(0, above.get(0).neighbours(), "a spent casing above is no neighbour, at y " + y);
			List<Stack> west = stacks(List.of(
					column(-x, y, -z, new int[] {0}, 0),
					column(-x - 1, y, -z, new int[] {0}, 0)), -x - 1, -z);
			assertEquals(1, west.get(0).neighbours(), "two racks side by side, far west and north, at y " + y);
		}
	}

	@Test
	void stacksRunNorthToSouthThenWestToEast() {
		List<Stack> stacks = stacks(List.of(
				column(2, 1, 1, new int[] {0}, 0),
				column(0, 1, 1, new int[] {0}, 0),
				column(1, 1, 0, new int[] {0}, 0)), 0, 0);
		assertEquals(List.of("1,0", "0,1", "2,1"),
				stacks.stream().map(s -> s.x() + "," + s.z()).toList());
	}

	/** Neighbours are fuelled columns in all six directions — the pairs that earn the room its output bonus. */
	@Test
	void theDensestStackHasTheMostFuelledNeighboursAndATieGoesToTheNorthernmost() {
		List<Column> columns = List.of(
				column(1, 1, 1, new int[] {0}, 0),
				column(0, 1, 1, new int[] {0}, 0),
				column(2, 1, 1, new int[] {0}, 0),
				column(1, 1, 0, new int[] {0}, 0),
				column(1, 1, 2, new int[] {}, 1)); // a rack of spent casings is not a fuelled neighbour
		List<Stack> stacks = stacks(columns, 0, 0);
		int densest = ReactorZone.densest(stacks);
		assertEquals("1,1", stacks.get(densest).x() + "," + stacks.get(densest).z());
		assertEquals(3, stacks.get(densest).neighbours());

		// Two equal stacks, the eastern one further north: order is north first, so it wins.
		List<Stack> tie = stacks(List.of(
				column(5, 1, 0, new int[] {0}, 0), column(5, 2, 0, new int[] {0}, 0),
				column(0, 1, 1, new int[] {0}, 0), column(0, 2, 1, new int[] {0}, 0)), 0, 0);
		assertEquals("5,0", tie.get(ReactorZone.densest(tie)).x() + "," + tie.get(ReactorZone.densest(tie)).z());
	}

	@Test
	void lonelyRacksHaveNoDensest() {
		List<Stack> stacks = stacks(List.of(column(0, 1, 0, new int[] {0}, 0), column(3, 1, 3, new int[] {0}, 0)), 0, 0);
		assertEquals(-1, ReactorZone.densest(stacks));
	}

	@Test
	void theStackToServiceFirstHoldsTheMostWornRodAndASpentCasingOutranksAnyLiveOne() {
		List<Stack> stacks = stacks(List.of(
				column(0, 1, 0, new int[] {600, 100}, 0),
				column(1, 1, 0, new int[] {990}, 0),
				column(2, 1, 0, new int[] {10}, 1)), 0, 0);
		assertEquals(2, ReactorZone.replaceFirst(stacks));

		List<Stack> live = stacks(List.of(column(0, 1, 0, new int[] {600}, 0), column(1, 1, 0, new int[] {990}, 0)), 0, 0);
		assertEquals(1, ReactorZone.replaceFirst(live));
	}

	@Test
	void freshOrEmptyRacksNeedNoService() {
		List<Stack> stacks = stacks(List.of(column(0, 1, 0, new int[] {0, 0}, 0), column(1, 1, 0, new int[] {}, 0)), 0, 0);
		assertEquals(-1, ReactorZone.replaceFirst(stacks));
		assertFalse(stacks.get(1).hasRods());
		assertEquals(0, stacks.get(1).averageWearPermille(), "no rods, no wear to average");
	}

	/**
	 * A zone past the span shows its north-west corner, and a rack outside that corner still counts as a neighbour
	 * of one inside it (review, MOD-620): cutting by list order instead left whole southern rows looking empty.
	 */
	@Test
	void aZonePastTheSpanShowsItsCornerAndStillCountsNeighboursAcrossTheCut() {
		List<Column> columns = new ArrayList<>();
		for (int z = 0; z < 20; z++) {
			for (int x = 0; x < 20; x++) {
				columns.add(column(x, 1, z, new int[] {0}, 0));
			}
		}
		List<Stack> stacks = ReactorZone.stacks(columns, 0, 0, 20, 20, ROD);
		assertEquals(ReactorZone.MAX_STACKS, stacks.size());
		Stack corner = stacks.get(stacks.size() - 1);
		assertEquals(ReactorZone.MAX_SPAN - 1, corner.x());
		assertEquals(ReactorZone.MAX_SPAN - 1, corner.z());
		assertEquals(4, corner.neighbours(), "its east and south neighbours are past the cut and still count");
	}

	@Test
	void aColumnOutsideTheZoneIsLeftOut() {
		List<Stack> stacks = ReactorZone.stacks(List.of(column(0, 1, 0, new int[] {0}, 0), column(3, 1, 0, new int[] {0}, 0)),
				0, 0, 3, 1, ROD);
		assertEquals(1, stacks.size());
	}

	/** The energy left is what the burn would still spend: the points left on the bar, each worth a point's EU. */
	@Test
	void remainingEnergyIsWhatTheBurnWouldStillSpend() {
		assertEquals(ROD, FuelRodMath.remainingEnergy(0, ROD));
		assertEquals(144, FuelRodMath.remainingEnergy(999, ROD));
		assertEquals(0, FuelRodMath.remainingEnergy(FuelRodMath.ROD_DURABILITY, ROD));
		assertEquals(0, FuelRodMath.remainingEnergy(0, 0));
		assertEquals(250, FuelRodMath.wearPermille(250));
		assertEquals(1000, FuelRodMath.wearPermille(5000));
		assertEquals(0, FuelRodMath.wearPermille(-3));
	}
}

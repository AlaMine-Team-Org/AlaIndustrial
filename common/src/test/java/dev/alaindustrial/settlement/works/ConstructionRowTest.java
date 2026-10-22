package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.ConstructionRow.Action;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * L1 for a row of the «Construction» tab and the names the queue saves by (MOD-857): the controls a row allows, the
 * request's ordinal, the percent range, a block reason as a line, and the saved names of states, statuses and types.
 */
class ConstructionRowTest {

	private static ConstructionRow row(int percent, int actions) {
		return new ConstructionRow("section", ConstructionRow.Text.of("key"), Optional.empty(), percent,
				ConstructionRow.Tone.NORMAL, Optional.empty(), 3, actions);
	}

	@Test
	void aRowAllowsExactlyItsControls() {
		ConstructionRow row = row(-1, Action.CANCEL.bit() | Action.START.bit());
		assertTrue(row.allows(Action.CANCEL));
		assertTrue(row.allows(Action.START));
		assertFalse(row.allows(Action.RAISE_CLEAR));
		assertFalse(row.allows(Action.RAISE_BUILD));
		assertFalse(row(-1, 0).allows(Action.CANCEL));
	}

	@Test
	void everyControlHasItsOwnBitAndTooltipAndComesBackFromItsOrdinal() {
		int seen = 0;
		for (Action action : Action.values()) {
			assertEquals(0, seen & action.bit(), action + " shares a bit");
			seen |= action.bit();
			assertEquals(Optional.of(action), Action.byOrdinal(action.ordinal()));
			assertTrue(action.key().startsWith("settlement.alaindustrial.works.action."), action.key());
		}
		assertEquals("settlement.alaindustrial.works.action.raise_clear", Action.RAISE_CLEAR.key());
		assertTrue(Action.byOrdinal(-1).isEmpty());
		assertTrue(Action.byOrdinal(Action.values().length).isEmpty());
	}

	@Test
	void thePercentStaysInItsRange() {
		assertEquals(-1, row(-7, 0).percent());
		assertEquals(100, row(140, 0).percent());
		assertEquals(52, row(52, 0).percent());
	}

	@Test
	void aBlockReasonBecomesALineOfTheSameShape() {
		BlockReason reason = BlockReason.noMaterials(List.of("12", "block.minecraft.bricks"));
		ConstructionRow.Text text = ConstructionRow.Text.of(reason);
		assertEquals(BlockReason.NO_MATERIALS, text.key());
		assertEquals(List.of("12", "block.minecraft.bricks"), text.args());
		assertTrue(text.joined());
		BlockReason obstructed = BlockReason.obstructed("block.minecraft.chest");
		assertEquals(new ConstructionRow.Text(BlockReason.OBSTRUCTED, List.of("block.minecraft.chest"), false),
				ConstructionRow.Text.of(obstructed));
		assertTrue(BlockReason.KEYS.contains(BlockReason.NO_ENERGY));
	}

	@Test
	void savedNamesComeBack() {
		for (BuildingState state : BuildingState.values()) {
			assertEquals(state, BuildingState.byName(state.serializedName()));
		}
		assertEquals("clearing", BuildingState.CLEARING.serializedName());
		assertEquals(BuildingState.RUIN, BuildingState.byName("collapsed"));
		for (WorkJobStatus status : WorkJobStatus.values()) {
			assertEquals(status, WorkJobStatus.byName(status.serializedName()));
		}
		assertEquals(WorkJobStatus.QUEUED, WorkJobStatus.byName("paused"));
		assertTrue(WorkJobStatus.BLOCKED.open());
		assertFalse(WorkJobStatus.DONE.open());
		assertFalse(WorkJobStatus.CANCELLED.open());
		for (WorkJobType type : WorkJobType.values()) {
			assertEquals(Optional.of(type), WorkJobType.byName(type.serializedName()));
		}
		assertEquals("build", WorkJobType.BUILD.serializedName());
		assertTrue(WorkJobType.byName("lay_road").isEmpty());
	}
}

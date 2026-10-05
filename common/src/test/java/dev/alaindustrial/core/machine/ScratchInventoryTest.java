package dev.alaindustrial.core.machine;

import static dev.alaindustrial.core.machine.FakeStack.EMPTY;
import static dev.alaindustrial.core.machine.FakeStack.Thing.BIRCH_PLANKS;
import static dev.alaindustrial.core.machine.FakeStack.Thing.COBBLESTONE;
import static dev.alaindustrial.core.machine.FakeStack.Thing.GLASS_BOTTLE;
import static dev.alaindustrial.core.machine.FakeStack.Thing.HAMMER;
import static dev.alaindustrial.core.machine.FakeStack.Thing.HONEY_BOTTLE;
import static dev.alaindustrial.core.machine.FakeStack.Thing.OAK_PLANKS;
import static dev.alaindustrial.core.machine.FakeStack.Thing.SPRUCE_PLANKS;
import static dev.alaindustrial.core.machine.FakeStack.Thing.STICK;
import static dev.alaindustrial.core.machine.FakeStack.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.machine.ScratchInventory.Reserved;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * L1 table for {@link ScratchInventory} — the assembler's planning over scratch lists (MOD-714).
 *
 * <p>The case names follow the world scenarios of {@code AssemblerPlanningScenarios}, which pinned the
 * same decisions on the code before it left the block entity; the extra cases pin the boundaries a
 * world scenario cannot reach cheaply (the exact fill of a home slot, an out-of-range home, the
 * canonical empty left behind). Expected lists are written out, never recomputed.
 */
class ScratchInventoryTest {

	private static final FakeStack.Ops OPS = FakeStack.Ops.INSTANCE;

	private static List<FakeStack> scratch(FakeStack... stacks) {
		return new ArrayList<>(Arrays.asList(stacks));
	}

	// -- insertInto -----------------------------------------------------------------------------------

	@Test
	void insertIntoMergesBeforeEmpty() {
		List<FakeStack> output = scratch(EMPTY, of(STICK, 4), EMPTY);
		assertTrue(ScratchInventory.insertInto(OPS, output, of(STICK, 4)));
		assertEquals(List.of(EMPTY, of(STICK, 8), EMPTY), output);
	}

	@Test
	void insertIntoAllOrNothing() {
		List<FakeStack> output = scratch(of(COBBLESTONE, 64), of(STICK, 62));
		List<FakeStack> before = List.copyOf(output);
		assertFalse(ScratchInventory.insertInto(OPS, output, of(STICK, 4)));
		assertEquals(before, output, "a stack that fits only in part must leave the scratch untouched");
	}

	@Test
	void insertIntoRespectsComponents() {
		List<FakeStack> output = scratch(of(STICK, 4, "named"), EMPTY);
		assertTrue(ScratchInventory.insertInto(OPS, output, of(STICK, 4)));
		assertEquals(List.of(of(STICK, 4, "named"), of(STICK, 4)), output);
	}

	@Test
	void insertIntoSpreadsOverPartialStacksThenEmpty() {
		List<FakeStack> output = scratch(of(STICK, 62), EMPTY, of(STICK, 63));
		assertTrue(ScratchInventory.insertInto(OPS, output, of(STICK, 4)));
		assertEquals(List.of(of(STICK, 64), of(STICK, 1), of(STICK, 64)), output);
	}

	@Test
	void insertIntoFillsEmptySlotsOneMaxStackAtATime() {
		List<FakeStack> output = scratch(EMPTY, EMPTY, EMPTY);
		assertTrue(ScratchInventory.insertInto(OPS, output, of(STICK, 100)));
		assertEquals(List.of(of(STICK, 64), of(STICK, 36), EMPTY), output);
	}

	@Test
	void insertIntoExactFitSucceeds() {
		List<FakeStack> output = scratch(of(STICK, 60), of(COBBLESTONE, 64));
		assertTrue(ScratchInventory.insertInto(OPS, output, of(STICK, 4)));
		assertEquals(List.of(of(STICK, 64), of(COBBLESTONE, 64)), output);
	}

	@Test
	void insertIntoFullStackOfSameItemIsSkipped() {
		List<FakeStack> output = scratch(of(STICK, 64), EMPTY);
		assertTrue(ScratchInventory.insertInto(OPS, output, of(STICK, 4)));
		assertEquals(List.of(of(STICK, 64), of(STICK, 4)), output);
	}

	@Test
	void insertIntoNoRoomAtAllRefuses() {
		List<FakeStack> output = scratch(of(COBBLESTONE, 64));
		assertFalse(ScratchInventory.insertInto(OPS, output, of(STICK, 1)));
		assertEquals(List.of(of(COBBLESTONE, 64)), output);
	}

	@Test
	void insertIntoLeavesUntouchedEmptiesCanonical() {
		List<FakeStack> output = scratch(of(STICK, 1), EMPTY);
		assertTrue(ScratchInventory.insertInto(OPS, output, of(STICK, 1)));
		assertSame(EMPTY, output.get(1), "a slot nothing was added to must keep the very same empty stack");
	}

	// -- returnRemainder ------------------------------------------------------------------------------

	@Test
	void remainderHomeEmptyTakesIt() {
		FakeStack hammer = of(HAMMER, 1, "damage=11");
		List<FakeStack> store = scratch(of(STICK, 5), EMPTY, EMPTY);
		List<FakeStack> output = scratch(EMPTY);
		assertTrue(ScratchInventory.returnRemainder(OPS, store, output, 2, hammer));
		assertSame(hammer, store.get(2), "the remainder itself goes home, not a copy");
		assertEquals(List.of(of(STICK, 5), EMPTY, hammer), store);
		assertEquals(List.of(EMPTY), output);
	}

	@Test
	void remainderHomeAtSlotZeroIsHonoured() {
		List<FakeStack> store = scratch(EMPTY, of(GLASS_BOTTLE, 1));
		assertTrue(ScratchInventory.returnRemainder(OPS, store, scratch(EMPTY), 0, of(GLASS_BOTTLE, 1)));
		assertEquals(List.of(of(GLASS_BOTTLE, 1), of(GLASS_BOTTLE, 1)), store,
				"home slot 0 takes the remainder although a partial stack elsewhere would merge it");
	}

	@Test
	void remainderHomeMergesTheSameStack() {
		List<FakeStack> store = scratch(of(GLASS_BOTTLE, 3), of(GLASS_BOTTLE, 1));
		assertTrue(ScratchInventory.returnRemainder(OPS, store, scratch(EMPTY), 1, of(GLASS_BOTTLE, 1)));
		assertEquals(List.of(of(GLASS_BOTTLE, 3), of(GLASS_BOTTLE, 2)), store,
				"a home holding the same stack with room takes the remainder, even with another stack lower");
	}

	@Test
	void remainderHomeFilledExactlyToMax() {
		List<FakeStack> store = scratch(of(HONEY_BOTTLE, 10), of(HONEY_BOTTLE, 15));
		assertTrue(ScratchInventory.returnRemainder(OPS, store, scratch(EMPTY), 1, of(HONEY_BOTTLE, 1)));
		assertEquals(List.of(of(HONEY_BOTTLE, 10), of(HONEY_BOTTLE, 16)), store,
				"a home with exactly one unit of room takes it, ahead of the lower partial stack");
	}

	@Test
	void remainderHomeFullGoesElsewhere() {
		List<FakeStack> store = scratch(EMPTY, of(HONEY_BOTTLE, 16), of(HONEY_BOTTLE, 3));
		assertTrue(ScratchInventory.returnRemainder(OPS, store, scratch(EMPTY), 1, of(HONEY_BOTTLE, 1)));
		assertEquals(List.of(EMPTY, of(HONEY_BOTTLE, 16), of(HONEY_BOTTLE, 4)), store,
				"a full home sends the remainder to a partial stack elsewhere before an empty slot");
	}

	@Test
	void remainderHomeWithOtherComponentsGoesElsewhere() {
		List<FakeStack> store = scratch(of(GLASS_BOTTLE, 1, "named"), EMPTY);
		assertTrue(ScratchInventory.returnRemainder(OPS, store, scratch(EMPTY), 0, of(GLASS_BOTTLE, 1)));
		assertEquals(List.of(of(GLASS_BOTTLE, 1, "named"), of(GLASS_BOTTLE, 1)), store);
	}

	@Test
	void remainderHomeOccupiedGoesElsewhere() {
		List<FakeStack> store = scratch(of(HONEY_BOTTLE, 1), EMPTY, of(GLASS_BOTTLE, 1));
		assertTrue(ScratchInventory.returnRemainder(OPS, store, scratch(EMPTY), 0, of(GLASS_BOTTLE, 1)));
		assertEquals(List.of(of(HONEY_BOTTLE, 1), EMPTY, of(GLASS_BOTTLE, 2)), store,
				"elsewhere in the warehouse means a matching partial stack first, then the first empty slot");
	}

	@Test
	void remainderWithoutHomeGoesElsewhere() {
		for (int home : new int[] {-1, 2}) {
			List<FakeStack> store = scratch(EMPTY, of(STICK, 1));
			assertTrue(ScratchInventory.returnRemainder(OPS, store, scratch(EMPTY), home, of(GLASS_BOTTLE, 1)));
			assertEquals(List.of(of(GLASS_BOTTLE, 1), of(STICK, 1)), store, "home " + home);
		}
	}

	@Test
	void remainderWarehouseFullGoesToOutput() {
		List<FakeStack> store = scratch(of(HONEY_BOTTLE, 1), of(COBBLESTONE, 64));
		List<FakeStack> output = scratch(of(STICK, 3), EMPTY);
		assertTrue(ScratchInventory.returnRemainder(OPS, store, output, 0, of(GLASS_BOTTLE, 1)));
		assertEquals(List.of(of(HONEY_BOTTLE, 1), of(COBBLESTONE, 64)), store);
		assertEquals(List.of(of(STICK, 3), of(GLASS_BOTTLE, 1)), output);
	}

	@Test
	void remainderNowhereRefusesOperation() {
		List<FakeStack> store = scratch(of(HONEY_BOTTLE, 1), of(COBBLESTONE, 64));
		List<FakeStack> output = scratch(of(STICK, 64));
		List<FakeStack> storeBefore = List.copyOf(store);
		List<FakeStack> outputBefore = List.copyOf(output);
		assertFalse(ScratchInventory.returnRemainder(OPS, store, output, 0, of(GLASS_BOTTLE, 1)));
		assertEquals(storeBefore, store);
		assertEquals(outputBefore, output);
	}

	// -- findAndTake ----------------------------------------------------------------------------------

	@Test
	void findAndTakeLowestSlotFirst() {
		List<FakeStack> store = scratch(EMPTY, of(OAK_PLANKS, 2), of(OAK_PLANKS, 2));
		Optional<Reserved<FakeStack>> taken = ScratchInventory.findAndTake(OPS, store, of(OAK_PLANKS, 1));
		assertEquals(Optional.of(new Reserved<>(1, of(OAK_PLANKS, 1))), taken);
		assertEquals(List.of(EMPTY, of(OAK_PLANKS, 1), of(OAK_PLANKS, 2)), store);
	}

	@Test
	void findAndTakeIgnoresComponentsAndKeepsThem() {
		List<FakeStack> store = scratch(of(STICK, 3), of(HAMMER, 1, "damage=10"));
		Optional<Reserved<FakeStack>> taken = ScratchInventory.findAndTake(OPS, store, of(HAMMER, 1));
		assertEquals(Optional.of(new Reserved<>(1, of(HAMMER, 1, "damage=10"))), taken,
				"a worn tool satisfies a recipe written against a fresh one, and keeps its wear");
		assertEquals(List.of(of(STICK, 3), EMPTY), store);
	}

	@Test
	void findAndTakeLastUnitLeavesTheCanonicalEmpty() {
		List<FakeStack> store = scratch(of(OAK_PLANKS, 1));
		ScratchInventory.findAndTake(OPS, store, of(OAK_PLANKS, 1));
		assertSame(EMPTY, store.get(0));
	}

	@Test
	void findAndTakeNothingLeavesScratchAlone() {
		List<FakeStack> store = scratch(EMPTY, of(BIRCH_PLANKS, 4));
		assertEquals(Optional.empty(), ScratchInventory.findAndTake(OPS, store, of(OAK_PLANKS, 1)));
		assertEquals(List.of(EMPTY, of(BIRCH_PLANKS, 4)), store);
	}

	// -- findAndTakeAny -------------------------------------------------------------------------------

	@Test
	void findAndTakeAnyFollowsCandidateOrderNotSlotOrder() {
		List<FakeStack> store = scratch(of(BIRCH_PLANKS, 1), EMPTY, of(OAK_PLANKS, 5, "dyed"));
		Optional<Reserved<FakeStack>> taken = ScratchInventory.findAndTakeAny(OPS, store,
				List.of(OAK_PLANKS, BIRCH_PLANKS), SPRUCE_PLANKS);
		assertEquals(Optional.of(new Reserved<>(2, of(OAK_PLANKS, 1, "dyed"))), taken);
		assertEquals(List.of(of(BIRCH_PLANKS, 1), EMPTY, of(OAK_PLANKS, 4, "dyed")), store);
	}

	@Test
	void findAndTakeAnySkipsTheRecordedItem() {
		List<FakeStack> store = scratch(of(OAK_PLANKS, 3), of(BIRCH_PLANKS, 1));
		Optional<Reserved<FakeStack>> taken = ScratchInventory.findAndTakeAny(OPS, store,
				List.of(OAK_PLANKS, BIRCH_PLANKS), OAK_PLANKS);
		assertEquals(Optional.of(new Reserved<>(1, of(BIRCH_PLANKS, 1))), taken);
		assertSame(EMPTY, store.get(1));
		assertEquals(of(OAK_PLANKS, 3), store.get(0));
	}

	@Test
	void findAndTakeAnyLowestSlotWithinACandidate() {
		List<FakeStack> store = scratch(of(STICK, 1), of(BIRCH_PLANKS, 2), of(BIRCH_PLANKS, 2));
		Optional<Reserved<FakeStack>> taken = ScratchInventory.findAndTakeAny(OPS, store,
				List.of(BIRCH_PLANKS), OAK_PLANKS);
		assertEquals(Optional.of(new Reserved<>(1, of(BIRCH_PLANKS, 1))), taken);
		assertEquals(List.of(of(STICK, 1), of(BIRCH_PLANKS, 1), of(BIRCH_PLANKS, 2)), store);
	}

	@Test
	void findAndTakeAnyNothingSuppliableLeavesScratchAlone() {
		List<FakeStack> store = scratch(EMPTY, of(OAK_PLANKS, 2));
		assertEquals(Optional.empty(), ScratchInventory.findAndTakeAny(OPS, store,
				List.of(OAK_PLANKS, BIRCH_PLANKS), OAK_PLANKS));
		assertEquals(List.of(EMPTY, of(OAK_PLANKS, 2)), store);
	}
}

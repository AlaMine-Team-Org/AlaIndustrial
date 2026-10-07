package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** L1 rules of the Garden Drone Station's output block (MOD-782). */
class GardenDroneOutputBlockTest {

	@Test
	void startsOpenAndAMissingKeyLoadsOpen() {
		GardenDroneOutputBlock block = new GardenDroneOutputBlock();
		assertFalse(block.isBlocked());
		block.restore(GardenDroneOutputBlock.NOT_BLOCKED);
		assertFalse(block.isBlocked());
		block.restore(-42);
		assertFalse(block.isBlocked(), "a corrupt negative value must read as open, not as blocked");
	}

	@Test
	void takingAnythingOutLiftsTheBlock() {
		GardenDroneOutputBlock block = new GardenDroneOutputBlock();
		block.block(252);
		assertFalse(block.refresh(252, false), "nothing changed, nothing to save");
		assertTrue(block.isBlocked());
		assertTrue(block.refresh(251, false));
		assertFalse(block.isBlocked());
	}

	@Test
	void itemsAddedWhileBlockedRaiseTheSnapshot() {
		GardenDroneOutputBlock block = new GardenDroneOutputBlock();
		block.block(100);
		assertTrue(block.refresh(110, false));
		assertEquals(110, block.saved());
		// Back to 105: still more than when blocked, but one was taken since the snapshot rose.
		block.refresh(105, false);
		assertFalse(block.isBlocked());
	}

	@Test
	void aFullOutputBlocksWithoutATry() {
		GardenDroneOutputBlock block = new GardenDroneOutputBlock();
		assertTrue(block.refresh(256, true));
		assertTrue(block.isBlocked());
		assertEquals(256, block.saved());
	}

	@Test
	void aBlockOnAnEmptyOutputIsRetriedFromIdleNotEveryTick() {
		// A tile whose drop needs more than four slots is refused into an EMPTY output: nothing can ever
		// be taken out, so only retryIfEmpty may lift it — and refresh must not, or the drone would
		// re-fly every round trip instead of once per idle back-off.
		GardenDroneOutputBlock block = new GardenDroneOutputBlock();
		block.block(0);
		assertFalse(block.refresh(0, false));
		assertTrue(block.isBlocked());
		assertTrue(block.retryIfEmpty(0));
		assertFalse(block.isBlocked());
	}

	@Test
	void retryIfEmptyLeavesABlockOverItemsAlone() {
		GardenDroneOutputBlock block = new GardenDroneOutputBlock();
		block.block(5);
		assertFalse(block.retryIfEmpty(5));
		assertTrue(block.isBlocked());
	}

	@Test
	void savedValueRoundTrips() {
		GardenDroneOutputBlock block = new GardenDroneOutputBlock();
		block.block(7);
		GardenDroneOutputBlock loaded = new GardenDroneOutputBlock();
		loaded.restore(block.saved());
		assertTrue(loaded.isBlocked());
		assertTrue(loaded.refresh(6, false));
		assertFalse(loaded.isBlocked());
	}
}

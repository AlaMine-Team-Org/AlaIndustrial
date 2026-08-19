package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** L1 contract for the status ordinal shared by the five one-input processing machines (MOD-458). */
class ProcessingMachineStatusTest {

	/**
	 * The silent set is written out rather than derived from the enum, because it is a UX decision with a
	 * cost on both sides: caption a state that needs no caption and four machines that idle empty most of
	 * their life grow a permanent line; drop one that does and the machine is back to stalling in silence.
	 */
	@Test
	void onlyReadyAndNoInputStaySilent() {
		Set<ProcessingMachineStatus> silent =
				EnumSet.of(ProcessingMachineStatus.READY, ProcessingMachineStatus.NO_INPUT);
		for (ProcessingMachineStatus status : ProcessingMachineStatus.values()) {
			assertEquals(!silent.contains(status), status.isBlocking(),
					status + ": isBlocking() disagrees with the documented silent set");
		}
		// The one that must never drift: a partial batch is the whole reason this channel exists.
		assertTrue(ProcessingMachineStatus.NOT_ENOUGH_INPUT.isBlocking());
	}

	@Test
	void translationKeysAreStable() {
		assertEquals("gui.alaindustrial.processing_machine.status.ready",
				ProcessingMachineStatus.READY.translationKey());
		assertEquals("gui.alaindustrial.processing_machine.status.no_input",
				ProcessingMachineStatus.NO_INPUT.translationKey());
		assertEquals("gui.alaindustrial.processing_machine.status.no_recipe",
				ProcessingMachineStatus.NO_RECIPE.translationKey());
		assertEquals("gui.alaindustrial.processing_machine.status.not_enough_input",
				ProcessingMachineStatus.NOT_ENOUGH_INPUT.translationKey());
		assertEquals("gui.alaindustrial.processing_machine.status.output_blocked",
				ProcessingMachineStatus.OUTPUT_BLOCKED.translationKey());
		assertEquals("gui.alaindustrial.processing_machine.status.no_energy",
				ProcessingMachineStatus.NO_ENERGY.translationKey());
	}

	@Test
	void ordinalWireFormatRoundTripsAndRejectsInvalidValues() {
		for (ProcessingMachineStatus status : ProcessingMachineStatus.values()) {
			assertEquals(status, ProcessingMachineStatus.byOrdinal(status.ordinal()));
		}
		assertEquals(ProcessingMachineStatus.READY, ProcessingMachineStatus.byOrdinal(-1));
		assertEquals(ProcessingMachineStatus.READY,
				ProcessingMachineStatus.byOrdinal(ProcessingMachineStatus.values().length));
		assertEquals(ProcessingMachineStatus.READY, ProcessingMachineStatus.byOrdinal(Integer.MIN_VALUE));
		assertEquals(ProcessingMachineStatus.READY, ProcessingMachineStatus.byOrdinal(Integer.MAX_VALUE));
	}

	/**
	 * Ordinal 0 is load-bearing beyond the usual "clamp to something harmless".
	 *
	 * <p>A client menu stub starts its data array at zeroes and the L3 shot stands fill only the four base
	 * channels, so every frame that does not deliberately set a status reads 0 back. If anything but a
	 * silent state sat there, every existing screenshot of an idle machine would grow a caption.
	 */
	@Test
	void ordinalZeroIsSilent() {
		assertEquals(0, ProcessingMachineStatus.READY.ordinal());
		assertFalse(ProcessingMachineStatus.byOrdinal(0).isBlocking());
	}
}

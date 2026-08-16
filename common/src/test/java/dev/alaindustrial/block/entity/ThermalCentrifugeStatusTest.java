package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** L1 contract for the status ordinal synchronized by the Thermal Centrifuge menu (MOD-424). */
class ThermalCentrifugeStatusTest {

	@Test
	void readyIsTheOnlyNonBlockingState() {
		assertFalse(ThermalCentrifugeStatus.READY.isBlocking());
		for (ThermalCentrifugeStatus status : ThermalCentrifugeStatus.values()) {
			if (status != ThermalCentrifugeStatus.READY) {
				assertTrue(status.isBlocking(), status + " must explain a processing stall");
			}
		}
	}

	@Test
	void translationKeysAreStable() {
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.ready",
				ThermalCentrifugeStatus.READY.translationKey());
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.no_signal",
				ThermalCentrifugeStatus.NO_SIGNAL.translationKey());
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.no_input",
				ThermalCentrifugeStatus.NO_INPUT.translationKey());
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.no_recipe",
				ThermalCentrifugeStatus.NO_RECIPE.translationKey());
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.output_blocked",
				ThermalCentrifugeStatus.OUTPUT_BLOCKED.translationKey());
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.spinning_up",
				ThermalCentrifugeStatus.SPINNING_UP.translationKey());
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.no_heater",
				ThermalCentrifugeStatus.NO_HEATER.translationKey());
		assertEquals("gui.alaindustrial.thermal_centrifuge.status.heater_cold",
				ThermalCentrifugeStatus.HEATER_COLD.translationKey());
	}

	@Test
	void ordinalWireFormatRoundTripsAndRejectsInvalidValues() {
		for (ThermalCentrifugeStatus status : ThermalCentrifugeStatus.values()) {
			assertEquals(status, ThermalCentrifugeStatus.byOrdinal(status.ordinal()));
		}
		assertEquals(ThermalCentrifugeStatus.READY, ThermalCentrifugeStatus.byOrdinal(-1));
		assertEquals(ThermalCentrifugeStatus.READY,
				ThermalCentrifugeStatus.byOrdinal(ThermalCentrifugeStatus.values().length));
		assertEquals(ThermalCentrifugeStatus.READY, ThermalCentrifugeStatus.byOrdinal(Integer.MAX_VALUE));
	}

	/**
	 * Exactly three states may warm the heater below, and the set is written out here rather than derived
	 * from the enum: this is the one predicate on the class with real gameplay cost on BOTH sides. Too wide
	 * and a machine switched off at the lever burns a full heater ramp for nothing; too narrow and the
	 * heater cools between two paid ticks of the work it is actively serving.
	 */
	@Test
	void onlyReadySpinningUpAndHeaterColdWaitOnHeat() {
		Set<ThermalCentrifugeStatus> expected = EnumSet.of(ThermalCentrifugeStatus.READY,
				ThermalCentrifugeStatus.SPINNING_UP, ThermalCentrifugeStatus.HEATER_COLD);
		for (ThermalCentrifugeStatus status : ThermalCentrifugeStatus.values()) {
			assertEquals(expected.contains(status), status.waitsOnHeat(),
					status + ": waitsOnHeat() disagrees with the documented set");
		}
		// The one that must never drift: no signal means the player switched the machine off, and a
		// heater that warmed anyway would spend 1200 EU under a stopped rotor.
		assertFalse(ThermalCentrifugeStatus.NO_SIGNAL.waitsOnHeat());
	}
}

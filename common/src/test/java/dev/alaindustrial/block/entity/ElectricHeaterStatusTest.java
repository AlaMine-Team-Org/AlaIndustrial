package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 contract for the Electric Heater's status channel (MOD-418).
 *
 * <p>The enum is Minecraft-free and therefore a pitest target, so the boundary cases below are chosen
 * to kill the mutants that class of enum reliably produces rather than to restate the switch.
 */
class ElectricHeaterStatusTest {

	@Test
	void translationKeysAreStableAndStatusSpecific() {
		assertEquals("gui.alaindustrial.electric_heater.status.no_consumer",
				ElectricHeaterStatus.NO_CONSUMER.translationKey());
		assertEquals("gui.alaindustrial.electric_heater.status.no_energy",
				ElectricHeaterStatus.NO_ENERGY.translationKey());
		assertEquals("gui.alaindustrial.electric_heater.status.warming",
				ElectricHeaterStatus.WARMING.translationKey());
		assertEquals("gui.alaindustrial.electric_heater.status.hot",
				ElectricHeaterStatus.HOT.translationKey());
		assertEquals("gui.alaindustrial.electric_heater.status.cooling",
				ElectricHeaterStatus.COOLING.translationKey());
		assertEquals("gui.alaindustrial.electric_heater.status.cold",
				ElectricHeaterStatus.COLD.translationKey());
	}

	@Test
	void ordinalWireFormatRoundTripsEveryStatus() {
		for (ElectricHeaterStatus status : ElectricHeaterStatus.values()) {
			assertEquals(status, ElectricHeaterStatus.byOrdinal(status.ordinal()));
		}
	}

	/**
	 * Both edges of {@code byOrdinal}, chosen to be killable.
	 *
	 * <p>The upper one is exactly {@code values().length}: that is the only input separating
	 * {@code ordinal < length} from {@code <=}, and a suite that checks only -1 and some large number
	 * leaves that mutant alive (the trap MOD-244 documented). The lower one is ordinal 0, which is a real
	 * assertion here rather than a tautology precisely because the fallback is {@link
	 * ElectricHeaterStatus#COLD} and NOT the zeroth constant — so {@code ordinal >= 0} mutated to
	 * {@code > 0} changes the answer.
	 */
	@Test
	void outOfRangeOrdinalsFallBackToCold() {
		assertEquals(ElectricHeaterStatus.NO_CONSUMER, ElectricHeaterStatus.byOrdinal(0),
				"ordinal 0 must be the zeroth constant, not the fallback");
		assertEquals(ElectricHeaterStatus.COLD, ElectricHeaterStatus.byOrdinal(-1));
		assertEquals(ElectricHeaterStatus.COLD,
				ElectricHeaterStatus.byOrdinal(ElectricHeaterStatus.values().length));
		assertEquals(ElectricHeaterStatus.COLD, ElectricHeaterStatus.byOrdinal(Integer.MIN_VALUE));
		assertEquals(ElectricHeaterStatus.COLD, ElectricHeaterStatus.byOrdinal(Integer.MAX_VALUE));
	}

	/** Exactly the two states the player can act on are the ones drawn in red. */
	@Test
	void onlyActionableStatesAskForAttention() {
		Set<ElectricHeaterStatus> actionable =
				EnumSet.of(ElectricHeaterStatus.NO_CONSUMER, ElectricHeaterStatus.NO_ENERGY);
		for (ElectricHeaterStatus status : ElectricHeaterStatus.values()) {
			assertEquals(actionable.contains(status), status.needsAttention(),
					status + " needsAttention");
		}
	}

	/**
	 * Only the two working states report live numbers; everything else must draw a dash.
	 *
	 * <p>COOLING is the one worth stating out loud: the heater still holds heat, so it is tempting to
	 * treat it as working — but it is serving nobody, and printing a rate for it would be a measurement
	 * of nothing.
	 */
	@Test
	void onlyWorkingStatesReportLiveNumbers() {
		assertTrue(ElectricHeaterStatus.WARMING.isWorking());
		assertTrue(ElectricHeaterStatus.HOT.isWorking());
		assertFalse(ElectricHeaterStatus.COOLING.isWorking());
		assertFalse(ElectricHeaterStatus.COLD.isWorking());
		assertFalse(ElectricHeaterStatus.NO_CONSUMER.isWorking());
		assertFalse(ElectricHeaterStatus.NO_ENERGY.isWorking());
	}
}

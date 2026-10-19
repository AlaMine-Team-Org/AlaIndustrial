package dev.alaindustrial.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.energy.EnergyTier;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

/**
 * L1 for the settlement core's tiers (MOD-831): the ladder LV → MV → HV → atomic only climbs (epic MOD-824, R5 and
 * R11 — a higher tier is a larger town), whatever an operator writes into the file, and the shipped defaults climb on
 * their own.
 */
class SettlementTierTest {

	/**
	 * The acceptance property: each next tier is not below the previous one in radius, house limit or worker limit —
	 * for any knob values, not only the shipped ones. Each tier also keeps its own knob when that is the larger.
	 */
	@Property
	void everyNextTierIsNotBelowThePreviousOne(
			@ForAll @Size(4) List<@IntRange(min = 0, max = 10_000) Integer> radii,
			@ForAll @Size(4) List<@IntRange(min = 0, max = 10_000) Integer> houses,
			@ForAll @Size(4) List<@IntRange(min = 0, max = 10_000) Integer> workers) {
		Knobs saved = Knobs.read();
		try {
			new Knobs(radii, houses, workers, saved.upkeep()).write();
			SettlementTier[] tiers = SettlementTier.values();
			for (int i = 0; i < tiers.length; i++) {
				SettlementTier tier = tiers[i];
				assertTrue(tier.radius() >= radii.get(i), tier + " lost its own radius");
				assertTrue(tier.houseLimit() >= houses.get(i), tier + " lost its own house limit");
				assertTrue(tier.workerLimit() >= workers.get(i), tier + " lost its own worker limit");
				if (i > 0) {
					SettlementTier below = tiers[i - 1];
					assertTrue(tier.radius() >= below.radius(), tier + " radius below " + below);
					assertTrue(tier.houseLimit() >= below.houseLimit(), tier + " house limit below " + below);
					assertTrue(tier.workerLimit() >= below.workerLimit(), tier + " worker limit below " + below);
				}
			}
		} finally {
			saved.write();
		}
	}

	/** A file whose tiers already climb is read exactly as written — the floor never invents a number. */
	@Test
	void aClimbingFileIsReadAsWritten() {
		Knobs saved = Knobs.read();
		try {
			new Knobs(List.of(5, 6, 7, 8), List.of(1, 2, 3, 4), List.of(2, 3, 4, 5), saved.upkeep()).write();
			assertEquals(List.of(5, 6, 7, 8), map(SettlementTier::radius));
			assertEquals(List.of(1, 2, 3, 4), map(SettlementTier::houseLimit));
			assertEquals(List.of(2, 3, 4, 5), map(SettlementTier::workerLimit));
		} finally {
			saved.write();
		}
	}

	/** An MV radius written below the LV one reads as the LV one: upgrading must not shrink the town. */
	@Test
	void aLowerTierRadiusIsLiftedToTheOneBeneath() {
		Knobs saved = Knobs.read();
		try {
			new Knobs(List.of(40, 10, 50, 20), saved.houses(), saved.workers(), saved.upkeep()).write();
			assertEquals(List.of(40, 40, 50, 50), map(SettlementTier::radius));
		} finally {
			saved.write();
		}
	}

	/**
	 * The shipped defaults climb strictly on their own, upkeep included — the floor exists for operators' files, not
	 * to hide a bad default — and the LV upkeep fits one LV packet, so a single LV line holds a town (core spec).
	 */
	@Test
	void shippedDefaultsClimbStrictlyAndLvFitsAnLvLine() {
		Knobs defaults = Knobs.read();
		List<List<Integer>> columns =
				List.of(defaults.radii(), defaults.houses(), defaults.workers(), defaults.upkeep());
		for (List<Integer> column : columns) {
			for (int i = 1; i < column.size(); i++) {
				assertTrue(column.get(i) > column.get(i - 1), "defaults must climb strictly: " + column);
			}
		}
		assertTrue(SettlementTier.LV.euPerTick() <= EnergyTier.LV.defaultMaxVoltage(),
				"the LV upkeep must fit one LV packet");
		assertTrue(SettlementConfig.settlementCoreBuffer >= SettlementTier.LV.euPerTick()
						* (long) SettlementConfig.settlementCoreGraceTicks,
				"the buffer must hold the reserve a dark core waits for");
	}

	@Test
	void namesRoundTripAndUnknownsFallBackToTheFirstTier() {
		for (SettlementTier tier : SettlementTier.values()) {
			assertEquals(tier, SettlementTier.byName(tier.serializedName()));
			assertEquals(tier, SettlementTier.byOrdinal(tier.ordinal()));
		}
		assertEquals("atomic", SettlementTier.ATOMIC.serializedName());
		assertEquals(SettlementTier.FIRST, SettlementTier.byName("ev"));
		assertEquals(SettlementTier.FIRST, SettlementTier.byOrdinal(-1));
		assertEquals(SettlementTier.FIRST, SettlementTier.byOrdinal(4));
		assertEquals(SettlementTier.LV, SettlementTier.FIRST);
		assertEquals(SettlementTier.MV, SettlementTier.LV.next());
		assertEquals(SettlementTier.ATOMIC, SettlementTier.ATOMIC.next());
	}

	/**
	 * Epic decision R44: a core takes the packets of the tier above its own — LV an MV packet, MV an HV one — and the
	 * two top tiers the mod's top packet, HV, because no energy tier goes higher. The intake always covers the upkeep,
	 * with room to spare below the top.
	 */
	@Test
	void intakeIsOneTierAboveAndCappedAtTheTopEnergyTier() {
		assertEquals(EnergyTier.MV, SettlementTier.LV.intakeTier());
		assertEquals(EnergyTier.HV, SettlementTier.MV.intakeTier());
		assertEquals(EnergyTier.HV, SettlementTier.HV.intakeTier());
		assertEquals(EnergyTier.HV, SettlementTier.ATOMIC.intakeTier());
		for (SettlementTier tier : SettlementTier.values()) {
			assertTrue(tier.intakeTier().defaultMaxVoltage() > tier.euPerTick(),
					tier + " must take more than its upkeep per tick");
		}
	}

	private static List<Integer> map(ToIntFunction<SettlementTier> f) {
		return Arrays.stream(SettlementTier.values()).map(f::applyAsInt).toList();
	}

	/** The sixteen tier knobs, read from and written back to {@link SettlementConfig}. */
	private record Knobs(List<Integer> radii, List<Integer> houses, List<Integer> workers, List<Integer> upkeep) {
		static Knobs read() {
			return new Knobs(
					List.of(SettlementConfig.settlementRadiusLv, SettlementConfig.settlementRadiusMv,
							SettlementConfig.settlementRadiusHv, SettlementConfig.settlementRadiusAtomic),
					List.of(SettlementConfig.settlementHouseLimitLv, SettlementConfig.settlementHouseLimitMv,
							SettlementConfig.settlementHouseLimitHv, SettlementConfig.settlementHouseLimitAtomic),
					List.of(SettlementConfig.settlementWorkerLimitLv, SettlementConfig.settlementWorkerLimitMv,
							SettlementConfig.settlementWorkerLimitHv, SettlementConfig.settlementWorkerLimitAtomic),
					List.of(SettlementConfig.settlementCoreEuPerTickLv, SettlementConfig.settlementCoreEuPerTickMv,
							SettlementConfig.settlementCoreEuPerTickHv,
							SettlementConfig.settlementCoreEuPerTickAtomic));
		}

		void write() {
			SettlementConfig.settlementRadiusLv = radii.get(0);
			SettlementConfig.settlementRadiusMv = radii.get(1);
			SettlementConfig.settlementRadiusHv = radii.get(2);
			SettlementConfig.settlementRadiusAtomic = radii.get(3);
			SettlementConfig.settlementHouseLimitLv = houses.get(0);
			SettlementConfig.settlementHouseLimitMv = houses.get(1);
			SettlementConfig.settlementHouseLimitHv = houses.get(2);
			SettlementConfig.settlementHouseLimitAtomic = houses.get(3);
			SettlementConfig.settlementWorkerLimitLv = workers.get(0);
			SettlementConfig.settlementWorkerLimitMv = workers.get(1);
			SettlementConfig.settlementWorkerLimitHv = workers.get(2);
			SettlementConfig.settlementWorkerLimitAtomic = workers.get(3);
			SettlementConfig.settlementCoreEuPerTickLv = upkeep.get(0);
			SettlementConfig.settlementCoreEuPerTickMv = upkeep.get(1);
			SettlementConfig.settlementCoreEuPerTickHv = upkeep.get(2);
			SettlementConfig.settlementCoreEuPerTickAtomic = upkeep.get(3);
		}
	}
}

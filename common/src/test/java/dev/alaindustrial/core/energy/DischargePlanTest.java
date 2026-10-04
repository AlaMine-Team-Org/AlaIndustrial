package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 tests for {@link DischargePlan} — which of the three storage discharge channels opens, how wide, and
 * which stores sit out the serve pass (MOD-715, batch 4; ADR-004, ADR-002).
 *
 * <p>Moved from {@code :neoforge:test} in batch 8, when the plan became generic over its position type: the
 * stores here are keyed by plain integers. A new channel is one component of the plan, one branch of
 * {@link DischargePlan#decide} and one case here — not a change to {@code EnergyNetwork.tick()}.
 *
 * @implements MOD-715-DP01 — the discharge plan opens one channel at a time, in ADR-004's order
 */
class DischargePlanTest {

	private static final long DEADBAND = 12;
	private static final long PACKET_CAP = 32;
	private static final double RESERVE = 0.5;

	private static final Integer A = 0;
	private static final Integer B = 5;
	private static final Integer FUND = 500;

	/** A store of {@code amount}/{@code capacity} EU that both takes and gives. */
	private static EnergyBuffer store(long capacity, long amount) {
		EnergyBuffer buffer = new EnergyBuffer(capacity, PACKET_CAP, PACKET_CAP, () -> { });
		buffer.setAmountUntracked(amount);
		return buffer;
	}

	/** Stores in {@code cascade} take the cascade, {@link #FUND} takes the feed; the line holds {@code inFlight}. */
	private static final class Stores implements DischargePlan.Stores<Integer> {
		final Set<Integer> cascade;
		final long inFlight;
		int inFlightAsked;

		Stores(Set<Integer> cascade, long inFlight) {
			this.cascade = cascade;
			this.inFlight = inFlight;
		}

		@Override
		public boolean acceptsCascade(Integer pos) {
			return cascade.contains(pos);
		}

		@Override
		public long feedRate(Integer pos) {
			return pos.equals(FUND) ? 24 : 0;
		}

		@Override
		public long inFlight() {
			inFlightAsked++;
			return inFlight;
		}
	}

	private static EnergyLineDistributor.LiveProducer<Integer> source(Integer pos, EnergyBuffer buffer) {
		return new EnergyLineDistributor.LiveProducer<>(pos, buffer);
	}

	private static EnergyLineDistributor.LiveConsumer<Integer> sink(Integer pos, EnergyBuffer buffer) {
		return new EnergyLineDistributor.LiveConsumer<>(pos, buffer, buffer.getCapacity() - buffer.getAmount());
	}

	@Test
	void backupOnlyIsTheDeficitAndNothingElse() {
		DischargePlan<Integer> plan = DischargePlan.backupOnly(50, 20);
		assertEquals(30, plan.backupBudget());
		assertTrue(plan.cascadeAllowances().isEmpty());
		assertTrue(plan.feedAllowances().isEmpty());
		assertEquals(0, DischargePlan.<Integer>backupOnly(20, 50).backupBudget(), "a covered demand opens nothing");
	}

	@Test
	void backupOpenKeepsTheCascadeClosedAndNeverAsksForTheLine() {
		Stores stores = new Stores(Set.of(A, B), 0);
		DischargePlan<Integer> plan = DischargePlan.decide(40, 10, List.of(source(A, store(20_000, 20_000))),
				List.of(sink(B, store(20_000, 0))), stores, DEADBAND, PACKET_CAP, RESERVE);
		assertEquals(30, plan.backupBudget());
		assertTrue(plan.cascadeAllowances().isEmpty(), "ADR-004: no cascade while machines are short");
		assertTrue(plan.feedAllowances().isEmpty());
		assertEquals(0, stores.inFlightAsked, "the cables are summed only when a cascade is sized");
		assertTrue(plan.excludes(A, Set.of(A)), "backup excludes every storage source from the serve pass");
		assertFalse(plan.excludes(B, Set.of(A)));
	}

	@Test
	void cascadeOpensWithoutMachineDemandAndExcludesOnlyTheDonor() {
		EnergyBuffer full = store(20_000, 20_000);
		EnergyBuffer empty = store(20_000, 0);
		Stores stores = new Stores(Set.of(A, B), 36);
		DischargePlan<Integer> plan = DischargePlan.decide(0, 0, List.of(source(A, full), source(B, empty)),
				List.of(sink(B, empty)), stores, DEADBAND, PACKET_CAP, RESERVE);
		long expected = CascadeShare.allowance(20_000, 20_000, 36, 20_000, DEADBAND, PACKET_CAP);
		assertTrue(expected > 0, "precondition: a full box beside an empty one is worth a transfer");
		assertEquals(Map.of(A, expected), plan.cascadeAllowances());
		assertEquals(0, plan.backupBudget());
		assertTrue(plan.feedAllowances().isEmpty());
		assertEquals(1, stores.inFlightAsked);
		assertTrue(plan.excludes(A, Set.of(A, B)), "the donor sits the serve pass out");
		assertFalse(plan.excludes(B, Set.of(A, B)), "an empty box with a cabled OUT face still charges (ADR-002)");
	}

	@Test
	void cascadeOutranksTheFeed() {
		EnergyBuffer donor = store(20_000, 20_000);
		DischargePlan<Integer> plan = DischargePlan.decide(0, 0, List.of(source(A, donor)),
				List.of(sink(B, store(20_000, 0)), sink(FUND, store(500_000, 0))), new Stores(Set.of(A, B), 0),
				DEADBAND, PACKET_CAP, RESERVE);
		assertFalse(plan.cascadeAllowances().isEmpty());
		assertTrue(plan.feedAllowances().isEmpty(), "ADR-004: the feed waits for the cascade to close");
	}

	@Test
	void feedOpensWhenBothEarlierChannelsAreClosed() {
		EnergyBuffer donor = store(20_000, 20_000);
		DischargePlan<Integer> plan = DischargePlan.decide(0, 0, List.of(source(A, donor)),
				List.of(sink(FUND, store(500_000, 0))), new Stores(Set.of(A), 0), DEADBAND, PACKET_CAP, RESERVE);
		long expected = StorageFeedShare.feedAllowance(20_000, 20_000, RESERVE, 500_000, 24, PACKET_CAP);
		assertTrue(expected > 0, "precondition: a full box may feed a fund");
		assertEquals(Map.of(A, expected), plan.feedAllowances());
		assertTrue(plan.cascadeAllowances().isEmpty());
		assertTrue(plan.excludes(A, Set.of(A)));
	}

	@Test
	void aStoreThatTakesTheFeedNeverFeeds() {
		DischargePlan<Integer> plan = DischargePlan.decide(0, 0, List.of(source(FUND, store(500_000, 500_000))),
				List.of(sink(B, store(20_000, 0))), new Stores(Set.of(), 0), DEADBAND, PACKET_CAP, RESERVE);
		assertTrue(plan.feedAllowances().isEmpty(), "a fund is a destination, not a donor");
		assertTrue(plan.cascadeAllowances().isEmpty());
	}

	@Test
	void noStorageSourceOpensNothing() {
		DischargePlan<Integer> plan = DischargePlan.decide(0, 0, List.of(), List.of(sink(B, store(20_000, 0))),
				new Stores(Set.of(B), 0), DEADBAND, PACKET_CAP, RESERVE);
		assertEquals(DischargePlan.<Integer>backupOnly(0, 0), plan);
	}

	@Test
	void aPlanWithTwoChannelsOpenCannotExist() {
		assertThrows(IllegalArgumentException.class, () -> new DischargePlan<>(5, Map.of(A, 1L), Map.of()));
		assertThrows(IllegalArgumentException.class, () -> new DischargePlan<>(0, Map.of(A, 1L), Map.of(A, 1L)));
		assertThrows(IllegalArgumentException.class, () -> new DischargePlan<>(5, Map.of(), Map.of(A, 1L)));
	}
}

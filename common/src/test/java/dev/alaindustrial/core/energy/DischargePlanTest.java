package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
	/** A sink outside both the cascade and the feed — an Energy Condenser. */
	private static final Integer CONDENSER = 50;

	/** A store of {@code amount}/{@code capacity} EU that both takes and gives. */
	private static EnergyBuffer store(long capacity, long amount) {
		EnergyBuffer buffer = new EnergyBuffer(capacity, PACKET_CAP, PACKET_CAP, () -> { });
		buffer.setAmountUntracked(amount);
		return buffer;
	}

	/**
	 * Stores in {@code cascade} take the cascade, {@link #FUND} takes the feed; the line holds {@code inFlight},
	 * and its generators put {@code surplus} in beyond the machines last tick.
	 */
	private static final class Stores implements DischargePlan.Stores<Integer> {
		final Set<Integer> cascade;
		final long inFlight;
		final long surplus;
		final long credit;
		int inFlightAsked;

		Stores(Set<Integer> cascade, long inFlight) {
			this(cascade, inFlight, 0L);
		}

		final boolean episode;

		Stores(Set<Integer> cascade, long inFlight, long surplus) {
			this(cascade, inFlight, surplus, false, 0L);
		}

		/** Stores in a backup episode ({@code episode}) whose sinks hold {@code credit}. */
		Stores(Set<Integer> cascade, long inFlight, long surplus, boolean episode, long credit) {
			this.cascade = cascade;
			this.inFlight = inFlight;
			this.surplus = surplus;
			this.episode = episode;
			this.credit = credit;
		}

		@Override
		public boolean backupEpisode() {
			return episode;
		}

		@Override
		public long sinkCredit() {
			return credit;
		}

		@Override
		public long generatorSurplus() {
			return surplus;
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
		assertTrue(plan.excludes(A, stores::acceptsCascade), "backup excludes every storage source from the pass");
		assertTrue(plan.excludes(B, stores::acceptsCascade), "and every other sink (MOD-756)");
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
		assertTrue(plan.excludes(A, stores::acceptsCascade), "the donor sits the serve pass out");
		assertFalse(plan.excludes(B, stores::acceptsCascade),
				"an empty box with a cabled OUT face still charges (ADR-002)");
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
		Stores stores = new Stores(Set.of(A), 0);
		DischargePlan<Integer> plan = DischargePlan.decide(0, 0, List.of(source(A, donor)),
				List.of(sink(FUND, store(500_000, 0))), stores, DEADBAND, PACKET_CAP, RESERVE);
		long expected = StorageFeedShare.feedAllowance(20_000, 20_000, RESERVE, 500_000, 24, PACKET_CAP);
		assertTrue(expected > 0, "precondition: a full box may feed a fund");
		assertEquals(Map.of(A, expected), plan.feedAllowances());
		assertTrue(plan.cascadeAllowances().isEmpty());
		assertTrue(plan.excludes(A, stores::acceptsCascade));
	}

	/**
	 * MOD-731: the cascade's EU is addressed to the stores that take it. While it runs, a fund (a Teleporter,
	 * fed by the feed) and a condenser-like sink (no feed, no cascade) sit the tick out of the serve pass and
	 * the sink field, beside the donor; the empty box it levels into stays in. The feed branch keeps every
	 * non-donor sink — a fund is the feed's own destination; backup is MOD-756's (below).
	 *
	 * @implements MOD-731-DP01 — under the cascade the sinks outside it sit the tick out; under the feed they
	 *     do not
	 */
	@Test
	void cascadeExcludesSinksOutsideTheCascade() {
		Stores stores = new Stores(Set.of(A, B), 0);
		DischargePlan<Integer> cascade = DischargePlan.decide(0, 0, List.of(source(A, store(20_000, 12_000))),
				List.of(sink(B, store(20_000, 0)), sink(FUND, store(500_000, 0)), sink(CONDENSER, store(4_000_000, 0))),
				stores, DEADBAND, PACKET_CAP, RESERVE);
		assertFalse(cascade.cascadeAllowances().isEmpty(), "precondition: a box at 60 % beside an empty one levels");
		assertTrue(cascade.excludes(A, stores::acceptsCascade), "the donor sits the cascade out");
		assertTrue(cascade.excludes(FUND, stores::acceptsCascade),
				"a fund must not take the cascade's EU — it is fed only by the feed, above the donor's reserve");
		assertTrue(cascade.excludes(CONDENSER, stores::acceptsCascade),
				"a sink with no feed and no cascade takes nothing a store released");
		assertFalse(cascade.excludes(B, stores::acceptsCascade), "the box being levelled is served");

		DischargePlan<Integer> feed = DischargePlan.decide(0, 0, List.of(source(A, store(20_000, 20_000))),
				List.of(sink(FUND, store(500_000, 0)), sink(CONDENSER, store(4_000_000, 0))), stores, DEADBAND,
				PACKET_CAP, RESERVE);
		assertFalse(feed.feedAllowances().isEmpty(), "precondition: with no box to level, the feed opens");
		assertFalse(feed.excludes(FUND, stores::acceptsCascade), "a fund is the feed's destination");
		assertFalse(feed.excludes(CONDENSER, stores::acceptsCascade),
				"the feed keeps every non-donor sink in the pass — it stays open for hours");

		DischargePlan<Integer> backup = DischargePlan.backupOnly(40, 10);
		assertTrue(backup.excludes(FUND, stores::acceptsCascade), "a backup tick feeds the machines alone (MOD-756)");
	}

	/**
	 * MOD-731, owner's decision of 2026-10-05: while the cascade runs, the sinks outside it still charge from the
	 * generators' surplus — out of the serve pass and the seeds as before, but served apart with at most the
	 * surplus the generators put in beyond the machines last tick. Not the donor, not the box being levelled;
	 * nothing on a feed or backup tick, nothing without a surplus.
	 *
	 * @implements MOD-731-DP02 — under the cascade the sinks outside it draw the generators' surplus apart
	 */
	@Test
	void cascadeLetsTheSinksOutsideItDrawTheGeneratorsSurplus() {
		Stores stores = new Stores(Set.of(A, B), 0, 20);
		List<EnergyLineDistributor.LiveConsumer<Integer>> sinks = List.of(sink(B, store(20_000, 0)),
				sink(FUND, store(500_000, 0)), sink(CONDENSER, store(4_000_000, 0)));
		DischargePlan<Integer> cascade = DischargePlan.decide(0, 0, List.of(source(A, store(20_000, 12_000))), sinks,
				stores, DEADBAND, PACKET_CAP, RESERVE);
		assertFalse(cascade.cascadeAllowances().isEmpty(), "precondition: a box at 60 % beside an empty one levels");
		assertEquals(20, cascade.surplusBudget(), "the budget is the generators' surplus of the last tick");
		assertTrue(cascade.excludes(FUND, stores::acceptsCascade)
				&& cascade.drawsSurplus(FUND, Set.of(A), stores::acceptsCascade),
				"a fund stays out of the pass and the seeds, and draws the surplus apart");
		assertTrue(cascade.drawsSurplus(CONDENSER, Set.of(A), stores::acceptsCascade), "so does a condenser");
		assertFalse(cascade.drawsSurplus(B, Set.of(A), stores::acceptsCascade), "the levelled box is served");
		assertFalse(cascade.drawsSurplus(A, Set.of(A), stores::acceptsCascade), "the donor is served by nobody");
		List<EnergyLineDistributor.LiveConsumer<Integer>> pass = new ArrayList<>(sinks);
		List<EnergyLineDistributor.LiveConsumer<Integer>> apart =
				cascade.setAside(pass, Set.of(A), stores::acceptsCascade);
		assertEquals(List.of(B), pass.stream().map(EnergyLineDistributor.LiveConsumer::pos).toList(),
				"the serve pass keeps the box being levelled");
		assertEquals(List.of(FUND, CONDENSER), apart.stream().map(EnergyLineDistributor.LiveConsumer::pos).toList(),
				"the sinks outside the cascade are served apart, in list order");

		DischargePlan<Integer> noSurplus = DischargePlan.decide(0, 0, List.of(source(A, store(20_000, 12_000))),
				sinks, new Stores(Set.of(A, B), 0, 0), DEADBAND, PACKET_CAP, RESERVE);
		assertEquals(0, noSurplus.surplusBudget());
		assertFalse(noSurplus.drawsSurplus(FUND, Set.of(A), stores::acceptsCascade),
				"without a generator's surplus the fund sits the cascade out, as before");
		List<EnergyLineDistributor.LiveConsumer<Integer>> before = new ArrayList<>(sinks);
		assertTrue(noSurplus.setAside(before, Set.of(A), stores::acceptsCascade).isEmpty());
		assertEquals(List.of(B), before.stream().map(EnergyLineDistributor.LiveConsumer::pos).toList());
		DischargePlan<Integer> negative = DischargePlan.decide(0, 0, List.of(source(A, store(20_000, 12_000))),
				sinks, new Stores(Set.of(A, B), 0, -5), DEADBAND, PACKET_CAP, RESERVE);
		assertEquals(0, negative.surplusBudget(), "a deficit is no budget");

		DischargePlan<Integer> feed = DischargePlan.decide(0, 0, List.of(source(A, store(20_000, 20_000))),
				List.of(sink(FUND, store(500_000, 0))), stores, DEADBAND, PACKET_CAP, RESERVE);
		assertFalse(feed.feedAllowances().isEmpty(), "precondition: with no box to level, the feed opens");
		assertEquals(0, feed.surplusBudget(), "a feed tick serves the fund in the pass; no budget apart");
		assertFalse(feed.drawsSurplus(FUND, Set.of(A), stores::acceptsCascade));
		assertEquals(36, DischargePlan.decide(40, 10, List.of(source(A, store(20_000, 20_000))),
				List.of(sink(FUND, store(500_000, 0))), new Stores(Set.of(A, B), 0, 20, true, 36), DEADBAND,
				PACKET_CAP, RESERVE).surplusBudget(), "a backup tick has one too: the sinks' credit (MOD-756)");
	}

	/**
	 * MOD-756: what a store releases on a backup tick is for the machines. Every sink sits the tick out of the
	 * serve pass and the seeds — the discharging box, a box being charged, a fund, a condenser — and every one of
	 * them but the storage source draws the generators' surplus apart: backup opens on the machines' free room,
	 * not on what the line can carry to them, so the generators may well have EU to spare (review, 2026-10-08).
	 * Without credit nothing is served apart. With the machines covered and no episode running, every sink is
	 * back in the pass.
	 *
	 * @implements MOD-756-DP01 — under backup every sink sits the pass out; all but a storage source draw the
	 *     generators' surplus apart
	 */
	@Test
	void backupTickServesTheMachinesAndTheSurplusApart() {
		Stores stores = new Stores(Set.of(A, B), 0, 99, true, 20);
		List<EnergyLineDistributor.LiveConsumer<Integer>> sinks = List.of(sink(A, store(20_000, 9_000)),
				sink(B, store(20_000, 0)), sink(FUND, store(500_000, 0)), sink(CONDENSER, store(4_000_000, 0)));
		DischargePlan<Integer> backup = DischargePlan.decide(40, 10, List.of(source(A, store(20_000, 9_000))), sinks,
				stores, DEADBAND, PACKET_CAP, RESERVE);
		assertEquals(30, backup.backupBudget(), "precondition: the generators fall short of the machines' room");
		assertEquals(20, backup.surplusBudget(), "the budget is the sinks' credit, not last tick's surplus");
		assertFalse(backup.settling());
		for (Integer pos : List.of(A, B, FUND, CONDENSER)) {
			assertTrue(backup.excludes(pos, stores::acceptsCascade), "sink " + pos + " sits the backup tick out");
		}
		assertFalse(backup.drawsSurplus(A, Set.of(A), stores::acceptsCascade), "the discharging store draws nothing");
		List<EnergyLineDistributor.LiveConsumer<Integer>> pass = new ArrayList<>(sinks);
		assertEquals(List.of(B, FUND, CONDENSER), backup.setAside(pass, Set.of(A), stores::acceptsCascade).stream()
				.map(EnergyLineDistributor.LiveConsumer::pos).toList(), "the other sinks draw the surplus apart");
		assertTrue(pass.isEmpty(), "the serve pass holds no sink on a backup tick");

		DischargePlan<Integer> noSurplus = DischargePlan.decide(40, 10, List.of(source(A, store(20_000, 9_000))),
				sinks, new Stores(Set.of(A, B), 0, 99, true, -40), DEADBAND, PACKET_CAP, RESERVE);
		List<EnergyLineDistributor.LiveConsumer<Integer>> none = new ArrayList<>(sinks);
		assertTrue(noSurplus.setAside(none, Set.of(A), stores::acceptsCascade).isEmpty(), "no surplus, none apart");
		assertTrue(none.isEmpty());

		DischargePlan<Integer> covered = DischargePlan.decide(10, 40, List.of(source(A, store(20_000, 9_000))),
				List.of(sink(FUND, store(500_000, 0))), new Stores(Set.of(A, B), 0, 99), DEADBAND, PACKET_CAP,
				RESERVE);
		assertEquals(0, covered.backupBudget(), "precondition: the machines are covered");
		assertFalse(covered.excludes(FUND, stores::acceptsCascade), "outside an episode the fund is back");
	}

	/**
	 * MOD-756, second review: the backup episode's ledger. Between two backup ticks of an episode — every channel
	 * closed — the plan is settling: every fund sits out of the pass and the seeds and draws apart only its
	 * credit, so a fund that walls the machine off cannot take the generators' EU the machine then goes short
	 * of; a box stays in. Outside an episode, with no sink, or with a feed open: an ordinary tick.
	 *
	 * @implements MOD-756-DP02 — in a backup episode the sinks take no more than their credit, between backup
	 *     ticks too
	 */
	@Test
	void backupEpisodeSettlesTheSinksApart() {
		List<EnergyLineDistributor.LiveConsumer<Integer>> sinks = List.of(sink(B, store(20_000, 0)),
				sink(FUND, store(500_000, 0)));
		DischargePlan<Integer> settling = DischargePlan.decide(0, 10, List.of(source(A, store(20_000, 9_000))), sinks,
				new Stores(Set.of(A), 0, 99, true, 6), DEADBAND, PACKET_CAP, RESERVE);
		assertTrue(settling.settling(), "an episode runs, no channel open");
		assertEquals(6, settling.surplusBudget(), "the sinks take their credit, not last tick's surplus");
		List<EnergyLineDistributor.LiveConsumer<Integer>> pass = new ArrayList<>(sinks);
		assertEquals(List.of(FUND), settling.setAside(pass, Set.of(A), pos -> pos.equals(B)).stream()
				.map(EnergyLineDistributor.LiveConsumer::pos).toList(), "a fund draws apart");
		assertEquals(List.of(B), pass.stream().map(EnergyLineDistributor.LiveConsumer::pos).toList(),
				"a box stays in the pass and the seeds (MOD-254)");

		DischargePlan<Integer> inDebt = DischargePlan.decide(0, 10, List.of(source(A, store(20_000, 9_000))), sinks,
				new Stores(Set.of(A), 0, 99, true, -30), DEADBAND, PACKET_CAP, RESERVE);
		assertTrue(inDebt.settling());
		assertEquals(0, inDebt.surplusBudget(), "a store's unpaid backup leaves nothing");

		assertFalse(DischargePlan.decide(0, 10, List.of(source(A, store(20_000, 9_000))), sinks,
				new Stores(Set.of(A), 0, 99, false, 6), DEADBAND, PACKET_CAP, RESERVE).settling(), "no episode");
		assertFalse(DischargePlan.decide(0, 10, List.of(source(A, store(20_000, 9_000))), List.of(),
				new Stores(Set.of(A), 0, 99, true, 6), DEADBAND, PACKET_CAP, RESERVE).settling(), "no sink to settle");
		DischargePlan<Integer> feed = DischargePlan.decide(0, 10, List.of(source(A, store(20_000, 20_000))),
				List.of(sink(FUND, store(500_000, 0))), new Stores(Set.of(A), 0, 99, true, 6), DEADBAND, PACKET_CAP,
				RESERVE);
		assertFalse(feed.feedAllowances().isEmpty(), "precondition: the feed opens");
		assertFalse(feed.settling(), "an open feed is not settling");
		assertThrows(IllegalArgumentException.class, () -> new DischargePlan<>(5, Map.of(), Map.of(), 0, true),
				"a settling tick has every channel closed");
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
		assertThrows(IllegalArgumentException.class, () -> new DischargePlan<>(0, Map.of(), Map.of(A, 1L), 4),
				"MOD-731: a surplus budget belongs to a cascade or backup tick only");
		assertThrows(IllegalArgumentException.class, () -> new DischargePlan<>(0, Map.of(A, 1L), Map.of(), -1));
		assertEquals(4, new DischargePlan<Integer>(5, Map.of(), Map.of(), 4).surplusBudget(),
				"MOD-756: a backup tick may carry one");
	}
}

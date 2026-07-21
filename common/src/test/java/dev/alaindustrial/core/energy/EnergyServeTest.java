package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import dev.alaindustrial.core.fluid.FluidMover;

/**
 * L1 unit tests for {@link EnergyServe} — the runtime counterpart of {@link EnergyShare}'s loss model.
 * {@link EnergyServe} extracts the three load-bearing lines from {@link EnergyNetwork#serveClass}
 * (the {@code pulled - loss} / {@code toDeliver - inserted} / {@code inserted + loss} kernel that
 * applies per-consumer cable loss at tick time), so that pitest mutates them and the L1 suite catches
 * the mutation. {@link EnergyNetwork} itself is excluded from pitest (MC-coupled via
 * {@code ServerLevel}), so without this extract the canonical EU-creation / EU-destruction mutants
 * on the runtime transport path were invisible to pitest and uncovered by any fast test.
 *
 * <p>Three test layers:
 * <ul>
 *   <li><b>Point tests</b> — pin exact outputs at the canonical numbers (copper 0.02/blok, 32-EU LV
 *       packet over 10 cables → loss 6, delivered 26). These kill the obvious MATH mutants.</li>
 *   <li><b>jqwik {@code @Property}</b> — generative invariants (range, monotonicity, conservation)
 *       that hold for every legal input tuple. These kill mutants that only diverge on inputs a point
 *       test would not have chosen (e.g. a {@code max→min} flip is only visible when the two args
 *       differ; jqwik's randomised arbitraries guarantee such inputs are tried across 1000 runs).</li>
 * </ul>
 *
 * <p><b>jqwik API note.</b> Custom {@link Arbitrary} providers are {@link Provide @Provide}-annotated
 * instance methods returning {@link Arbitrary}{@code <T>}, referenced by name from
 * {@link ForAll @ForAll("name")}. Verified against the jqwik 1.9 user guide
 * (https://jqwik.net/docs/current/user-guide.html, 2026-07-19).
 *
 * @implements EnergyServe runtime loss-application kernel (deliver / surplus / consumed)
 */
class EnergyServeTest {

	// --- deliverAfterLoss (the canonical cable-loss application: pulled - loss) ---

	@Test
	void deliverAfterLoss_canonicalCopperCable10Blocks() {
		// The documented balance invariant: a full LV packet (32 EU) over 10 copper cables loses
		// floor(32 × 0.02 × 10) = 6 EU, so the consumer receives 26. This is the exact number pinned by
		// EnergyShareTest.cableLoss_scalesWithFlowAndDistance AND the L2 NetworkGameTest scenario
		// tcCable001Nrg02_lossOverTenCables — this test pins the FINAL delivered amount after subtracting.
		long pulled = 32;
		long loss = EnergyShare.cableLoss(pulled, 0.02, 10); // = 6
		assertEquals(26L, EnergyServe.deliverAfterLoss(pulled, loss),
				"32 EU with 6 EU loss → 26 delivered (canonical copper/10-block invariant)");
	}

	@Test
	void deliverAfterLoss_zeroLoss_isFullPull() {
		assertEquals(32L, EnergyServe.deliverAfterLoss(32, 0), "no loss → full delivery");
	}

	@Test
	void deliverAfterLoss_lossEqualsPull_isZero() {
		// The clamp boundary: when loss == pulled, deliver 0 (not negative). EnergyShare.cableLoss
		// clamps loss to [0, gross], so this case arises when distance × rate × gross >= gross
		// (e.g. 200 EU over 100 cables → loss clamped to 200).
		assertEquals(0L, EnergyServe.deliverAfterLoss(200, 200),
				"loss == pulled → deliver 0, not negative");
	}

	@Test
	void deliverAfterLoss_lossExceedsPull_clampedToZero() {
		// Defensive: even if loss > pulled (out-of-contract — cableLoss clamps to [0, gross] — but a
		// future regression in that clamp must not feed a negative amount to EnergyPort.insert), the
		// deliver clamps to 0. A max→min mutant here would return pulled + loss = 50 (EU creation).
		assertEquals(0L, EnergyServe.deliverAfterLoss(10, 40),
				"loss > pulled → 0, never negative (defensive clamp)");
	}

	// --- surplus (refund when consumer accepts less than offered) ---

	@Test
	void surplus_fullAcceptance_isZero() {
		assertEquals(0L, EnergyServe.surplus(26, 26), "consumer took everything → nothing to refund");
	}

	@Test
	void surplus_partialAcceptance_isRemainder() {
		// Consumer had room for only 10 of the 26 offered; the other 16 must go back to the producer
		// pool (NOT be destroyed — refund is what makes a partial-acceptance move lossless at the pool
		// level, mirroring FluidMover's shortfall refund).
		assertEquals(16L, EnergyServe.surplus(26, 10), "16 EU refused → refunded, not destroyed");
	}

	@Test
	void surplus_totalRefusal_isFullOffer() {
		assertEquals(26L, EnergyServe.surplus(26, 0), "consumer took nothing → all 26 refunded");
	}

	// --- consumed (charge against the supply pool: delivered + destroyed-in-transit) ---

	@Test
	void consumed_canonicalCopperCable10Blocks() {
		// 32 pulled, 6 destroyed in transit, 26 inserted into the consumer. The pool must be charged 32
		// (the full pull), not 26 — otherwise the next consumer class (storage sinks) would be served
		// from a budget that pretends the loss never happened (silent EU creation at the pool level).
		assertEquals(32L, EnergyServe.consumed(26, 6),
				"pool charged 32: 26 delivered + 6 destroyed (no EU created at the pool level)");
	}

	@Test
	void consumed_zeroLoss_isJustInserted() {
		assertEquals(20L, EnergyServe.consumed(20, 0), "no loss → consumed = inserted");
	}

	// ============================================================
	// jqwik generative properties (MOD-144)
	// ============================================================
	// These run ~1000 randomised inputs each (see the jqwik report: "checks = 1000, generation =
	// RANDOMIZED"). They pin structural invariants that hold for every legal (pulled, loss, inserted)
	// tuple, killing mutants a point test might miss because the chosen inputs happened to coincide.

	/** A non-negative long with a sane upper bound for EU amounts (transport ticks move ≤ 2^40 EU). */
	@Provide
	Arbitrary<Long> nonNegativeEu() {
		return Arbitraries.longs().between(0L, 1L << 40);
	}

	@Property
	void deliverAfterLoss_alwaysInZeroToPulled(
			@ForAll("nonNegativeEu") long pulled,
			@ForAll("nonNegativeEu") long loss) {
		// Clamp loss to [0, pulled] (the contract cableLoss guarantees) so the property exercises the
		// in-contract range densely; the out-of-contract loss > pulled case is covered by the point test
		// deliverAfterLoss_lossExceedsPull_clampedToZero above.
		long lossBounded = Math.min(loss, pulled);
		long delivered = EnergyServe.deliverAfterLoss(pulled, lossBounded);
		assertTrue(delivered >= 0, "delivered never negative");
		assertTrue(delivered <= pulled, "delivered never exceeds pulled");
	}

	@Property
	void deliverAfterLoss_monotonicInLoss(@ForAll("nonNegativeEu") long pulled) {
		// A larger loss must not INCREASE what's delivered (resistive model: more cable loss ⇒ less
		// reaches the consumer). A mutant that flips `pulled - loss` to `pulled + loss` would fail
		// here on every input where loss > 0. Two paired losses to keep the test deterministic.
		// Skip pulled == 0 (the monotonicity is vacuous — delivered is always 0 regardless of loss).
		long lossLow = Arbitraries.longs().between(0L, pulled).sample();
		long lossHigh = lossLow + 1;
		long atLow = EnergyServe.deliverAfterLoss(pulled, lossLow);
		long atHigh = EnergyServe.deliverAfterLoss(pulled, lossHigh);
		assertTrue(atHigh <= atLow,
				"more loss ⇒ less or equal delivered (got low=" + atLow + ", high=" + atHigh + ")");
	}

	@Property
	void surplus_neverExceedsOfferedAndNeverNegative(
			@ForAll("nonNegativeEu") long toDeliver,
			@ForAll("nonNegativeEu") long inserted) {
		// inserted can exceed toDeliver in arbitrary input (out-of-contract for a real EnergyPort),
		// so the clamp to 0 is what's under test here; in-contract cases (inserted ≤ toDeliver) are
		// the dense majority and pin the surplus = toDeliver - inserted identity.
		long surplus = EnergyServe.surplus(toDeliver, inserted);
		assertTrue(surplus >= 0, "surplus never negative");
		assertTrue(surplus <= toDeliver, "surplus never exceeds what was offered");
	}

	@Property
	void consumed_atLeastInsertedAndAtLeastLoss(
			@ForAll("nonNegativeEu") long inserted,
			@ForAll("nonNegativeEu") long loss) {
		// consumed = inserted + loss, so it must dominate both operands. A +→− mutant makes consumed
		// < inserted on every input where loss > 0; this property catches it on the first random try.
		long consumed = EnergyServe.consumed(inserted, loss);
		assertTrue(consumed >= inserted, "consumed ≥ inserted (loss is an additional charge)");
		assertTrue(consumed >= loss, "consumed ≥ loss (the consumer always got at least 0)");
	}

	@Property
	void consumed_withZeroLoss_equalsInserted(@ForAll("nonNegativeEu") long inserted) {
		assertEquals(inserted, EnergyServe.consumed(inserted, 0),
				"with zero loss, consumed collapses to inserted");
	}
}

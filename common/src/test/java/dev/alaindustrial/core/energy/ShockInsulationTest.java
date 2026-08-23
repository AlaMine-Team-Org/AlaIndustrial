package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ShockInsulation} (MOD-466) — what a worn insulated set stops, and what that
 * costs it.
 *
 * <p>Three of the cases here exist because the opposite behaviour is a real, shipped bug shape rather
 * than a hypothetical:
 *
 * <ul>
 * <li>{@link #wearIsNeverZeroForARealContact()} — a set that absorbs for free is not a consumable,
 * and the whole balance of a leather-cheap craft rests on it wearing out. The shielding suit had
 * exactly this hole in reverse: it gated wear behind a threshold and then never wore at all against
 * anything but a live reactor.</li>
 * <li>{@link #preventedAndRemainingAlwaysAddUpToTheRawHit()} — the two halves are what the caller
 * bills durability for and what it passes to {@code hurtServer}. If they stopped summing to the raw
 * hit, a full set would either leave a sliver of damage behind or invent some.</li>
 * <li>{@link #aGenerousConfigCannotHealTheWearer()} — the per-piece share is operator-tunable, and
 * without the ceiling four pieces at 40 % would cut 160 % of the hit and return negative damage.</li>
 * </ul>
 */
class ShockInsulationTest {

	/** The shipped share: four pieces at 25 % make a full set immune. */
	private static final int PER_PIECE = 25;

	/** LV / MV / HV contact damage, the three numbers {@code CableType.shockDamage()} yields. */
	private static final float LV = 2.0f;
	private static final float MV = 6.0f;
	private static final float HV = 10.0f;

	/** Shipped divisor: this much absorbed damage buys one point of durability. */
	private static final float PER_POINT = 4.0f;

	@Test
	void eachWornPieceCutsItsOwnShare() {
		assertEquals(0, ShockInsulation.cutPercent(0, PER_PIECE), "bare player");
		assertEquals(25, ShockInsulation.cutPercent(1, PER_PIECE));
		assertEquals(50, ShockInsulation.cutPercent(2, PER_PIECE));
		assertEquals(75, ShockInsulation.cutPercent(3, PER_PIECE));
		assertEquals(100, ShockInsulation.cutPercent(4, PER_PIECE), "a full set is immune");
	}

	@Test
	void aFullSetStopsTheShockAtEveryTier() {
		assertEquals(0.0f, ShockInsulation.remaining(LV, 4, PER_PIECE), 1.0e-6f);
		assertEquals(0.0f, ShockInsulation.remaining(MV, 4, PER_PIECE), 1.0e-6f);
		assertEquals(0.0f, ShockInsulation.remaining(HV, 4, PER_PIECE), 1.0e-6f);
	}

	/**
	 * The control case: without the set the hazard is untouched. A protection test that only asserts
	 * "with armour it is zero" is green even when the mechanic reduces everything to zero always.
	 */
	@Test
	void withoutTheSetTheShockIsUnchanged() {
		assertEquals(LV, ShockInsulation.remaining(LV, 0, PER_PIECE), 1.0e-6f);
		assertEquals(MV, ShockInsulation.remaining(MV, 0, PER_PIECE), 1.0e-6f);
		assertEquals(HV, ShockInsulation.remaining(HV, 0, PER_PIECE), 1.0e-6f);
		assertEquals(0, ShockInsulation.wearFor(ShockInsulation.prevented(HV, 0, PER_PIECE), PER_POINT),
				"nothing worn, nothing to wear out");
	}

	@Test
	void apartialSetProtectsPartially() {
		assertEquals(1.5f, ShockInsulation.remaining(LV, 1, PER_PIECE), 1.0e-6f);
		assertEquals(3.0f, ShockInsulation.remaining(MV, 2, PER_PIECE), 1.0e-6f);
		assertEquals(2.5f, ShockInsulation.remaining(HV, 3, PER_PIECE), 1.0e-6f);
	}

	@Test
	void preventedAndRemainingAlwaysAddUpToTheRawHit() {
		for (float raw : new float[] {LV, MV, HV, 0.5f, 13.7f}) {
			for (int worn = 0; worn <= 4; worn++) {
				float prevented = ShockInsulation.prevented(raw, worn, PER_PIECE);
				float remaining = ShockInsulation.remaining(raw, worn, PER_PIECE);
				assertEquals(raw, prevented + remaining, 1.0e-6f,
						"raw=" + raw + " worn=" + worn);
				assertTrue(remaining >= 0.0f, "damage must never go negative: raw=" + raw);
			}
		}
	}

	/**
	 * A full set pays what the tier costs — the ladder a player feels through durability.
	 *
	 * <p>These are the numbers that turn into seconds, and they are the point of the divisor. Contact
	 * is once a second, so 1/2/3 per hit means a 275-point helmet survives roughly 4.5 minutes of
	 * unbroken LV contact, 2.3 of MV and 1.5 of HV. The first version charged the full absorbed damage
	 * (2/6/10) and burned that same helmet out in 27 seconds.
	 */
	@Test
	void wearFollowsTheTierAFullSetStopped() {
		assertEquals(1, ShockInsulation.wearFor(ShockInsulation.prevented(LV, 4, PER_PIECE), PER_POINT));
		assertEquals(2, ShockInsulation.wearFor(ShockInsulation.prevented(MV, 4, PER_PIECE), PER_POINT));
		assertEquals(3, ShockInsulation.wearFor(ShockInsulation.prevented(HV, 4, PER_PIECE), PER_POINT));
	}

	/** The tier ladder must survive the divisor — otherwise voltage stops mattering to the suit. */
	@Test
	void aStrongerTierAlwaysCostsAtLeastAsMuch() {
		int lv = ShockInsulation.wearFor(ShockInsulation.prevented(LV, 4, PER_PIECE), PER_POINT);
		int mv = ShockInsulation.wearFor(ShockInsulation.prevented(MV, 4, PER_PIECE), PER_POINT);
		int hv = ShockInsulation.wearFor(ShockInsulation.prevented(HV, 4, PER_PIECE), PER_POINT);
		assertTrue(lv < mv && mv < hv, "wear must still rise with voltage: " + lv + "/" + mv + "/" + hv);
	}

	/** A bigger divisor buys more time, and that is the only thing an operator turns this knob for. */
	@Test
	void aBiggerDivisorMakesTheSetLastLonger() {
		int tight = ShockInsulation.wearFor(ShockInsulation.prevented(HV, 4, PER_PIECE), 1.0f);
		int generous = ShockInsulation.wearFor(ShockInsulation.prevented(HV, 4, PER_PIECE), 20.0f);
		assertEquals(10, tight, "divisor 1 is the old per-damage behaviour");
		assertEquals(1, generous, "a huge divisor still cannot make a contact free");
		assertTrue(generous < tight);
	}

	/** A partial set stops less and is billed less — it must not pay for protection it never gave. */
	@Test
	void aPartialSetIsBilledOnlyForWhatItStopped() {
		assertEquals(1, ShockInsulation.wearFor(ShockInsulation.prevented(LV, 2, PER_PIECE), PER_POINT),
				"half a set against LV stops 1.0 and pays the floor");
		assertEquals(1, ShockInsulation.wearFor(ShockInsulation.prevented(MV, 2, PER_PIECE), PER_POINT));
		assertTrue(ShockInsulation.wearFor(ShockInsulation.prevented(HV, 2, PER_PIECE), PER_POINT)
						< ShockInsulation.wearFor(ShockInsulation.prevented(HV, 4, PER_PIECE), PER_POINT),
				"a partial set must cost less than a full one against the same hit");
	}

	@Test
	void wearIsNeverZeroForARealContact() {
		// One piece against the weakest cable stops 0.5 damage, which rounds to nothing. Charging zero
		// there would make a single boot an infinite, free partial immunity.
		assertEquals(1, ShockInsulation.wearFor(ShockInsulation.prevented(LV, 1, PER_PIECE), PER_POINT));
		assertEquals(1, ShockInsulation.wearFor(0.01f, PER_POINT), "any absorbed sliver still costs a point");
		assertEquals(0, ShockInsulation.wearFor(0.0f, PER_POINT), "but nothing absorbed costs nothing");
		assertEquals(0, ShockInsulation.wearFor(-3.0f, PER_POINT), "and a negative can never refund durability");
	}

	@Test
	void aGenerousConfigCannotHealTheWearer() {
		assertEquals(100, ShockInsulation.cutPercent(4, 40), "160 % is clamped to a whole hit");
		assertEquals(0.0f, ShockInsulation.remaining(HV, 4, 40), 1.0e-6f);
		assertEquals(100, ShockInsulation.cutPercent(9, PER_PIECE),
				"more pieces than a set exist cannot be worn, and cannot over-cut either");
	}

	@Test
	void aDisabledShareLeavesTheHazardAlone() {
		assertEquals(HV, ShockInsulation.remaining(HV, 4, 0), 1.0e-6f, "0 % disables the set");
		assertEquals(HV, ShockInsulation.remaining(HV, 4, -5), 1.0e-6f, "and so does a negative");
	}

	@Test
	void aHitOfZeroIsNotAContact() {
		assertEquals(0.0f, ShockInsulation.prevented(0.0f, 4, PER_PIECE), 1.0e-6f);
		assertEquals(0.0f, ShockInsulation.remaining(0.0f, 4, PER_PIECE), 1.0e-6f);
		assertEquals(0, ShockInsulation.wearFor(ShockInsulation.prevented(0.0f, 4, PER_PIECE), PER_POINT),
				"a config that zeroes shock damage must not still eat the suit");
	}
}

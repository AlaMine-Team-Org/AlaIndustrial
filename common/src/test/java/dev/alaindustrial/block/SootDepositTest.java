package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The soot roll of a burnt-out oil fire (MOD-638). The world scenario forces the chance to 1, so the
 * other end of the knob and everything in between live here, where no Minecraft is needed.
 */
class SootDepositTest {

	@Test
	@DisplayName("chance 0 never deposits, whatever the draw")
	void zeroNeverDeposits() {
		assertFalse(SootDeposit.deposits(0.0, 0.0));
		assertFalse(SootDeposit.deposits(0.0, 0.5));
		assertFalse(SootDeposit.deposits(0.0, Math.nextDown(1.0)));
	}

	@Test
	@DisplayName("chance 1 always deposits, whatever the draw")
	void oneAlwaysDeposits() {
		assertTrue(SootDeposit.deposits(1.0, 0.0));
		assertTrue(SootDeposit.deposits(1.0, Math.nextDown(1.0)));
	}

	@Test
	@DisplayName("the draw is compared strictly: exactly the chance does not deposit")
	void boundaryIsExclusive() {
		assertTrue(SootDeposit.deposits(0.2, Math.nextDown(0.2)));
		assertFalse(SootDeposit.deposits(0.2, 0.2));
	}

	@Test
	@DisplayName("the default 0.2 deposits on about a fifth of uniform draws")
	void defaultRateIsAFifth() {
		Random random = new Random(638);
		int hits = 0;
		int rolls = 100_000;
		for (int i = 0; i < rolls; i++) {
			if (SootDeposit.deposits(0.2, random.nextDouble())) {
				hits++;
			}
		}
		assertEquals(0.2, hits / (double) rolls, 0.01);
	}
}

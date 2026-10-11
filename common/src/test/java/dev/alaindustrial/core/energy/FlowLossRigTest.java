package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * A long copper line loses 2 % a block on the way (MOD-021); a machine at its far end must still get its whole need
 * while a box beside the generator charges (MOD-789, review of Variant 1, finding 0). The plan asks the cables for
 * the need plus what the path loses, so a box beside the generator changes nothing for the machine: it works on the
 * same ticks with the box as without it. The rig of the review ({@code revV1/Loss.java}): a 32 EU/t generator, a
 * copper line of 10, 20 or 40 cables, a machine of 8 EU/t at its end, a box beside the first cable.
 *
 * @implements MOD-789-FW02 — a machine behind a store on the bus is fed before the store, losses included
 */
class FlowLossRigTest {

	private static final int Z = 2;

	/** The ticks of 400 on which the machine paid its work, after 200 ticks of warm-up. */
	private static int paid(int length, boolean box) {
		LineRig r = new LineRig("loss" + length + (box ? "box" : ""), 0.02).source(0, Z, 32).run(1, Z, length, Z)
				.machine("M", length + 1, Z, 8);
		if (box) {
			r.box("B", 1, Z + 1, 1_000_000);
		}
		int paid = 0;
		for (int t = 0; t < 600; t++) {
			r.tick();
			if (r.work().get("M") > 0 && t >= 200) {
				paid++;
			}
		}
		return paid;
	}

	@Test
	void aBoxBesideTheGeneratorDoesNotSlowAMachineAtTheEndOfALossyLine() {
		for (int length : new int[] {10, 20, 40}) {
			assertEquals(paid(length, false), paid(length, true),
					"copper line of " + length + ": the machine works on as many ticks with a box as without one");
		}
	}
}

package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The rigs on which revision 2 of the machine corridor failed review, and the two random networks on which the
 * first cuts of revision 3 failed the property check (MOD-789, ADR-048) — over production's core, no world. Every
 * machine must do its whole work rate once the line is running; a box gets what the machines leave, at once (ADR-049).
 *
 * <ul>
 *   <li>R2-1 fork: a near machine with a box behind it on one branch, a far machine on the other — revision 2
 *       gave the box the far machine's share forever (the far machine 0).</li>
 *   <li>R2-1 ring: the same on a ring.</li>
 *   <li>R2-2 two generators, the box at the far one: the far generator idled while the machine went short.</li>
 *   <li>Seeds 103 and 104 of {@code sim/fuzz.py}: a generator on the ridge between two generators' fields, a
 *       generator standing on a satisfied machine's cable.</li>
 * </ul>
 *
 * @implements MOD-789-FW06 — no machine goes short beside a box, an idling generator or a satisfied machine
 */
class FlowUnderFedRigTest {

	private static final int Z = 3;

	/** Run {@code ticks}; per machine, the work it did over the last {@code tail} ticks. */
	private static Map<String, Long> run(LineRig r, int ticks, int tail) {
		Map<String, Long> done = new LinkedHashMap<>();
		for (int t = 1; t <= ticks; t++) {
			r.tick();
			Map<String, Long> w = r.work();
			if (t > ticks - tail) {
				w.forEach((k, v) -> done.merge(k, v, Long::sum));
			}
		}
		return done;
	}

	private static void assertFullRate(LineRig r, Map<String, Long> done, int tail) {
		for (LineRig.End e : r.ends) {
			if (e.machine()) {
				long full = e.work() * tail;
				assertTrue(done.get(e.name()) >= full * 95 / 100, r.name + ": machine " + e.name() + " did "
						+ done.get(e.name()) + " of " + full + " EU of work in the last " + tail + " ticks");
			}
		}
	}

	@Test
	void theFarMachineOfAForkIsFedBeforeTheBoxBehindTheNearOne() {
		LineRig r = new LineRig("fork", 0.0).source(0, Z, 24).run(1, Z, 4, Z).machine("M1", 2, Z - 1, 2)
				.box("B", 5, Z, 20_000).run(1, Z + 1, 1, Z + 4).machine("M2", 1, Z + 5, 2);
		boolean carried = false;
		for (int t = 0; t < 60; t++) {
			r.tick();
			r.work();
			carried |= r.charge(1, Z + 2) > 0;
		}
		assertTrue(carried, "the far branch's middle cable carried charge in the first 60 ticks (ADR-001)");
		run(r, 1000, 1);
		long box = r.end("B");
		assertFullRate(r, run(r, 500, 500), 500);
		assertTrue(r.end("B") - box >= 8 * 500 * 95 / 100, "the box gets the other 8 EU/t of the source's one cable "
				+ "at once (ADR-049, rule 2): " + (r.end("B") - box));
	}

	@Test
	void bothArcsOfARingAreFedBeforeTheBox() {
		LineRig r = new LineRig("ring", 0.0).source(0, Z, 24).run(1, Z, 6, Z).run(1, Z + 4, 6, Z + 4)
				.run(1, Z + 1, 1, Z + 3).run(6, Z + 1, 6, Z + 3).machine("M1", 4, Z - 1, 2).machine("M2", 4, Z + 5, 2)
				.box("B", 7, Z + 2, 20_000);
		run(r, 1000, 1);
		long box = r.end("B");
		assertFullRate(r, run(r, 500, 500), 500);
		assertTrue(r.end("B") - box >= 8 * 500 * 95 / 100, "the box gets the other 8 EU/t at once: "
				+ (r.end("B") - box));
	}

	@Test
	void theFarGeneratorFeedsTheMachineBeforeTheBoxBesideIt() {
		LineRig r = new LineRig("2genbox", 0.0).source(0, Z, 4).source(10, Z, 20).run(1, Z, 9, Z)
				.machine("M", 3, Z + 1, 10).box("B", 8, Z + 1, 20_000);
		assertFullRate(r, run(r, 1500, 500), 500);
		assertTrue(r.end("B") > 0, "the box gets what the machine leaves");
	}

	@Test
	void theFarGeneratorFeedsAMachineItDoesNotStandBeside() {
		LineRig r = new LineRig("2gen", 0.0).source(0, Z, 4).source(10, Z, 20).run(1, Z, 9, Z)
				.machine("M", 3, Z + 1, 10);
		assertFullRate(r, run(r, 1500, 500), 500);
	}

	/** Seed 103 of {@code sim/fuzz.py}: a far machine behind the ridge between three generators' fields. */
	@Test
	void seed103() {
		LineRig r = new LineRig("seed103", 0.0);
		int[][] cables = {{0, 0}, {0, -1}, {1, 0}, {-1, -1}, {0, 1}, {0, -2}, {-1, 1}, {1, -1}, {-1, 2}, {-1, -2},
			{-1, 3}, {-2, 2}, {1, 1}, {-2, 1}, {0, -3}, {-2, 0}, {2, 1}, {-1, -3}, {0, -4}};
		for (int[] c : cables) {
			r.cable(c[0], c[1]);
		}
		r.source(-1, 0, 4).source(-2, -2, 8).source(1, -2, 8).machine("M0", 0, -5, 2).machine("M1", -1, 4, 4)
				.box("B0", -3, 0, 20_000).box("B1", 2, -1, 20_000);
		assertFullRate(r, run(r, 600, 200), 200);
	}

	/** Seed 104 of {@code sim/fuzz.py}: a generator standing on a satisfied machine's cable. */
	@Test
	void seed104() {
		LineRig r = new LineRig("seed104", 0.0);
		int[][] cables = {{0, 0}, {0, 1}, {-1, 1}, {0, -1}, {-2, 1}};
		for (int[] c : cables) {
			r.cable(c[0], c[1]);
		}
		r.source(1, 0, 4).source(0, -2, 8).machine("M0", 1, 1, 2).machine("M1", -3, 1, 4).machine("M2", -1, -1, 2);
		assertFullRate(r, run(r, 600, 200), 200);
	}
}

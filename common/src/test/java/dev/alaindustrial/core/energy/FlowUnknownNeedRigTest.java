package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A consumer the network cannot ask for its per-tick need — a foreign mod's machine or battery, or one of ours
 * without {@link PerTickDemand} — on a line with one of our machines and a box (MOD-789, review round 3, finding 1).
 * Its need is unknown, so it is never under-fed: it must not stop our machine, and it must not lock the box out of
 * the surplus. The rigs of the review ({@code rev789/fo2.py}, {@code ws2.py}): a 20 EU/t generator, four copper
 * cables, a macerator (2 EU/t) at the end, a box beside cable 3 that does not fill during the run.
 *
 * @implements MOD-789-FW07 — a consumer of unknown need neither starves a machine nor locks the stores
 */
class FlowUnknownNeedRigTest {

	private static final int Z = 3;

	private static LineRig line(String name) {
		return new LineRig(name, 0.0).source(0, Z, 20).run(1, Z, 4, Z).machine("M", 5, Z, 2)
				.box("B", 3, Z + 1, 1_000_000);
	}

	/** Run {@code ticks}; the work each consumer did over the last {@code tail} ticks, and the box's gain. */
	private static Map<String, Long> run(LineRig r, int ticks, int tail) {
		Map<String, Long> done = new LinkedHashMap<>();
		long box0 = 0;
		for (int t = 1; t <= ticks; t++) {
			if (t == ticks - tail) {
				box0 = r.end("B");
			}
			r.tick();
			Map<String, Long> w = r.work();
			if (t > ticks - tail) {
				w.forEach((k, v) -> done.merge(k, v, Long::sum));
			}
		}
		done.put("box", r.end("B") - box0);
		return done;
	}

	private static void assertMachineFull(LineRig r, Map<String, Long> done, int tail) {
		assertTrue(done.get("M") >= 2L * tail * 95 / 100, r.name + ": the macerator did " + done.get("M") + " of "
				+ 2L * tail + " EU of work in the last " + tail + " ticks");
	}

	/** Our Workstation as it was: no {@link PerTickDemand}, 40 000 EU buffer, intake 128 — just placed, empty. */
	@Test
	void anEmptyBufferOfUnknownNeedBesideTheGeneratorDoesNotStopTheMachine() {
		LineRig r = line("ws").foreign("W", 1, Z + 1, 6, 40_000, 128);
		Map<String, Long> done = run(r, 1500, 1000);
		assertMachineFull(r, done, 1000);
	}

	/** The same Workstation declaring its draw (6 EU/t): fed first like any machine, the box takes the rest. */
	@Test
	void aWorkstationThatDeclaresItsDrawIsAMachineLikeAnyOther() {
		LineRig r = line("ws-declared").consumer("W", 1, Z + 1, 6, 6, 40_000, 128);
		Map<String, Long> done = run(r, 1500, 1000);
		assertMachineFull(r, done, 1000);
		assertTrue(done.get("W") >= 6L * 1000 * 95 / 100, "the workstation works at 6 EU/t, did " + done.get("W"));
	}

	/** A foreign battery of a million EU beside the generator. */
	@Test
	void aForeignBatteryBesideTheGeneratorDoesNotStopTheMachine() {
		LineRig r = line("fbattery-near").foreign("F", 1, Z + 1, 0, 1_000_000, 32);
		assertMachineFull(r, run(r, 3000, 500), 500);
	}

	/** A foreign battery at the far end, in one group with the macerator: the box still charges. */
	@Test
	void aForeignBatteryAtTheFarEndDoesNotLockTheBox() {
		LineRig r = line("fbattery-far").foreign("F", 4, Z + 1, 0, 1_000_000, 32);
		Map<String, Long> done = run(r, 3000, 500);
		assertMachineFull(r, done, 500);
		assertTrue(done.get("box") > 0, "the box charges beside a foreign battery, gained " + done.get("box"));
	}

	/** A foreign machine spending 16 EU/t at the far end: the macerator works, the box takes what is left. */
	@Test
	void aForeignMachineAtTheFarEndDoesNotLockTheBox() {
		LineRig r = line("fmach16-far").foreign("F", 4, Z + 1, 16, 4000, 32);
		Map<String, Long> done = run(r, 3000, 500);
		assertMachineFull(r, done, 500);
		assertTrue(done.get("box") > 0, "the box charges beside a foreign machine, gained " + done.get("box"));
	}

	/**
	 * The packet cap holds per receiver across both passes (review round 3, finding 3): on a tin network (packet 8)
	 * a machine touching three cables used to take its need in the first pass and up to a whole packet more in the
	 * second — 2 + 8 = 10 EU in one tick.
	 */
	@Test
	void aMachineNeverTakesMoreThanOnePacketPerTick() {
		LineRig r = new LineRig("tin", 0.0).packet(8).source(0, Z, 8).source(0, Z + 1, 8).source(4, Z + 1, 8)
				.run(1, Z, 1, Z + 1).run(2, Z + 1, 3, Z + 1)
				.cable(3, Z).machine("M", 2, Z, 2);
		long most = 0;
		long total = 0;
		for (int t = 0; t < 300; t++) {
			r.tick();
			most = Math.max(most, r.gain.get("M"));
			total += r.gain.get("M");
			r.work();
		}
		assertTrue(total > 0, "the machine is fed");
		assertTrue(most <= 8, "at most one packet (8) per tick into one receiver, took " + most);
	}

	/**
	 * A machine short because its path cannot carry its rate, not because the generator is short (review round 3,
	 * finding 2, {@code rev789/sc.py oc2}): an overclocked assembler (24 EU/t) behind four copper cables (12 EU/t), a
	 * 32 EU/t generator, a box on a second arm of the generator. The machine gets all its path carries; the box takes
	 * what the generator holds back — before, it took nothing and the generator idled 20 EU/t away. And a machine
 * short of supply, not of throughput, still gets every unit: {@code FlowUnderFedRigTest} 2genbox.
	 */
	@Test
	void aBoxTakesWhatTheGeneratorHoldsBackWhileAMachineIsShortOfThroughput() {
		LineRig r = new LineRig("oc2", 0.0).source(0, Z, 32).run(1, Z, 4, Z).cable(0, Z + 1).cable(1, Z + 1)
				.box("B", 0, Z + 2, 1_000_000).consumer("M", 5, Z, 24, 24, 800, 32);
		long box0 = 0;
		long machine = 0;
		for (int t = 1; t <= 2000; t++) {
			if (t == 1500) {
				box0 = r.end("B");
			}
			r.tick();
			if (t > 1500) {
				machine += r.gain.get("M");
			}
			r.work();
		}
		long box = r.end("B") - box0;
		assertTrue(machine >= 12L * 500 * 95 / 100, "the machine gets what its path carries, 12 EU/t; got " + machine);
		// The box draws from one copper cable, 12 EU a tick at most: of the 20 the generator holds back it gets 12.
		assertTrue(box >= 10L * 500, "the box takes the generator's held surplus (12 EU/t), gained " + box);
	}
}

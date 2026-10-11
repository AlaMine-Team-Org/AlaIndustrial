package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The findings of the energy test stand (MOD-799: E26, E27, E31, E34) as rigs on the toy grid (MOD-789, review of
 * Variant 1). Each bay's rule, as the stand checks it over a window of 200 ticks after 60 of warm-up: a machine is
 * fed when it pays its work (a Macerator: 2 EU/t) on at least 90 % of the window's ticks — the stand asks every
 * tick; the rig allows the few ticks a buffer of 800 rounds away.
 *
 * @implements MOD-789-FW05 — no machine starves while a store gains, a box discharges or a generator idles
 */
class FlowStandFindingsTest {

	private static final int WARM = 60;
	private static final int WINDOW = 200;

	/** Ticks of the window on which each machine paid its work, and each box's gain over the window. */
	private static Map<String, Long> run(LineRig r) {
		Map<String, Long> out = new LinkedHashMap<>();
		Map<String, Long> start = new LinkedHashMap<>();
		for (int t = 0; t < WARM + WINDOW; t++) {
			if (t == WARM) {
				for (LineRig.End e : r.ends) {
					start.put(e.name(), e.buf().getAmount());
				}
			}
			r.tick();
			Map<String, Long> done = r.work();
			if (t >= WARM) {
				done.forEach((k, v) -> out.merge(k, v > 0 ? 1L : 0L, Long::sum));
			}
		}
		for (LineRig.End e : r.ends) {
			if (!e.machine()) {
				out.put(e.name(), e.buf().getAmount() - start.get(e.name()));
			}
		}
		return out;
	}

	private static void assertFed(LineRig r, Map<String, Long> got, String... machines) {
		for (String m : machines) {
			assertTrue(got.get(m) >= WINDOW * 9 / 10, r.name + ": " + m + " paid its work on " + got.get(m) + " of "
					+ WINDOW + " ticks; all: " + got);
		}
	}

	/** The looped box of E26/E27: a bus of five, the box beside its first cable with a loop behind it. */
	private static LineRig loopedBox(String name, long source) {
		LineRig r = new LineRig(name, 0.0).source(0, 2, source).run(1, 2, 5, 2);
		r.battery("B1", 1, 3, 40_000, 10_000).run(1, 4, 2, 4).cable(2, 3);
		return r.machine("M1", 4, 3, 2).machine("M2", 6, 2, 2);
	}

	/** E26: a source of half the machines' need and a box on a loop: the box covers the rest, both are fed. */
	@Test
	void aBoxOnALoopCoversAShortSource() {
		LineRig r = loopedBox("e26", 2);
		Map<String, Long> got = run(r);
		assertFed(r, got, "M1", "M2");
		assertTrue(got.get("B1") < 0, "e26: the box discharged: " + got);
	}

	/** E27: the source off, a full box feeds both machines — their needs first, the buffers after. */
	@Test
	void aBoxAloneFeedsBothMachinesNeedsFirst() {
		LineRig r = loopedBox("e27", 0);
		Map<String, Long> got = run(r);
		assertFed(r, got, "M1", "M2");
		assertTrue(got.get("B1") < 0, "e27: the box discharged: " + got);
	}

	/** E31: forty bare copper cables (2 %/block), a machine after the last and a box beside it. */
	@Test
	void aMachineAtTheEndOfFortyBareCopperCablesIsFed() {
		LineRig r = new LineRig("e31", 0.02).source(0, 0, 20).run(1, 0, 40, 0).machine("M1", 41, 0, 2)
				.box("B1", 40, 1, 40_000);
		Map<String, Long> got = run(r);
		assertFed(r, got, "M1");
		assertTrue(got.get("B1") >= WINDOW / 2, "e31: the box charged at least 0.5 EU/t: " + got);
	}

	/**
	 * E34: a 32 x 32 sheet of bare copper, eight 64 EU/t sources set into it (a copper packet: 32 each, 256 in
	 * all), a Macerator on top of every fourth cell (64 x 2 EU/t = 128), four boxes on the west edge. Twice the
	 * need is offered: every machine is fed, and the boxes charge on the rest.
	 */
	@Test
	void everyMachineOfTheMegaSheetIsFed() {
		LineRig r = megaSheet();
		Map<String, Long> got = run(r);
		String[] all = new String[64];
		for (int i = 0; i < 64; i++) {
			all[i] = "M" + (i + 1);
		}
		assertFed(r, got, all);
		assertTrue(got.get("B1") > 0 && got.get("B4") > 0, "e34: the boxes charged: " + got);
	}

	/**
	 * Review of the stand round: on the sheet a machine's planned draw sometimes finds its cable short — the cable
	 * gives what it held when the tick began — and the stock must make up what the draw actually missed, not what
	 * the plan promised. Twice the need offered: no machine misses a single tick of the window.
	 */
	@Test
	void noMachineOfTheMegaSheetMissesATick() {
		LineRig r = megaSheet();
		Map<String, Long> got = run(r);
		for (int i = 1; i <= 64; i++) {
			assertTrue(got.get("M" + i) == WINDOW, "e34: M" + i + " paid its work on " + got.get("M" + i) + " of "
					+ WINDOW + " ticks");
		}
	}

	static LineRig megaSheet() {
		LineRig r = new LineRig("e34", 0.02);
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 32; z++) {
				if (x % 8 == 4 && z % 16 == 4) {
					r.source(x, z, 64);
				} else {
					r.cable(x, z);
				}
			}
		}
		int m = 0;
		for (int x = 2; x < 32; x += 4) {
			for (int z = 2; z < 32; z += 4) {
				r.machineOver("M" + ++m, x, z, 2);
			}
		}
		for (int b = 1; b <= 4; b++) {
			r.box("B" + b, -1, 8 * b - 4, 40_000);
		}
		return r;
	}
}

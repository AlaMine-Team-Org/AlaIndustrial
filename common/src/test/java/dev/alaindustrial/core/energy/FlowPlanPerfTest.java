package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What a line tick costs (MOD-789, ADR-049): a comb of about 500 copper cables — a bus of 100 with 20 branches of
 * 20 — a machine at the end of every branch. Three cases: one 32 EU/t generator short of 20 machines of 8 EU/t (the
 * nearest first, sharing at one distance), the same generator over 20 machines of 1 EU/t (every need met, the
 * buffers fill), and four generators along the bus with more than the bus carries (the generators' even share is
 * searched every tick). Times the kernel's part of the tick only (plan, draws, hops, stock, recharge) and the heap it
 * allocates.
 *
 * <p>One gate runs in every build: on the HV grid a tick runs a few hundred path searches, not one per EU shared
 * ({@link #anHvGridSharesInAFewPathSearches} — a count, so it holds on any machine). The timing is a measurement, not
 * a gate: run it with {@code ALAMOD_PERF=1 ./gradlew :common:test --tests
 * dev.alaindustrial.core.energy.FlowPlanPerfTest --rerun}; it writes {@code common/build/mod789-plan-perf.txt}. The
 * ceiling it asserts (2 ms a tick) fails only on a gross regression.
 */
class FlowPlanPerfTest {

	private static final int Z = 0;

	static LineRig comb(long work) {
		LineRig r = new LineRig("comb", 0.0).source(-1, Z, 32).run(0, Z, 99, Z);
		for (int b = 0; b < 20; b++) {
			int x = 2 + b * 5;
			r.run(x, Z + 1, x, Z + 20).machine("M" + b, x, Z + 21, work);
		}
		return r;
	}

	/** Ticks the rig 300 times to warm up, then measures 500: kernel ms and bytes per tick. */
	private static double[] measure(LineRig r) {
		for (int t = 0; t < 300; t++) {
			r.tick();
			r.work();
		}
		r.kernelNanos = 0;
		r.kernelBytes = 0;
		int ticks = 500;
		for (int t = 0; t < ticks; t++) {
			r.tick();
			r.work();
		}
		return new double[] {r.kernelNanos / 1e6 / ticks, (double) r.kernelBytes / ticks};
	}

	/**
	 * A 32 x 32 grid of HV cables (1024; segment buffer 192, packet 512), a box on both ends of every column, 16
	 * generators of 512 EU/t down the west side: every box at one distance wants a packet the grid cannot carry to
	 * all of them, so the plan shares at every distance (MOD-789, review of Variant 1: 25.8 ms a tick when a sharing
	 * handed out one EU per path search).
	 */
	static LineRig hvGrid(int side, int generators) {
		LineRig r = new LineRig("hvgrid", 0.0).grade(192, 512).source(-1, 0, 512);
		for (int z = 0; z < side; z++) {
			r.run(0, z, side - 1, z);
		}
		for (int x = 0; x < side; x++) {
			r.box("N" + x, x, -1, 100_000_000L).box("S" + x, x, side, 100_000_000L);
		}
		for (int g = 1; g < generators; g++) {
			r.source(-1, g * side / generators, 512);
		}
		return r;
	}

	@Test
	void anHvGridSharesInAFewPathSearches() {
		LineRig r = hvGrid(32, 16);
		for (int t = 0; t < 30; t++) {
			r.tick();
		}
		long before = r.planner.graph().searches();
		int ticks = 20;
		for (int t = 0; t < ticks; t++) {
			r.tick();
		}
		long perTick = (r.planner.graph().searches() - before) / ticks;
		assertTrue(perTick < 200, "path searches per tick on the 1024-cable HV grid: " + perTick);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "ALAMOD_PERF", matches = "1")
	void aWideCombTicksWellUnderTheBudget() throws IOException {
		double[] short20 = measure(comb(8)); // 20 x 8 EU/t on a 32 EU/t generator: short, the nearest first
		double[] fed = measure(comb(1)); // 20 x 1 EU/t: every need met, the buffers fill
		LineRig four = comb(1);
		for (int g = 1; g <= 3; g++) {
			four.source(25 * g, Z - 1, 32); // three more generators on the bus: more than the bus carries
		}
		double[] shared = measure(four);
		double[] grid = measure(hvGrid(32, 16));
		String line = String.format(Locale.ROOT,
				"cables=500 machines=20 short: %.3f ms/tick, %.0f bytes/tick; fed: %.3f ms/tick, %.0f bytes/tick;"
						+ " 4 generators: %.3f ms/tick, %.0f bytes/tick; HV grid 1024 cables 64 boxes 16 generators:"
						+ " %.3f ms/tick, %.0f bytes/tick%n",
				short20[0], short20[1], fed[0], fed[1], shared[0], shared[1], grid[0], grid[1]);
		Files.writeString(Path.of("build", "mod789-plan-perf.txt"), line, StandardCharsets.UTF_8);
		assertTrue(short20[0] < 2.0 && fed[0] < 2.0 && shared[0] < 2.0 && grid[0] < 2.0,
				"the line tick stays under 2 ms: " + line);
	}
}

package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What one energy-network tick actually costs (MOD-404).
 *
 * <h2>Why this exists</h2>
 *
 * The network is the most heavily tested part of the mod for CORRECTNESS and, until this scenario,
 * was tested for SPEED by nothing at all — while being the most expensive thing in the server tick:
 * five BFS fields over the cable graph, an endpoint refresh every tick, three mutually exclusive
 * storage-discharge channels. "The mod started lagging somewhere in the last ten releases" was a
 * report nobody could turn into a red test.
 *
 * <p>The gap is not hypothetical. Every field is rebuilt when the set of LIVE endpoints changes, and
 * a generator whose buffer empties and refills flips that set — so a perfectly ordinary base can walk
 * the whole cable graph every tick without anything looking wrong.
 *
 * <h2>What is measured</h2>
 *
 * Three phases on the same 50-cable rig, back to back, timing THIS network's tick (not
 * {@link NetworkManager#tickAll} — see {@link #measure} for why that distinction is the difference
 * between measuring the subject and measuring the neighbours):
 *
 * <ol>
 *   <li><b>noise</b> — the timing loop around an empty body. The floor: without it a figure like
 *       "60 µs" cannot be told from measurement overhead.</li>
 *   <li><b>asleep</b> — a fuelled generator on a FULL line with no consumer attached. Not "no
 *       power": a network with any waiting consumer is awake by contract, so this is the only
 *       shape a sleeping network can have — and it is the state a real base spends most of its
 *       time in.</li>
 *   <li><b>running</b> — the same line with a hungry macerator at the far end, 48 hops away.</li>
 * </ol>
 *
 * <h2>The two assertions, and why they are these two</h2>
 *
 * <ul>
 *   <li><b>Sleep is asserted structurally, not by the clock.</b> The asleep phase checks
 *       {@link EnergyNetwork#isAwake()} is actually {@code false} — a deterministic fact that cannot
 *       flake, unlike "the sleeping tick was faster than the working one", which on two sub-millisecond
 *       numbers measures the scheduler more than the code.</li>
 *   <li><b>Cost is asserted as a ceiling with two orders of magnitude of headroom.</b> Wall-clock on
 *       a developer machine (or a loaded CI runner) is not reproducible to the microsecond, so a tight
 *       threshold would flap. The ceiling exists to catch a STRUCTURAL regression — a walk that went
 *       quadratic, a cache that stopped caching — not to police a number. The numbers themselves are
 *       printed on every run.</li>
 * </ul>
 *
 * <p><b>Warm-up is not optional.</b> The first hundred ticks run interpreted; measuring them would
 * report the JIT, not the code. The rig is driven for {@link #WARMUP_TICKS} before the clock starts.
 */
public final class EnergyNetworkPerfScenarios {

	private EnergyNetworkPerfScenarios() {
	}

	/** Ticks driven before any measurement, so the JIT has compiled the tick path. */
	private static final int WARMUP_TICKS = 120;
	/** Measured ticks per phase. Large enough to average out scheduler noise, short enough for the lane. */
	private static final int MEASURED_TICKS = 200;

	/**
	 * Ceiling for ONE tick of a 50-cable network, above the noise floor.
	 *
	 * <p>2 ms is deliberately absurd next to the ~10–100 µs this actually takes: it is a structural
	 * tripwire, not a budget. A quadratic walk over 50 cables, or a BFS rebuilt from scratch every tick
	 * where it used to be cached, lands orders of magnitude above this; normal variance between a
	 * developer laptop and a loaded CI runner does not.
	 */
	private static final long TICK_CEILING_NS = 2_000_000L;

	/** The cable plane. The source and the sink sit one level BELOW it, so neither can be overwritten. */
	private static final int CABLE_Y = 2;
	/** Fuelled generator, directly under the first cable of the snake. */
	private static final BlockPos GEN = new BlockPos(0, CABLE_Y - 1, 0);
	/** Hungry macerator, directly under the last cable of the snake — 48 hops away through the graph. */
	private static final BlockPos MACERATOR = new BlockPos(6, CABLE_Y - 1, 6);

	/**
	 * Fifty cables folded into the rig — a long LINE, not a blob: what this scenario guards is per-hop
	 * propagation down a bus, and a compact cube of cables would measure a much shorter graph.
	 *
	 * <p>A 7×7 boustrophedon snake in one plane (49 segments, every consecutive pair face-adjacent),
	 * plus one segment stacked above the tail to make it 50. The endpoints attach from BELOW, out of
	 * the plane entirely — the first version of this fixture put the generator at the snake's own start
	 * position, so the cable loop overwrote it and the network ran with no source at all. The benchmark
	 * caught that itself (the mid-line buffer assertion below), which is precisely why that assertion
	 * exists: a benchmark measuring an idle network reports a flattering number and proves nothing.
	 */
	private static List<BlockPos> cablePath() {
		List<BlockPos> path = new ArrayList<>(50);
		for (int z = 0; z <= 6; z++) {
			if ((z & 1) == 0) {
				for (int x = 0; x <= 6; x++) {
					path.add(new BlockPos(x, CABLE_Y, z));
				}
			} else {
				for (int x = 6; x >= 0; x--) {
					path.add(new BlockPos(x, CABLE_Y, z));
				}
			}
		}
		path.add(new BlockPos(6, CABLE_Y + 1, 6)); // 50th, stacked on the tail
		if (path.size() != 50) {
			throw new IllegalStateException("perf rig must hold exactly 50 cables, built " + path.size());
		}
		return path;
	}

	/** Median of a sample. Median, not mean: one scheduler hiccup must not move the reported figure. */
	private static long median(long[] samples) {
		long[] copy = samples.clone();
		java.util.Arrays.sort(copy);
		return copy[copy.length / 2];
	}

	/**
	 * Time {@code runs} repetitions of THIS network's tick, returning the median nanoseconds per tick.
	 *
	 * <p>Deliberately ticks the one network under test rather than calling
	 * {@link NetworkManager#tickAll}. Gametests share a single {@code ServerLevel}, so {@code tickAll}
	 * also ticks every rig every other scenario has built — the first version of this benchmark did
	 * exactly that and reported the asleep and running phases as 78 µs and 80 µs, i.e. the neighbours'
	 * noise with this rig's contribution invisible inside it. A benchmark that cannot see its own
	 * subject cannot regress on it either.
	 *
	 * <p>The sleep gate is included on purpose: {@code tickAll} asks {@code isAwake()} before ticking,
	 * so measuring {@code tick()} alone would price a tick this network does not actually pay.
	 */
	private static long measure(EnergyNetwork network, int runs, boolean tickNetwork) {
		long[] samples = new long[runs];
		for (int i = 0; i < runs; i++) {
			long t0 = System.nanoTime();
			if (tickNetwork && network.isAwake()) {
				network.tick();
			}
			samples[i] = System.nanoTime() - t0;
		}
		return median(samples);
	}

	/**
	 * The measurement itself: a 50-cable bus, asleep and then running, timed against an empty-loop floor.
	 *
	 * @implements MOD-404 — energy-network tick cost stays structurally flat.
	 */
	public static void perf01FiftyCableLineTickCost(GameTestHelper helper) {
		List<BlockPos> cables = cablePath();
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		for (BlockPos cable : cables) {
			helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		}
		// The macerator is deliberately NOT placed yet — see the asleep phase below.
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}

		// Registration happens on a cable's first server tick, so the network does not exist until the
		// segments have ticked once. Everything below depends on it existing.
		for (BlockPos cable : cables) {
			tick(helper, be(helper, cable));
		}

		EnergyNetwork network = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(cables.get(0)));
		if (network == null) {
			helper.fail("perf rig built no network — the 50 cables did not register");
		}
		if (network.size() != 50) {
			helper.fail("perf rig is not one network of 50 cables (got " + network.size()
					+ ") — the fold left a gap, so this would measure a shorter line than it claims");
		}

		// ── phase 1: noise floor ───────────────────────────────────────────────────────────────
		long noise = measure(network, MEASURED_TICKS, false);

		// ── phase 2: asleep — a fuelled generator on a FULL line with nothing to serve ──────────
		//
		// This is what "asleep" actually means for an energy network, and it is not "no power": a
		// network with any waiting consumer is awake by contract (ADR-002 serves it, ADR-007 keeps it
		// ticking), so a rig with an idle machine attached could never reach this state. The state a
		// real base spends most of its time in is this one — the generator has filled the wire, no
		// machine is asking, and the network sleeps on the memoized "line is full" flag instead of
		// re-walking every cable to keep answering "nothing to do".
		//
		// The first version of this phase left the macerator in place and simply withheld fuel; the
		// assertion below caught it, which is the whole point of asserting sleep as a fact rather than
		// inferring it from a stopwatch.
		for (int i = 0; i < WARMUP_TICKS; i++) {
			tick(helper, be(helper, GEN));
			NetworkManager.tickAll(helper.getLevel());
		}
		long asleep = measure(network, MEASURED_TICKS, true);
		boolean sleeping = !network.isAwake();

		// ── phase 3: running — the same line, now with a hungry macerator at the far end ────────
		helper.setBlock(MACERATOR, ModContent.MACERATOR.get());
		if (be(helper, MACERATOR) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 64));
		}
		for (int i = 0; i < WARMUP_TICKS; i++) {
			tick(helper, be(helper, GEN));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, MACERATOR));
		}
		long running = measure(network, MEASURED_TICKS, true);

		long asleepNet = Math.max(0, asleep - noise);
		long runningNet = Math.max(0, running - noise);
		// Prefix shared with the assembler benchmark, so tools/bench_*.py can pull these lines out of a
		// few thousand lines of server log on either loader.
		System.out.printf(Locale.ROOT,
				"[ala-bench] energy network, 50 cables: noise %d ns%n", noise);
		System.out.printf(Locale.ROOT,
				"[ala-bench] %-8s per network tick %8d ns (net of noise %8d ns) | %6.3f %% of the 50 ms budget%n",
				"asleep", asleep, asleepNet, asleepNet / 500_000.0);
		System.out.printf(Locale.ROOT,
				"[ala-bench] %-8s per network tick %8d ns (net of noise %8d ns) | %6.3f %% of the 50 ms budget%n",
				"running", running, runningNet, runningNet / 500_000.0);

		// The sleep gate is asserted as a FACT, not as a stopwatch comparison: two sub-millisecond
		// numbers compared against each other measure the scheduler, and this must never flake.
		if (!sleeping) {
			helper.fail("a fuelless 50-cable network with a satisfied line stayed awake — the sleep gate "
					+ "(ADR-007) no longer fires, so every base pays a full graph walk per tick");
		}
		if (runningNet > TICK_CEILING_NS) {
			helper.fail("one working network tick over 50 cables took " + runningNet + " ns (> "
					+ TICK_CEILING_NS + " ns ceiling) — this is a structural regression (a walk that went "
					+ "quadratic, or a cache that stopped caching), not measurement noise");
		}
		// A cable buffer mid-line must actually hold EU by now; otherwise the "running" phase measured
		// an idle network and the ceiling above proved nothing (the vacuous-benchmark trap).
		long mid = be(helper, cables.get(25)) instanceof CableBlockEntity cable
				? cable.getEnergyStorage().getAmount() : -1;
		if (mid <= 0) {
			helper.fail("mid-line cable holds " + mid + " EU after the running phase — the benchmark "
					+ "measured a network that was not actually transporting anything");
		}
		helper.succeed();
	}
}

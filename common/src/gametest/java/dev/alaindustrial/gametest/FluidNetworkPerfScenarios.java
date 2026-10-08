package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

/**
 * What one fluid-network tick costs on a running 30-segment line (MOD-715, batch 0) — the fluid twin of
 * {@code EnergyNetworkPerfScenarios}, added as the baseline the network-core refactoring is measured
 * against (its acceptance asks for the cost of {@code EnergyNetwork.tick} and {@code FluidNetwork.tick}).
 *
 * <p>Same method as the energy benchmark: this network ticked directly (never {@code tickAll}, which would
 * time every other rig in the shared level), a warm-up so the JIT has compiled the path, the median of the
 * measured ticks, an empty-loop noise floor, and a ceiling two orders of magnitude above the real cost —
 * a structural tripwire, not a budget. The numbers are printed with the {@code [ala-bench]} prefix.
 */
public final class FluidNetworkPerfScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidNetworkPerfScenarios::thirtySegmentLineTickCost, "mod715_fluid_network_tick_cost")
						.ticks(100));

		private Roster() {}
	}

	private static final int SEGMENTS = 30;
	private static final int WARMUP_TICKS = 120;
	private static final int MEASURED_TICKS = 200;
	/**
	 * Ceiling for one tick of a 30-segment line, above the noise floor: a tripwire, not a budget. The first
	 * measurement (Fabric lane, 2026-10-02) was about 1.7 ms — every hop commits its own transaction and
	 * the hungry-sink field asks each consumer through a simulated insert — so the energy benchmark's 2 ms
	 * would flap here; 20 ms still catches a walk that went quadratic.
	 */
	private static final long TICK_CEILING_NS = 20_000_000L;

	private FluidNetworkPerfScenarios() {
	}

	private static long median(long[] samples) {
		long[] copy = samples.clone();
		Arrays.sort(copy);
		return copy[copy.length / 2];
	}

	/**
	 * Tank, thirty basic segments, tank; the source topped up and the target emptied every tick so the
	 * line runs at its steady rate while it is timed.
	 *
	 * @implements MOD-715-PF01 — fluid-network tick cost baseline
	 */
	public static void thirtySegmentLineTickCost(GameTestHelper helper) {
		List<BlockPos> path = FluidLineThroughputScenarios.serpentine(SEGMENTS);
		BlockPos source = path.get(0).west();
		BlockPos last = path.get(SEGMENTS - 1);
		Direction out = Direction.EAST;
		for (Direction d : Direction.values()) {
			if (path.get(SEGMENTS - 2).relative(d).equals(last)) {
				out = d;
			}
		}
		BlockPos target = last.relative(out);
		helper.setBlock(source, ModContent.FLUID_TANK.get());
		for (BlockPos p : path) {
			helper.setBlock(p, ModContent.FLUID_PIPE.get());
		}
		helper.setBlock(target, ModContent.FLUID_TANK.get());
		FluidTankBlockEntity src = helper.getBlockEntity(source, FluidTankBlockEntity.class);
		FluidTankBlockEntity dst = helper.getBlockEntity(target, FluidTankBlockEntity.class);
		helper.getBlockEntity(path.get(0), FluidPipeBlockEntity.class)
				.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		helper.getBlockEntity(last, FluidPipeBlockEntity.class).setFaceMode(out, PipeFaceMode.INSERT);
		for (BlockPos p : path) {
			FluidPipeBlockEntity pipe = helper.getBlockEntity(p, FluidPipeBlockEntity.class);
			pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
		}
		FluidNetwork network = FluidNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(path.get(0)));
		if (network == null || network.size() != SEGMENTS) {
			helper.fail("perf rig is not one fluid network of " + SEGMENTS + " segments: " + network);
			return;
		}
		long[] noise = new long[MEASURED_TICKS];
		long[] running = new long[MEASURED_TICKS];
		for (int i = 0; i < WARMUP_TICKS + MEASURED_TICKS; i++) {
			src.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			src.fluidTank.amount = Config.fluidTankCapacity;
			long t0 = System.nanoTime();
			long t1 = System.nanoTime();
			if (network.isAwake()) {
				network.tick();
			}
			long t2 = System.nanoTime();
			if (i >= WARMUP_TICKS) {
				noise[i - WARMUP_TICKS] = t1 - t0;
				running[i - WARMUP_TICKS] = t2 - t1;
			}
			dst.fluidTank.amount = 0;
			dst.fluidTank.fluid = FluidHolder.EMPTY;
		}
		long floor = median(noise);
		long tick = median(running);
		long net = Math.max(0, tick - floor);
		System.out.printf(Locale.ROOT, "[ala-bench] fluid network, %d segments: noise %d ns%n", SEGMENTS, floor);
		System.out.printf(Locale.ROOT,
				"[ala-bench] %-8s per fluid tick  %8d ns (net of noise %8d ns) | %6.3f %% of the 50 ms budget%n",
				"running", tick, net, net / 500_000.0);
		if (net > TICK_CEILING_NS) {
			helper.fail("one fluid-network tick over " + SEGMENTS + " segments took " + net + " ns (> "
					+ TICK_CEILING_NS + " ns ceiling) — a structural regression, not noise");
			return;
		}
		if (helper.getBlockEntity(path.get(SEGMENTS / 2), FluidPipeBlockEntity.class).fluidBuffer.amount <= 0) {
			helper.fail("the mid-line segment is empty — the benchmark timed a line that was not running");
			return;
		}
		long idle = idleTickCost(helper, network, dst);
		if (idle < 0) {
			return;
		}
		System.out.printf(Locale.ROOT,
				"[ala-bench] %-8s per fluid tick  %8d ns (net of noise %8d ns) | %6.3f %% of the 50 ms budget%n",
				"idle", idle, Math.max(0, idle - floor), Math.max(0, idle - floor) / 500_000.0);
		helper.succeed();
	}

	/**
	 * The same line with both tanks full (MOD-734): the source is no longer topped up and the target fills
	 * up, the line settles and the network falls asleep; then the median of what the manager's per-tick
	 * question — {@code isAwake}, and a tick only if it says so — costs. Returns -1 after failing the test
	 * when the line never falls asleep.
	 */
	private static long idleTickCost(GameTestHelper helper, FluidNetwork network, FluidTankBlockEntity dst) {
		dst.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
		dst.fluidTank.amount = dst.fluidTank.capacity;
		int settled = 0;
		while (network.isAwake()) {
			network.tick();
			if (++settled > 4 * WARMUP_TICKS * SEGMENTS) {
				helper.fail("the full line never fell asleep — the idle cost cannot be measured (MOD-734)");
				return -1;
			}
		}
		long[] samples = new long[MEASURED_TICKS];
		for (int i = 0; i < WARMUP_TICKS + MEASURED_TICKS; i++) {
			long t1 = System.nanoTime();
			if (network.isAwake()) {
				network.tick();
			}
			long t2 = System.nanoTime();
			if (i >= WARMUP_TICKS) {
				samples[i - WARMUP_TICKS] = t2 - t1;
			}
		}
		return median(samples);
	}
}

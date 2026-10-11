package dev.alaindustrial.command.demo.energy;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.energy.PerTickDemand;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * {@code /ala demo energy check} (MOD-799): resets every bay of the energy stand, lets the world run a warm-up,
 * then measures each probe over a window of real server ticks and judges the bay's {@link Expectation}s.
 *
 * <p>Measuring goes through what the blocks already keep, never through the core: a machine's and a store's
 * lifetime counters (switched on by a Statistics Chip in the last upgrade slot, MOD-125), every block's charge,
 * and for a creative source what was taken from its buffer, which it tops up to its rate on its own tick before
 * the networks run ({@code onServerTick → refillToLimit}). The session is sampled at the end of the server tick,
 * after the networks ({@code ServerHookRoster.SERVER_TICK}), and times the level's energy-network pass
 * ({@link #beforeNetworks}/{@link #afterNetworks}) so the report shows what the stand — the mega network above
 * all — costs a tick.
 */
public final class EnergyStandCheck {
	/** Ticks the world runs after the reset before measuring: a forty-cable line fills one hop per tick. */
	public static final int WARMUP = 60;
	/** Length of the measured window. */
	public static final int WINDOW = 200;

	private static final List<Session> SESSIONS = new CopyOnWriteArrayList<>();
	private static volatile EnergyStandReport last;

	private EnergyStandCheck() {
	}

	/** The last finished check, or {@code null} when none ran since the server started. */
	public static EnergyStandReport last() {
		return last;
	}

	/** Whether a check is still running anywhere. */
	public static boolean running() {
		return !SESSIONS.isEmpty();
	}

	/** Reset the stand at {@code origin} and start measuring; {@code done} gets the report when it is finished. */
	public static void start(ServerLevel level, BlockPos origin, int warmup, int window,
			Consumer<EnergyStandReport> done) {
		Session session = new Session(level, origin, EnergyStand.plans(level, origin), warmup, window, done);
		session.reset();
		SESSIONS.add(session);
	}

	/** {@code ServerHookRoster.SERVER_TICK}: advance every running check by one tick. */
	public static void tick(MinecraftServer server) {
		for (Session session : SESSIONS) {
			if (session.step()) {
				SESSIONS.remove(session);
				last = session.report;
				session.done.accept(session.report);
			}
		}
	}

	/** {@code ServerHookRoster.SERVER_TICK_PER_LEVEL}, right before the energy networks of {@code level} tick. */
	public static void beforeNetworks(ServerLevel level) {
		for (Session session : SESSIONS) {
			if (session.level == level) {
				session.networkStart = System.nanoTime();
			}
		}
	}

	/** Right after the energy networks of {@code level} ticked. */
	public static void afterNetworks(ServerLevel level) {
		for (Session session : SESSIONS) {
			if (session.level == level && session.networkStart != 0) {
				session.recordNetworkTime(System.nanoTime() - session.networkStart);
				session.networkStart = 0;
			}
		}
	}

	/** One block's numbers at the start of the window. */
	private record Snapshot(long amount, long consumed, long in, boolean counters) {
		static Snapshot of(BlockEntity be) {
			if (!(be instanceof EnergyBlockEntity e)) {
				return new Snapshot(0, 0, 0, false);
			}
			EnergyBuffer buf = e.getEnergyStorage();
			return new Snapshot(buf.getAmount(), buf.getTotalEnergyConsumed(), buf.getTotalEnergyIn(),
					buf.countersEnabled());
		}
	}

	private static final class Session {
		private final ServerLevel level;
		private final BlockPos origin;
		private final List<BayPlan> plans;
		private final int warmup;
		private final int window;
		private final Consumer<EnergyStandReport> done;
		private final Map<BlockPos, Snapshot> start = new HashMap<>();
		private final Map<BlockPos, Long> sourceOut = new HashMap<>();
		private final Map<BlockPos, Integer> rates = new HashMap<>();
		private final Map<String, Integer> awake = new HashMap<>();
		private final Map<String, Long> cablesAtStart = new HashMap<>();
		private int tick;
		private long networkStart;
		private long networkNanos;
		private long networkMaxNanos;
		private int networkTicks;
		private EnergyStandReport report;

		Session(ServerLevel level, BlockPos origin, List<BayPlan> plans, int warmup, int window,
				Consumer<EnergyStandReport> done) {
			this.level = level;
			this.origin = origin;
			this.plans = plans;
			this.warmup = warmup;
			this.window = window;
			this.done = done;
		}

		void recordNetworkTime(long nanos) {
			if (tick <= warmup) {
				return;
			}
			networkNanos += nanos;
			networkMaxNanos = Math.max(networkMaxNanos, nanos);
			networkTicks++;
		}

		/** Put every bay back where its build left it: empty cables, stocked machines, stores at their charge. */
		void reset() {
			for (BayPlan plan : plans) {
				for (BlockPos cable : plan.cables()) {
					if (level.getBlockEntity(cable) instanceof EnergyBlockEntity e) {
						e.getEnergyStorage().setAmountUntracked(0);
					}
				}
				Map<String, Long> needs = new HashMap<>();
				for (BayPlan.Probe probe : plan.probes()) {
					resetProbe(probe, needs);
				}
				for (BayPlan.Probe probe : plan.probes()) {
					if (probe.kind() == ProbeKind.SOURCE) {
						resetSource(probe, rate(plan, probe, needs));
					}
				}
				EnergyNetwork net = network(plan);
				if (net != null) {
					net.markDirty();
				}
			}
		}

		private void resetProbe(BayPlan.Probe probe, Map<String, Long> needs) {
			BlockEntity be = level.getBlockEntity(probe.pos());
			if (!(be instanceof MachineBlockEntity machine) || probe.kind() == ProbeKind.SOURCE) {
				return;
			}
			if (probe.kind() == ProbeKind.MACHINE) {
				int firstUpgrade = machine.hasUpgradeSlots() ? machine.upgradeSlotStart() : machine.getContainerSize();
				for (int slot = 1; slot < firstUpgrade; slot++) {
					machine.setItem(slot, ItemStack.EMPTY);
				}
				machine.setItem(0, new ItemStack(Items.RAW_IRON, 64));
			}
			if (machine.hasUpgradeSlots()) {
				int first = machine.upgradeSlotStart();
				for (int i = 0; i < MachineBlockEntity.UPGRADE_SLOT_COUNT - 1; i++) {
					machine.setItem(first + i, i < probe.chips()
							? new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get()) : ItemStack.EMPTY);
				}
				machine.setItem(first + MachineBlockEntity.UPGRADE_SLOT_COUNT - 1,
						new ItemStack(ModContent.STATS_CHIP.get()));
			}
			machine.getEnergyStorage().setAmountUntracked(probe.kind() == ProbeKind.STORE ? probe.charge() : 0L);
			if (machine instanceof PerTickDemand demand && probe.kind() == ProbeKind.MACHINE) {
				needs.put(probe.name(), demand.perTickDemand());
			}
			machine.setChangedQuietly();
			machine.wake();
		}

		/** A source's rate from the bay's machine needs; 0 when a machine it depends on is gone (the bay fails). */
		private static int rate(BayPlan plan, BayPlan.Probe probe, Map<String, Long> needs) {
			try {
				return probe.rate().applyAsInt(needs);
			} catch (NullPointerException e) {
				Industrialization.LOGGER.warn("energy stand: bay {} source {} has no machine to size its rate by",
						plan.bay().id(), probe.name());
				return 0;
			}
		}

		private void resetSource(BayPlan.Probe probe, int rate) {
			if (level.getBlockEntity(probe.pos()) instanceof CreativeEnergySourceBlockEntity source) {
				source.setOutputLimit(rate);
				source.setEnabled(true);
				source.wake();
			}
			rates.put(probe.pos(), rate);
		}

		private EnergyNetwork network(BayPlan plan) {
			return plan.cables().isEmpty() ? null : NetworkManager.networkAt(level, plan.cables().get(0));
		}

		/** One server tick; true when the window is over and the report is ready. */
		boolean step() {
			tick++;
			if (tick == warmup) {
				snapshot();
			} else if (tick > warmup) {
				sample();
			}
			if (tick < warmup + window) {
				return false;
			}
			report = EnergyStandReport.of(origin, window, judge(), networkNanos, networkMaxNanos, networkTicks,
					NetworkManager.stats(level), EnergyStand.lastBuildProblems());
			return true;
		}

		private void snapshot() {
			for (BayPlan plan : plans) {
				for (BayPlan.Probe probe : plan.probes()) {
					start.put(probe.pos(), Snapshot.of(level.getBlockEntity(probe.pos())));
					sourceOut.put(probe.pos(), 0L);
				}
				cablesAtStart.put(plan.bay().id(), cableSum(plan));
				awake.put(plan.bay().id(), 0);
			}
		}

		private void sample() {
			for (BayPlan plan : plans) {
				for (BayPlan.Probe probe : plan.probes()) {
					if (probe.kind() == ProbeKind.SOURCE
							&& level.getBlockEntity(probe.pos()) instanceof CreativeEnergySourceBlockEntity source) {
						long taken = Math.max(0, rates.getOrDefault(probe.pos(), 0)
								- source.getEnergyStorage().getAmount());
						sourceOut.merge(probe.pos(), taken, Long::sum);
					}
				}
				EnergyNetwork net = network(plan);
				if (net != null && net.isAwake()) {
					awake.merge(plan.bay().id(), 1, Integer::sum);
				}
			}
		}

		private long cableSum(BayPlan plan) {
			long sum = 0;
			for (BlockPos cable : plan.cables()) {
				if (level.getBlockEntity(cable) instanceof EnergyBlockEntity e) {
					sum += e.getEnergyStorage().getAmount();
				}
			}
			return sum;
		}

		private List<EnergyStandReport.BayResult> judge() {
			List<EnergyStandReport.BayResult> results = new ArrayList<>();
			for (BayPlan plan : plans) {
				BayReading reading = read(plan);
				List<Expectation.Verdict> verdicts = new ArrayList<>();
				for (Expectation expectation : plan.bay().expect()) {
					verdicts.add(expectation.judge(reading));
				}
				results.add(new EnergyStandReport.BayResult(plan.bay().id(), plan.bay().title(), reading,
						plan.bay().expect(), verdicts));
			}
			return results;
		}

		private BayReading read(BayPlan plan) {
			Map<String, ProbeReading> probes = new LinkedHashMap<>();
			for (BayPlan.Probe probe : plan.probes()) {
				probes.put(probe.name(), readProbe(probe));
			}
			Map<String, Boolean> flags = new HashMap<>();
			plan.gaps().forEach((flag, pos) ->
					flags.put(flag, level.getBlockEntity(pos) instanceof EnergyBlockEntity));
			long cableDelta = cableSum(plan) - cablesAtStart.getOrDefault(plan.bay().id(), 0L);
			return new BayReading(plan.bay().id(), window, probes, cableDelta, plan.cables().size(),
					awake.getOrDefault(plan.bay().id(), 0), flags);
		}

		private ProbeReading readProbe(BayPlan.Probe probe) {
			BlockEntity be = level.getBlockEntity(probe.pos());
			Snapshot s0 = start.getOrDefault(probe.pos(), new Snapshot(0, 0, 0, false));
			Snapshot s1 = Snapshot.of(be);
			long need = be instanceof PerTickDemand demand && probe.kind() == ProbeKind.MACHINE
					? demand.perTickDemand() : 0L;
			boolean counters = s0.counters() && s1.counters();
			return new ProbeReading(probe.name(), probe.kind(), window, need, s1.consumed() - s0.consumed(),
					s1.in() - s0.in(), s1.amount() - s0.amount(), sourceOut.getOrDefault(probe.pos(), 0L), counters);
		}
	}
}

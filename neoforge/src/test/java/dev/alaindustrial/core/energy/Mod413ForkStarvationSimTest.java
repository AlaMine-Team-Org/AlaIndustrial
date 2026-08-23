package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-413 regression scenarios — the exact player rig from the bug report, driven for thousands of
 * ticks through the REAL {@link EnergyLineDistributor} with a faithful replica of
 * {@link EnergyNetwork#tick()}'s orchestration and {@link EnergyTopologyCache}'s BFS fields (the
 * cache itself needs a live {@code ServerLevel}; the replica mirrors its seeding, flood and ordering
 * rules — if those change, update {@link Sim} in step).
 *
 * <p>Rig (extracted from the world save's region files, 2026-08-13): solar panel → 4-cable line →
 * T-junction → one cable north to macerator A, one cable south to macerator B. A symmetric fork with
 * identical machines. Observed in game before the fix: A charged to 800/800 while B sat at 24/800
 * forever with the whole line saturated behind it.
 *
 * <p>Pins both MOD-413 defects end to end: the greedy {@code EnergyShare.split} remainder (machine A
 * took 100% of a trickle supply) and the stranded-fill reversal (the spur toward the topped-off
 * machine swapped one packet with the junction every tick, freezing the network solid).
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class Mod413ForkStarvationSimTest {

	private static final Direction[] DIRECTIONS = Direction.values();
	private static final double COPPER_LOSS = 0.02;
	private static final long PACKET_CAP = 32;
	private static final long CABLE_BUFFER = 12;
	private static final long MACHINE_BUFFER = 800;
	private static final long MACHINE_MAX_INSERT = 32;
	private static final long SOLAR_BUFFER = 8000;
	private static final long SOLAR_MAX_EXTRACT = 20;
	private static final long SOLAR_EU_PER_TICK = 1;

	private static final class FakeTransactions implements EnergyTransactions {
		final FakeTxn txn = new FakeTxn();

		@Override
		public void runCommitting(Consumer<EnergyPort.Txn> body) {
			body.accept(txn);
		}

		@Override
		public <T> T simulate(Function<EnergyPort.Txn, T> body) {
			return body.apply(txn);
		}
	}

	private static final class FakeTxn implements EnergyPort.Txn {
		@Override
		public void enlist(EnergyPort.Participant participant) {
		}
	}

	private FakeTransactions txns;

	@BeforeEach
	void installFakeTransactions() {
		txns = new FakeTransactions();
		EnergyTransactions.install(txns);
	}

	@AfterEach
	void clearFakeTransactions() {
		EnergyTransactions.install(null);
	}

	/** The full network state + the EnergyNetwork.tick() replica. */
	private static final class Sim {
		final Set<BlockPos> cables = new LinkedHashSet<>();
		final Map<BlockPos, EnergyBuffer> cableBuffers = new LinkedHashMap<>();
		// endpoints
		final BlockPos solarPos = new BlockPos(18, 0, 12);
		final BlockPos machineAPos = new BlockPos(12, 0, 11);
		final BlockPos machineBPos = new BlockPos(12, 0, 13);
		final EnergyBuffer solar = new EnergyBuffer(SOLAR_BUFFER, 0, SOLAR_MAX_EXTRACT, () -> {
		});
		final EnergyBuffer machineA = new EnergyBuffer(MACHINE_BUFFER, MACHINE_MAX_INSERT, 0, () -> {
		});
		final EnergyBuffer machineB = new EnergyBuffer(MACHINE_BUFFER, MACHINE_MAX_INSERT, 0, () -> {
		});

		// topology-cache replica state
		final Map<BlockPos, Integer> producerDistance = new LinkedHashMap<>();
		final Map<BlockPos, Integer> sinkDistance = new LinkedHashMap<>();
		final Map<BlockPos, Integer> machineDistance = new LinkedHashMap<>();
		final Map<BlockPos, Integer> consumerDistance = new LinkedHashMap<>();
		final List<BlockPos> propagationOrder = new ArrayList<>();
		final List<BlockPos> strandedFillOrder = new ArrayList<>();
		boolean sinkMode;
		Set<BlockPos> supplyingProducers = Set.of();
		Set<BlockPos> flowSinkSeeds = Set.of();
		Set<BlockPos> flowMachineSeeds = Set.of();
		int producerCursor;

		Sim() {
			// main line west from the panel: (17..14, z=12), junction (13,12), branches (13,11)/(13,13)
			for (int x = 17; x >= 13; x--) {
				addCable(new BlockPos(x, 0, 12));
			}
			addCable(new BlockPos(13, 0, 11));
			addCable(new BlockPos(13, 0, 13));
		}

		private void addCable(BlockPos pos) {
			cables.add(pos);
			cableBuffers.put(pos, new EnergyBuffer(CABLE_BUFFER, CABLE_BUFFER, CABLE_BUFFER, () -> {
			}));
		}

		// --- EnergyTopologyCache replicas -------------------------------------------------------

		private void floodFrom(Queue<BlockPos> queue, Map<BlockPos, Integer> dist) {
			while (!queue.isEmpty()) {
				BlockPos cur = queue.poll();
				int next = dist.get(cur) + 1;
				for (Direction dir : DIRECTIONS) {
					BlockPos np = cur.relative(dir);
					if (cables.contains(np) && dist.putIfAbsent(np, next) == null) {
						queue.add(np);
					}
				}
			}
		}

		private void computeProducerField() {
			consumerDistance.clear();
			producerDistance.clear();
			// single producer (the panel); seeded from it when supplying, from all producers otherwise —
			// with one producer both filters resolve to the same seed
			Queue<BlockPos> queue = new ArrayDeque<>();
			for (Direction dir : DIRECTIONS) {
				BlockPos cable = solarPos.relative(dir);
				if (cables.contains(cable) && producerDistance.putIfAbsent(cable, 1) == null) {
					queue.add(cable);
				}
			}
			floodFrom(queue, producerDistance);
			for (BlockPos consumer : List.of(machineAPos, machineBPos)) {
				int best = 0;
				for (Direction dir : DIRECTIONS) {
					Integer d = producerDistance.get(consumer.relative(dir));
					if (d != null && (best == 0 || d < best)) {
						best = d;
					}
				}
				if (best > 0) {
					consumerDistance.put(consumer, best);
				}
			}
		}

		private void floodFromSinks(Set<BlockPos> seeds, Map<BlockPos, Integer> dist) {
			dist.clear();
			if (seeds.isEmpty()) {
				return;
			}
			Queue<BlockPos> queue = new ArrayDeque<>();
			for (BlockPos seed : seeds) {
				for (Direction dir : DIRECTIONS) {
					BlockPos cable = seed.relative(dir);
					if (cables.contains(cable) && dist.putIfAbsent(cable, 1) == null) {
						queue.add(cable);
					}
				}
			}
			floodFrom(queue, dist);
		}

		private void rebuildFlowOrder() {
			sinkMode = !sinkDistance.isEmpty();
			propagationOrder.clear();
			if (sinkMode) {
				propagationOrder.addAll(sinkDistance.keySet());
				propagationOrder.sort((a, b) -> Integer.compare(sinkDistance.get(a), sinkDistance.get(b)));
			} else {
				propagationOrder.addAll(producerDistance.keySet());
				propagationOrder.sort((a, b) -> Integer.compare(producerDistance.get(b), producerDistance.get(a)));
			}
			rebuildStrandedOrder();
		}

		private void rebuildStrandedOrder() {
			strandedFillOrder.clear();
			if (!sinkMode || producerDistance.isEmpty()) {
				return;
			}
			Set<BlockPos> reachable = new LinkedHashSet<>();
			Queue<BlockPos> queue = new ArrayDeque<>();
			for (Map.Entry<BlockPos, Integer> entry : producerDistance.entrySet()) {
				if (entry.getValue() == 1 && sinkDistance.containsKey(entry.getKey())
						&& reachable.add(entry.getKey())) {
					queue.add(entry.getKey());
				}
			}
			while (!queue.isEmpty()) {
				BlockPos cur = queue.poll();
				int potential = sinkDistance.get(cur);
				for (Direction dir : DIRECTIONS) {
					BlockPos np = cur.relative(dir);
					Integer next = sinkDistance.get(np);
					if (next != null && next < potential && reachable.add(np)) {
						queue.add(np);
					}
				}
			}
			for (BlockPos cable : cables) {
				if (!reachable.contains(cable) && producerDistance.containsKey(cable)) {
					strandedFillOrder.add(cable);
				}
			}
			strandedFillOrder.sort((a, b) -> {
				int byDistance = Integer.compare(producerDistance.get(b), producerDistance.get(a));
				if (byDistance != 0) {
					return byDistance;
				}
				return PosOrder.compare(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
			});
		}

		private void updateLiveEndpoints(Set<BlockPos> supplying, Set<BlockPos> sinkSeeds,
				Set<BlockPos> machineSeeds) {
			boolean changed = false;
			if (!supplyingProducers.equals(supplying)) {
				supplyingProducers = new LinkedHashSet<>(supplying);
				computeProducerField();
				changed = true;
			}
			if (!flowSinkSeeds.equals(sinkSeeds)) {
				flowSinkSeeds = new LinkedHashSet<>(sinkSeeds);
				floodFromSinks(flowSinkSeeds, sinkDistance);
				changed = true;
			}
			if (!flowMachineSeeds.equals(machineSeeds)) {
				flowMachineSeeds = new LinkedHashSet<>(machineSeeds);
				floodFromSinks(flowMachineSeeds, machineDistance);
			}
			if (changed) {
				rebuildFlowOrder();
			}
		}

		// --- EnergyNetwork.tick() replica -------------------------------------------------------

		long tick(EnergyPort.Txn txn) {
			// generator produces first (its own BE tick), then the network moves EU
			solar.produceInternal(SOLAR_EU_PER_TICK);

			long genSupply = Math.min(solar.getAmount(), SOLAR_MAX_EXTRACT);
			Set<BlockPos> supplying = genSupply > 0 ? Set.of(solarPos) : Set.of();
			List<EnergyLineDistributor.LiveProducer> generators =
					List.of(new EnergyLineDistributor.LiveProducer(solarPos, solar));

			// consumers in endpoint-list order (geometric: A's cable sorts before B's)
			List<EnergyLineDistributor.LiveConsumer> machines = new ArrayList<>();
			long machineDemand = 0;
			for (var pair : List.of(Map.entry(machineAPos, machineA), Map.entry(machineBPos, machineB))) {
				long room = Math.min(pair.getValue().capacity - pair.getValue().getAmount(), MACHINE_MAX_INSERT);
				if (room > 0) {
					machines.add(new EnergyLineDistributor.LiveConsumer(pair.getKey(), pair.getValue(), room));
					machineDemand += room;
				}
			}

			Set<BlockPos> sinkSeeds = new LinkedHashSet<>();
			for (EnergyLineDistributor.LiveConsumer c : machines) {
				sinkSeeds.add(c.pos());
			}
			updateLiveEndpoints(supplying, sinkSeeds, sinkSeeds);

			boolean hasSupply = !supplying.isEmpty();
			EnergyLineDistributor distributor = new EnergyLineDistributor(
					cables::contains,
					cableBuffers::get,
					pos -> consumerDistance.getOrDefault(pos, 0),
					pos -> sinkMode ? sinkDistance.get(pos)
							: (producerDistance.get(pos) == null ? null : -producerDistance.get(pos)),
					machineDistance::get,
					propagationOrder,
					(p, d) -> true,
					(p, d) -> true,
					hasSupply ? strandedFillOrder : List.of(),
					producerDistance::get);

			long moved = distributor.serveConsumersFromLine(machines, PACKET_CAP, COPPER_LOSS, txn, producerCursor);
			distributor.chargeAndPropagateLine(generators, List.of(), machineDemand, genSupply,
					PACKET_CAP, txn, producerCursor);
			producerCursor = (producerCursor + 1) & Integer.MAX_VALUE;
			return moved;
		}

		String cableState() {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<BlockPos, EnergyBuffer> e : cableBuffers.entrySet()) {
				BlockPos p = e.getKey();
				sb.append(String.format("(%d,%d)=%d ", p.getX(), p.getZ(), e.getValue().getAmount()));
			}
			return sb.toString();
		}
	}

	@Test
	void playerRig_resumeFromSavedWorldState_feedsB() {
		// Exact frozen state from the world save: A full, B stuck at 24, main line + north branch full,
		// junction and south branch empty, solar accumulating.
		Sim sim = new Sim();
		sim.machineA.setAmountUntracked(800);
		sim.machineB.setAmountUntracked(24);
		sim.solar.setAmountUntracked(2758);
		for (Map.Entry<BlockPos, EnergyBuffer> e : sim.cableBuffers.entrySet()) {
			BlockPos p = e.getKey();
			boolean southBranchOrJunction = p.getZ() == 13 || (p.getX() == 13 && p.getZ() == 12);
			e.getValue().setAmountUntracked(southBranchOrJunction ? 0 : 12);
		}
		for (int t = 1; t <= 200; t++) {
			sim.tick(txns.txn);
		}
		assertTrue(sim.machineB.getAmount() > 24 + 50,
				"B must keep charging once A is full; B=" + sim.machineB.getAmount());
	}

	@Test
	void playerRig_workingMachines_starvesBForever() {
		// NotYuno's report: machines under load (each drains 2 EU/t for its work while powered).
		// A working machine is never full, so it never leaves the seed set.
		Sim sim = new Sim();
		long aWorked = 0;
		long bWorked = 0;
		for (int t = 1; t <= 4000; t++) {
			sim.tick(txns.txn);
			// crusher work: drain 2 EU/t when the buffer has it (mirrors machineEuPerTick)
			aWorked += sim.machineA.drainInternal(2) > 0 ? 1 : 0;
			bWorked += sim.machineB.drainInternal(2) > 0 ? 1 : 0;
		}
		assertTrue(bWorked > aWorked / 4,
				"under load, B never works: A=" + aWorked + " ticks, B=" + bWorked + " ticks");
	}

	@Test
	void playerRig_bothMachinesChargeAtAComparableRate() {
		Sim sim = new Sim();
		for (int t = 1; t <= 4000; t++) {
			sim.tick(txns.txn);
		}
		long a = sim.machineA.getAmount();
		long b = sim.machineB.getAmount();
		// A symmetric fork with identical machines must not starve one side: after 4000 ticks of
		// 1 EU/t supply, both machines should hold a comparable share. Pre-MOD-413 this was
		// A=744, B=0 at tick 750 — machine A took 100% of the trickle until it was full.
		assertTrue(b >= a / 4,
				"symmetric fork starved machine B: A=" + a + " B=" + b);
	}
}

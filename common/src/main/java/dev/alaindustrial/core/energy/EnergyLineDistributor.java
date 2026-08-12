package dev.alaindustrial.core.energy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The per-tick distribution kernel of an {@link EnergyNetwork}. Extracted from {@code EnergyNetwork}
 * so the segment-to-segment flow math (MOD-070) sits in a small, focused class rather than being
 * scattered across ~300 lines of the 760-line façade. The pure delivery/loss arithmetic itself
 * already lives in {@link EnergyShare}/{@link EnergyServe}; this class orchestrates WHO pulls from
 * WHERE, in what order, at what time.
 *
 * <p>A new instance is constructed per {@link EnergyNetwork#tick()} pass and bound to that network's
 * topology + buffer lookups. The two-state-mutating fields that survive across ticks (round-robin
 * cursor, line-full flag, telemetry) live in {@link EnergyNetwork}; the kernel itself is stateless
 * beyond what the constructor captures.
 *
 * <p>MC-coupled: reads the live cable buffer at a position through a {@link Function} supplied by
 * the façade (the actual {@code level.getBlockEntity} stays there). This keeps the kernel close to
 * the runtime path it has always run on, while letting the topology/BFS half live in
 * {@link EnergyTopologyCache}.
 *
 * <p>Package-private — part of the {@code EnergyNetwork} implementation; not a public API.
 */
final class EnergyLineDistributor {
	/** A live producer endpoint resolved for this tick: its pos (for self-churn checks) and storage. */
	record LiveProducer(BlockPos pos, EnergyPort storage) {
	}

	/** A live consumer endpoint resolved for this tick: its pos, storage and free room. */
	record LiveConsumer(BlockPos pos, EnergyPort storage, long room) {
	}

	private static final Direction[] DIRECTIONS = Direction.values();

	/**
	 * How much more of a forked buffer goes to the branch that leads toward a waiting machine (MOD-254 /
	 * MOD-009). A weight, not a veto: the storage-ward branch keeps a guaranteed share, so a Battery Box
	 * behind the fork still charges while a machine in front of it is fed first. Two is the smallest value
	 * that is visibly a preference at all; a larger one would starve the far side of long buses.
	 */
	private static final long MACHINE_WARD_WEIGHT = 2L;

	private final Predicate<BlockPos> isCable;
	private final Function<BlockPos, EnergyBuffer> cableBufferAt;
	private final Function<BlockPos, Integer> consumerDistance;
	private final Function<BlockPos, Integer> flowPotential;
	/**
	 * Distance to the nearest waiting machine, or {@code null} when none is waiting (MOD-254). Used ONLY to
	 * weigh a fork: of two cables entitled to pull from the same buffer, the one whose machine distance is
	 * strictly lower is carrying the unit toward a machine and gets the larger share. It never decides
	 * whether a cable may be filled at all — that stays {@link #flowPotential}, which is seeded from every
	 * waiting endpoint (narrowing it is exactly the reachability defect MOD-252 fixed).
	 */
	private final Function<BlockPos, Integer> machinePotential;
	private final List<BlockPos> propagationOrder;
	/**
	 * Can the endpoint at this position DRAW energy through the face pointing in this direction (MOD-255)?
	 * The kernel finds the cables an endpoint touches by pure adjacency, which says nothing about whether
	 * the touched face is allowed to move EU that way — a Battery Box's OUT face touches a cable exactly
	 * like its IN face does. Without the gate a dual-role box drinks straight back out of the cable it is
	 * discharging into (R-NRG-03 is enforced on the direct path by {@link DirectAdjacencyDistributor}, and
	 * this is its cabled twin).
	 */
	private final BiPredicate<BlockPos, Direction> canDrawFace;
	/** Can the source at this position FEED energy through the face pointing this way (MOD-255)? Mirror of {@link #canDrawFace}. */
	private final BiPredicate<BlockPos, Direction> canFeedFace;

	/**
	 * The cables the downhill rule cannot reach, farthest from the source first, and the field that gives
	 * them a direction (MOD-318). Empty by default: a caller that has no stranded segments — every fixture
	 * in the L1.5 suite, and any network in fallback mode — gets exactly the pre-MOD-318 behaviour.
	 */
	private final List<BlockPos> strandedOrder;
	private final Function<BlockPos, Integer> strandedProducerDistance;

	EnergyLineDistributor(
			EnergyTopologyCache topology,
			Function<BlockPos, EnergyBuffer> cableBufferAt,
			Function<BlockPos, Integer> consumerDistance,
			Function<BlockPos, Integer> flowPotential,
			Function<BlockPos, Integer> machinePotential,
			List<BlockPos> propagationOrder,
			BiPredicate<BlockPos, Direction> canDrawFace,
			BiPredicate<BlockPos, Direction> canFeedFace,
			List<BlockPos> strandedOrder,
			Function<BlockPos, Integer> strandedProducerDistance) {
		this(topology::contains, cableBufferAt, consumerDistance, flowPotential, machinePotential,
				propagationOrder, canDrawFace, canFeedFace, strandedOrder, strandedProducerDistance);
	}

	/**
	 * Predicate-based constructor for tests and other callers that want to drive the kernel against a
	 * synthetic cable graph without spinning up a full {@link EnergyTopologyCache} (which requires a
	 * live {@link ServerLevel}). All behavioural methods read topology only through the captured
	 * {@code isCable} predicate, so a hand-rolled {@code Set::contains} over fake positions exercises
	 * the same code path.
	 */
	EnergyLineDistributor(
			Predicate<BlockPos> isCable,
			Function<BlockPos, EnergyBuffer> cableBufferAt,
			Function<BlockPos, Integer> consumerDistance,
			Function<BlockPos, Integer> flowPotential,
			Function<BlockPos, Integer> machinePotential,
			List<BlockPos> propagationOrder,
			BiPredicate<BlockPos, Direction> canDrawFace,
			BiPredicate<BlockPos, Direction> canFeedFace,
			List<BlockPos> strandedOrder,
			Function<BlockPos, Integer> strandedProducerDistance) {
		this.strandedOrder = strandedOrder;
		this.strandedProducerDistance = strandedProducerDistance;
		this.isCable = isCable;
		this.cableBufferAt = cableBufferAt;
		this.consumerDistance = consumerDistance;
		this.flowPotential = flowPotential;
		this.machinePotential = machinePotential;
		this.propagationOrder = propagationOrder;
		this.canDrawFace = canDrawFace;
		this.canFeedFace = canFeedFace;
	}

	/**
	 * How much EU storage sources are allowed to discharge into the line this tick <em>as backup power</em>:
	 * the machine demand generators fall short of, never less than zero (MOD-070 backup-power rule).
	 * Shared with {@link EnergyNetwork#tick()}, which needs the same number to decide whether a dual-role
	 * storage node is discharging — and therefore must not also be served from the line this tick
	 * (MOD-255). Kept in one place because two copies of this expression would drift and silently
	 * decouple the two decisions.
	 *
	 * <p>Since MOD-314 this is no longer the ONLY reason storage discharges: when this budget is zero
	 * (machines absent or already covered) a fuller store may still feed an emptier one through the
	 * cascade. The two are mutually exclusive by construction — see
	 * {@link #chargeAndPropagateLine(List, List, long, long, long, EnergyPort.Txn, int, Map)} — so this
	 * number still answers "is backup power flowing", just not "is any storage discharging".
	 */
	static long storageBudget(long machineDemand, long genSupply) {
		return Math.max(0L, machineDemand - genSupply);
	}

	/**
	 * Serve a class of consumers (machines, or storage sinks) from the network's cable buffers (the
	 * "line"). Reuses {@link #serveClass} with the live cable buffers as the supply pool, so the
	 * proportional split, tier packet cap and — crucially — the per-consumer MOD-021 distance loss are
	 * exactly the same math as the old direct path; only the <em>source</em> of the EU is the line.
	 * Returns the EU delivered. Both machines and storage sinks route through here (MOD-070): all
	 * transfer flows through the wires, so any active cable shows its buffered energy.
	 *
	 * @param lossPerBlock the network's resistive loss per cable block, taken from its strongest cable
	 *     grade (MOD-219). Passed in rather than read from {@link dev.alaindustrial.Config} so the rate can
	 *     differ per network — before that it was hardcoded to copper's knob for every cable in the game.
	 * @param producerCursor the round-robin rotation offset for {@link #serveClass}'s pull across the
	 *     line supply pool; bounded by the pool size, re-applied modulo inside.
	 */
	long serveConsumersFromLine(List<LiveConsumer> consumers, long packetCap, double lossPerBlock,
			EnergyPort.Txn tx, int producerCursor) {
		if (consumers.isEmpty()) {
			return 0L;
		}
		// Locality: a consumer draws only from the cable segments it physically touches, NOT the whole
		// network. This is what forces energy to propagate along the line to reach a distant consumer
		// (so intermediate cables genuinely buffer it) instead of teleporting from anywhere in the graph.
		// serveClass still splits a shared touched-cable pool proportionally, so two consumers on one cable
		// share it fairly, and it applies the unchanged per-consumer distance loss (MOD-021).
		// MOD-255: adjacency alone is not permission. A face that cannot accept EU (a Battery Box's OUT
		// face, a machine's inert FACING front) must not put the cable behind it into the supply pool, or a
		// dual-role storage node sucks back the very EU it discharged into that cable a moment earlier and
		// the line never advances. Tested per direction rather than off the endpoint's cached side because
		// the endpoint cache keeps only ONE side per position — a machine with two working input faces
		// would otherwise lose half its draw.
		Set<BlockPos> touched = new LinkedHashSet<>();
		for (LiveConsumer m : consumers) {
			for (Direction dir : DIRECTIONS) {
				BlockPos np = m.pos().relative(dir);
				if (isCable.test(np) && canDrawFace.test(m.pos(), dir)) {
					touched.add(np);
				}
			}
		}
		List<LiveProducer> lineSupply = new ArrayList<>();
		long[] lineTotal = {0L};
		for (BlockPos pos : touched) {
			EnergyBuffer buf = cableBufferAt.apply(pos);
			if (buf != null && buf.getAmount() > 0) {
				lineSupply.add(new LiveProducer(pos, buf));
				lineTotal[0] += buf.getAmount();
			}
		}
		if (lineSupply.isEmpty()) {
			return 0L;
		}
		return serveClass(consumers, lineSupply, lineTotal, packetCap, lossPerBlock, tx, producerCursor);
	}

	/**
	 * Stage 2 of {@link EnergyNetwork#tick()}: push existing line energy one hop toward consumers,
	 * then charge the producer-adjacent cables from producers. Returns the EU actually drawn from
	 * producers into the line (so the caller can dock it from the supply left for storage sinks).
	 * Loss-free between cables — the resistive cost is charged once, per consumer, on delivery
	 * (stage 1); charging it again per hop would break the MOD-021 numbers ({@code floor(0.02·1)=0}
	 * per hop ≠ {@code floor(0.02·32·10)=6}).
	 *
	 * @param rotation the network's per-tick rotation offset, forwarded to {@link #chargeLineFrom} so the
	 *     source sweep does not start at the same source (and the same face) every tick, and to
	 *     {@link #propagateLineOneHop} so the indivisible remainder of a forked buffer does not land on the
	 *     same branch every tick — see MOD-254.
	 */
	/**
	 * Cascade-free overload: the line stage as it behaved before MOD-314. Kept because most callers —
	 * every scenario in the L1.5 distributor suite that is about generators, machines, loss or
	 * propagation — have no opinion about the storage→storage cascade, and threading an empty map
	 * through them would add noise without adding coverage. Scenarios that ARE about the cascade pass
	 * allowances explicitly.
	 */
	void chargeAndPropagateLine(List<LiveProducer> generators, List<LiveProducer> storageSources,
			long machineDemand, long genSupply, long packetCap, EnergyPort.Txn tx, int rotation) {
		chargeAndPropagateLine(generators, storageSources, machineDemand, genSupply, packetCap, tx, rotation,
				null);
	}

	void chargeAndPropagateLine(List<LiveProducer> generators, List<LiveProducer> storageSources,
			long machineDemand, long genSupply, long packetCap, EnergyPort.Txn tx, int rotation,
			Map<BlockPos, Long> cascadeAllowances) {
		chargeAndPropagateLine(generators, storageSources, machineDemand, genSupply, packetCap, tx,
				rotation, cascadeAllowances, java.util.Map.of());
	}

	/**
	 * Full form with the MOD-353 feed stage.
	 *
	 * <p><b>Storage discharges through exactly one of three stages per tick, never two.</b> They are
	 * ordered by how strong the claim on the energy is, and each one {@code return}s so the next cannot
	 * also run:
	 * <ol>
	 *   <li><b>backup power</b> — machines are asking and generators fall short;</li>
	 *   <li><b>cascade</b> (MOD-314) — no machine demand, but another store is proportionally emptier;</li>
	 *   <li><b>feed</b> (MOD-353) — neither of the above, and a non-cascade sink (Teleporter, Charging
	 *       Station) is waiting.</li>
	 * </ol>
	 *
	 * <p>The exclusivity is not stylistic. Each stage starts a fresh per-source {@code fromThis} budget
	 * inside {@link #chargeLineFrom}, so two stages in one tick would let a single store inject
	 * 2 × {@code packetCap} and quietly break the tier ceiling this class documents. The caller
	 * independently guarantees the same thing by only computing the later allowances when the earlier
	 * stages are closed; stating it in both places is deliberate, because a future fourth stage will be
	 * added at exactly one of them.
	 */
	void chargeAndPropagateLine(List<LiveProducer> generators, List<LiveProducer> storageSources,
			long machineDemand, long genSupply, long packetCap, EnergyPort.Txn tx, int rotation,
			Map<BlockPos, Long> cascadeAllowances, Map<BlockPos, Long> feedAllowances) {
		propagateLineOneHop(packetCap, tx, rotation);
		// MOD-318 — top up the segments the downhill rule cannot reach. THE POSITION OF THIS CALL IS
		// LOAD-BEARING: it must sit after the sweep and BEFORE the sources recharge the line.
		//
		// The pass takes a saturated cable's whole packet, so the cable it drew from ends up with room —
		// and one tick later that is indistinguishable from room made by a machine drinking. Run it after
		// chargeLineFrom and the spur hands its charge straight back down the gradient on the next sweep,
		// takes it again on the next fill, and oscillates one packet forever without ever advancing past
		// the first stranded segment. Run it here and the sources close the tick by refilling the corridor,
		// so the next sweep finds no room, the spur keeps what it was given, and the fill front moves on.
		// Guarded by fillStrandedOneHop_energizesTheSpurTheDownhillRuleAbandoned, which failed on exactly
		// that oscillation while this call sat one line lower.
		fillStrandedOneHop(strandedOrder, strandedProducerDistance, packetCap, tx);
		// Generators fill the line freely (free energy → inertia); only their draw is docked from the
		// supply left for storage sinks.
		chargeLineFrom(generators, packetCap, Long.MAX_VALUE, tx, rotation);
		// Storage discharges into the line ONLY to cover the machine demand generators fall short of
		// (backup power). When generators already cover it, storageBudget is 0 and no battery bleeds into
		// the wires — this closes the dual-role wash the audit flagged.
		long storageBudget = storageBudget(machineDemand, genSupply);
		if (storageBudget > 0 && !storageSources.isEmpty()) {
			chargeLineFrom(storageSources, packetCap, storageBudget, tx, rotation);
			// Backup power and cascade are mutually exclusive, and the caller has already guaranteed it
			// (EnergyNetwork only computes allowances when this budget is 0). Returning here states the
			// same invariant at the point it protects: two storage stages in one tick would each start a
			// fresh per-source `fromThis`, letting one battery inject 2 × packetCap and quietly break the
			// tier ceiling this class documents on chargeLineFrom.
			return;
		}
		// MOD-314: the cascade — a fuller store topping up an emptier one once machines are satisfied.
		// Per-donor budgets, never a shared pool: eligibility is decided per (donor, sink) pair, so a
		// single scalar would let a donor that is NOT proportionally fuller spend a budget opened by one
		// that is — washing energy backwards while looking like a cascade. Iterating `storageSources`
		// rather than the map keeps the order the topology fixed (MOD-304).
		if (cascadeAllowances != null && !cascadeAllowances.isEmpty()) {
			for (LiveProducer donor : storageSources) {
				Long allowance = cascadeAllowances.get(donor.pos());
				if (allowance != null && allowance > 0) {
					chargeLineFrom(List.of(donor), packetCap, allowance, tx, rotation);
				}
			}
			return;
		}
		// MOD-353: stage three — a store trickling into a sink the cascade refuses. Reached only when the
		// two stages above moved nothing, so the "one source ≤ packetCap per tick" invariant holds.
		// Per-donor budgets for the same reason the cascade uses them: a single shared scalar would let a
		// donor below its own reserve spend an allowance opened by one that is above it.
		if (feedAllowances == null || feedAllowances.isEmpty()) {
			return;
		}
		for (LiveProducer donor : storageSources) {
			Long allowance = feedAllowances.get(donor.pos());
			if (allowance != null && allowance > 0) {
				chargeLineFrom(List.of(donor), packetCap, allowance, tx, rotation);
			}
		}
	}

	/**
	 * Charge the source-adjacent cables from {@code sources}. Each source injects at most {@code packetCap}
	 * EU total this tick (the tier throughput limit — NOT {@code packetCap × adjacent-cables}, which the
	 * audit flagged as multiplying the per-source cap), and the whole call stops once it has drawn
	 * {@code totalBudget} EU. Returns the EU actually drawn. Loss-free (the resistive cost is charged
	 * once on delivery, stage 1).
	 *
	 * <p>MOD-255: only faces that can actually EMIT feed a cable. The source's port is resolved once, for
	 * the endpoint's cached side, so without this gate a Battery Box would pour EU out of its INPUT face
	 * into the cable there — energy leaving through the face the player wired as an input, straight into
	 * the half of the line it is supposed to drink from.
	 *
	 * <p>MOD-254: both sweeps start at {@code rotation} rather than at index 0. Neither loop is a fair
	 * split — the first source to reach a cable takes all the room it has, and the first feedable face of a
	 * source takes the whole packet — so a fixed start point is a permanent winner. On a saturated line
	 * (the cable next to two sources has room for one packet) the later source in the list injected nothing
	 * on EVERY tick and sat at a full buffer forever, which is the same starvation MOD-252 fixed on the
	 * propagation side. Rotating keeps every source's long-run share equal without capping how many of them
	 * may feed in one tick: the loop still visits ALL sources, so N sources on one cable still inject N
	 * packets in a single tick.
	 */
	private long chargeLineFrom(List<LiveProducer> sources, long packetCap, long totalBudget,
			EnergyPort.Txn tx, int rotation) {
		int sourceCount = sources.size();
		if (sourceCount == 0) {
			return 0;
		}
		long drawn = 0;
		for (int s = 0; s < sourceCount; s++) {
			if (drawn >= totalBudget) {
				break;
			}
			LiveProducer prod = sources.get(Math.floorMod(rotation + s, sourceCount));
			long fromThis = 0; // per-source throughput this tick, capped at packetCap
			for (int d = 0; d < DIRECTIONS.length; d++) {
				if (fromThis >= packetCap || drawn >= totalBudget) {
					break;
				}
				Direction dir = DIRECTIONS[Math.floorMod(rotation + d, DIRECTIONS.length)];
				BlockPos np = prod.pos().relative(dir);
				if (!isCable.test(np) || !canFeedFace.test(prod.pos(), dir)) {
					continue;
				}
				EnergyBuffer buf = cableBufferAt.apply(np);
				if (buf == null) {
					continue;
				}
				long room = buf.getCapacity() - buf.getAmount();
				if (room <= 0) {
					continue;
				}
				long want = Math.min(Math.min(room, packetCap - fromThis), totalBudget - drawn);
				long got = prod.storage().extract(want, tx);
				if (got > 0) {
					buf.insert(got, tx);
					fromThis += got;
					drawn += got;
				}
			}
		}
		return drawn;
	}

	/**
	 * Move line energy one cable-hop DOWN the flow potential: every cable pulls from the neighbours whose
	 * potential is strictly higher. Visiting the cables in <em>ascending</em> potential
	 * ({@code propagationOrder}) makes each unit advance at most one hop per tick — the "fill front" that
	 * gives the line its inertia. Bounded by the tier throughput ({@code packetCap}) and each cable's free
	 * space.
	 *
	 * <p>The potential comes from {@link EnergyTopologyCache#flowPotentialOrNull}: distance to the nearest
	 * sink that wants energy (MOD-252) or, when nobody is waiting, the negated distance to the nearest
	 * supplying producer (MOD-070/MOD-214 fallback). One comparison serves both, so the fallback path is
	 * literally the same code that ran before the field was reversed.
	 *
	 * <p>The strictness of {@code <} below is load-bearing: two cables at equal potential must not exchange,
	 * or a unit could travel two hops in one sweep. That is why seams (local maxima) exist at all — MOD-252
	 * is about moving them from the sources, where a maximum can only accumulate, to the sinks, where a
	 * maximum simply drains toward demand on both sides.
	 *
	 * <p><b>MOD-254 — the sweep is DONOR-centric and its buffer is SHARED.</b> It used to run over
	 * receivers, each one taking {@code min(free, donor.getAmount(), packetCap)} from every upstream neighbour it
	 * found: whichever receiver the iteration reached first emptied the donor and the rest got nothing. At a
	 * seam that is fatal rather than merely unfair — the seam on a source's own cable is exactly where the
	 * line forks toward the demand on either side, so one branch received the source's entire output and the
	 * other stayed dead at 0 EU forever. Which branch won was decided by {@code HashMap} bucket order over
	 * absolute {@link BlockPos}, i.e. by where in the world the player happened to build: the same base
	 * worked or starved depending on its coordinates.
	 *
	 * <p>Now each donor collects every neighbour entitled to pull from it and splits its buffer between
	 * them (see {@link #shareAmongClaimants}). Iterating donors in ASCENDING potential is the same schedule
	 * the receiver sweep ran on — adjacent cables of a BFS field differ by at most one step, so "lowest
	 * first" is the same bucket-brigade that gives the line its fill front, and a claimant is always visited
	 * as a donor BEFORE the donor that feeds it, which is what still bounds every unit to one hop per tick.
	 * A donor with a single claimant hands over exactly what it handed over before, so a straight line is
	 * bit-for-bit unchanged.
	 *
	 * @param rotation the network's per-tick rotation offset, handed to {@link #shareAmongClaimants} for the
	 *     indivisible remainder of the split. A proportional share alone is not enough: when the donor holds
	 *     less than one EU per claimant the WHOLE buffer is remainder, and awarding it in a fixed order gives
	 *     one branch everything on every tick — the very starvation this sweep exists to remove.
	 */
	private void propagateLineOneHop(long packetCap, EnergyPort.Txn tx, int rotation) {
		if (propagationOrder.isEmpty()) {
			return;
		}
		EnergyBuffer[] claimants = new EnergyBuffer[DIRECTIONS.length];
		long[] free = new long[DIRECTIONS.length];
		boolean[] machineWard = new boolean[DIRECTIONS.length];
		for (BlockPos pos : propagationOrder) {
			EnergyBuffer donor = cableBufferAt.apply(pos);
			if (donor == null || donor.getAmount() <= 0) {
				continue;
			}
			Integer pFromBoxed = flowPotential.apply(pos);
			if (pFromBoxed == null) {
				continue;
			}
			int pFrom = pFromBoxed;
			Integer machineFrom = machinePotential.apply(pos);
			int n = 0;
			for (Direction dir : DIRECTIONS) {
				BlockPos np = pos.relative(dir);
				Integer pTo = flowPotential.apply(np);
				if (pTo == null || pTo >= pFrom) {
					continue; // only push strictly downstream (lower potential = closer to demand)
				}
				EnergyBuffer to = cableBufferAt.apply(np);
				if (to == null) {
					continue;
				}
				// The claimant's room is capped by the tier packet here, so the split below can treat it as
				// the claimant's whole appetite and never hand out more than one packet per hop.
				long room = Math.min(to.getCapacity() - to.getAmount(), packetCap);
				if (room <= 0) {
					continue;
				}
				Integer machineTo = machinePotential.apply(np);
				claimants[n] = to;
				free[n] = room;
				// MOD-009 geometrically: this claimant carries the unit CLOSER to a waiting machine than the
				// donor is, so it outweighs a claimant that only leads to storage. Serving machines before
				// sinks cannot help if the EU never reaches the machine's half of the wire in the first place.
				machineWard[n] = machineFrom != null && machineTo != null && machineTo < machineFrom;
				n++;
			}
			if (n == 0) {
				continue;
			}
			long[] give = shareAmongClaimants(donor.getAmount(), free, machineWard, n, rotation);
			for (int i = 0; i < n; i++) {
				if (give[i] > 0) {
					donor.extract(give[i], tx);
					claimants[i].insert(give[i], tx);
				}
			}
		}
	}

	/**
	 * Split one cable's buffer between the {@code n} neighbours entitled to pull from it (MOD-254).
	 *
	 * <p>Proportional to each claimant's free room, weighted {@link #MACHINE_WARD_WEIGHT}× for a claimant
	 * that carries the unit toward a waiting machine, and never above that claimant's own room. What integer
	 * division leaves over is handed out machine-ward claimants first, so the last EU of an odd buffer is
	 * never stranded (the failure mode a bare proportional split has when every share floors to zero).
	 *
	 * <p><b>The remainder is the whole game at low totals.</b> Review finding: dealing it greedily in a fixed
	 * order re-created the original 100 %:0 % starvation instead of merely rounding unfairly. A donor holding
	 * fewer EU than it has claimants floors every proportional share to zero, so the ENTIRE buffer is
	 * remainder — and one solar panel injecting {@code solarEuPerTick = 1} into the middle of a symmetric bus
	 * is exactly that case. With a fixed scan the first {@link Direction} constant always won and the other
	 * branch sat at 0 EU forever; rotating the base 90° swapped which machine starved, i.e. the build-orientation
	 * dependence MOD-254 exists to remove, surviving in the rounding path. So the remainder is dealt ONE EU at a
	 * time, round-robin from a per-tick rotating start: no claimant can take a second EU while another that
	 * still has room has taken none, and which claimant is served first advances every tick. Deterministic —
	 * the offset is the network's tick-driven cursor, never a random source.
	 *
	 * <p>Degenerate on purpose: with a single claimant the result is {@code min(total, free[0])}, which is
	 * exactly what the pre-MOD-254 sweep moved. Straight lines — every numeric expectation in the L1 and
	 * gametest suites — are therefore untouched; only a genuine fork behaves differently.
	 *
	 * <p>Total handed out is {@code ≤ total}, so the caller can extract each share from the donor without
	 * ever overdrawing it, and no EU is created or destroyed.
	 */
	private static long[] shareAmongClaimants(long total, long[] free, boolean[] machineWard, int n,
			int rotation) {
		long[] give = new long[n];
		if (total <= 0) {
			return give;
		}
		long weighted = 0;
		for (int i = 0; i < n; i++) {
			weighted += free[i] * weightOf(machineWard[i]);
		}
		if (weighted <= 0) {
			return give;
		}
		long assigned = 0;
		for (int i = 0; i < n; i++) {
			long s = Math.floorDiv(total * free[i] * weightOf(machineWard[i]), weighted);
			s = Math.min(s, free[i]);
			give[i] = s;
			assigned += s;
		}
		long remainder = total - assigned;
		// Two passes so a spare EU lands on the machine-ward branch before the storage-ward one.
		for (int pass = 0; pass < 2 && remainder > 0; pass++) {
			boolean wantMachineWard = pass == 0;
			// One EU per claimant per sweep, starting at the rotating offset. A sweep that hands out nothing
			// means every claimant in this pass is full, which is the only way out of the loop besides an
			// exhausted remainder — so it always terminates.
			boolean progress = true;
			while (remainder > 0 && progress) {
				progress = false;
				for (int k = 0; k < n && remainder > 0; k++) {
					int i = Math.floorMod(rotation + k, n);
					if (machineWard[i] != wantMachineWard || give[i] >= free[i]) {
						continue;
					}
					give[i]++;
					remainder--;
					progress = true;
				}
			}
		}
		return give;
	}

	private static long weightOf(boolean machineWard) {
		return machineWard ? MACHINE_WARD_WEIGHT : 1L;
	}

	/**
	 * Top up the cables {@link #propagateLineOneHop} can never reach, one hop per tick (MOD-318).
	 *
	 * <p>The downhill rule fills a cable only if it lies on a strictly descending path from a producer's
	 * own cable to waiting demand. A spur with no consumer on it, the run past the last consumer, the
	 * stretch behind the generators — all of it sits ABOVE everything that could feed it and reads 0 EU
	 * for as long as anything on the network wants energy, while the far end of the same line is visibly
	 * powered. This pass gives those segments the only well-defined direction they have left: outward from
	 * the source, along {@code producerDistance}, which is exactly the pre-MOD-252 fill restricted to the
	 * cables MOD-252 stopped filling.
	 *
	 * <p><b>Receiver-centric, and surplus-only.</b> A stranded cable pulls, and only from a neighbour that
	 * is brim-full. A donor below its capacity is still moving energy toward demand, and taking from it
	 * would make this pass compete with delivery rather than mop up after it; requiring a full donor makes
	 * "machines first" hold by construction instead of by tuning. It also makes the pass mutually exclusive
	 * with the sweep above on any given pair — that one moves energy out of a stranded cable when the
	 * corridor has ROOM, this one moves energy in when the corridor is FULL, and a cable cannot be both.
	 * So when demand rises the spur drains back toward the machines on its own, and the wire behaves as
	 * the buffer MOD-070 made it.
	 *
	 * <p>Loss-free, like every other cable-to-cable move: the resistive cost is charged once, per consumer,
	 * on delivery.
	 *
	 * @param strandedOrder the unreachable cables, farthest from the source first, so a claimant is always
	 *     visited before the cable that feeds it and no unit advances two hops in one tick.
	 * @param producerDistance BFS distance to the nearest supplying producer; {@code null} off the field.
	 *     Only cables carry an entry, so a non-null answer doubles as the "is a cable" test.
	 */
	void fillStrandedOneHop(List<BlockPos> strandedOrder, Function<BlockPos, Integer> producerDistance,
			long packetCap, EnergyPort.Txn tx) {
		for (BlockPos pos : strandedOrder) {
			EnergyBuffer to = cableBufferAt.apply(pos);
			if (to == null) {
				continue;
			}
			// Capped by the tier packet for the same reason the sweep above caps it: one hop, one packet.
			long room = Math.min(to.getCapacity() - to.getAmount(), packetCap);
			Integer toDistance = producerDistance.apply(pos);
			if (room <= 0 || toDistance == null) {
				continue;
			}
			for (Direction dir : DIRECTIONS) {
				if (room <= 0) {
					break;
				}
				BlockPos np = pos.relative(dir);
				Integer fromDistance = producerDistance.apply(np);
				if (fromDistance == null || fromDistance >= toDistance) {
					continue; // only pull from strictly closer to the source
				}
				EnergyBuffer from = cableBufferAt.apply(np);
				if (from == null || from.getAmount() < from.getCapacity()) {
					continue; // surplus only — see the contract above
				}
				long got = from.extract(Math.min(room, from.getAmount()), tx);
				if (got <= 0) {
					continue;
				}
				long inserted = to.insert(got, tx);
				if (inserted < got) {
					// Hand back what the claimant would not take. The sweep above can assume insert()
					// swallows everything because a cable's capacity sits below its own maxInsert; not
					// leaning on that here keeps this pass EU-neutral by construction rather than by luck.
					from.insert(got - inserted, tx);
				}
				room -= inserted;
			}
		}
	}

	/**
	 * Serve one consumer class (all machines, or all storage sinks) from the shared supply pool.
	 * Splits this class's allocation proportionally to room (capped at {@code packetCap}); pulls each
	 * consumer's share round-robin from producers, never from a producer co-located with the consumer
	 * (no storage self-churn). Decrements {@code remainingSupply[0]} by what actually moved.
	 *
	 * @param producerCursor the round-robin rotation offset; bounded by the producer pool size.
	 */
	private long serveClass(List<LiveConsumer> cls, List<LiveProducer> liveProducers,
			long[] remainingSupply, long packetCap, double lossPerBlock, EnergyPort.Txn tx, int producerCursor) {
		if (cls.isEmpty() || remainingSupply[0] <= 0) {
			return 0L;
		}
		long[] room = new long[cls.size()];
		long demand = 0;
		for (int i = 0; i < cls.size(); i++) {
			room[i] = cls.get(i).room();
			demand += room[i];
		}
		long moveTotal = EnergyShare.deliverable(remainingSupply[0], demand);
		long[] share = EnergyShare.split(moveTotal, room, demand, packetCap);

		long moved = 0;
		long consumed = 0; // EU drawn from producers and not returned = delivered + cable loss
		for (int i = 0; i < cls.size(); i++) {
			long want = share[i];
			if (want <= 0) {
				continue;
			}
			LiveConsumer c = cls.get(i);
			long pulled = pullRoundRobin(liveProducers, want, c.pos(), tx, producerCursor);
			if (pulled <= 0) {
				continue;
			}
			// MOD-021/MOD-253: attenuate the pulled flow once per traversed cable, per consumer.
			// EnergyShare preserves at least 1 EU from a positive packet, so a small top-off still reaches
			// exact capacity (no MOD-009 regression). The lost EU is not returned to any producer.
			// The three lines below (deliver / surplus / consumed) are extracted into EnergyServe (MOD-144)
			// so the runtime loss-application kernel — the +→− / −→+ EU-creation/destruction mutants — is
			// covered by the L1 suite + pitest. The runtime math is the pure extract path; this kernel
			// (MC-coupled) is the consumer of those pure helpers.
			int distance = consumerDistance.apply(c.pos());
			long loss = EnergyShare.cableLoss(pulled, lossPerBlock, distance);
			long toDeliver = EnergyServe.deliverAfterLoss(pulled, loss);
			long inserted = toDeliver > 0 ? c.storage().insert(toDeliver, tx) : 0;
			long surplus = EnergyServe.surplus(toDeliver, inserted);
			if (surplus > 0) {
				// Consumer took less than we offered; push only the surplus back (loss stays destroyed).
				returnRoundRobin(liveProducers, surplus, c.pos(), tx);
			}
			moved += inserted;
			consumed += EnergyServe.consumed(inserted, loss);
		}
		remainingSupply[0] -= consumed;
		return moved;
	}

	/**
	 * Pull up to {@code want} EU from producers, starting at the round-robin cursor, skipping any
	 * producer co-located with {@code consumerPos} so a storage node never charges itself.
	 *
	 * <p>Note (MOD-255): on the line path the "producers" are cable buffers, so this positional check never
	 * fires there — a cable is never at the consumer's position. Self-churn on that path is prevented one
	 * level up instead, by the network: a storage node that discharges into the line this tick is dropped
	 * from the served sinks, and the face gate in {@link #serveConsumersFromLine} keeps a node from drawing
	 * through a face that cannot accept. The positional rule stays for the paired producer→sink path.
	 *
	 * <p>{@link Math#floorMod} rather than {@code %}, to match the sweeps in {@link #chargeLineFrom}. Since
	 * MOD-254 the cursor is a free-running counter instead of one pre-reduced by the producer count, so
	 * {@code producerCursor + k} can overflow into the negatives near {@link Integer#MAX_VALUE} and
	 * {@code %} would keep that sign and index out of range.
	 */
	private long pullRoundRobin(List<LiveProducer> liveProducers, long want, BlockPos consumerPos,
			EnergyPort.Txn tx, int producerCursor) {
		long pulled = 0;
		int n = liveProducers.size();
		for (int k = 0; k < n && pulled < want; k++) {
			LiveProducer prod = liveProducers.get(Math.floorMod(producerCursor + k, n));
			if (prod.pos().equals(consumerPos)) {
				continue; // no self-churn: a storage sink must not pull from itself
			}
			pulled += prod.storage().extract(want - pulled, tx);
		}
		return pulled;
	}

	/**
	 * Return surplus EU back into producers (a consumer accepted less than was pulled), skipping self.
	 *
	 * <p><b>Invariant (keeps this loss-free):</b> {@code surplus} is 0 in practice because {@link EnergyNetwork#tick}
	 * sizes each consumer's demand from a simulated {@code insert}, so {@code room} is already capped by
	 * the consumer's {@code maxInsert} and the kernel never pulls more than the consumer will take. This
	 * matters because producers can be generators ({@code maxInsert == 0}); a non-zero surplus landing on
	 * one would be <em>destroyed</em> here (the rate-capped {@code insert} returns 0), the same EU-loss
	 * shape fixed in {@code EnergyMover}. If a future change ever sizes the pull without that
	 * insert-simulate, restore surplus without the rate cap instead of via {@code insert}. Guarded by
	 * gametest {@code NetworkGameTest#tcCable001Nrg03_generatorNotDrainedByPartialConsumer}.
	 */
	private void returnRoundRobin(List<LiveProducer> liveProducers, long surplus, BlockPos consumerPos,
			EnergyPort.Txn tx) {
		for (LiveProducer prod : liveProducers) {
			if (surplus <= 0) {
				break;
			}
			if (prod.pos().equals(consumerPos)) {
				continue;
			}
			surplus -= prod.storage().insert(surplus, tx);
		}
	}
}

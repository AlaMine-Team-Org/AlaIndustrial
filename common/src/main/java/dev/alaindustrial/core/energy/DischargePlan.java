package dev.alaindustrial.core.energy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which of the three storage discharge channels is open this tick, and how wide (MOD-715, CORE-5; ADR-004).
 *
 * <ol>
 *   <li><b>backup</b> — {@link #backupBudget()} EU: the machine demand the generators fall short of;</li>
 *   <li><b>cascade</b> — {@link #cascadeAllowances()}: per donor, a fuller store topping up an emptier one
 *       (MOD-314), only when backup is closed;</li>
 *   <li><b>feed</b> — {@link #feedAllowances()}: per donor, a store trickling into a sink the cascade refuses
 *       (MOD-353), only when both are closed.</li>
 * </ol>
 *
 * <p><b>The mutual exclusion lives in two places, on purpose (ADR-004).</b> {@link #decide} computes a later
 * channel only when the earlier ones are closed; the record itself refuses to exist with more than one
 * channel open, and {@code EnergyLineDistributor.chargeAndPropagateLine} still serves the channels in order
 * and stops after the first that is open. A fourth channel is one more component, one more branch in
 * {@link #decide}, one more stage in the distributor — and this constructor tells whoever adds it if the
 * exclusion was forgotten in either place.
 *
 * <p>Minecraft-free and free of {@code Config}: the donors and sinks are keyed by the line's position type
 * {@code P} (MOD-715, batch 8; the network passes {@code BlockPos}), and every number — the cascade deadband,
 * the packet cap, the feed reserve — comes in as a parameter.
 *
 * @param backupBudget EU stores may release to cover the machine deficit; 0 when the channel is closed
 * @param cascadeAllowances per donor, EU it may push toward an emptier store; empty when closed
 * @param feedAllowances per donor, EU it may push toward a fund; empty when closed
 * @param <P> the position type the donors and sinks are keyed by
 */
record DischargePlan<P>(long backupBudget, Map<P, Long> cascadeAllowances, Map<P, Long> feedAllowances) {

	/** What {@link #decide} needs to know about the blocks around the line. */
	interface Stores<P> {
		/** Whether the store at {@code pos} may receive the cascade (MOD-314). */
		boolean acceptsCascade(P pos);

		/** The flat feed rate the block at {@code pos} takes, or 0 (MOD-353). */
		long feedRate(P pos);

		/**
		 * EU in the line's cables right now — in transit, absent from every stored amount. Asked only when
		 * the cascade is actually sized, so a tick that never gets there pays no walk over the cables.
		 */
		long inFlight();
	}

	DischargePlan {
		int open = (backupBudget > 0 ? 1 : 0) + (cascadeAllowances.isEmpty() ? 0 : 1)
				+ (feedAllowances.isEmpty() ? 0 : 1);
		if (open > 1) {
			throw new IllegalArgumentException("ADR-004: more than one storage discharge channel open — backup "
					+ backupBudget + ", cascade " + cascadeAllowances + ", feed " + feedAllowances);
		}
	}

	/**
	 * Backup only, the plan of a line with no cascade or feed to consider: what generators fall short of
	 * the machines' demand, never less than zero (MOD-070).
	 */
	static <P> DischargePlan<P> backupOnly(long machineDemand, long genSupply) {
		return new DischargePlan<>(backupBudget(machineDemand, genSupply), Map.of(), Map.of());
	}

	/** The machine demand the generators fall short of, never less than zero (MOD-070). */
	static long backupBudget(long machineDemand, long genSupply) {
		return Math.max(0L, machineDemand - genSupply);
	}

	/**
	 * Decide this tick's channel.
	 *
	 * @param storageSources the stores that can discharge into the line this tick, in topology order
	 * @param sinks the stores and funds waiting for energy this tick
	 * @param deadband the cascade's "too small to bother" threshold — the network's segment buffer
	 * @param packetCap the tier packet cap of the line
	 * @param feedReserve the fraction of its capacity a feeding donor keeps
	 */
	static <P> DischargePlan<P> decide(long machineDemand, long genSupply,
			List<EnergyLineDistributor.LiveProducer<P>> storageSources,
			List<EnergyLineDistributor.LiveConsumer<P>> sinks, Stores<P> stores, long deadband, long packetCap,
			double feedReserve) {
		long backup = backupBudget(machineDemand, genSupply);
		Map<P, Long> cascade = new LinkedHashMap<>();
		if (backup == 0 && !storageSources.isEmpty() && !sinks.isEmpty()) {
			cascade = cascadeAllowances(storageSources, sinks, stores, deadband, packetCap);
		}
		Map<P, Long> feed = new LinkedHashMap<>();
		if (backup == 0 && cascade.isEmpty() && !storageSources.isEmpty() && !sinks.isEmpty()) {
			feed = feedAllowances(storageSources, sinks, stores, feedReserve, packetCap);
		}
		return new DischargePlan<>(backup, cascade, feed);
	}

	/**
	 * Whether the sink at {@code pos} must sit this tick out because it is itself discharging into the line
	 * (ADR-002): every storage source while backup runs, only the donors with an allowance while the cascade
	 * or the feed runs. Not interchangeable — {@code storageSources} is a pure face-role test, so an EMPTY box
	 * with a cable on its OUT face is in it, and excluding it under the cascade would keep it from ever
	 * charging from its full neighbour.
	 */
	boolean excludes(P pos, Set<P> storageSourcePositions) {
		if (backupBudget > 0) {
			return storageSourcePositions.contains(pos);
		}
		if (!cascadeAllowances.isEmpty()) {
			return cascadeAllowances.containsKey(pos);
		}
		return feedAllowances.containsKey(pos);
	}

	/**
	 * How much each storage source may push into the line as a cascade donor this tick (MOD-314).
	 *
	 * <p>Per (donor, sink) pair, keeping only the single most-favourable target per donor: the line
	 * decides where the EU actually lands (proportionally to room, {@code EnergyShare.split}), so this
	 * only has to answer "is there a target worth opening the tap for, and how wide". Sizing off the
	 * emptiest eligible target is what makes the allowance shrink as the pair levels out.
	 *
	 * <p>The deadband is the network's segment buffer, taken from its strongest cable grade — not the
	 * tier packet cap and not the global {@code Config.cableBuffer}. MOD-070 established the segment
	 * buffer as the real per-tick throughput between two points, so it is the unit a "too small to
	 * bother" threshold has to be measured in; MOD-219 made it per grade, so reading the global would
	 * under-set the deadband fourfold on a gold line and weaken the anti-oscillation guarantee exactly
	 * where packets are largest.
	 *
	 * <p>Only self is excluded as a target — deliberately not "every other storage source". A node is a
	 * source by face role alone, charge not consulted, so excluding the whole set would make an empty
	 * mid-bus box permanently ineligible (see {@link #excludes}). Two nodes cannot donate to
	 * each other anyway: the gradient is strict, and {@code fill(a) > fill(b)} and {@code fill(b) >
	 * fill(a)} cannot both hold. A chain (c → a → b) is legitimate, and the guard keeps whoever is
	 * actually donating out of the serve pass.
	 */
	private static <P> Map<P, Long> cascadeAllowances(List<EnergyLineDistributor.LiveProducer<P>> donors,
			List<EnergyLineDistributor.LiveConsumer<P>> sinks, Stores<P> stores, long deadband, long packetCap) {
		Map<P, Long> allowances = new LinkedHashMap<>();
		// EU that has already left a donor and is still travelling: the line advances one hop per tick, so
		// on an N-cable run up to N × segmentBuffer EU is in transit and absent from every stored amount
		// the comparison below can see. Ignoring it makes the donor keep giving for as many ticks as the
		// line is long, and it lands PAST level — measured at 77 EU over ten copper cables, which is the
		// first half of an oscillation on any layout where the receiver can donate back. Counting it as
		// already delivered removes the blind spot. Attributing it per (donor, sink) pair is not possible
		// — a cable buffer does not record where its EU came from or is going — so the whole line counts
		// against every sink, which biases toward refusing a transfer rather than overshooting one.
		long inFlight = stores.inFlight();
		for (EnergyLineDistributor.LiveProducer<P> donor : donors) {
			if (!stores.acceptsCascade(donor.pos())) {
				continue;
			}
			long donorAmount = donor.storage().getAmount();
			long donorCapacity = donor.storage().getCapacity();
			long best = 0L;
			for (EnergyLineDistributor.LiveConsumer<P> sink : sinks) {
				if (sink.pos().equals(donor.pos()) || !stores.acceptsCascade(sink.pos())) {
					continue;
				}
				long sinkCapacity = sink.storage().getCapacity();
				long sinkAmount = Math.min(sinkCapacity, sink.storage().getAmount() + inFlight);
				long allowance = CascadeShare.allowance(donorAmount, donorCapacity,
						sinkAmount, sinkCapacity, deadband, packetCap);
				if (allowance > best) {
					best = allowance;
				}
			}
			if (best > 0) {
				allowances.put(donor.pos(), best);
			}
		}
		return allowances;
	}

	/**
	 * MOD-353 — how much each donor store may release to sinks that are OUTSIDE the cascade
	 * (Teleporter, Charging Station), when nothing else on the segment wants power.
	 *
	 * <p>Deliberately a separate aggregate from {@code machineDemand}: folding these sinks into machine
	 * demand would have been two lines, but it also feeds the MOD-255 self-serve guard, the cascade's
	 * mutual exclusion and {@link #backupBudget} — so a Teleporter with room would silently close the
	 * box↔box cascade, which is the MOD-314 regression this must not cause.
	 *
	 * <p>Unlike the cascade this compares nothing proportionally. Each donor offers a flat rate out of
	 * what it holds above its own reserve, so a 500 000 EU fund cannot pull harder than a 20 000 EU box
	 * would — that asymmetry is precisely why the cascade refuses these blocks in the first place.
	 */
	private static <P> Map<P, Long> feedAllowances(List<EnergyLineDistributor.LiveProducer<P>> donors,
			List<EnergyLineDistributor.LiveConsumer<P>> sinks, Stores<P> stores, double feedReserve,
			long packetCap) {
		Map<P, Long> allowances = new LinkedHashMap<>();
		for (EnergyLineDistributor.LiveProducer<P> donor : donors) {
			// A donor that would itself accept this feed is a store, not a fund: those level out through
			// the cascade, and letting them use this channel too would give one pair two ways to move EU
			// in the same tick.
			if (stores.feedRate(donor.pos()) > 0) {
				continue;
			}
			long donorAmount = donor.storage().getAmount();
			long donorCapacity = donor.storage().getCapacity();
			long best = 0L;
			for (EnergyLineDistributor.LiveConsumer<P> sink : sinks) {
				if (sink.pos().equals(donor.pos())) {
					continue;
				}
				long rate = stores.feedRate(sink.pos());
				if (rate <= 0) {
					continue;
				}
				long allowance = StorageFeedShare.feedAllowance(donorAmount, donorCapacity,
						feedReserve, sink.room(), rate, packetCap);
				if (allowance > best) {
					best = allowance;
				}
			}
			if (best > 0) {
				allowances.put(donor.pos(), best);
			}
		}
		return allowances;
	}
}

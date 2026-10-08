package dev.alaindustrial.core.energy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which of the three storage discharge channels is open this tick, and how wide (MOD-715, CORE-5; ADR-004).
 *
 * <ol>
 *   <li><b>backup</b> — {@link #backupBudget()} EU: the machine demand the generators fall short of; every
 *       sink sits the tick out, so the stores' EU reaches the machines (MOD-756, {@link #excludes}) — the sinks
 *       that are not discharging stores draw apart no more than {@link #surplusBudget()}, the sinks' credit
 *       ({@link Stores#sinkCredit()});</li>
 *   <li><b>cascade</b> — {@link #cascadeAllowances()}: per donor, a fuller store topping up an emptier one
 *       (MOD-314), only when backup is closed; the donors and every sink outside the cascade sit the tick
 *       out, so the EU reaches the stores it was released for (MOD-731, {@link #excludes}) — the sinks outside
 *       it draw apart no more than {@link #surplusBudget()}, what the generators put in beyond the machines;</li>
 *   <li><b>feed</b> — {@link #feedAllowances()}: per donor, a store trickling into a sink the cascade refuses
 *       (MOD-353), only when both are closed.</li>
 * </ol>
 *
 * <p>A fourth state is not a channel: <b>settling</b> ({@link #settling()}, MOD-756) — every channel closed in
 * the middle of a backup episode ({@link Stores#backupEpisode()}). Its funds sit the tick out as on a backup
 * tick and draw apart only their credit, so a fund that walls the machine off between two backup ticks cannot
 * collect the store's charge from the cables, nor take the generators' EU the machine then goes short of.
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
 * @param surplusBudget EU the sinks set aside may draw from the line (MOD-731, MOD-756, {@link #drawsSurplus}):
 *     on a cascade tick {@link Stores#generatorSurplus()}, on a backup or settling tick the sinks' credit
 *     {@link Stores#sinkCredit()}; 0 otherwise
 * @param settling every channel closed in the middle of a backup episode
 * @param <P> the position type the donors and sinks are keyed by
 */
record DischargePlan<P>(long backupBudget, Map<P, Long> cascadeAllowances, Map<P, Long> feedAllowances,
		long surplusBudget, boolean settling) {

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

		/**
		 * EU the generators put into the line on its previous tick beyond what its machines drew (MOD-731):
		 * the most the sinks outside the cascade may take on a cascade tick. Asked only when the cascade opens.
		 */
		long generatorSurplus();

		/**
		 * Whether the line is in a backup episode (MOD-756): a store backed its machines up on some tick, and no
		 * cascade or feed has opened since. False on a line where no store ever backed a machine up.
		 */
		default boolean backupEpisode() {
			return false;
		}

		/**
		 * The sinks' credit in the current backup episode (MOD-756): what the generators put into the line since
		 * it began beyond what the machines and the sinks drew, never above what the cables hold, negative while
		 * the stores' backup is still unpaid. The most the sinks may take apart on a backup or settling tick
		 * (when positive): over an episode they take no more than the generators gave beyond the machines, so a
		 * store's backup reaches the machines only. Asked only on a backup or settling tick.
		 */
		default long sinkCredit() {
			return 0L;
		}
	}

	DischargePlan {
		int open = (backupBudget > 0 ? 1 : 0) + (cascadeAllowances.isEmpty() ? 0 : 1)
				+ (feedAllowances.isEmpty() ? 0 : 1);
		if (open > 1) {
			throw new IllegalArgumentException("ADR-004: more than one storage discharge channel open — backup "
					+ backupBudget + ", cascade " + cascadeAllowances + ", feed " + feedAllowances);
		}
		if (settling && open > 0) {
			throw new IllegalArgumentException("MOD-756: a settling tick has every channel closed — backup "
					+ backupBudget + ", cascade " + cascadeAllowances + ", feed " + feedAllowances);
		}
		if (surplusBudget < 0 || (surplusBudget > 0 && cascadeAllowances.isEmpty() && backupBudget == 0
				&& !settling)) {
			throw new IllegalArgumentException("MOD-731, MOD-756: a surplus budget of " + surplusBudget
					+ " EU belongs to a cascade, backup or settling tick only — cascade " + cascadeAllowances
					+ ", backup " + backupBudget);
		}
	}

	/** A plan that is not settling. */
	DischargePlan(long backupBudget, Map<P, Long> cascadeAllowances, Map<P, Long> feedAllowances,
			long surplusBudget) {
		this(backupBudget, cascadeAllowances, feedAllowances, surplusBudget, false);
	}

	/** A plan whose set-aside sinks draw nothing apart. */
	DischargePlan(long backupBudget, Map<P, Long> cascadeAllowances, Map<P, Long> feedAllowances) {
		this(backupBudget, cascadeAllowances, feedAllowances, 0L);
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
		if (!cascade.isEmpty()) {
			return new DischargePlan<>(0L, cascade, feed, Math.max(0L, stores.generatorSurplus()));
		}
		boolean settling = backup == 0 && feed.isEmpty() && !sinks.isEmpty() && stores.backupEpisode();
		if (backup > 0 || settling) {
			return new DischargePlan<>(backup, cascade, feed, Math.max(0L, stores.sinkCredit()), settling);
		}
		return new DischargePlan<>(backup, cascade, feed, 0L);
	}

	/**
	 * Whether the sink at {@code pos} must sit this tick out of the serve pass — and so out of the sink field's
	 * seeds, which read the same list (ADR-003).
	 *
	 * <ul>
	 *   <li><b>backup</b> — every sink (MOD-756): what a store releases on a backup tick is for the machines. A
	 *       storage source is discharging into the line (ADR-002); left in, a Teleporter took its share of what
	 *       the box released for the machine, by room, and seeded the sink field — where it sat closer to the
	 *       source than the machine it walled the machine off for good, the backup channel never closed, and the
	 *       box drained into the fund past its reserve (the reserve is the feed's, MOD-353; backup has none). The
	 *       sinks that are not discharging stores still draw the generators' surplus apart ({@link
	 *       #drawsSurplus}): backup opens on the machines' free room, not on what the line can carry to them, so
	 *       a generator stronger than the machines' cables has EU to spare on a backup tick. The budget is the
	 *       sinks' credit over the backup episode ({@link Stores#sinkCredit()}): a budget of last tick's surplus
	 *       let a fund that walls the machine off take the generators' EU in an ordinary pass and the box's
	 *       release on the next backup tick;</li>
	 *   <li><b>settling</b> — every sink that does not take the cascade (a fund: Teleporter, Charging Station,
	 *       Energy Condenser): the episode's ledger holds between backup ticks too, and a fund out of the seeds no
	 *       longer walls the machine off (MOD-756). A box stays in the pass and the seeds — it levels and charges
	 *       as before, and a fork toward it keeps its share (MOD-254);</li>
	 *   <li><b>cascade</b> — the donors with an allowance, and every sink that does not accept the cascade
	 *       (MOD-731): a Teleporter, a Charging Station, an Energy Condenser. The cascade sizes a tap on the
	 *       donor's side for a box worth levelling, but the line does not know where its EU came from — it
	 *       serves every waiting sink by room and seeds the field from every one of them. Left in, a fund
	 *       beside the bus took eleven twelfths of each packet, kept the far box behind a seam of the field,
	 *       and the donor drained to the empty box's level with no reserve, because the reserve is the feed's
	 *       (MOD-353). Out of the pass and out of the seeds, the cascade's EU reaches the boxes, and the
	 *       in-flight correction of {@link #cascadeAllowances} counts energy that really goes where it says.
	 *       Such a sink is still served, apart, from the generators' surplus ({@link #drawsSurplus});</li>
	 *   <li><b>feed</b> — the donors with an allowance only: a fund is the feed's destination, and the feed is
	 *       open for hours, so a store or a condenser beside it keeps its share of the generators' surplus.</li>
	 * </ul>
	 *
	 * <p>One known edge of the cascade branch, accepted (owner, 2026-10-05): the tail. When the cascade closes,
	 * the EU still in the cables — already counted to the box by the in-flight correction — may reach a fund
	 * once it is back in the pass. One load of the cables does not bound it: the box comes out short of what it
	 * was counted, the cascade re-opens for a smaller round, and that round's cables may leak too. On the golden
	 * {@code fund} rig the fund ended with 52 EU against 48 in the cables at the first closing; the rounds shrink
	 * by the half step, so the scenarios hold a levelling episode to two loads of the cables.
	 *
	 * <p>Under the cascade not interchangeable with "every storage source": that set is a pure face-role test,
	 * so an EMPTY box with a cable on its OUT face is in it, and excluding it would keep it from ever charging
	 * from its full neighbour.
	 *
	 * @param acceptsCascade whether the sink at a position may receive the cascade ({@link
	 *     Stores#acceptsCascade}); asked only while the cascade is open
	 */
	boolean excludes(P pos, Predicate<P> acceptsCascade) {
		if (backupBudget > 0) {
			return true;
		}
		if (settling) {
			return !acceptsCascade.test(pos);
		}
		if (!cascadeAllowances.isEmpty()) {
			return cascadeAllowances.containsKey(pos) || !acceptsCascade.test(pos);
		}
		return feedAllowances.containsKey(pos);
	}

	/**
	 * Whether the sink at {@code pos}, out of the serve pass on this cascade or backup tick ({@link #excludes}), is
	 * served apart from the generators' surplus — at most {@link #surplusBudget()} EU between all such sinks,
	 * from the cables each one touches, before the stores (MOD-731; owner, 2026-10-05: a Teleporter charges from the
	 * generators' surplus even while the boxes level, and a box's charge still never reaches it).
	 *
	 * <p>The line cannot tell a generator's EU from a store's (ADR-001), so the budget is an account, not an
	 * address: the generators' surplus beyond the machines on the previous tick, each tick's surplus spent at
	 * most once. Over any stretch of ticks these sinks take no more than the generators put in beyond the
	 * machines, so the rest of what is on the cables — the cascade's EU among it — goes to the stores. Served
	 * apart, never seeded: seeded, such a sink would put the far box behind a seam of the field again and stop
	 * the levelling. With no generator on the line the budget is 0 and the sink sits the tick out, as before.
	 *
	 * <p>On a backup tick (MOD-756) every sink but a storage source draws: the stores' EU is the machines', and
	 * the account keeps it theirs; a storage source discharging into the line must not drink its own discharge
	 * (ADR-002). On a settling tick nobody discharges, and every fund set aside draws. Seeding is the same story
	 * as on a cascade tick — a seeded fund closer to the source than the machine walls the machine off. The
	 * budget on both is the sinks' credit over the backup episode ({@link Stores#sinkCredit()}).
	 *
	 * @param storageSourcePositions the stores that can discharge into the line this tick (backup only)
	 */
	boolean drawsSurplus(P pos, Set<P> storageSourcePositions, Predicate<P> acceptsCascade) {
		if (surplusBudget <= 0) {
			return false;
		}
		if (backupBudget > 0) {
			return !storageSourcePositions.contains(pos);
		}
		if (settling) {
			return !acceptsCascade.test(pos);
		}
		return !cascadeAllowances.containsKey(pos) && !acceptsCascade.test(pos);
	}

	/**
	 * Take out of {@code sinks} every sink that sits this tick out of the serve pass ({@link #excludes}) and
	 * return those of them that draw the generators' surplus apart ({@link #drawsSurplus}), in list order. Empty
	 * and not allocated unless there is a surplus budget.
	 */
	List<EnergyLineDistributor.LiveConsumer<P>> setAside(List<EnergyLineDistributor.LiveConsumer<P>> sinks,
			Set<P> storageSourcePositions, Predicate<P> acceptsCascade) {
		List<EnergyLineDistributor.LiveConsumer<P>> surplusTakers =
				surplusBudget > 0 ? new ArrayList<>() : List.of();
		sinks.removeIf(c -> {
			if (!excludes(c.pos(), acceptsCascade)) {
				return false;
			}
			if (drawsSurplus(c.pos(), storageSourcePositions, acceptsCascade)) {
				surplusTakers.add(c);
			}
			return true;
		});
		return surplusTakers;
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

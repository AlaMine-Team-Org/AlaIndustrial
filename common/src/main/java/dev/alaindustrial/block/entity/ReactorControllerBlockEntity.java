package dev.alaindustrial.block.entity;

import dev.alaindustrial.advancement.ReactorMilestone;
import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.block.entity.reactor.ReactorBlastTimer;
import dev.alaindustrial.block.entity.reactor.ReactorChannels;
import dev.alaindustrial.block.entity.reactor.ReactorEventLog;
import dev.alaindustrial.block.entity.reactor.ReactorHazards;
import dev.alaindustrial.block.entity.reactor.ReactorRoom;
import dev.alaindustrial.block.entity.reactor.ReactorStacks;
import dev.alaindustrial.block.entity.reactor.ReactorVoice;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.BareReactorScan;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModCriteria;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The reactor controller's block entity (MOD-468, stage 1): it re-scans the room and publishes the
 * verdict to the screen.
 *
 * <p><b>Why it re-scans on a timer as well as on demand.</b> {@code neighborChanged} only fires for
 * the six blocks touching the controller, and a reactor room is up to 14 blocks across — a wall mined
 * on the far side, a door blown up by a creeper, a port pushed by a piston are all invisible to it. A
 * periodic sweep is the only way a controller notices its own room being taken apart, so the scan runs
 * every {@link ReactorConfig#reactorScanIntervalTicks} even when nothing nearby changed.
 *
 * <p><b>Positions travel as offsets, not coordinates.</b> {@link ContainerData} ships each channel as a
 * <em>short</em>: an absolute block position (up to ±30 000 000) arrives on the client as garbage. The
 * breach is therefore sent relative to the controller — a range of at most ±14 by construction, which
 * fits with room to spare — and the screen phrases it as a direction ("4 blocks east, 2 up"). That is
 * also the more useful sentence: the player is standing at the controller when they read it.
 *
 * <p><b>Power leaves through the controller's own faces</b> — every one of them except the screen,
 * which R-NRG-03 keeps energy-inert so a cable never draws an arm across the interface. Inside a
 * sealed room those faces are buried in the wall and the {@code reactor_outlet} carries the power out
 * instead; a controller running in the open (MOD-469) has them exposed, and a cable plugs straight
 * into it. (An earlier note here said stage 1 had no energy at all and sat on
 * {@link dev.alaindustrial.registry.BlockCapabilityRoster#NO_ENERGY_CAPABILITY}. Both halves stopped
 * being true in stage 2, when the buffer and the HV tier arrived.)
 */
public class ReactorControllerBlockEntity extends MachineBlockEntity implements MenuProvider, NoUpgradePanel {

	/** Coolant boiled on the last tick, in mB. Zero while nothing reacts and no overheat is left to bring down. */
	private int lastWater;

	/**
	 * Share of the reaction's heat the water carried on the last tick — see {@link ReactorChannels#COOLANT_SHARE}.
	 * Starts full: a controller that has not ticked yet is not short of water.
	 */
	private int coolantShare = 100;

	/**
	 * Why the reactor produced nothing this tick, as an {@link ReactorIdleReason} ordinal.
	 *
	 * <p>A reactor that is built, sealed, fuelled and silent is the worst state this machine can be
	 * in: the panel used to print a dash for the output and leave the player to guess between a
	 * missing redstone signal, a closed throttle, an empty rack and a full buffer. Every one of those
	 * is a different fix.
	 */
	private int idleReason = ReactorIdleReason.RUNNING.ordinal();

	/** The room: the scan's verdict and measurements, its racks and sockets, the bare racks, the sealed box. */
	private final ReactorRoom room = new ReactorRoom(this::setChanged);


	// ── stage 2: the reactor itself ──
	/** Heat on the 0…{@link ReactorConfig#reactorHeatCapacity} scale. */
	private long heat;
	/** How deeply the control rods are lowered, 0…1000. The player's throttle. */
	private int depthPermille = ReactorCore.FULL_DEPTH;
	/** What the last tick actually produced, for the readout. */
	private int lastOutput;


	// ── MOD-469: the bare reactor and the meltdown ──



	/**
	 * Whether the core is enabled and fuelled this tick, whether or not anybody wanted the power.
	 *
	 * <p><b>Not the same thing as producing, and the difference is the whole of finding 1.</b> Fuel is
	 * only spent when the energy is wanted (MOD-468's rule, and a good one), so a bare reactor with a
	 * full buffer reports zero output — but the rods are still racked, still unshielded and still
	 * dangerous. Hanging the hazard on output meant a player could silence it by simply not consuming,
	 * which is neither physical nor consistent with radiation, which has never cared about the buffer.
	 * The scram — pulling the redstone — remains the one way to make a bare core safe.
	 */
	private boolean reacting;

	/** What the reactor melts: its own contents, the scenery, or a working room's plain pipes. */
	private final ReactorHazards hazards = new ReactorHazards(this::setChanged);

	/** Ticks until the next sweep. Zero means "scan on the next server tick". */
	private int scanCooldown;

	// ── MOD-471: the accident at the top of the scale ──
	/**
	 * Instability of a bare core: the second scale, and the only one a reactor with no room has.
	 *
	 * <p><b>Persisted ({@value #INSTABILITY_KEY}, MOD-727).</b> It used to be left out on the grounds that a
	 * reloaded pile climbs back to its equilibrium within seconds. It does — but a pile big enough to arm the
	 * countdown needs about two hundred ticks to climb back, and the countdown it armed IS saved: the reloaded
	 * pile sat under the line for the whole release window and called its own accident off, so a reload was a
	 * free rescue. A save from before the key loads it as zero, exactly as before.
	 */
	private long instability;

	/** The accident countdown and the blast at its end. */
	private final ReactorBlastTimer blast = new ReactorBlastTimer(this::setChanged);

	/** Save key of {@link #instability} (MOD-727): additive, a save without it loads the old way. */
	private static final String INSTABILITY_KEY = "Instability";

	// ── MOD-472: the room's voice ──
	/** The drone, the sirens and the steam puffs. */
	private final ReactorVoice voice = new ReactorVoice(this::setChanged);

	// ── MOD-622: the event log ──
	/** The event log and the latches that decide when it writes a line. */
	private final ReactorEventLog log = new ReactorEventLog(this::setChanged);

	/** Rods with fuel in them, counted by this tick's reaction pass — the number a "started" line reports. */
	private int lastLiveRods;

	// ── MOD-473: the advancement branch ──
	/**
	 * Whether this controller has already offered its owner the "made power" and "boiled steam" steps.
	 *
	 * <p><b>Deliberately not persisted.</b> These are latches against firing a criterion sixty times a
	 * second, not a record of what the player has earned — the advancement system already remembers
	 * that, per player, and it is the only place that can. Persisting them would put a per-player fact
	 * into a block that anyone can operate, and losing them on a chunk reload costs one extra trigger
	 * call for a reactor that is running anyway.
	 */
	private boolean powerMilestoneOffered;

	private boolean steamMilestoneOffered;


	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		// Stage 2: a real HV producer. The buffer is sized to a few seconds of full output so a grid
		// that cannot take the power immediately does not stall the reactor mid-tick.
		super(ModContent.REACTOR_CONTROLLER_BE.get(), pos, state, EnergyTier.HV, 0,
				ReactorConfig.reactorBuffer, 0L, EnergyTier.HV.maxVoltage());
	}

	// No upgrade panel (NoUpgradePanel): a controller with a hidden four-slot inventory would both accept
	// hoppers and promise upgrades it does not have.

	/** Re-arms the scan for the next tick — called when a neighbour changes or the block is placed. */
	public void requestScan() {
		scanCooldown = 0;
		wake();
	}

	public ReactorRoomStatus getStatus() {
		return room.status();
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = ReactorConfig.reactorScanIntervalTicks;
			rescan(level, pos, state);
		}
		runReactor(level, pos);
		// Never sleep: the periodic sweep is the only thing that notices a room being dismantled out of
		// neighbour range, and heat has to keep bleeding away even with the reactor shut down (R-29).
		return 0;
	}

	/**
	 * One tick of the reactor, in this order — and the order is part of the behaviour, pinned tick by tick by
	 * {@code ReactorGoldenTraceScenarios}:
	 * <ol>
	 *   <li>the columns, live rods and fuelled pairs, counted fresh, and whether the reaction runs
	 *       ({@link #noteReaction});</li>
	 *   <li>the reaction: what it is asked for and the heat it makes ({@link #react});</li>
	 *   <li>cooling by water — the shell's own cooling is taken first, from the heat before this tick
	 *       ({@link #coolByWater});</li>
	 *   <li>the sale: power into the buffer, fuel burnt, the output row ({@link #sell});</li>
	 *   <li>{@link #settleHeat}, then {@link #settleInstability};</li>
	 *   <li>the stacks settled ({@link ReactorStacks#settle}), the steam puffs ({@link ReactorVoice#puffSteam});</li>
	 *   <li>the outlets fed ({@link #feedOutlets});</li>
	 *   <li>the voice: its latch, the log's started/stopped line, the drone painted ({@link ReactorVoice});</li>
	 *   <li>{@link #warnOnOverheat}, {@link #runHazards}, {@link #runCountdown}.</li>
	 * </ol>
	 *
	 * <p><b>Fuel burns only when the energy is wanted</b> — the player's own call for this stage. A
	 * full buffer with nothing drawing from it costs no uranium, exactly as the charging station spends
	 * only per transfer. Heat, by contrast, is settled every tick whether the reactor ran or not: a
	 * shut-down core still has to cool down, and "scram and wait" must actually work.
	 *
	 * <p><b>And heat is PRODUCED whenever the reaction is running, full buffer or not</b> (MOD-471).
	 * That is not the same rule as the one above, and the difference is the whole of the accident: a
	 * reactor nobody is drawing from is still a reactor, and if its coolant is missing it still cooks
	 * itself to the top of the scale. See {@link #react} for what a playtest looked like before it.
	 */
	private void runReactor(Level level, BlockPos pos) {
		boolean sealed = room.isSealed();
		// No signal is the scram: a lever by the door stops the reaction without dismantling anything.
		// It is the ONE control a bare reactor still answers to. The throttle is deliberately not asked:
		// the bare panel has no room to show it, and a hidden control that silently holds a reactor at
		// zero is the worst kind — a player whose breached room stops producing would have no way to
		// learn that the slider they left at 0% is why. Bare rods are always fully lowered.
		boolean allowed = (sealed ? depthPermille > 0 : room.isBare()) && level.hasNeighborSignal(pos);
		// Resolved ONCE per tick and handed to all three passes. Burning, boiling and levelling each
		// used to walk `assemblies` and call getBlockEntity themselves, which in a room packed to the
		// 12-block limit is several hundred chunk lookups a tick for a machine that ticks every tick.
		List<FuelRodAssemblyBlockEntity> columns = room.collectColumns(level);
		int liveRods = countLiveRods(columns);
		lastLiveRods = liveRods;
		// Density is counted fresh too, for the same reason the rods are. It used to come from the
		// periodic scan, so for up to reactorScanIntervalTicks after a column was pulled the remaining
		// ones went on being paid a neighbour bonus for a rack that was no longer there — free EU, and
		// exactly the kind that is invisible because it is small and brief.
		int pairs = countNeighbourPairs(columns);
		boolean running = allowed && liveRods > 0;
		noteReaction(level, pos, sealed, running, liveRods);
		Reaction reaction = running ? react(liveRods, pairs) : Reaction.IDLE;
		long cooling = ReactorCore.shellCooling(heat, reacting && !room.isBare(), ReactorConfig.reactorPassiveCooling,
				ReactorConfig.reactorHeatLossPermille);
		HeatFlow flow = coolByWater(columns, reaction);
		sell(level, pos, sealed, running, reaction.wanted(), columns);
		settleHeat(flow, cooling, reaction.produced());
		settleInstability(liveRods);
		if (!room.isBare()) {
			ReactorStacks.settle(columns);
		}
		if (sealed && !room.isBare() && level instanceof ServerLevel serverLevel) {
			voice.puffSteam(serverLevel, columns);
		}
		feedOutlets(level);
		// Empty when bare, and that is the whole point (MOD-469 audit). The drone is painted ONTO the
		// racks and taken off them only inside the box this controller last sealed — a bare rack switched
		// on here would stand humming for as long as it existed, with nothing left in the world able to
		// switch it off. The latch and the spin-down edge still run, so a room that breaks with no racks
		// nearby still announces that it stopped.
		boolean voiced = voice.latch(reacting);
		log.logRunning(getLevel(), voiced, lastLiveRods, room.isBare(), depthPermille);
		voice.sing(level, pos, room.isBare() ? List.of() : columns, voiced);
		warnOnOverheat(level, pos);
		runHazards(level, pos);
		if (level instanceof ServerLevel serverLevel) {
			runCountdown(serverLevel, pos);
		}
	}

	/** What the reaction is asked for this tick, in EU/t, and the heat it makes. */
	private record Reaction(long wanted, long produced) {
		static final Reaction IDLE = new Reaction(0, 0);
	}

	/** The heat this tick put into a room, and the share of it the water carried away. */
	private record HeatFlow(long heatIn, long carried) {}

	/**
	 * Rods with fuel in them, counted FRESH every tick, not taken from the periodic scan. The room's rod count is
	 * refreshed once every reactorScanIntervalTicks, and output runs every tick — so a room whose last rod had just
	 * burnt out went on making full power for up to two seconds, on nothing. Twenty thousand EU out of thin air per
	 * refuelling, which is exactly the class of hole the fuel cycle closed everywhere else.
	 */
	private static int countLiveRods(List<FuelRodAssemblyBlockEntity> columns) {
		int liveRods = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			liveRods += column.getRods();
		}
		return liveRods;
	}

	/**
	 * Records whether the reaction runs — before the buffer is consulted: this is "the reaction is running", not
	 * "we sold power".
	 */
	private void noteReaction(Level level, BlockPos pos, boolean sealed, boolean nowReacting, int liveRods) {
		if (nowReacting != reacting) {
			reacting = nowReacting;
			// Only this tick still knows why a reaction stopped (MOD-622). The line itself is written on the drone's
			// latch, forty ticks on, so a redstone clock or a full buffer does not log a stop every second.
			log.reactionChanged(nowReacting, sealed, room.isBare(), liveRods, level.hasNeighborSignal(pos),
					depthPermille);
			setChanged();
		}
	}

	/** The reaction of a core that runs: what it is asked for, and the heat it makes doing it. */
	private Reaction react(int liveRods, int pairs) {
		// What this core could give with the rods all the way down. The tier ceiling is applied to
		// THIS, and the throttle is applied after it — not the other way round. Clipping a
		// depth-scaled figure against the ceiling looked equivalent and was not: on any core whose
		// potential already cleared 512 EU/t every slider stop produced the same 512, so the control
		// the player was given did nothing at exactly the scale it was built for.
		long full = ReactorCore.output(liveRods, pairs, ReactorConfig.reactorEuPerRod,
				ReactorConfig.reactorNeighbourBonusPercent, ReactorCore.FULL_DEPTH);
		// Two ceilings: the tier's voltage (a reactor is an HV machine, and nothing in the mod could
		// carry tens of thousands of EU/t anyway) and whatever room is left in the buffer.
		long ceiling = Math.min(full, EnergyTier.HV.maxVoltage());
		// A bare core is scaled and capped instead of throttled. Both ceilings still apply above it,
		// so the bare cap can only ever make the figure smaller — it is a floor on how bad the
		// shortcut is, never a way around the tier.
		long wanted = room.isBare()
				? ReactorCore.bareOutput(ceiling, ReactorConfig.reactorBarePowerPercent,
						ReactorConfig.reactorBarePowerCap)
				: ceiling * depthPermille / ReactorCore.FULL_DEPTH;

		// ── Heat follows the REACTION. Fuel follows the SALE. ──
		//
		// The asymmetry is deliberate and it was paid for by a playtest (MOD-471). Heat used to be
		// charged against the energy actually banked, which meant a sealed, fuelled, redstone-powered
		// reactor with a full buffer produced no heat at all: the gauge fell back to zero and the
		// core cooled itself down. A player watched exactly that — twelve rods, no coolant, no
		// consumers — and pointed out the obvious: nobody switched the reactor off, so what stopped
		// the chain reaction? Nothing did. A reactor is not a machine that decides to stop when the
		// warehouse is full; it is a fire, and a fire that nobody is drawing heat from is the most
		// dangerous kind. Since then the temperature is driven by {@code wanted} — what the reaction
		// is producing — and the buffer only decides how much of it is banked.
		//
		// This is the same lesson MOD-469 learned on the bare core, where the melting was hung on
		// output and a player could silence the hazard by unplugging their machines. Two features
		// made the identical mistake; both now key on "the reaction is running", never on the sale.
		//
		// Fuel deliberately did NOT move with it. A rod is an amount of energy (MOD-468's own
		// invariant, and the whole fuel cycle rests on it), so uranium is spent only on energy that
		// was actually delivered. An idling reactor therefore heats up for free — which is precisely
		// what makes "I filled the buffer and went to bed" an accident rather than a rounding error.
		//
		// A bare core still makes NO heat: it has no shell to hold it, no gauge to show it and no
		// coolant loop to answer it. Its own scale is instability, and that one already keys on the
		// reaction (see settleInstability).
		long heatFull = room.isBare() ? 0 : ReactorCore.heatProduced(liveRods, pairs,
				ReactorConfig.reactorHeatPerRod, ReactorConfig.reactorHeatNeighbourBonusPercent,
				ReactorCore.FULL_DEPTH);
		return new Reaction(wanted, ReactorCore.heatForOutput(heatFull, wanted, full));
	}

	/**
	 * ── Water is the only cooling a working room has (MOD-623). ──
	 *
	 * <p>The reaction pays out whether or not there is water; what the water decides is the temperature.
	 * While the rods work the shell sheds nothing, so every unit of heat the water did not carry stays
	 * on the gauge, and a dry room climbs to the top and into the countdown however small it is. The
	 * shell used to shed up to 84 heat a tick by itself, and a player kept the rods shallow, left the
	 * plumbing out and ran a reactor that never heated (playtest, MOD-618). Once the reaction stops, the
	 * shell cools the room as it always did.
	 */
	private HeatFlow coolByWater(List<FuelRodAssemblyBlockEntity> columns, Reaction reaction) {
		if (room.isBare()) {
			// The coolant loop and the stack settling are the ROOM's plumbing. A bare rack has no shell
			// to plumb and makes no heat to answer, and running them anyway would quietly boil away water
			// a player had poured into a column for the room they are still building around it.
			lastWater = 0;
			coolantShare = 100;
			return new HeatFlow(reaction.produced(), 0);
		}
		long heatIn = ReactorCore.reactionHeat(reaction.produced(), reaction.wanted());
		long carried = ReactorCore.heatRemovedByWater(coolWithWater(columns, heatIn),
				ReactorConfig.reactorHeatPerWater);
		coolantShare = ReactorCore.coolantSharePercent(heatIn, carried);
		return new HeatFlow(heatIn, carried);
	}

	/** The sale: what the reaction banks, the fuel it burns for it, and what the output row says. */
	private void sell(Level level, BlockPos pos, boolean sealed, boolean running, long wanted,
			List<FuelRodAssemblyBlockEntity> columns) {
		if (!running) {
			lastOutput = 0;
			idleReason = idleReasonFor(level, pos, sealed).ordinal();
			return;
		}
		long space = energy.getCapacity() - energy.getAmount();
		long output = Math.min(wanted, space);
		if (output > 0) {
			energy.setAmountUntracked(energy.getAmount() + output);
			burnFuel(columns, output);
			lastOutput = (int) Math.min(Short.MAX_VALUE, output);
			idleReason = ReactorIdleReason.RUNNING.ordinal();
			// MOD-473: the first EU this core ever made. Fired here rather than on the outlet, because
			// this is the tick the reaction actually paid out — a socket only ever hands on what it was
			// already given, and a room with no cable run yet would never reach one.
			if (!powerMilestoneOffered) {
				powerMilestoneOffered = true;
				awardMilestone(level, ReactorMilestone.POWER);
			}
		} else {
			// Cleared, not left over. A stale figure here would both mis-report on the panel and —
			// since MOD-472 — keep the room's drone alive on a core banking nothing. The reactor is
			// still burning, and the temperature above says so; this row is about the sale.
			lastOutput = 0;
			// A full buffer, or a reaction too weak to pay a whole EU. The old version reported the second as
			// "buffer full" on a buffer that was empty (audit, MOD-623).
			idleReason = (space <= 0 ? ReactorIdleReason.BUFFER_FULL : ReactorIdleReason.RUNNING).ordinal();
		}
	}

	/** The room's temperature after this tick: what the reaction put in, less what the water and the shell took. */
	private void settleHeat(HeatFlow flow, long cooling, long produced) {
		// May go NEGATIVE, and must: the recovery term boils more than this tick's heat so a core that ran
		// away comes back down. Clamping it at zero threw that surplus away — the loop drank the water and
		// the temperature did not move. settleHeat clamps the temperature at zero, the right place for the floor.
		long settled = ReactorCore.settleHeat(heat, flow.heatIn() - flow.carried(), cooling,
				ReactorConfig.reactorHeatCapacity);
		if (settled != heat || produced > 0) {
			heat = settled;
			setChanged();
		}
	}

	/**
	 * One tick of the bare core's own scale (MOD-471).
	 *
	 * <p><b>Why a bare reactor needs a scale at all, when it deliberately makes no heat.</b> Players
	 * discovered that a bare core is a lava generator — it melts the scenery, the scenery is cobblestone,
	 * and a pump underneath turns that into an endless supply. That invention stays, and it stays free:
	 * the melt costs no fuel, because the hazard hangs on the reaction rather than on the sale. But a
	 * mechanic with no ceiling is not a choice, and until now a bare pile was strictly safer than the
	 * sealed room that was supposed to be the safe option.
	 *
	 * <p>So the danger of a bare core is measured by the one thing it actually has: the size of the pile.
	 * Gain is linear in the rods, decay is a share of the current value — the same curve the room's heat
	 * runs on, and with it the same property. A small cluster has an equilibrium below the ceiling and
	 * sits there for ever; a large one has an equilibrium above it and therefore runs away. On the
	 * shipped numbers that boundary falls between three racks and four: the farm has a limit the player
	 * reads off the panel instead of out of a config file.
	 *
	 * <p>Driven by {@link #reacting} — the reaction, not the sale. A bare core with a full buffer is
	 * still a bare core, exactly as MOD-469's playtest concluded for the melting.
	 */
	private void settleInstability(int liveRods) {
		long gain = room.isBare() && reacting
				? ReactorCore.instabilityGain(liveRods, ReactorConfig.reactorBareInstabilityPerRod)
				// Scrammed, or no longer bare: it only falls. A pile the player switched off has to become
				// safe again, or the scram is not a scram.
				: 0L;
		long next = ReactorCore.settleHeat(instability, gain,
				ReactorCore.instabilityDecay(instability, ReactorConfig.reactorBareSettlePermille),
				ReactorConfig.reactorBareInstabilityCapacity);
		if (next != instability) {
			instability = next;
			// Saved since MOD-727, so a change has to reach the chunk's save like the room's heat does.
			setChanged();
		}
	}

	/**
	 * The scale this reactor is judged on, as a percentage — heat in a room, instability in the open.
	 *
	 * <p>One accessor so the countdown, the panel and the tests cannot disagree about which scale is
	 * live. A controller is either running a room or running bare; it is never both.
	 */
	private int criticalPercent() {
		return room.isBare()
				? ReactorCore.heatPercent(instability, ReactorConfig.reactorBareInstabilityCapacity)
				: ReactorCore.heatPercent(heat, ReactorConfig.reactorHeatCapacity);
	}

	/**
	 * The countdown between a pinned gauge and the explosion — see {@link ReactorBlastTimer#runCountdown}. Before a
	 * blast the room is taken apart and the hidden accident step is handed out (MOD-473), while the controller still
	 * stands.
	 */
	private void runCountdown(ServerLevel level, BlockPos pos) {
		blast.runCountdown(level, pos, ReactorCore.isCritical(criticalPercent()), room,
				kind -> log.event(getLevel(), kind, 0, 0, 0, ""), () -> {
					awardMilestone(level, ReactorMilestone.BLAST);
					unformOnRemoval(level);
				});
	}

	/** One tick of whatever this reactor is currently destroying — see {@link ReactorHazards#run}. */
	private void runHazards(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		heat = hazards.run(serverLevel, pos, heat, reacting, room, log,
				() -> awardMilestone(level, ReactorMilestone.MELTDOWN));
	}

	/** Sounds the overheat siren on the scale this reactor is judged on — see {@link ReactorVoice#warnOnOverheat}. */
	private void warnOnOverheat(Level level, BlockPos pos) {
		// The scale the reactor is judged on, not the room's heat alone. A bare pile makes no heat — its
		// danger is instability — so reading heat here left the one reactor with no walls silent through its
		// whole countdown (playtest, MOD-623). Now both scales sound the same way: one blast crossing the
		// warning line, then the siren for as long as the scale sits at the top.
		int percent = criticalPercent();
		// The siren's one blast is the log's line (MOD-622): its latch is persisted, so a reload does not repeat it.
		// A bare pile's scale is instability rather than heat, and the line says so.
		voice.warnOnOverheat(level, pos, percent,
				() -> log.event(getLevel(), ReactorLog.Kind.OVERHEAT, percent, room.isBare() ? 1 : 0, 0, ""));
	}

	/**
	 * Tops up the room's sockets from the reactor's buffer, round by round.
	 *
	 * <p>Even-handed rather than first-come: a room with two outlets on opposite walls must not have
	 * the one the scan happened to reach first starve the other whenever the buffer is short. Each
	 * round hands every socket an equal share of what is left, and rounds stop as soon as one changes
	 * nothing — which is also what makes a room whose sockets are all full cost a single pass.
	 */
	private void feedOutlets(Level level) {
		List<BlockPos> outlets = room.outlets();
		if (outlets.isEmpty() || energy.getAmount() <= 0) {
			return;
		}
		List<ReactorOutletBlockEntity> sockets = new ArrayList<>(outlets.size());
		for (BlockPos at : outlets) {
			if (level.getBlockEntity(at) instanceof ReactorOutletBlockEntity socket) {
				sockets.add(socket);
			}
		}
		while (!sockets.isEmpty() && energy.getAmount() > 0) {
			long share = Math.max(1, energy.getAmount() / sockets.size());
			long moved = 0;
			for (ReactorOutletBlockEntity socket : sockets) {
				if (energy.getAmount() <= 0) {
					break;
				}
				long taken = socket.fillFromReactor(Math.min(share, energy.getAmount()));
				energy.setAmountUntracked(energy.getAmount() - taken);
				moved += taken;
			}
			if (moved == 0) {
				break;
			}
		}
	}

	/**
	 * Adjacent pairs of FUELLED columns among the ones collected this tick.
	 *
	 * <p>Adjacency is about fuel, not about racks: two empty columns side by side breed nothing, and
	 * counting them would pay a neighbour bonus for scaffolding.
	 */
	private static int countNeighbourPairs(List<FuelRodAssemblyBlockEntity> columns) {
		java.util.Set<BlockPos> loaded = new java.util.HashSet<>();
		for (FuelRodAssemblyBlockEntity column : columns) {
			if (column.hasFuel()) {
				loaded.add(column.getBlockPos());
			}
		}
		int pairs = 0;
		for (BlockPos rack : loaded) {
			if (loaded.contains(rack.east())) {
				pairs++;
			}
			if (loaded.contains(rack.above())) {
				pairs++;
			}
			if (loaded.contains(rack.south())) {
				pairs++;
			}
		}
		return pairs;
	}

	/**
	 * Boils the water this tick calls for and returns how much boiled, in mB (MOD-623).
	 *
	 * <p><b>Every running room needs its loop.</b> The water carries the reaction's whole heat — nothing else
	 * cools a working room — plus a twentieth of the heat already stored, which is what brings a core that
	 * ran dry back down within a few seconds. Neither term waits for a threshold any more;
	 * {@link ReactorCore#waterDemand} says why.
	 *
	 * <p>Whatever the columns could not boil stays on the heat scale. That is
	 * the entire failure mode of a starved loop: not an error message, a rising gauge.
	 */
	private long coolWithWater(List<FuelRodAssemblyBlockEntity> columns, long reactionHeat) {
		long wanted = ReactorCore.waterDemand(reactionHeat, heat, ReactorConfig.reactorHeatPerWater);
		if (wanted <= 0 || columns.isEmpty()) {
			lastWater = 0;
			return 0;
		}
		long boiled = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			if (boiled >= wanted) {
				break;
			}
			boiled += column.boil(wanted - boiled);
		}
		lastWater = (int) Math.min(Short.MAX_VALUE, boiled);
		// MOD-473: the coolant loop did work for the first time. The step is "this room boiled water",
		// not "steam left through the nozzle": the exhaust is a plain block entity with no owner and no
		// way back to the reactor that filled it, so crediting a player there is not possible at all.
		if (boiled > 0 && !steamMilestoneOffered) {
			steamMilestoneOffered = true;
			awardMilestone(level, ReactorMilestone.STEAM);
		}
		return boiled;
	}


	/**
	 * Charges this tick's output against the rods, split across the columns in proportion to how many
	 * each holds.
	 *
	 * <p>Proportional rather than one column at a time: the racks are one core, and draining them in
	 * scan order would empty the corner the scan happens to start from while the rest sat full. The
	 * remainder of the division goes to the first column with fuel, so a room whose rod count does not
	 * divide the output evenly still pays for every EU it made.
	 */
	private void burnFuel(List<FuelRodAssemblyBlockEntity> columns, long output) {
		int total = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			total += column.getRods();
		}
		if (total <= 0 || output <= 0) {
			return;
		}
		long handed = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			long share = output * column.getRods() / total;
			if (share > 0) {
				column.burn(share);
				handed += share;
			}
		}
		if (handed < output) {
			for (FuelRodAssemblyBlockEntity column : columns) {
				if (column.getRods() > 0) {
					column.burn(output - handed);
					break;
				}
			}
		}
	}

	/** Reads the state once and names the first thing standing between the reactor and running. */
	private ReactorIdleReason idleReasonFor(Level level, BlockPos pos, boolean sealed) {
		// "Shell open" is the right answer only while there is nothing else going on. A bare reactor with
		// racks in reach is not waiting for a shell — it is a running machine, and telling its owner to
		// close a shell they never intended to build would send them to fix the wrong thing.
		if (!sealed && !room.isBare()) {
			return ReactorIdleReason.NOT_SEALED;
		}
		if (room.rods() <= 0) {
			return ReactorIdleReason.NO_FUEL;
		}
		// The throttle is a room control; a bare core ignores it (see runReactor), so naming it here would
		// point at a slider that changes nothing.
		if (sealed && depthPermille <= 0) {
			return ReactorIdleReason.RODS_WITHDRAWN;
		}
		if (!level.hasNeighborSignal(pos)) {
			return ReactorIdleReason.NO_SIGNAL;
		}
		if (energy.getAmount() >= energy.getCapacity()) {
			return ReactorIdleReason.BUFFER_FULL;
		}
		return ReactorIdleReason.RUNNING;
	}

	private int steamPercent() {
		return tankPercent(false);
	}

	private int waterPercent() {
		return tankPercent(true);
	}

	/**
	 * Coolant or steam in the room as a percentage of every column's capacity together.
	 *
	 * <p>Rounded to NEAREST, not truncated. The pipe network stops moving fluid once the imbalance
	 * between a segment and its neighbour is down to a single millibucket, so a loop that is genuinely
	 * full parks a few mB short of capacity — and truncation reported that as 99% forever. A readout
	 * that can never reach its own maximum reads as broken, and here it would be.
	 */
	private int tankPercent(boolean water) {
		List<BlockPos> assemblies = room.assemblies();
		if (level == null || assemblies.isEmpty()) {
			return 0;
		}
		long held = 0;
		long capacity = 0;
		for (BlockPos rack : assemblies) {
			if (level.getBlockEntity(rack) instanceof FuelRodAssemblyBlockEntity column) {
				held += water ? column.waterAmount() : column.steamAmount();
				capacity += water ? column.waterCapacity() : column.steamCapacity();
			}
		}
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(100, (held * 200 + capacity) / (capacity * 2));
	}

	private void rescan(Level level, BlockPos pos, BlockState state) {
		RoomScan.Result result = room.measure(level, pos, state.getValue(ReactorControllerBlock.FACING));
		ReactorRoomStatus scanned = ReactorRoomStatus.of(result.status());
		boolean wasFormed = state.getValue(ReactorControllerBlock.FORMED);
		boolean changed = room.adopt(scanned);
		int repainted = room.paintShell(level, result);
		if (wasFormed != result.formed()) {
			level.setBlock(pos, state.setValue(ReactorControllerBlock.FORMED, result.formed()), 3);
		}
		if (changed) {
			setChanged();
			syncBlockEntityToClient();
		}
		if (result.formed()) {
			room.claimRoom(level, result);
		} else {
			// Silence the racks BEFORE forgetting where they are (MOD-472). The drone is painted onto the
			// columns and cleared the same way, so a list emptied first would leave the flag set on blocks
			// nothing owns any more — a breached room that goes on humming for as long as it stands.
			//
			// Order matters twice over: silenceColumns resolves the columns through collectColumns, which
			// answers with the BARE list once the flag is set, so the bare sweep has to come after the room
			// list has been silenced and cleared. Otherwise a breach would silence the wrong racks.
			voice.silence(level, room);
			room.forgetRoom();
			room.rescanBare(level, pos);
		}
		log.logRoom(getLevel(), result, wasFormed, room.isBare(), room.bareRacks().size());

		if (level instanceof ServerLevel serverLevel) {
			if (result.formed() && !wasFormed) {
				ReactorRoom.announceAssembled(serverLevel, pos, repainted);
				// MOD-473: the same edge the room announces itself on — the scan that turned a shell into
				// a sealed room. No latch needed: this branch is an edge by construction.
				ModCriteria.fireReactorMilestone(serverLevel, getOwner(), ReactorMilestone.ROOM_SEALED);
			} else if (!result.formed() && scanned.hasLocation()) {
				ReactorRoom.markProblem(serverLevel, new BlockPos(result.x(), result.y(), result.z()), wasFormed);
				// Every listed hole smokes, not only the first (playtest, MOD-619): a player with three holes to
				// fill walks to three plumes. The first is the one above; the alarm sounds once, there.
				for (int i = 1; i < result.listedHoles(); i++) {
					ReactorRoom.markProblem(serverLevel, new BlockPos(result.holeX(i), result.holeY(i),
							result.holeZ(i)), false);
				}
			}
		}
	}

	/**
	 * The core as the «Core» tab shows it (MOD-620): one entry per stack of columns, counted from the sealed room's
	 * north-west interior corner — or, with no sealed room, from the corner of the reachable racks' own footprint.
	 *
	 * <p>Built from what the last scan found, so the tab shows the columns that belong to this controller: a rack
	 * outside the room, or one another controller claims, is not on it. With no sealed room that is every reachable
	 * rack, including one holding only spent casings — it burns nothing, but it is the rack that needs a player.
	 */
	public dev.alaindustrial.network.ReactorZonePayload zoneSnapshot(int containerId) {
		boolean sealed = room.isSealed();
		List<BlockPos> racks = sealed ? room.assemblies() : room.bareShown();
		List<dev.alaindustrial.core.structure.ReactorZone.Column> columns = new ArrayList<>();
		if (level != null) {
			for (BlockPos at : racks) {
				if (level.getBlockEntity(at) instanceof FuelRodAssemblyBlockEntity rack) {
					columns.add(rack.zoneColumn());
				}
			}
		}
		int originX = 0;
		int originZ = 0;
		int width = 0;
		int depth = 0;
		if (sealed && room.box().isSet()) {
			originX = room.box().minX();
			originZ = room.box().minZ();
			width = room.box().maxX() - room.box().minX() + 1;
			depth = room.box().maxZ() - room.box().minZ() + 1;
		} else if (!columns.isEmpty()) {
			int minX = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (dev.alaindustrial.core.structure.ReactorZone.Column column : columns) {
				minX = Math.min(minX, column.x());
				minZ = Math.min(minZ, column.z());
				maxX = Math.max(maxX, column.x());
				maxZ = Math.max(maxZ, column.z());
			}
			originX = minX;
			originZ = minZ;
			width = maxX - minX + 1;
			depth = maxZ - minZ + 1;
		}
		width = Math.min(width, dev.alaindustrial.core.structure.ReactorZone.MAX_SPAN);
		depth = Math.min(depth, dev.alaindustrial.core.structure.ReactorZone.MAX_SPAN);
		return new dev.alaindustrial.network.ReactorZonePayload(containerId, originX - worldPosition.getX(),
				originZ - worldPosition.getZ(), width, depth,
				dev.alaindustrial.core.structure.ReactorZone.stacks(columns, originX, originZ, width, depth,
						ReactorCore.rodEnergy(ReactorConfig.reactorEuPerRod, ReactorConfig.reactorRodBurnTicks)));
	}

	/**
	 * Hands the last sealed shell back its ordinary unbuilt look when the controller is mined, and puts
	 * its lights out.
	 *
	 * <p>Without this a room stayed visually sealed forever: the seamless art, the interior light and
	 * the {@code formed} flag are painted by the controller, so removing the one block that maintains
	 * them left a structure that looked assembled and was not, with no way to un-form it short of
	 * breaking a wall.
	 *
	 * <p><b>This has now been in two wrong places, and the second one was worse than the first.</b>
	 * The block's {@code affectNeighborsAfterRemoval} runs after the block entity has been detached, so
	 * the lookup inside it finds nothing and the room simply stays formed. Moving it to
	 * {@code BlockEntity.setRemoved} fixed that and introduced a hang: {@code setRemoved} is also how
	 * every block entity in a chunk is told the chunk is going away, and reading a block state from
	 * inside that teardown makes the server thread wait on the very chunk operation it is running. The
	 * symptom was a client that stopped dead at "Saving worlds" and had to be killed by the shutdown
	 * watchdog.
	 *
	 * <p>So it lives on the player's own removal path, which is the only one where anything is safe to
	 * touch: the block entity is still attached, the chunk is not being torn down, and nothing here can
	 * re-enter the world. A controller taken out by an explosion or a piston leaves the shell painted —
	 * a cosmetic loose end, and a far better failure than a world that will not save.
	 */
	public void unformOnRemoval(Level level) {
		// Same reason as on a breach: the racks wear the drone flag, and the controller is the only thing
		// that can take it off them (MOD-472).
		voice.silence(level, room);
		room.box().clear(level);
	}

	@Override
	protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		room.box().save(output);
		output.putLong("Heat", heat);
		voice.saveWarned(output);
		output.putInt("Depth", depthPermille);
		blast.save(output);
		output.putLong(INSTABILITY_KEY, instability);
		log.save(output);
	}

	@Override
	protected void loadMachineData(net.minecraft.world.level.storage.ValueInput input) {
		super.loadMachineData(input);
		room.box().load(input);
		heat = input.getLongOr("Heat", 0L);
		voice.loadWarned(input);
		depthPermille = input.getIntOr("Depth", ReactorCore.FULL_DEPTH);
		blast.load(input);
		instability = input.getLongOr(INSTABILITY_KEY, 0L);
		log.load(input);
	}

	/**
	 * What chunk loading and status syncs send to every player nearby, minus the event log (MOD-622): the log reaches
	 * a player only through the controller's screen, while it is open.
	 */
	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
		net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(provider);
		ReactorEventLog.stripFromUpdateTag(tag);
		return tag;
	}

	/** The event log, oldest first — for the menu and for tests. */
	public java.util.List<ReactorLog.Entry> logEntries() {
		return log.entries();
	}

	/** The «Log» tab's snapshot for one viewer: every entry, and how far that player has read. */
	public dev.alaindustrial.network.ReactorLogPayload logSnapshot(int containerId, java.util.UUID viewer) {
		return log.snapshot(containerId, viewer);
	}

	/**
	 * Records that a player has seen the log up to {@code seq} — the newest entry their screen was actually sent,
	 * never "now": an alarm written after that send has not reached them and must keep their badge lit.
	 */
	public void markLogSeen(java.util.UUID viewer, int seq) {
		log.markSeen(viewer, seq);
	}

	/**
	 * Hands a milestone to this controller's owner, if the reactor is on a server and they are online
	 * (MOD-473). A no-op off-server and for an unowned controller — the {@code /ala demo} stand runs a
	 * reactor nobody placed.
	 */
	private void awardMilestone(Level level, ReactorMilestone milestone) {
		if (level instanceof ServerLevel serverLevel) {
			ModCriteria.fireReactorMilestone(serverLevel, getOwner(), milestone);
		}
	}

	/**
	 * The console's channels ({@link ReactorChannels}), each bound to the state it reports. All read-only:
	 * every readout is derived from the scan and server-authoritative; only the base energy and progress
	 * take a write.
	 */
	@Override
	protected SyncChannels createChannels() {
		return channels(ReactorChannels.class)
				.read(ReactorChannels.STATUS, () -> room.status().ordinal())
				.read(ReactorChannels.BREACH_DX, () -> room.readout().breachDx())
				.read(ReactorChannels.BREACH_DY, () -> room.readout().breachDy())
				.read(ReactorChannels.BREACH_DZ, () -> room.readout().breachDz())
				.read(ReactorChannels.SIZE_X, () -> room.readout().sizeX())
				.read(ReactorChannels.SIZE_Y, () -> room.readout().sizeY())
				.read(ReactorChannels.SIZE_Z, () -> room.readout().sizeZ())
				.read(ReactorChannels.HEAT_PERCENT, () -> ReactorCore.heatPercent(heat,
						ReactorConfig.reactorHeatCapacity))
				.read(ReactorChannels.RODS, () -> room.rods())
				.read(ReactorChannels.DEPTH_PERCENT, () -> depthPermille / 10)
				.read(ReactorChannels.OUTPUT, () -> lastOutput)
				.read(ReactorChannels.WATER_PERCENT, () -> waterPercent())
				.read(ReactorChannels.WATER_RATE, () -> lastWater)
				.read(ReactorChannels.STEAM_PERCENT, () -> steamPercent())
				.read(ReactorChannels.IDLE_REASON, () -> idleReason)
				.read(ReactorChannels.ENERGY_PERCENT, () -> energy.getCapacity() <= 0 ? 0
						: (int) Math.min(100, energy.getAmount() * 100 / energy.getCapacity()))
				.read(ReactorChannels.ENERGY_HUNDREDS, () -> (int) Math.min(Short.MAX_VALUE, energy.getAmount() / 100))
				.read(ReactorChannels.MELTDOWN, () -> hazards.isMeltingDown() ? 1 : 0)
				.read(ReactorChannels.BLAST_PERCENT, () -> blast.percentLeft())
				.read(ReactorChannels.INSTABILITY, () -> room.isBare()
						? ReactorCore.heatPercent(instability, ReactorConfig.reactorBareInstabilityCapacity) : 0)
				.read(ReactorChannels.COOLANT_SHARE, () -> coolantShare)
				.read(ReactorChannels.HEAT_WARN, () -> ReactorConfig.reactorHeatWarnPercent)
				.read(ReactorChannels.HEAT_MELTDOWN, () -> ReactorConfig.reactorMeltdownStartPercent)
				.read(ReactorChannels.BOX_WEST, () -> room.readout().boxWest())
				.read(ReactorChannels.BOX_NORTH, () -> room.readout().boxNorth())
				.read(ReactorChannels.ROOM_MIN_INNER, () -> ReactorConfig.reactorRoomMinInner)
				.read(ReactorChannels.ROOM_MAX_INNER, () -> ReactorConfig.reactorRoomMaxInner)
				.read(ReactorChannels.ROOM_MAX_GLASS, () -> ReactorConfig.reactorRoomMaxGlassPercent)
				.read(ReactorChannels.HOLE_COUNT, () -> Math.min(Short.MAX_VALUE, room.readout().holeCount()))
				.tail(3 * RoomScan.MAX_LISTED_HOLES,
						offset -> offset < room.readout().holeOffsetCount() ? room.readout().holeOffset(offset) : 0)
				.build();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.reactor_controller");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ReactorControllerMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	/**
	 * The <em>verdict</em> is not stored: the room is the save, and the first tick after load re-derives
	 * the status from whatever the shell actually looks like — persisting "formed" would let a stale yes
	 * survive a world edit that took the walls away.
	 *
	 * <p>The sealed box itself IS stored (see {@code saveAdditional}), and for the opposite reason: it
	 * is not a verdict but the address of the shell this controller must be able to switch back off. A
	 * chunk can unload while the room is whole and reload after a creeper has opened it, and a
	 * controller that forgot its box would leave that shell stuck looking sealed for good.
	 */
	@Override
	public boolean tracksOwner() {
		return true;
	}

	/**
	 * Every face but the screen pushes power out. The front carries the panel the player reads, and
	 * R-NRG-03 keeps it energy-inert so a cable never draws an arm across the interface.
	 */
	@Override
	public dev.alaindustrial.core.energy.EnergyRole energyRoleForFace(Direction worldFace) {
		// The controller is the panel, not the socket: power leaves the room through
		// {@code reactor_outlet} blocks set into the shell, which this block tops up every tick. Its
		// own faces are almost all unreachable anyway — four are buried in the wall and one opens into
		// a sealed room — so publishing them would promise a connection a player cannot make.
		return worldFace == getBlockState().getValue(ReactorControllerBlock.FACING)
				? dev.alaindustrial.core.energy.EnergyRole.NONE
				: dev.alaindustrial.core.energy.EnergyRole.OUT;
	}

	/** Moves the throttle with nobody to name in the log. */
	public void setDepthPermille(int value) {
		setDepthPermille(value, "");
	}

	/**
	 * Moves the throttle. Called from the menu's button handler, clamped here rather than there. The log names the
	 * player who moved it (MOD-622) — on a server with several players, the question the log is asked most.
	 */
	public void setDepthPermille(int value, String actor) {
		int clamped = Math.min(ReactorCore.FULL_DEPTH, Math.max(0, value));
		if (clamped != depthPermille) {
			log.depthChanged(getLevel(), depthPermille, clamped, actor);
			depthPermille = clamped;
			setChanged();
			syncBlockEntityToClient();
			wake();
		}
	}

	/** EU produced on the last tick. Zero while idle; {@link #getIdleReason()} then says why. */
	public int getLastOutput() {
		return lastOutput;
	}

	/** Rods racked across the whole room, as the last scan counted them. */
	public int getRods() {
		return room.rods();
	}

	/** Why the reactor produced nothing, or {@code RUNNING}. */
	public ReactorIdleReason getIdleReason() {
		return ReactorIdleReason.byOrdinal(idleReason);
	}

	public long getHeat() {
		return heat;
	}

	// ── MOD-469 ──

	/** Whether this controller is running on racks it found in the open, with no room around it. */
	public boolean isBare() {
		return room.isBare();
	}

	/** Whether the room is melting its own contents right now. */
	public boolean isMeltingDown() {
		return hazards.isMeltingDown();
	}

	/**
	 * Whether this controller currently holds a sealed room.
	 *
	 * <p>Asked by {@link BareReactorScan} on behalf of a NEIGHBOURING controller: a rack inside a working
	 * room belongs to that room and to nothing else, and this is how a bare machine finds that out
	 * without reaching into another block entity's internals.
	 */
	public boolean isRoomSealed() {
		return room.isSealed();
	}

	// ── MOD-471 ──

	/** Stacks this controller has puffed steam over since it was loaded (MOD-662) — "are the puffs being sent". */
	public int getSteamPuffsSent() {
		return voice.steamPuffsSent();
	}

	/** Blocks this reactor has marked for melting since it was loaded — "is the hazard running". */
	public int getMeltsScheduled() {
		return hazards.meltsScheduled();
	}

	/** Ticks left before this core blows up; zero when no accident is under way. */
	public int getBlastCountdown() {
		return blast.remaining();
	}

	/** What the countdown started from — the duration this particular accident rolled. */
	public int getBlastCountdownTotal() {
		return blast.total();
	}

	/** A bare core's instability on its own 0…capacity scale. Always zero for a sealed room. */
	public long getInstability() {
		return instability;
	}

	/** Whether {@code at} lies inside the interior this controller last sealed — see {@link ReactorRoom}. */
	public boolean sealedBoxContains(BlockPos at) {
		return room.box().contains(at);
	}

	/** Whether the warning siren has sounded and not re-armed yet, on whichever scale is live (MOD-623). */
	public boolean hasSoundedOverheatAlarm() {
		return voice.overheatWarned();
	}

}

package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.advancement.ReactorMilestone;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.structure.BareReactorScan;
import dev.alaindustrial.core.structure.ReactorBlast;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorMeltdown;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.core.structure.RoomValidator;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The reactor controller's block entity (MOD-468, stage 1): it re-scans the room and publishes the
 * verdict to the screen.
 *
 * <p><b>Why it re-scans on a timer as well as on demand.</b> {@code neighborChanged} only fires for
 * the six blocks touching the controller, and a reactor room is up to 14 blocks across — a wall mined
 * on the far side, a door blown up by a creeper, a port pushed by a piston are all invisible to it. A
 * periodic sweep is the only way a controller notices its own room being taken apart, so the scan runs
 * every {@link Config#reactorScanIntervalTicks} even when nothing nearby changed.
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
public class ReactorControllerBlockEntity extends MachineBlockEntity implements MenuProvider {

	/** Base four plus: status, breach (3), size (3), heat/rods/depth/output (4), water/steam/idle/energy (5), meltdown, blast, instability. */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 20;
	public static final int DATA_STATUS = 4;
	public static final int DATA_BREACH_DX = 5;
	public static final int DATA_BREACH_DY = 6;
	public static final int DATA_BREACH_DZ = 7;
	public static final int DATA_SIZE_X = 8;
	public static final int DATA_SIZE_Y = 9;
	public static final int DATA_SIZE_Z = 10;
	public static final int DATA_HEAT_PERCENT = 11;
	public static final int DATA_RODS = 12;
	public static final int DATA_DEPTH_PERCENT = 13;
	public static final int DATA_OUTPUT = 14;
	/** Coolant left in the loop, as a percentage of what every column in the room could hold. */
	public static final int DATA_WATER_PERCENT = 15;
	/** Coolant boiled last tick, in mB — the loop's demand, and what an inlet has to keep up with. */
	public static final int DATA_WATER_RATE = 16;
	/**
	 * Steam waiting in the columns, as a percentage of what they can hold. Shown nowhere as a number
	 * — there is no room on the panel and it would be one gauge too many — but a full one is why a
	 * loop with plenty of water stops cooling, and the coolant bar changes colour on it. Without
	 * that, a blocked exhaust presents as a full tank next to a rising temperature, which reads as a
	 * bug rather than as a plumbing mistake.
	 */
	public static final int DATA_STEAM_PERCENT = 17;
	/** Ordinal of {@link ReactorIdleReason} — what the output row says instead of a bare dash. */
	public static final int DATA_IDLE_REASON = 18;
	/** Charge in the reactor's buffer, 0…100 — what the gauge fills to. */
	public static final int DATA_ENERGY_PERCENT = 19;
	/**
	 * Charge in HUNDREDS of EU, not in EU.
	 *
	 * <p>{@code ContainerData} is replicated as signed 16-bit shorts, and this buffer holds 200 000:
	 * sent raw it would arrive as a negative number and the readout would show nonsense at exactly the
	 * moment the reactor was doing well. Hundreds keep the whole range inside 2000, and a hundred EU is
	 * far below anything a player can read off a bar anyway.
	 */
	public static final int DATA_ENERGY_HUNDREDS = 20;
	/**
	 * 1 while the sealed room is melting its own contents (MOD-469).
	 *
	 * <p><b>Its own channel, and deliberately not a {@link ReactorRoomStatus} value.</b> That enum is
	 * recomputed from the shell's geometry by every sweep, so a "meltdown" written into it would be
	 * overwritten by the next scan — at most {@code reactorScanIntervalTicks} later — on a room that is
	 * still melting. The status answers "what shape is the shell in"; this answers "what is happening
	 * inside it", and the two have different lifetimes.
	 */
	public static final int DATA_MELTDOWN = 21;
	/**
	 * How much of the accident countdown is left, 0…100 (MOD-471).
	 *
	 * <p><b>A share, not the seconds, and that is the whole reason the channel exists in this shape.</b>
	 * The countdown is rolled fresh per accident between two and three minutes precisely so a player
	 * cannot learn its length; shipping the raw tick count would let the panel print a stopwatch and
	 * hand that knowledge straight back. A bar that empties tells them time is running out without
	 * telling them exactly how much is left. 0 means no accident is under way.
	 */
	public static final int DATA_BLAST_PERCENT = 22;
	/**
	 * The bare reactor's instability, 0…100 (MOD-471).
	 *
	 * <p>Its own channel rather than a second use of {@link #DATA_HEAT_PERCENT}: the two scales are
	 * never live at once, but they mean different things and a single channel would make the panel's
	 * gauge lie about which one it is showing the moment a breached room fell into bare mode with heat
	 * still on the clock.
	 */
	public static final int DATA_INSTABILITY = 23;

	/** Coolant boiled on the last tick, in mB. Zero while the reactor is cold or idle. */
	private int lastWater;

	/**
	 * Why the reactor produced nothing this tick, as an {@link ReactorIdleReason} ordinal.
	 *
	 * <p>A reactor that is built, sealed, fuelled and silent is the worst state this machine can be
	 * in: the panel used to print a dash for the output and leave the player to guess between a
	 * missing redstone signal, a closed throttle, an empty rack and a full buffer. Every one of those
	 * is a different fix.
	 */
	private int idleReason = ReactorIdleReason.RUNNING.ordinal();

	private ReactorRoomStatus status = ReactorRoomStatus.CONTROLLER_NOT_IN_WALL;
	private int breachDx;
	private int breachDy;
	private int breachDz;
	private int sizeX;
	private int sizeY;
	private int sizeZ;

	// ── stage 2: the reactor itself ──
	/** Heat on the 0…{@link Config#reactorHeatCapacity} scale. */
	private long heat;
	/** How deeply the control rods are lowered, 0…1000. The player's throttle. */
	private int depthPermille = ReactorCore.FULL_DEPTH;
	/** Rods burning across the room, refreshed each scan. */
	private int rods;
	/** What the last tick actually produced, for the readout. */
	private int lastOutput;
	/** Assemblies found inside the sealed room, refreshed on every scan. */
	private final List<BlockPos> assemblies = new ArrayList<>();

	/** Sockets set into this room's shell. Refreshed by the same sweep that finds the columns. */
	private final List<BlockPos> outlets = new ArrayList<>();

	// ── MOD-469: the bare reactor and the meltdown ──
	/**
	 * Racks a controller with no sealed room drives, refreshed by the same sweep as {@link #assemblies}
	 * and empty whenever the room is formed. The two lists are never both populated: a controller is
	 * either running a room or running in the open.
	 */
	private final List<BlockPos> bareRacks = new ArrayList<>();

	/**
	 * Whether this controller is running without a room around it.
	 *
	 * <p>Decided by the scan, not by the status alone: <em>every</em> status but {@code FORMED} could be
	 * a player halfway through building their shell, and those two cases want different things from the
	 * panel. A controller only counts as bare once the sweep has actually found racks to burn.
	 */
	private boolean bare;

	/** Whether the room is melting its own contents right now — the panel's "Meltdown" line. */
	private boolean meltingDown;

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

	/**
	 * The block marked to melt and the ticks left before it does.
	 *
	 * <p><b>Not persisted, on purpose.</b> A pending melt is at most two seconds of intent; carrying it
	 * through a chunk round-trip would mean writing a position to NBT so that a block the player never
	 * saw marked could melt on a world they have just loaded. Forgetting it and picking again is both
	 * cheaper and fairer.
	 */
	@org.jspecify.annotations.Nullable
	private BlockPos meltTarget;

	private int meltCountdown;

	/** Ticks until the next victim is chosen. Zero means "pick on the next tick that qualifies". */
	private int meltCooldown;

	/**
	 * Blocks this reactor has marked for melting since it was loaded.
	 *
	 * <p>Counts the CHOICE, not the change, so it moves even with {@code reactorMeltdownMeltsBlocks}
	 * off — the question it answers is "is the hazard running", which is exactly what a switch is not
	 * supposed to alter.
	 *
	 * <p><b>It exists because the hazard is otherwise unobservable except through the world</b>, and the
	 * world is a bad oracle for it: the melt reaches five blocks from any rack, a gametest rig is eight
	 * across, and where the victims land differs between the two loaders. A scenario counting lava
	 * passed on Fabric and failed on NeoForge with nothing between them but structure layout. Not
	 * persisted — it is a live counter, not a record.
	 */
	private int meltsScheduled;

	/** Ticks until the next sweep. Zero means "scan on the next server tick". */
	private int scanCooldown;

	// ── MOD-471: the accident at the top of the scale ──
	/**
	 * Instability of a bare core: the second scale, and the only one a reactor with no room has.
	 *
	 * <p><b>Deliberately not persisted.</b> It is a function of the pile's size and nothing else, so a
	 * chunk that reloads climbs back to the same equilibrium within seconds. Saving it would preserve
	 * nothing and would let a reactor come back from disk already at the top of a scale the player
	 * never watched fill.
	 */
	private long instability;

	/**
	 * Ticks left before this core blows up, or zero when no accident is under way.
	 *
	 * <p><b>Persisted, unlike everything else here, and for a specific reason.</b> The duration is
	 * rolled per accident; a countdown that reset on restart would turn "log out and back in" into a
	 * way to re-roll a bad number, and a server restart into a free rescue. The heat that caused it is
	 * already saved, so the accident survives anyway — this only keeps it honest about how far along it
	 * had got.
	 */
	private int blastCountdown;

	/** What {@link #blastCountdown} started from, so the panel can draw a share rather than seconds. */
	private int blastCountdownTotal;

	/**
	 * Consecutive ticks the scale has spent under a hundred percent while a countdown is armed.
	 *
	 * <p>Not persisted: it is at most a few seconds of intent, and a chunk that reloads mid-rescue
	 * simply asks the player to hold the core down a moment longer. The countdown itself IS persisted,
	 * so nothing is lost the other way round.
	 */
	private int blastBelowTicks;

	// ── MOD-472: the room's voice ──
	/**
	 * How many columns carry the drone at once.
	 *
	 * <p>Not "all of them", and the ceiling is the client's, not ours: a Minecraft client has on the
	 * order of twenty-five static sound channels for the whole game, and a minimum-size room packed
	 * solid already holds twenty-seven racks. Identical copies of one sample also sum at about +6 dB per
	 * doubling, so past a handful the room stops sounding bigger and starts sounding louder. Three keeps
	 * the drone spread across the floor — which is the whole reason it plays from the racks — while
	 * costing about a tenth of the channel budget.
	 */
	private static final int VOICED_COLUMNS = 3;

	/**
	 * Ticks the drone keeps playing after the last productive tick.
	 *
	 * <p><b>Without this the loop would stutter at twenty hertz.</b> A healthy reactor with somewhere to
	 * put its power alternates between producing and {@code BUFFER_FULL} tick by tick, because the
	 * sockets are drained and refilled every tick; and between the last rod burning out and the next room
	 * scan there is a gap of up to {@code reactorScanIntervalTicks}. Both would chop the sound to pieces.
	 * Two seconds of latch spans either.
	 */
	private static final int VOICE_LATCH_TICKS = 40;

	/** Counts down from {@link #VOICE_LATCH_TICKS} after the last tick that actually made power. */
	private int voiceLatch;

	/** Whether the drone was sounding last tick — the edge that fires the spin-down. */
	private boolean wasVoiced;

	/**
	 * Whether the overheat alarm has already sounded and not yet re-armed.
	 *
	 * <p>Persisted, because the alternative is an alarm that fires again every time the chunk reloads on
	 * a core that has been sitting hot and unattended the whole time.
	 */
	private boolean overheatWarned;

	/**
	 * Ticks until the critical alarm sounds again, while the core sits at the top of the scale.
	 *
	 * <p>Deliberately NOT persisted: on the tick a chunk reloads this is zero, so a core that is still
	 * critical announces itself immediately rather than waiting out a countdown nobody heard. There is
	 * nothing to preserve — the state that matters is the temperature, and that is saved.
	 */
	private int criticalAlarmCooldown;

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

	/**
	 * The box this controller last sealed, or an empty one if it never has.
	 *
	 * <p><b>This is what lets a room come apart.</b> A failed scan measures nothing, so a sweep driven
	 * by the scan result can only ever switch the shell ON — punch a hole in a finished room and every
	 * block stayed seamless and lit, which is precisely what the first playtest reported. Remembering
	 * the sealed box gives the controller something to clear.
	 *
	 * <p>Kept in NBT: a chunk can unload while the room is whole and reload after a creeper has opened
	 * it, and a controller that forgot its box on load would leave the shell stuck looking sealed.
	 */
	private int boxMinX;
	private int boxMinY;
	private int boxMinZ;
	private int boxMaxX = Integer.MIN_VALUE;
	private int boxMaxY = Integer.MIN_VALUE;
	private int boxMaxZ = Integer.MIN_VALUE;

	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		// Stage 2: a real HV producer. The buffer is sized to a few seconds of full output so a grid
		// that cannot take the power immediately does not stall the reactor mid-tick.
		super(ModContent.REACTOR_CONTROLLER_BE.get(), pos, state, EnergyTier.HV, 0,
				Config.reactorBuffer, 0L, EnergyTier.HV.maxVoltage());
	}

	/**
	 * No upgrade panel. {@link MachineBlockEntity}'s constructor appends four upgrade slots to every
	 * {@link MenuProvider} that says yes, and a controller with a hidden four-slot inventory would both
	 * accept hoppers and promise upgrades it does not have.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/** Re-arms the scan for the next tick — called when a neighbour changes or the block is placed. */
	public void requestScan() {
		scanCooldown = 0;
		wake();
	}

	public ReactorRoomStatus getStatus() {
		return status;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = Config.reactorScanIntervalTicks;
			rescan(level, pos, state);
		}
		runReactor(level, pos);
		// Never sleep: the periodic sweep is the only thing that notices a room being dismantled out of
		// neighbour range, and heat has to keep bleeding away even with the reactor shut down (R-29).
		return 0;
	}

	/**
	 * One tick of the reactor.
	 *
	 * <p><b>Fuel burns only when the energy is wanted</b> — the player's own call for this stage. A
	 * full buffer with nothing drawing from it costs no uranium, exactly as the charging station spends
	 * only per transfer. Heat, by contrast, is settled every tick whether the reactor ran or not: a
	 * shut-down core still has to cool down, and "scram and wait" must actually work.
	 *
	 * <p><b>And heat is PRODUCED whenever the reaction is running, full buffer or not</b> (MOD-471).
	 * That is not the same rule as the one above, and the difference is the whole of the accident: a
	 * reactor nobody is drawing from is still a reactor, and if its coolant is missing it still cooks
	 * itself to the top of the scale. See the block below for what a playtest looked like before it.
	 */
	private void runReactor(Level level, BlockPos pos) {
		boolean sealed = status == ReactorRoomStatus.FORMED;
		// No signal is the scram: a lever by the door stops the reaction without dismantling anything.
		// It is the ONE control a bare reactor still answers to. The throttle is deliberately not asked:
		// the bare panel has no room to show it, and a hidden control that silently holds a reactor at
		// zero is the worst kind — a player whose breached room stops producing would have no way to
		// learn that the slider they left at 0% is why. Bare rods are always fully lowered.
		boolean allowed = (sealed ? depthPermille > 0 : bare) && level.hasNeighborSignal(pos);
		long produced = 0;
		// Resolved ONCE per tick and handed to all three passes. Burning, boiling and levelling each
		// used to walk `assemblies` and call getBlockEntity themselves, which in a room packed to the
		// 12-block limit is several hundred chunk lookups a tick for a machine that ticks every tick.
		List<FuelRodAssemblyBlockEntity> columns = collectColumns(level);
		// Counted FRESH every tick, not taken from the periodic scan. `rods` is refreshed once every
		// reactorScanIntervalTicks, and output runs every tick — so a room whose last rod had just
		// burnt out went on making full power for up to two seconds, on nothing. Twenty thousand EU
		// out of thin air per refuelling, which is exactly the class of hole the fuel cycle closed
		// everywhere else.
		int liveRods = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			liveRods += column.getRods();
		}
		// Density is counted fresh too, for the same reason the rods are. It used to come from the
		// periodic scan, so for up to reactorScanIntervalTicks after a column was pulled the remaining
		// ones went on being paid a neighbour bonus for a rack that was no longer there — free EU, and
		// exactly the kind that is invisible because it is small and brief.
		int pairs = countNeighbourPairs(columns);
		// Recorded before the buffer is consulted: this is "the reaction is running", not "we sold power".
		boolean nowReacting = allowed && liveRods > 0;
		if (nowReacting != reacting) {
			reacting = nowReacting;
			setChanged();
		}

		if (allowed && liveRods > 0) {
			// What this core could give with the rods all the way down. The tier ceiling is applied to
			// THIS, and the throttle is applied after it — not the other way round. Clipping a
			// depth-scaled figure against the ceiling looked equivalent and was not: on any core whose
			// potential already cleared 512 EU/t every slider stop produced the same 512, so the control
			// the player was given did nothing at exactly the scale it was built for.
			long full = ReactorCore.output(liveRods, pairs, Config.reactorEuPerRod,
					Config.reactorNeighbourBonusPercent, ReactorCore.FULL_DEPTH);
			// Two ceilings: the tier's voltage (a reactor is an HV machine, and nothing in the mod could
			// carry tens of thousands of EU/t anyway) and whatever room is left in the buffer.
			long ceiling = Math.min(full, EnergyTier.HV.maxVoltage());
			// A bare core is scaled and capped instead of throttled. Both ceilings still apply above it,
			// so the bare cap can only ever make the figure smaller — it is a floor on how bad the
			// shortcut is, never a way around the tier.
			long wanted = bare
					? ReactorCore.bareOutput(ceiling, Config.reactorBarePowerPercent,
							Config.reactorBarePowerCap)
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
			long heatFull = bare ? 0 : ReactorCore.heatProduced(liveRods, pairs,
					Config.reactorHeatPerRod, Config.reactorHeatNeighbourBonusPercent,
					ReactorCore.FULL_DEPTH);
			produced = ReactorCore.heatForOutput(heatFull, wanted, full);

			long output = Math.min(wanted, energy.getCapacity() - energy.getAmount());
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
				idleReason = ReactorIdleReason.BUFFER_FULL.ordinal();
			}
		} else {
			lastOutput = 0;
			idleReason = idleReasonFor(level, pos, sealed).ordinal();
		}

		long cooling = ReactorCore.naturalCooling(heat, Config.reactorPassiveCooling,
				Config.reactorHeatLossPermille);
		if (bare) {
			// The coolant loop and the stack settling are the ROOM's plumbing. A bare rack has no shell
			// to plumb and makes no heat to answer, and running them anyway would quietly boil away water
			// a player had poured into a column for the room they are still building around it.
			lastWater = 0;
		} else {
			produced = coolWithWater(columns, produced, cooling);
		}

		long settled = ReactorCore.settleHeat(heat, produced, cooling, Config.reactorHeatCapacity);
		if (settled != heat || produced > 0) {
			heat = settled;
			setChanged();
		}
		settleInstability(liveRods);
		if (!bare) {
			settleStacks(columns);
		}
		feedOutlets(level);
		// Empty when bare, and that is the whole point (MOD-469 audit). The drone is painted ONTO the
		// racks and taken off them only inside the box this controller last sealed — a bare rack switched
		// on here would stand humming for as long as it existed, with nothing left in the world able to
		// switch it off. The latch and the spin-down edge still run, so a room that breaks with no racks
		// nearby still announces that it stopped.
		updateVoice(level, pos, bare ? List.of() : columns);
		warnOnOverheat(level, pos);
		runHazards(level, pos);
		if (level instanceof ServerLevel serverLevel) {
			runCountdown(serverLevel, pos);
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
		long gain = bare && reacting
				? ReactorCore.instabilityGain(liveRods, Config.reactorBareInstabilityPerRod)
				// Scrammed, or no longer bare: it only falls. A pile the player switched off has to become
				// safe again, or the scram is not a scram.
				: 0L;
		long next = ReactorCore.settleHeat(instability, gain,
				ReactorCore.instabilityDecay(instability, Config.reactorBareSettlePermille),
				Config.reactorBareInstabilityCapacity);
		if (next != instability) {
			instability = next;
		}
	}

	/**
	 * The scale this reactor is judged on, as a percentage — heat in a room, instability in the open.
	 *
	 * <p>One accessor so the countdown, the panel and the tests cannot disagree about which scale is
	 * live. A controller is either running a room or running bare; it is never both.
	 */
	private int criticalPercent() {
		return bare
				? ReactorCore.heatPercent(instability, Config.reactorBareInstabilityCapacity)
				: ReactorCore.heatPercent(heat, Config.reactorHeatCapacity);
	}

	/**
	 * The countdown between a pinned gauge and the explosion (MOD-471).
	 *
	 * <p><b>Armed by the scale and disarmed by the scale, which is what makes every cancellation work
	 * without any of them being written down.</b> Water, the scram lever, a hole punched in the wall,
	 * even unplugging the machines that were drawing the power — all four end the same way, with the
	 * gauge coming off a hundred percent, and that one condition covers them. There is no point of no
	 * return: the reactor can be saved on the last tick.
	 *
	 * <p>The duration is rolled once, when the countdown arms, somewhere between two and three minutes.
	 * A fixed delay would be memorised within a week and stop being read.
	 */
	private void runCountdown(ServerLevel level, BlockPos pos) {
		boolean critical = ReactorCore.isCritical(criticalPercent());
		ReactorCore.BlastTimer before =
				new ReactorCore.BlastTimer(blastCountdown, blastCountdownTotal, blastBelowTicks);
		// Rolled every tick and used only on the tick that arms — cheaper than branching, and it keeps
		// the whole transition inside one Minecraft-free function that a unit test can drive.
		int roll = ReactorCore.blastCountdown(Config.reactorBlastCountdownMinTicks,
				Config.reactorBlastCountdownMaxTicks, level.getRandom().nextInt(Integer.MAX_VALUE));
		ReactorCore.BlastTimer after = ReactorCore.tickBlast(before, critical,
				Config.reactorBlastReleaseTicks, roll);
		if (!after.equals(before)) {
			blastCountdown = after.remaining();
			blastCountdownTotal = after.total();
			blastBelowTicks = after.belowTicks();
			setChanged();
		}
		if (after.armed()) {
			if (critical) {
				ReactorBlast.telegraphCountdown(level, pos, after.remaining(), after.total());
			}
			return;
		}
		// Not armed any more. Either it was never armed, or the core has been held under the line long
		// enough to call the accident off — in both cases there is nothing to do. Only a timer that ran
		// out WHILE the core was still critical detonates.
		if (!before.armed() || !critical) {
			return;
		}
		// The switch is read HERE rather than at the top, so an operator who turned the damage off still
		// gets the whole performance — siren, particles, a panel counting down — and simply no crater. A
		// hazard that goes completely silent teaches nobody anything; MOD-469's rule, kept.
		if (Config.reactorBlastEnabled) {
			explode(level, pos);
		}
	}

	/**
	 * The accident itself.
	 *
	 * <p><b>The room is taken apart BEFORE the blast, and that order is load-bearing.</b>
	 * {@link #unformOnRemoval} only ever runs from the player's own mining hook, because touching the
	 * world from a block entity's removal path deadlocks the server on chunk unload — something this
	 * repository has already paid for once. A controller destroyed by an explosion therefore never runs
	 * it, and the racks it painted with the drone flag would hum for the rest of the world's life with
	 * nothing left able to switch them off. Here we ARE the explosion, so it can be done properly: while
	 * the controller is still standing.
	 */
	private void explode(ServerLevel level, BlockPos pos) {
		// MOD-473: the hidden accident step goes FIRST. Everything below this line dismantles the
		// reactor, and the last statement destroys the controller itself — a trigger fired after that
		// would be fired from a block entity the world has already dropped.
		awardMilestone(level, ReactorMilestone.BLAST);
		unformOnRemoval(level);
		BlockPos epicentre = blastEpicentre(pos);
		float power = ReactorCore.blastPower(rods, Config.reactorBlastBasePower,
				Config.reactorBlastPowerPerTenRods, Config.reactorBlastMaxPower);
		Set<BlockPos> before = ReactorBlast.snapshotSolids(level, epicentre, Config.reactorFalloutRadius);
		ReactorBlast.detonate(level, Vec3.atCenterOf(epicentre), power, Config.reactorBlastFire);
		// Everything after this is keyed to what the blast ACTUALLY destroyed, never to a radius. If a
		// land-claim mod refused the explosion, this list comes back empty and there is no aftermath at
		// all — the protection is honoured without this class knowing such mods exist.
		List<BlockPos> destroyed = ReactorBlast.destroyedSince(level, before);
		ReactorBlast.pourLava(level, destroyed, epicentre, Config.reactorBlastLavaCells);
		ReactorBlast.scatterFallout(level, destroyed, epicentre, Config.reactorFalloutRadius);
		// The controller goes LAST, and by hand rather than by hoping the blast reaches it.
		//
		// It is built of the same shielding alloy as the wall, so at the powers a small core produces
		// only a lucky ray breaks it — the first run of the gametest found the controller standing in a
		// gutted room. That is not a cosmetic loose end: the core it is still driving is still at a
		// hundred percent, so it would re-arm the countdown and explode again, and again, for ever. A
		// reactor gets to have exactly one accident.
		level.destroyBlock(pos, false);
	}

	/**
	 * Where the blast is centred.
	 *
	 * <p>The middle of the sealed interior for a room — the point furthest from every wall, so the shell
	 * gets its fair chance to contain the thing it was built to contain, and a fixed point a gametest can
	 * assert. For a bare core, the middle of the pile it was driving: there is no shell to be fair to,
	 * and the fuel is what exploded.
	 */
	private BlockPos blastEpicentre(BlockPos pos) {
		if (!bare && boxMaxX != Integer.MIN_VALUE) {
			return new BlockPos((boxMinX + boxMaxX) / 2, (boxMinY + boxMaxY) / 2, (boxMinZ + boxMaxZ) / 2);
		}
		if (!bareRacks.isEmpty()) {
			long x = 0;
			long y = 0;
			long z = 0;
			for (BlockPos at : bareRacks) {
				x += at.getX();
				y += at.getY();
				z += at.getZ();
			}
			int count = bareRacks.size();
			return new BlockPos((int) (x / count), (int) (y / count), (int) (z / count));
		}
		return pos;
	}

	/**
	 * One tick of whatever this reactor is currently destroying (MOD-469).
	 *
	 * <p>Two hazards, one schedule, because they can never be running at once: a sealed room melts its
	 * own contents when it is allowed to overheat, and a reactor with no room melts the scenery around
	 * it. A controller is one or the other.
	 *
	 * <p><b>The warning is issued even when the switch is off.</b> An operator who has turned the block
	 * damage off should still be shown that their reactor has reached the state where it would have
	 * melted something — a hazard that goes completely silent teaches nobody anything, and the switch is
	 * meant to protect the world, not to hide the reactor's condition.
	 */
	private void runHazards(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		// Melting the contents requires the room to still BE a room. A breached shell that is merely
		// still warm melts nothing: there is no containment left, so there is nothing being contained,
		// and its leftover heat simply bleeds away.
		boolean melting = status == ReactorRoomStatus.FORMED
				&& ReactorCore.isMeltingDown(ReactorCore.heatPercent(heat, Config.reactorHeatCapacity),
						Config.reactorMeltdownStartPercent);
		if (melting != meltingDown) {
			meltingDown = melting;
			setChanged();
			// MOD-473: the hidden meltdown step, on the EDGE rather than on a melted block. A room that
			// crosses the line has had its accident whether or not reactorMeltdownMeltsBlocks lets it
			// take the furniture with it, and an edge needs no latch of its own.
			if (melting) {
				awardMilestone(level, ReactorMilestone.MELTDOWN);
			}
		}
		// The scenery hazard runs on the REACTION, not on this tick's output. A core whose buffer is full
		// has stopped selling power and has not stopped being a reactor — hanging the danger on output let
		// a player switch it off by unplugging their machines (playtest finding 1). The redstone scram is
		// still a real safety measure, and still the only one: no signal, no reaction, no melting.
		boolean scenery = bare && reacting;
		if (!melting && !scenery) {
			meltTarget = null;
			meltCountdown = 0;
			return;
		}
		if (meltTarget != null) {
			if (meltCountdown > 0) {
				meltCountdown--;
				return;
			}
			BlockPos victim = meltTarget;
			meltTarget = null;
			if (Config.reactorMeltdownMeltsBlocks && ReactorMeltdown.melt(serverLevel, victim) && melting) {
				// Every melted block carries heat out with it, which is what stops a meltdown being a
				// one-way trip: the room eats its own contents and cools as it does, and the player is
				// left with a wrecked interior inside a shell they can refit.
				heat = ReactorCore.heatAfterMelt(heat, Config.reactorMeltdownHeatRelief);
				setChanged();
			}
			return;
		}
		if (meltCooldown > 0) {
			meltCooldown--;
			return;
		}
		meltCooldown = melting
				? Math.max(1, Config.reactorMeltdownIntervalTicks)
				: ReactorCore.meltInterval(rods, Config.reactorBareMeltIntervalTicks,
						Config.reactorBareMeltMinIntervalTicks);
		BlockPos victim = melting
				? ReactorMeltdown.pickContentsVictim(serverLevel, boxMinX, boxMinY, boxMinZ,
						boxMaxX, boxMaxY, boxMaxZ, serverLevel.getRandom())
				: ReactorMeltdown.pickSceneryVictim(serverLevel, hazardSource(serverLevel, pos),
						Config.reactorBareMeltRadius, serverLevel.getRandom());
		if (victim == null) {
			return;
		}
		ReactorMeltdown.telegraph(serverLevel, victim);
		meltsScheduled++;
		meltTarget = victim;
		meltCountdown = Math.max(0, Config.reactorMeltWarnTicks);
	}

	/**
	 * Where this round's damage radiates from: one of the racks, chosen fresh each time (MOD-469).
	 *
	 * <p><b>The racks, not the controller, and the difference is visible from across the room.</b> A
	 * controller stands in a wall — in a half-built shell it is in ITS wall — so a sphere centred on it
	 * has the reactor's own body filling one half, where everything is either air or an exempt reactor
	 * block. The first playtest showed exactly that: a deliberately leaky reactor with holes on every
	 * side put lava only in front of the controller and left the ground behind it untouched. Rolling a
	 * rack per round instead puts the danger where the fuel is, spreads it evenly around the cluster,
	 * and makes turning the controller round change nothing. It is also the model radiation already
	 * uses, so the two hazards finally answer "how far is it dangerous" the same way.
	 *
	 * <p>Falls back to the controller only when the rack list is momentarily empty, which cannot happen
	 * while the scenery hazard is armed (it needs output, which needs rods) but keeps the method total.
	 */
	private BlockPos hazardSource(ServerLevel level, BlockPos pos) {
		if (bareRacks.isEmpty()) {
			return pos;
		}
		return bareRacks.get(level.getRandom().nextInt(bareRacks.size()));
	}

	/**
	 * Keeps the room's drone in step with what the reactor is doing (MOD-472).
	 *
	 * <p>The signal is {@code lastOutput > 0}, not {@code idleReason}: the idle reason can read
	 * {@code RUNNING} on a room making nothing at all, because it is derived from {@code rods}, which the
	 * periodic scan refreshes only every {@code reactorScanIntervalTicks} while output is recomputed
	 * every tick. A core whose last rod just burnt out would have gone on announcing itself for two
	 * seconds.
	 */
	private void updateVoice(Level level, BlockPos pos, List<FuelRodAssemblyBlockEntity> columns) {
		if (lastOutput > 0) {
			voiceLatch = VOICE_LATCH_TICKS;
		} else if (voiceLatch > 0) {
			voiceLatch--;
		}
		boolean voiced = voiceLatch > 0;
		paintVoicedColumns(level, columns, voiced);
		if (wasVoiced && !voiced && level instanceof ServerLevel serverLevel) {
			// The core going quiet gets its own cue. It covers every way a reactor stops — the lever
			// pulled, the last rod spent, the throttle wound shut, the shell breached — because all four
			// arrive here as the same thing: power that was being made a moment ago and is not now.
			serverLevel.playSound(null, pos, ModSounds.REACTOR_SPINDOWN.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
		}
		wasVoiced = voiced;
	}

	/**
	 * Takes the drone off every rack in the room this controller last sealed.
	 *
	 * <p><b>Sweeps the remembered BOX, not the in-memory list</b>, and that is the whole point of the
	 * method. {@link #assemblies} is rebuilt by the scan and never saved, so after a chunk round-trip it
	 * is empty — and a room whose breach is first noticed on that very tick would have had nothing to
	 * silence. The flag, meanwhile, IS saved: it rides in the chunk like any blockstate. The box is
	 * likewise persisted for exactly this class of problem, so it is the only handle that survives the
	 * gap and can still find the racks.
	 *
	 * <p>Costs one sweep of the interior, on the transition only — never on a running tick.
	 */
	private void silenceColumns(Level level) {
		paintVoicedColumns(level, collectColumns(level), false);
		clearActiveInRememberedBox(level);
		voiceLatch = 0;
		// wasVoiced is deliberately NOT cleared here. This runs from the scan, which happens BEFORE
		// runReactor in the same tick, so wiping it would swallow the very edge the spin-down listens
		// for — and a breach is the loudest of the four cases that cue is meant to cover.
	}

	/**
	 * Clears the drone flag across the last sealed interior, block by block.
	 *
	 * <p>The recovery path for every case where the list of racks is gone but the flag is not: a room
	 * reloaded from disk and found broken, an interior partitioned so that some racks fell outside the
	 * new box, a controller taken out by something that skips the mining hook. Without it those racks
	 * hum for as long as they stand, and nothing in the world can switch them off.
	 */
	private int clearActiveInRememberedBox(Level level) {
		if (boxMaxX == Integer.MIN_VALUE) {
			return 0;
		}
		int cleared = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = boxMinX; x <= boxMaxX; x++) {
			for (int y = boxMinY; y <= boxMaxY; y++) {
				for (int z = boxMinZ; z <= boxMaxZ; z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (state.getBlock() instanceof FuelRodAssemblyBlock
							&& state.getValue(FuelRodAssemblyBlock.ACTIVE)) {
						level.setBlock(cursor.immutable(),
								state.setValue(FuelRodAssemblyBlock.ACTIVE, false), 2);
						cleared++;
					}
				}
			}
		}
		return cleared;
	}

	/**
	 * Marks the first {@link #VOICED_COLUMNS} racks as the ones that sound, and clears the rest.
	 *
	 * <p>Scan order is a stable walk of the room's box, so the same racks keep the voice from one sweep
	 * to the next and the drone does not wander around the floor. The blockstate is written only when the
	 * value actually changes — the same discipline the shell's {@code formed} flag uses, and the reason
	 * painting a room full of columns costs nothing on the ticks in between.
	 *
	 * <p>Walks the block entities the tick already resolved rather than {@link #assemblies}, and reads
	 * each state off its block entity, where it is cached. Re-deriving the list would mean a second
	 * chunk lookup per rack on every tick of every reactor, for nothing.
	 */
	private void paintVoicedColumns(Level level, List<FuelRodAssemblyBlockEntity> columns, boolean voiced) {
		int painted = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			BlockState state = column.getBlockState();
			if (!(state.getBlock() instanceof FuelRodAssemblyBlock)) {
				continue;
			}
			boolean wanted = voiced && painted < VOICED_COLUMNS && column.hasFuel();
			if (wanted) {
				painted++;
			}
			if (state.getValue(FuelRodAssemblyBlock.ACTIVE) != wanted) {
				level.setBlock(column.getBlockPos(), state.setValue(FuelRodAssemblyBlock.ACTIVE, wanted), 2);
			}
		}
	}

	/**
	 * Sounds the overheat siren once per excursion (MOD-472).
	 *
	 * <p>Edge, not level, and the reason is in the balance: an unplumbed pair of columns settles at 66 %
	 * of the heat scale, three points below the 70 % warning line, so a plain threshold test would fire
	 * and clear several times a second on a reactor that is merely warm. {@link ReactorCore} owns the
	 * arithmetic — it re-arms only once the coolant loop has pulled the core back to its own target — so
	 * the rule is covered by a unit test rather than by listening.
	 *
	 * <p>Played from the controller with a fixed long range and no muffling of any kind. The drone
	 * belongs to the room and is held in by the shell; the alarm is the opposite kind of sound — its
	 * entire job is reaching somebody who is not in the room.
	 */
	private void warnOnOverheat(Level level, BlockPos pos) {
		int percent = ReactorCore.heatPercent(heat, Config.reactorHeatCapacity);
		if (ReactorCore.shouldSoundAlarm(percent, Config.reactorHeatWarnPercent,
				Config.reactorCoolantTargetPercent, overheatWarned)
				&& level instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, pos, ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		boolean latched = ReactorCore.alarmStaysLatched(percent, Config.reactorHeatWarnPercent,
				Config.reactorCoolantTargetPercent, overheatWarned);
		if (latched != overheatWarned) {
			overheatWarned = latched;
			setChanged();
		}
		soundCriticalAlarm(level, pos, percent);
	}

	/**
	 * Keeps the siren going while the core is pinned at the top of the scale (MOD-472).
	 *
	 * <p>The threshold alarm above is a single blast by design, and that is exactly what leaves a core
	 * at a hundred percent sitting in silence: it crossed the warning line long ago and latched. A
	 * reactor in its worst state should not be quieter than one that is merely warm, so here it re-sounds
	 * every three to five seconds for as long as it stays there.
	 *
	 * <p>The gap is re-rolled after every blast rather than fixed. A siren on an exact metronome turns
	 * into background texture within a minute; an irregular one keeps reading as an alarm. Louder than
	 * the threshold blast, too — this is the emergency, not the warning.
	 *
	 * <p>Coming down off the top clears the countdown, so the next excursion sounds immediately instead
	 * of finishing a wait left over from the last one.
	 */
	private void soundCriticalAlarm(Level level, BlockPos pos, int heatPercent) {
		if (!ReactorCore.isCritical(heatPercent)) {
			criticalAlarmCooldown = 0;
			return;
		}
		if (criticalAlarmCooldown > 0) {
			criticalAlarmCooldown--;
			return;
		}
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, pos, ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
			criticalAlarmCooldown = serverLevel.getRandom().nextIntBetweenInclusive(
					ReactorCore.CRITICAL_ALARM_MIN_TICKS, ReactorCore.CRITICAL_ALARM_MAX_TICKS);
		}
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
	 * Looks for racks to burn with no room to walk (MOD-469).
	 *
	 * <p>Runs on the same timer as the room sweep and for the same reason: a radius scan every tick would
	 * be absurd, and a rack racked by hand counting from the next sweep is at most two seconds of delay
	 * against a rod that burns for minutes.
	 *
	 * <p><b>A controller is bare only once it has actually found something.</b> An empty-handed sweep
	 * leaves {@code bare} false, so a player halfway through building a shell keeps the whole building
	 * layout on their panel instead of being told they are running a reactor they have not started.
	 */
	private void rescanBare(Level level, BlockPos pos) {
		bareRacks.clear();
		if (!(level instanceof ServerLevel serverLevel)) {
			bare = false;
			return;
		}
		BareReactorScan.Result found = BareReactorScan.scan(serverLevel, pos,
				Config.reactorBareSearchRadius);
		bareRacks.addAll(found.racks());
		rods = found.rods();
		bare = !found.isEmpty();
	}

	/**
	 * The live block entities behind {@link #assemblies}, in scan order.
	 *
	 * <p>Entries whose block entity has gone are simply absent: the list is rebuilt every tick, so a
	 * column mined mid-tick drops out immediately rather than waiting for the next room scan.
	 */
	private List<FuelRodAssemblyBlockEntity> collectColumns(Level level) {
		// Whichever list this controller is actually driving. The two are never both populated, so this
		// is a switch rather than a merge — see rescanBare.
		List<BlockPos> racked = bare ? bareRacks : assemblies;
		if (racked.isEmpty()) {
			return List.of();
		}
		List<FuelRodAssemblyBlockEntity> columns = new ArrayList<>(racked.size());
		for (BlockPos rack : racked) {
			if (level.getBlockEntity(rack) instanceof FuelRodAssemblyBlockEntity column) {
				columns.add(column);
			}
		}
		return columns;
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
	 * Boils as much coolant as this tick's heat calls for and returns the heat left over.
	 *
	 * <p><b>A starter reactor needs no plumbing at all.</b> The shell sheds up to 84 heat a tick by
	 * itself (a flat 4 plus 8‰ of the current temperature), and anything under that simply settles at
	 * its own temperature: one column near 15% of the scale, two side by side near two thirds (dry —
	 * plumbed, they sit at the coolant target instead). The loop
	 * becomes necessary at the third column, which is exactly where the player should meet it. Nothing
	 * here forces plumbing early; scale does.
	 *
	 * <p>Whatever the columns could not boil stays in {@code produced} and goes on the heat scale. That
	 * is the entire failure mode of a starved loop: not an error message, a rising gauge.
	 */
	private long coolWithWater(List<FuelRodAssemblyBlockEntity> columns, long produced, long cooling) {
		// The coolant loop is a SAFETY system, not a radiator: it engages at its own target, which sits
		// DELIBERATELY BELOW the warning line, and takes only the heat that would carry the core past it.
		// Aiming at the warning line itself parked every healthy reactor on amber, so amber stopped
		// meaning "look at me". Below the target the shell sheds everything by itself, so a small reactor
		// runs warm, steady and dry — which is what makes the loop a thing the player builds when they
		// scale up rather than a tax on their first one.
		long coolantTarget = (long) Config.reactorHeatCapacity * Config.reactorCoolantTargetPercent / 100;
		// The INFLOW this tick, not the overshoot accumulated so far. Asking the loop to undo the whole
		// backlog in one tick demanded fifteen hundred millibuckets a tick and drained a flooded core in
		// eight of them — the loop looked useless precisely when it was needed. Countering the ongoing
		// gain instead holds the core at the coolant target and costs tens of millibuckets, which is a
		// rate a pipe can actually sustain.
		// Two terms: hold the line, then walk back to it. The first counters this tick's inflow, which
		// is what keeps a hot core from climbing. The second takes a twentieth of however far past the
		// target it already is, so a core that ran away before the loop was plumbed comes down over a few
		// seconds instead of sitting at the top forever — countering the inflow alone froze it exactly
		// where it was, which looks identical to a loop that does nothing.
		long excess = heat < coolantTarget ? 0 : (produced - cooling) + (heat - coolantTarget) / 20;
		if (excess <= 0 || columns.isEmpty()) {
			lastWater = 0;
			return produced;
		}
		long wanted = ReactorCore.waterForHeat(excess, Config.reactorHeatPerWater);
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
		// May go NEGATIVE, and must: the recovery term deliberately boils more than this tick's heat so a
		// core that ran away comes back down. Clamping the result at zero threw that surplus away — the
		// loop drank the water, the temperature did not move, and the coolant looked useless at exactly
		// the moment it was working hardest. settleHeat clamps the temperature at zero, which is the
		// right place for the floor.
		return produced - ReactorCore.heatRemovedByWater(boiled, Config.reactorHeatPerWater);
	}

	/**
	 * Settles fluid inside each vertical run of columns.
	 *
	 * <p><b>A stack is one vessel, and this is the rule that makes it look like one.</b> Columns joined
	 * top to bottom are already drawn as a single unbroken tower, so a pipe touching any block of that
	 * tower fills the whole tower — connecting to a five-block column at the floor and having only the
	 * bottom block fill would contradict what the player is looking at. Columns standing side by side
	 * are separate vessels and stay separate: coolant appearing in a tower nothing is plumbed to was
	 * the version before this one, and it read as a bug however convenient it was.
	 *
	 * <p><b>Water settles to the bottom, steam collects at the top</b> — filled from one end rather than
	 * shared out evenly, which is both what a liquid does and what makes the tower readable: a half-full
	 * stack shows a solid body of water with one surface, instead of the same puddle repeated in every
	 * block with a gap above each. It also puts the steam where the exhaust is, since only the topmost
	 * block of a stack has a free upper face to vent through.
	 */
	private void settleStacks(List<FuelRodAssemblyBlockEntity> columns) {
		if (columns.size() < 2) {
			return;
		}
		Map<Long, List<FuelRodAssemblyBlockEntity>> byColumn = new HashMap<>();
		for (FuelRodAssemblyBlockEntity column : columns) {
			BlockPos at = column.getBlockPos();
			// One key per (x, z): the vertical runs inside it are separated below, after sorting.
			byColumn.computeIfAbsent(((long) at.getX() << 32) ^ (at.getZ() & 0xFFFFFFFFL),
					key -> new ArrayList<>()).add(column);
		}
		for (List<FuelRodAssemblyBlockEntity> shaft : byColumn.values()) {
			shaft.sort(Comparator.comparingInt(column -> column.getBlockPos().getY()));
			int runStart = 0;
			for (int i = 1; i <= shaft.size(); i++) {
				boolean broken = i == shaft.size()
						|| shaft.get(i).getBlockPos().getY() != shaft.get(i - 1).getBlockPos().getY() + 1;
				if (broken) {
					settleRun(shaft.subList(runStart, i));
					runStart = i;
				}
			}
		}
	}

	/** One unbroken tower: water poured in at the bottom, steam pushed up to the top. */
	private static void settleRun(List<FuelRodAssemblyBlockEntity> run) {
		if (run.size() < 2) {
			return;
		}
		long water = 0;
		long steam = 0;
		for (FuelRodAssemblyBlockEntity column : run) {
			water += column.waterTank.amount;
			steam += column.steamTank.amount;
		}
		for (int i = 0; i < run.size(); i++) {
			FuelRodAssemblyBlockEntity column = run.get(i);
			long take = Math.min(water, column.waterTank.capacity);
			column.setTank(true, take);
			water -= take;
		}
		for (int i = run.size() - 1; i >= 0; i--) {
			FuelRodAssemblyBlockEntity column = run.get(i);
			long take = Math.min(steam, column.steamTank.capacity);
			column.setTank(false, take);
			steam -= take;
		}
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
		if (!sealed && !bare) {
			return ReactorIdleReason.NOT_SEALED;
		}
		if (rods <= 0) {
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
		if (level == null || assemblies.isEmpty()) {
			return 0;
		}
		long held = 0;
		long capacity = 0;
		for (BlockPos rack : assemblies) {
			if (level.getBlockEntity(rack) instanceof FuelRodAssemblyBlockEntity column) {
				held += water ? column.waterTank.amount : column.steamTank.amount;
				capacity += water ? column.waterTank.capacity : column.steamTank.capacity;
			}
		}
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(100, (held * 200 + capacity) / (capacity * 2));
	}

	private void rescan(Level level, BlockPos pos, BlockState state) {
		RoomScan.Result result = RoomValidator.scan(level, pos, state.getValue(ReactorControllerBlock.FACING),
				Config.reactorRoomMinInner, Config.reactorRoomMaxInner, Config.reactorRoomMaxGlassPercent);
		ReactorRoomStatus scanned = ReactorRoomStatus.of(result.status());

		breachDx = result.x() - pos.getX();
		breachDy = result.y() - pos.getY();
		breachDz = result.z() - pos.getZ();
		boolean measured = scanned.hasSize();
		sizeX = measured ? result.sizeX() : 0;
		sizeY = measured ? result.sizeY() : 0;
		sizeZ = measured ? result.sizeZ() : 0;

		boolean wasFormed = state.getValue(ReactorControllerBlock.FORMED);
		boolean changed = scanned != status;
		status = scanned;

		// The whole shell wears the flag, not just this block: that is what makes a finished room read
		// as one surface instead of a stack of crates, and what lights its lamps.
		int repainted;
		if (result.formed()) {
			repainted = RoomValidator.applyFormed(level, result.minX(), result.minY(), result.minZ(),
					result.maxX(), result.maxY(), result.maxZ(), true);
			// A room can shrink without ever failing its scan — wall a running interior in two and the
			// near half is still a valid room. Racks left on the far side drop out of the sweep with the
			// drone flag still on them and nothing left that would ever take it off, so the OLD box gets
			// swept before it is forgotten. Racks still inside are repainted in the same tick, so the
			// clearing is invisible (MOD-472).
			if (boxChanges(result)) {
				clearActiveInRememberedBox(level);
			}
			rememberBox(result);
		} else {
			// Clear the box we last sealed — not the one this scan measured, which is empty. Without
			// this the shell would stay seamless and lit around a hole (playtest, 2026-08-19).
			repainted = clearRememberedBox(level);
		}
		if (wasFormed != result.formed()) {
			level.setBlock(pos, state.setValue(ReactorControllerBlock.FORMED, result.formed()), 3);
		}
		if (changed) {
			setChanged();
			syncBlockEntityToClient();
		}
		if (result.formed()) {
			bare = false;
			bareRacks.clear();
			collectAssemblies(level, result);
		} else {
			// Silence the racks BEFORE forgetting where they are (MOD-472). The drone is painted onto the
			// columns and cleared the same way, so a list emptied first would leave the flag set on blocks
			// nothing owns any more — a breached room that goes on humming for as long as it stands.
			//
			// Order matters twice over: silenceColumns resolves the columns through collectColumns, which
			// answers with the BARE list once the flag is set, so the bare sweep has to come after the room
			// list has been silenced and cleared. Otherwise a breach would silence the wrong racks.
			silenceColumns(level);
			assemblies.clear();
			outlets.clear();
			rods = 0;
			rescanBare(level, pos);
		}

		if (level instanceof ServerLevel serverLevel) {
			if (result.formed() && !wasFormed) {
				announceAssembled(serverLevel, pos, repainted);
				// MOD-473: the same edge the room announces itself on — the scan that turned a shell into
				// a sealed room. No latch needed: this branch is an edge by construction.
				ModCriteria.fireReactorMilestone(serverLevel, getOwner(), ReactorMilestone.ROOM_SEALED);
			} else if (!result.formed() && scanned.hasLocation()) {
				markProblem(serverLevel, new BlockPos(result.x(), result.y(), result.z()), wasFormed);
			}
		}
	}

	/**
	 * Walks the room's interior and records every loaded fuel assembly, the total rod count and how
	 * many of those racks stand next to each other.
	 *
	 * <p>Done on the scan timer rather than per tick: a 12³ interior is 1728 cells, which is fine every
	 * couple of seconds and absurd sixty times a second. The cost of that choice is that a rod inserted
	 * by hand counts from the next sweep, which is at most two seconds — imperceptible next to a rod
	 * that burns for two minutes.
	 */
	private void collectAssemblies(Level level, RoomScan.Result box) {
		assemblies.clear();
		outlets.clear();
		rods = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// The scan reports the INTERIOR box; the shell is the ring one block outside it. Sweeping the
		// interior alone found every column and no socket at all — a reactor outlet lives in the wall by
		// definition, so it can only ever be on that ring. Widening by one covers both without a second
		// pass, and cannot pick up strays: the ring is shell blocks, which are never columns.
		for (int y = box.minY() - 1; y <= box.maxY() + 1; y++) {
			for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
				for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
					// EVERY column, loaded or not. Filtering on hasFuel() looked harmless and was not: an
					// empty column was invisible to the controller, so it was left out of the coolant
					// readout and — worse — out of the pass that settles a stack, which is why water
					// pumped into the bottom of a tower never rose past the first block that happened to
					// hold rods. A column is part of the machine because it is in the room, not because
					// somebody has fuelled it yet.
					if (level.getBlockEntity(cursor.set(x, y, z))
							instanceof FuelRodAssemblyBlockEntity rack) {
						assemblies.add(cursor.immutable());
						rods += rack.getRods();
					} else if (level.getBlockEntity(cursor) instanceof ReactorOutletBlockEntity) {
						outlets.add(cursor.immutable());
					}
				}
			}
		}
		// Adjacency is NOT counted here any more (MOD-476). It used to be cached by this periodic scan
		// while the rods were counted every tick, so for up to reactorScanIntervalTicks after a column was
		// pulled the survivors went on being paid a neighbour bonus for a rack that was no longer there.
		// countNeighbourPairs does it per tick from the columns runReactor already has in hand.
	}

	/** Whether this scan measured a different interior than the one currently remembered. */
	private boolean boxChanges(RoomScan.Result result) {
		return boxMaxX != Integer.MIN_VALUE
				&& (boxMinX != result.minX() || boxMinY != result.minY() || boxMinZ != result.minZ()
						|| boxMaxX != result.maxX() || boxMaxY != result.maxY() || boxMaxZ != result.maxZ());
	}

	private void rememberBox(RoomScan.Result result) {
		boxMinX = result.minX();
		boxMinY = result.minY();
		boxMinZ = result.minZ();
		boxMaxX = result.maxX();
		boxMaxY = result.maxY();
		boxMaxZ = result.maxZ();
		setChanged();
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
		silenceColumns(level);
		clearRememberedBox(level);
	}

	private int clearRememberedBox(Level level) {
		if (boxMaxX == Integer.MIN_VALUE) {
			return 0;
		}
		int changed = RoomValidator.applyFormed(level, boxMinX, boxMinY, boxMinZ,
				boxMaxX, boxMaxY, boxMaxZ, false);
		boxMaxX = Integer.MIN_VALUE;
		boxMaxY = Integer.MIN_VALUE;
		boxMaxZ = Integer.MIN_VALUE;
		setChanged();
		return changed;
	}

	@Override
	protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("BoxMinX", boxMinX);
		output.putInt("BoxMinY", boxMinY);
		output.putInt("BoxMinZ", boxMinZ);
		output.putInt("BoxMaxX", boxMaxX);
		output.putInt("BoxMaxY", boxMaxY);
		output.putInt("BoxMaxZ", boxMaxZ);
		output.putLong("Heat", heat);
		output.putBoolean("OverheatWarned", overheatWarned);
		output.putInt("Depth", depthPermille);
		output.putInt("BlastCountdown", blastCountdown);
		output.putInt("BlastCountdownTotal", blastCountdownTotal);
	}

	@Override
	protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input);
		boxMinX = input.getIntOr("BoxMinX", 0);
		boxMinY = input.getIntOr("BoxMinY", 0);
		boxMinZ = input.getIntOr("BoxMinZ", 0);
		boxMaxX = input.getIntOr("BoxMaxX", Integer.MIN_VALUE);
		boxMaxY = input.getIntOr("BoxMaxY", Integer.MIN_VALUE);
		boxMaxZ = input.getIntOr("BoxMaxZ", Integer.MIN_VALUE);
		heat = input.getLongOr("Heat", 0L);
		overheatWarned = input.getBooleanOr("OverheatWarned", false);
		depthPermille = input.getIntOr("Depth", ReactorCore.FULL_DEPTH);
		blastCountdown = input.getIntOr("BlastCountdown", 0);
		blastCountdownTotal = input.getIntOr("BlastCountdownTotal", 0);
	}

	/**
	 * The moment the last block goes in. A multiblock that silently starts working leaves the player
	 * wondering whether it did, so the completion gets its own cue — the same anvil-land the
	 * distillation column uses when its tower forms, plus a ring of sparkle over the shell.
	 */
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

	private void announceAssembled(ServerLevel level, BlockPos pos, int repainted) {
		level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6f, 1.6f);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				12, 0.4, 0.4, 0.4, 0.02);
	}

	/**
	 * Marks the problem in the world. The screen names the offset, but a 14³ shell has 1016 cells and
	 * reading "4 east, 2 up" off a number line is not how anyone finds a missing block — walking to the
	 * smoke is.
	 *
	 * <p>Loud on purpose the first time: a room that just came apart plays a short alarm, because the
	 * player is usually looking somewhere else when a creeper opens their wall. Afterwards it is only
	 * the particles — repeating the sound every two seconds would turn a helpful cue into a nuisance.
	 */
	private void markProblem(ServerLevel level, BlockPos where, boolean wasFormed) {
		// Smoke only. The angry-villager puffs that used to go with it read as cartoon clouds hanging
		// over the wall rather than as a fault marker (playtest, 2026-08-19).
		level.sendParticles(ParticleTypes.SMOKE,
				where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5,
				16, 0.3, 0.3, 0.3, 0.01);
		if (wasFormed) {
			level.playSound(null, where, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.4f);
		}
	}

	private final ContainerData controllerData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_STATUS -> status.ordinal();
				case DATA_BREACH_DX -> breachDx;
				case DATA_BREACH_DY -> breachDy;
				case DATA_BREACH_DZ -> breachDz;
				case DATA_SIZE_X -> sizeX;
				case DATA_SIZE_Y -> sizeY;
				case DATA_SIZE_Z -> sizeZ;
				case DATA_HEAT_PERCENT -> ReactorCore.heatPercent(heat, Config.reactorHeatCapacity);
				case DATA_RODS -> rods;
				case DATA_DEPTH_PERCENT -> depthPermille / 10;
				case DATA_OUTPUT -> lastOutput;
				case DATA_WATER_PERCENT -> waterPercent();
				case DATA_WATER_RATE -> lastWater;
				case DATA_STEAM_PERCENT -> steamPercent();
				case DATA_IDLE_REASON -> idleReason;
				case DATA_ENERGY_PERCENT -> energy.getCapacity() <= 0 ? 0
						: (int) Math.min(100, energy.getAmount() * 100 / energy.getCapacity());
				case DATA_ENERGY_HUNDREDS -> (int) Math.min(Short.MAX_VALUE, energy.getAmount() / 100);
				case DATA_MELTDOWN -> meltingDown ? 1 : 0;
				case DATA_BLAST_PERCENT -> blastCountdownTotal <= 0 || blastCountdown <= 0
						? 0 : Math.max(1, blastCountdown * 100 / blastCountdownTotal);
				case DATA_INSTABILITY -> bare
						? ReactorCore.heatPercent(instability, Config.reactorBareInstabilityCapacity) : 0;
				default -> ReactorControllerBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			// Every readout channel is derived from the scan and server-authoritative.
			if (index < DATA_STATUS) {
				ReactorControllerBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return controllerData;
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

	/** Moves the throttle. Called from the menu's button handler, clamped here rather than there. */
	public void setDepthPermille(int value) {
		int clamped = Math.min(ReactorCore.FULL_DEPTH, Math.max(0, value));
		if (clamped != depthPermille) {
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
		return rods;
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
		return bare;
	}

	/** Whether the room is melting its own contents right now. */
	public boolean isMeltingDown() {
		return meltingDown;
	}

	/**
	 * Whether this controller currently holds a sealed room.
	 *
	 * <p>Asked by {@link BareReactorScan} on behalf of a NEIGHBOURING controller: a rack inside a working
	 * room belongs to that room and to nothing else, and this is how a bare machine finds that out
	 * without reaching into another block entity's internals.
	 */
	public boolean isRoomSealed() {
		return status == ReactorRoomStatus.FORMED;
	}

	/**
	 * Whether a position lies inside the interior this controller last sealed.
	 *
	 * <p>The INTERIOR, not the shell: the question being asked is "is this rack part of a working
	 * reactor", and a rack is only ever inside the room. An empty box answers no to everything, which is
	 * the right answer for a controller that has never sealed anything.
	 */
	// ── MOD-471 ──

	/** Blocks this reactor has marked for melting since it was loaded — "is the hazard running". */
	public int getMeltsScheduled() {
		return meltsScheduled;
	}

	/** Ticks left before this core blows up; zero when no accident is under way. */
	public int getBlastCountdown() {
		return blastCountdown;
	}

	/** What the countdown started from — the duration this particular accident rolled. */
	public int getBlastCountdownTotal() {
		return blastCountdownTotal;
	}

	/** A bare core's instability on its own 0…capacity scale. Always zero for a sealed room. */
	public long getInstability() {
		return instability;
	}

	public boolean sealedBoxContains(BlockPos at) {
		if (boxMaxX == Integer.MIN_VALUE) {
			return false;
		}
		return at.getX() >= boxMinX && at.getX() <= boxMaxX
				&& at.getY() >= boxMinY && at.getY() <= boxMaxY
				&& at.getZ() >= boxMinZ && at.getZ() <= boxMaxZ;
	}
}

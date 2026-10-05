package dev.alaindustrial.core.reactor;

import dev.alaindustrial.Config;
import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;
import dev.alaindustrial.core.radiation.RadiationConfig;

/**
 * Reactor knobs moved out of {@code Config} by {@code docs/tools/authoring/config_holder_codemod.py}
 * (MOD-710, ADR-034). The json key of each knob is still its field name and its section is still
 * declared on the field, so the operator's file is unchanged; {@code Config.REGISTRY} scans this
 * class next to {@code Config}.
 */
public final class ReactorConfig {

	private ReactorConfig() {
	}

	// ── MOD-468, stage 1: the reactor room ───────────────────────────────────────────────────────
	/**
	 * Ticks the airlock stays open after a redstone pulse (2 s). Holding the signal does not extend it; only a
	 * fresh rising edge opens it again.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the reactor airlock stays open after a redstone pulse before closing itself; holding the"
					+ " signal does not extend it.")
	public static int reactorDoorOpenTicks = 40;
	/**
	 * Delay before the door re-tests a doorway someone stands in: a politeness delay, not a timer the player
	 * should feel.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the airlock waits before re-testing a doorway that still has someone standing in it.")
	public static int reactorDoorOccupiedRecheckTicks = 10;
	/**
	 * Ticks the panel takes to slide its two blocks (MOD-493). Cosmetic: the {@code open} state flips in one
	 * tick; a fifth of {@link #reactorDoorOpenTicks}.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the airlock panel takes to slide its full two blocks; cosmetic only, the open state still"
					+ " flips in one tick.")
	public static int reactorDoorSlideTicks = 8;
	/**
	 * How often a controller re-scans its room: the only thing that notices a far wall being mined ({@code
	 * neighborChanged} sees six blocks).
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between full re-scans of a reactor room by its controller; catches shell changes out of"
					+ " neighbour range.")
	public static int reactorScanIntervalTicks = 40;
	/** Smallest interior edge a reactor room may have, in blocks (shell 5x5x5). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Smallest interior edge of a reactor room, in blocks.")
	public static int reactorRoomMinInner = 3;
	/** Largest reactor-room interior edge (shell 14x14x14): bounds the scan cost and one room's power. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Largest interior edge of a reactor room, in blocks; bounds both the scan cost and how much one room"
					+ " can hold.")
	public static int reactorRoomMaxInner = 12;
	/**
	 * Largest glass share of a reactor shell, in percent: real windows, but the structure still reads as
	 * containment.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Largest share of a reactor shell that may be glass, in percent; above it the room reports a weak"
					+ " structure.")
	public static int reactorRoomMaxGlassPercent = 30;
	/**
	 * Ticks the reactor button stays pressed, as vanilla's stone button; ample for the airlock to see the
	 * rising edge.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the reactor button stays pressed before releasing itself.")
	public static int reactorButtonPressTicks = 20;
	// ── MOD-468, stage 2: the reactor actually runs ───────────────────────────────────────────────
	/** EU/t one fully-lowered fuel rod contributes before neighbour bonuses (four per assembly). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t one fully lowered uranium rod contributes, before neighbour bonuses.")
	public static int reactorEuPerRod = 6;
	/**
	 * Extra output, in percent, per adjacent loaded assembly; heat rises on the same curve, so density is a
	 * risk/reward dial.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Extra output in percent granted per adjacent loaded fuel assembly; heat scales with it too.")
	public static int reactorNeighbourBonusPercent = 25;
	/**
	 * Ticks of burn in one uranium rod at full depth (20 minutes): the whole uranium economy, about a hundred
	 * ore an hour for a full room.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks of burn in one uranium fuel rod at full control-rod depth.")
	public static int reactorRodBurnTicks = 24000;
	/** Heat, in thousandths of the scale, added per active rod per tick at full depth. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Heat units added per active rod per tick at full depth.")
	public static int reactorHeatPerRod = 4;
	/**
	 * Extra HEAT per adjacency, in percent, larger than {@link #reactorNeighbourBonusPercent}: a tight core
	 * reaches full power on shallower rods but runs hotter and drinks more.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Extra heat in percent per adjacent loaded reactor column; larger than the energy bonus, which is"
					+ " what makes a dense core hotter for the same power.")
	public static int reactorHeatNeighbourBonusPercent = 40;
	/**
	 * Heat one mB of boiling water carries away: the reactor's thirst. Since MOD-623 water carries all heat,
	 * up to 273 mB/t — more than one {@link #reactorPortThroughput} inlet.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Heat units carried away by one mB of water as it boils into steam.")
	public static int reactorHeatPerWater = 2;
	/** Water one reactor column holds, in mB. Four buckets — a visible level and a few seconds of buffer. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Water a single reactor column holds, in mB.")
	public static int reactorColumnWaterCapacity = 4000;
	/** Steam one reactor column holds before it stops accepting water and cooling stalls. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Steam a single reactor column holds before cooling stalls, in mB.")
	public static int reactorColumnSteamCapacity = 4000;
	/**
	 * mB a single reactor inlet passes per tick: the shell's tightest crossing, below the pipe's {@link
	 * Config#fluidPipeSegmentBuffer}.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fluid a single reactor inlet passes per tick, in mB.")
	public static int reactorPortThroughput = 50;
	/** EU a reactor outlet holds: one tick of HV, a socket rather than a battery. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU a single reactor outlet holds before a cable drains it.")
	public static int reactorOutletBuffer = 512;
	/** Steam a nozzle releases per tick, in mB. Above one inlet's throughput, so the pipe is the limit. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Steam a nozzle releases into the world per tick, in mB.")
	public static int reactorNozzleVentRate = 100;
	/** Steam a nozzle holds. A few ticks of slack, far too little to be used as storage. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Steam a nozzle holds, in mB.")
	public static int reactorNozzleBuffer = 500;
	/** Ticks between steam puffs over a sealed room's boiling stacks (MOD-662). Visual only. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between the steam puffs over a sealed reactor room's boiling column stacks.")
	public static int reactorSteamPlumeIntervalTicks = 10;
	/** Boiling stacks that puff in one pulse; a packed room takes turns so the particle traffic stays bounded. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Boiling column stacks that puff steam in one pulse; the rest take turns on later pulses.")
	public static int reactorSteamPlumeStacksPerPulse = 6;
	/** Ticks a steam nozzle batches its venting into one geyser burst (MOD-662); the venting itself is every tick. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a steam nozzle gathers its vented steam before drawing one geyser burst for it.")
	public static int reactorNozzlePlumeIntervalTicks = 4;
	/**
	 * Heat a STOPPED room's shell sheds every tick, the floor of the cooling curve; while running, water is
	 * the only cooling (MOD-623). Small so the gauge moves.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Heat units a stopped reactor room bleeds away on its own each tick regardless of temperature; a"
					+ " running one sheds nothing but what its water carries.")
	public static int reactorPassiveCooling = 4;
	/**
	 * Extra heat shed per tick in thousandths of the current temperature, only once the reaction stopped
	 * (MOD-623): a scrammed room drains in about 20 s.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Extra heat a stopped reactor room sheds per tick, in thousandths of the current temperature.")
	public static int reactorHeatLossPermille = 8;
	/** Heat scale maximum. Above {@code reactorHeatWarnPercent} of it the controller warns. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Maximum of the reactor heat scale.")
	public static int reactorHeatCapacity = 10000;
	/** Percentage of the heat scale at which the reactor is reported as running hot. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Percentage of the heat scale above which the reactor reports running hot.")
	public static int reactorHeatWarnPercent = 70;
	/**
	 * Percent the warning siren must fall below before it can sound again, below {@link
	 * #reactorHeatWarnPercent}. The key keeps its old name (it once set the coolant target).
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Percentage of the heat scale the reactor must fall below before the warning siren can sound again;"
					+ " below the warning threshold on purpose.")
	public static int reactorCoolantTargetPercent = 60;
	/** EU the controller can bank. Sized to a few seconds of full output so the grid can lag behind. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU buffer of the reactor controller.")
	public static int reactorBuffer = 200000;
	// ── MOD-469: the meltdown and the bare reactor ────────────────────────────────────────────────
	/**
	 * Master switch for every block the reactor turns into lava (meltdown contents, pipes in a working room,
	 * bare-core scenery). Its own parts never melt; not tied to {@code mobGriefing}.
	 */
	@Knob(section = Section.MACHINES,
			doc = "When true, an overheating sealed room melts its own contents, a working sealed room melts the"
					+ " ordinary fluid and steam pipes inside it, and a working bare reactor melts the scenery around"
					+ " it. false keeps every cue and changes no block.")
	public static boolean reactorMeltdownMeltsBlocks = true;
	/**
	 * How far the bare-mode search may WALK from the controller through racks and shielding shell — a
	 * connectivity bound, not a sphere; wider than {@link #reactorBareMeltRadius}.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Blocks a controller with no sealed room reaches when looking for fuel racks; wider than the melt"
					+ " radius.")
	public static int reactorBareSearchRadius = 8;
	/** Blocks around EACH charged rack within which scenery melts, measured from the fuel, not the controller. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Blocks around a working bare reactor within which the scenery melts.")
	public static int reactorBareMeltRadius = 5;
	/** Share of the sealed-room output a bare core keeps: a real early generator, clearly worse than the room. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Share of the sealed-room output a bare reactor keeps, in percent.")
	public static int reactorBarePowerPercent = 40;
	/**
	 * Cap on a bare core's EU/t however many rods it holds: a quarter of the HV ceiling, so a heap never beats
	 * a built room.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Hard ceiling on a bare reactor's output in EU/t, however many rods are piled into it.")
	public static int reactorBarePowerCap = 128;
	/**
	 * Ticks between melts under a bare core with ONE rod; divided by the rod count, floored by {@link
	 * #reactorBareMeltMinIntervalTicks}.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between melts under a bare reactor carrying one rod; divided by the rod count.")
	public static int reactorBareMeltIntervalTicks = 600;
	/** Shortest gap between two melts however large the cluster grows. Two seconds. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Shortest gap between two melts under a bare reactor however large the cluster grows.")
	public static int reactorBareMeltMinIntervalTicks = 40;
	/**
	 * Ticks between marking a block for melting and turning it to lava; the warning is pointed at the block
	 * itself, 2 s to step off.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Ticks between a block being marked for melting and turning to lava; the pointed warning the player"
					+ " can act on.")
	public static int reactorMeltWarnTicks = 40;
	/**
	 * Heat percent at which a sealed room starts melting its contents: between the {@link
	 * #reactorHeatWarnPercent} warning and the explosion (MOD-471).
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Percentage of the heat scale at which a sealed room starts melting its own contents; between the"
					+ " warning line and the top.")
	public static int reactorMeltdownStartPercent = 85;
	/** Ticks between two blocks of the room's contents melting while the core is over the line. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between two blocks of the room's contents melting while the core is over the meltdown line.")
	public static int reactorMeltdownIntervalTicks = 60;
	/**
	 * Ticks between two ordinary pipes melting inside a WORKING sealed room (MOD-660/662): slow, so the lesson
	 * is "use the reinforced pipe".
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between two ordinary fluid or steam pipes melting inside a working sealed reactor room; the"
					+ " reinforced pipes are immune.")
	public static int reactorPipeMeltIntervalTicks = 120;
	/**
	 * Heat carried away by one melted block: the meltdown is self-limiting, leaving a wrecked interior in an
	 * intact shell.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Heat carried away by one melted block of the room's contents; what makes a meltdown self-limiting.")
	public static int reactorMeltdownHeatRelief = 400;
	// ── MOD-471: the accident at the top of the scale ─────────────────────────────────────────────
	/**
	 * Master switch for the explosion. Off, the core still sounds, reports and counts down — then nothing
	 * happens (as {@link #reactorMeltdownMeltsBlocks}).
	 */
	@Knob(section = Section.MACHINES,
			doc = "When true, a core pinned at the top of its scale counts down and explodes. false keeps the"
					+ " countdown, the siren and the panel and changes no block.")
	public static boolean reactorBlastEnabled = true;
	/**
	 * Shortest countdown from a pinned gauge to the blast. Rolled per accident so the alarm is never a
	 * memorised norm; 2 minutes is the actionable warning.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Shortest countdown between a pinned gauge and the explosion, in ticks; rolled fresh per accident.")
	public static int reactorBlastCountdownMinTicks = 2400;
	/** Longest countdown, in ticks. Three minutes — time to run back from the far end of a base. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Longest countdown between a pinned gauge and the explosion, in ticks.")
	public static int reactorBlastCountdownMaxTicks = 3600;
	/**
	 * How long the core must stay under 100 % before an armed countdown is called off, so a redstone duty
	 * cycle cannot dodge it; honest fixes clear it easily.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "How long the core must stay under a hundred percent before an armed countdown is called off; stops a"
					+ " redstone clock resetting it.")
	public static int reactorBlastReleaseTicks = 100;
	/**
	 * Explosion power of a core with no rods: under the ~8 a ray needs to break a reactor wall, so the
	 * smallest accident leaves the shell.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Explosion power of a reactor carrying no rods; below what it takes to break a reactor wall.")
	public static int reactorBlastBasePower = 6;
	/**
	 * Explosion power added per TEN rods (0.4 per rod): three columns reach 10.8, contained by a sealed room;
	 * twelve reach 25.2, where it starts to leak.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Explosion power added per ten rods burning when the countdown ran out.")
	public static int reactorBlastPowerPerTenRods = 4;
	/**
	 * Cap on explosion power (TNT is 4). A shell absorbs 28-37 per wall cell; raising this costs chunk loads,
	 * as the blast traces 1352 rays.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Hard ceiling on explosion power; a sealed room contains everything up to about 24.")
	public static int reactorBlastMaxPower = 45;
	/** Whether the blast sets fire to what it touches, like TNT lit in the Nether does. */
	@Knob(section = Section.MACHINES,
			doc = "Whether the blast sets fire to what it touches.")
	public static boolean reactorBlastFire = true;
	/**
	 * Most lava sources poured into the crater, only in cells the explosion destroyed ({@code ReactorBlast}),
	 * so claim mods that block the blast also block this.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Lava sources poured into the crater, only into cells the explosion itself destroyed.")
	public static int reactorBlastLavaCells = 6;
	/**
	 * Instability per rod per tick of a bare reactor (MOD-471). Against {@link #reactorBareSettlePermille} one
	 * to three racks settle below the ceiling; a fourth runs away.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Instability a bare reactor gains per rod per tick; against the settle rate this sets how many racks"
					+ " a lava farm may carry.")
	public static int reactorBareInstabilityPerRod = 6;
	/** Share of current instability a bare core sheds per tick, per mille. The decay half of the curve. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Share of current instability a bare reactor sheds per tick, per mille.")
	public static int reactorBareSettlePermille = 8;
	/** Top of the bare reactor's instability scale. The same shape as the room's heat scale. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Top of the bare reactor's instability scale.")
	public static int reactorBareInstabilityCapacity = 10000;
	/** Whether an explosion leaves irradiated ground behind. */
	@Knob(section = Section.MACHINES,
			doc = "Whether an explosion leaves irradiated ground behind.")
	public static boolean reactorFalloutEnabled = true;
	/** Blocks around the epicentre within which fallout may settle, on top of what the blast destroyed. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Blocks around the epicentre within which fallout may settle, on top of what the blast destroyed.")
	public static int reactorFalloutRadius = 8;
	/**
	 * Dose one fallout block delivers per sweep before distance and shielding; must clear {@link
	 * RadiationConfig#radiationTickInterval} or decay cancels it.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Dose one fallout block delivers per radiation sweep; must exceed radiationTickInterval or its own"
					+ " decay cancels it.")
	public static int reactorFalloutDosePerBlock = 30;
	/**
	 * Most fallout blocks one player ever counts, so a crater is not instantly lethal from its edge (as {@link
	 * RadiationConfig#radiationContainerMaxItems}).
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "How many fallout blocks one player ever counts, however large the scar.")
	public static int reactorFalloutMaxBlocksCounted = 8;
	/**
	 * Percent chance per random tick that a fallout block fades one step (doubled under water): about a day
	 * for a crater, minutes if flooded.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Percent chance per random tick that a fallout block fades one step; water on top doubles it.")
	public static int reactorFalloutDecayChancePercent = 20;
}

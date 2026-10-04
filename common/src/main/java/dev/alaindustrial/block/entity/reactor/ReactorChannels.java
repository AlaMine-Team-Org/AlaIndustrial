package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.core.structure.RoomScan;

/**
 * The reactor console's GUI sync channels (MOD-618 … MOD-623, declared once since MOD-713 batch 1f on the
 * form of MOD-712 BE-7): the four of {@link dev.alaindustrial.block.entity.machine.MachineChannels}, then
 * the room, the core, the coolant loop, the hazards, the heat marks and the room limits, then the listed
 * holes of a breached shell — {@link #HOLE_FIRST} onwards, three channels each, past the named ones.
 * The controller binds them in {@code ReactorControllerBlockEntity.createChannels}; the menu reads them
 * by name. A channel is a signed short on the wire, which is why several travel as a share or in hundreds.
 */
public enum ReactorChannels {
	ENERGY,
	CAPACITY,
	PROGRESS,
	MAX_PROGRESS,
	STATUS,
	BREACH_DX,
	BREACH_DY,
	BREACH_DZ,
	SIZE_X,
	SIZE_Y,
	SIZE_Z,
	HEAT_PERCENT,
	RODS,
	DEPTH_PERCENT,
	OUTPUT,
	/** Coolant left in the loop, as a percentage of what every column in the room could hold. */
	WATER_PERCENT,
	/** Coolant boiled last tick, in mB — the loop's demand, and what an inlet has to keep up with. */
	WATER_RATE,
	/**
	 * Steam waiting in the columns, as a percentage of what they can hold. Shown nowhere as a number
	 * — there is no room on the panel and it would be one gauge too many — but a full one is why a
	 * loop with plenty of water stops cooling, and the coolant bar changes colour on it. Without
	 * that, a blocked exhaust presents as a full tank next to a rising temperature, which reads as a
	 * bug rather than as a plumbing mistake.
	 */
	STEAM_PERCENT,
	/** Ordinal of {@code ReactorIdleReason} — what the output row says instead of a bare dash. */
	IDLE_REASON,
	/** Charge in the reactor's buffer, 0…100 — what the gauge fills to. */
	ENERGY_PERCENT,
	/**
	 * Charge in HUNDREDS of EU, not in EU.
	 *
	 * <p>{@code ContainerData} is replicated as signed 16-bit shorts, and this buffer holds 200 000:
	 * sent raw it would arrive as a negative number and the readout would show nonsense at exactly the
	 * moment the reactor was doing well. Hundreds keep the whole range inside 2000, and a hundred EU is
	 * far below anything a player can read off a bar anyway.
	 */
	ENERGY_HUNDREDS,
	/**
	 * 1 while the sealed room is melting its own contents (MOD-469).
	 *
	 * <p><b>Its own channel, and deliberately not a {@code ReactorRoomStatus} value.</b> That enum is
	 * recomputed from the shell's geometry by every sweep, so a "meltdown" written into it would be
	 * overwritten by the next scan — at most {@code reactorScanIntervalTicks} later — on a room that is
	 * still melting. The status answers "what shape is the shell in"; this answers "what is happening
	 * inside it", and the two have different lifetimes.
	 */
	MELTDOWN,
	/**
	 * How much of the accident countdown is left, 0…100 (MOD-471).
	 *
	 * <p><b>A share, not the seconds, and that is the whole reason the channel exists in this shape.</b>
	 * The countdown is rolled fresh per accident between two and three minutes precisely so a player
	 * cannot learn its length; shipping the raw tick count would let the panel print a stopwatch and
	 * hand that knowledge straight back. A bar that empties tells them time is running out without
	 * telling them exactly how much is left. 0 means no accident is under way.
	 */
	BLAST_PERCENT,
	/**
	 * The bare reactor's instability, 0…100 (MOD-471).
	 *
	 * <p>Its own channel rather than a second use of {@link #HEAT_PERCENT}: the two scales are
	 * never live at once, but they mean different things and a single channel would make the panel's
	 * gauge lie about which one it is showing the moment a breached room fell into bare mode with heat
	 * still on the clock.
	 */
	INSTABILITY,
	/**
	 * Share of the reaction's heat the water carried last tick, 0…100 (MOD-623). A hundred while nothing
	 * reacts: a stopped room is not short of water.
	 *
	 * <p>It took the coolant target's slot. The target was a mark on the heat bar where a running loop held
	 * the room; since the water carries the reaction's whole heat, a well-plumbed room sits near zero and the
	 * mark stopped describing anything a player could see.
	 */
	COOLANT_SHARE,
	/**
	 * The two heat marks the console draws — warning and start of meltdown — in percent of the scale
	 * (MOD-618).
	 *
	 * <p><b>Sent rather than read on the client.</b> {@code Config} is not synced, so a screen that read its
	 * own copy drew the lines where its local file put them rather than where this server's reactor acts —
	 * and the old gauge did exactly that with its amber step. A channel costs nothing while the value holds
	 * still: vanilla only resends a channel that changed.
	 */
	HEAT_WARN,
	HEAT_MELTDOWN,
	/**
	 * Where the measured interior starts, as an offset from the controller: its west edge (smallest X) and
	 * its north edge (smallest Z), in blocks (MOD-619). With the size channels this places the walls on the
	 * «Room» tab's map. Zero while no box was measured — the size channels say which, being zero too.
	 */
	BOX_WEST,
	BOX_NORTH,
	/**
	 * The limits the scan applies — smallest and largest interior edge, and the glass cap in percent — for the
	 * «Room» tab's checklist (MOD-619). Sent for the reason the heat marks are: {@code Config} is not synced,
	 * and a checklist reading its own copy would quote the local file's limits rather than this server's.
	 */
	ROOM_MIN_INNER,
	ROOM_MAX_INNER,
	ROOM_MAX_GLASS,
	/** How many holes the scan found (MOD-619), 0 unless it is a breach; they follow at {@link #HOLE_FIRST}. */
	HOLE_COUNT;

	/**
	 * The holes of a breached shell (MOD-619): the first {@link RoomScan#MAX_LISTED_HOLES} as offsets from the
	 * controller, three channels each — east, up and south — starting here, right after the named channels.
	 * Three channels rather than one packed short: the room limit has no ceiling in the config, and a packed
	 * offset would wrap on a large room.
	 */
	public static final int HOLE_FIRST = values().length;

	/** Width of the console's channels: the named ones and the listed holes — the menu's client stub. */
	public static final int COUNT = HOLE_FIRST + 3 * RoomScan.MAX_LISTED_HOLES;
}

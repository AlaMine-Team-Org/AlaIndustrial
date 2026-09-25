package dev.alaindustrial.block.entity;

/**
 * Decides when the Charging Station plays its "all charged" chime (MOD-668). Pure logic with no game
 * types, so the rules are pinned by a plain JUnit test rather than by listening in a client.
 *
 * <p>Fed once per payout (every five ticks while a player stands on the station), it answers "play now?"
 * under four rules:
 * <ul>
 *   <li><b>Only after real charging.</b> A visit arms the chime only once a payout actually moved EU. A
 *   player who steps on already full is READY from the first payout and hears nothing — there was
 *   nothing to finish.</li>
 *   <li><b>Debounced.</b> READY has to hold for {@link #READY_PAYOUTS} payouts in a row before the chime
 *   plays, so an item that loses a sliver of charge on the station (CHARGING for one payout, READY for
 *   the next) cannot ring a series of dings.</li>
 *   <li><b>Once per visit.</b> After it plays it stays silent until the player steps off and on again, or
 *   until a real new charge begins: {@link #REARM_PAYOUTS} CHARGING payouts in a row — a fresh item put
 *   into the inventory, not a flicker.</li>
 *   <li><b>Never on an empty station.</b> EMPTY is a different problem (the grid, not the player) and
 *   the indicator already says so; it breaks a READY run but neither arms nor plays.</li>
 * </ul>
 */
public final class ChargePadChime {

	/** Consecutive READY payouts (a quarter second each) needed before the chime plays. */
	public static final int READY_PAYOUTS = 2;

	/** Consecutive CHARGING payouts after a chime that count as a new charge and re-arm it. */
	public static final int REARM_PAYOUTS = 8;

	/** What one payout concluded, mirroring the indicator states a visitor can produce. */
	public enum Payout { CHARGING, READY, EMPTY }

	private boolean armed;
	private boolean played;
	private int readyRun;
	private int chargingRun;

	/** A fresh visit begins: forget everything about the previous one. */
	public void onArrive() {
		armed = false;
		played = false;
		readyRun = 0;
		chargingRun = 0;
	}

	/** Records one payout and returns whether the chime should play on it. */
	public boolean onPayout(Payout payout) {
		switch (payout) {
			case CHARGING -> {
				readyRun = 0;
				chargingRun++;
				if (played && chargingRun >= REARM_PAYOUTS) {
					played = false;
				}
				armed = true;
				return false;
			}
			case EMPTY -> {
				readyRun = 0;
				chargingRun = 0;
				return false;
			}
			default -> {
				chargingRun = 0;
				if (!armed || played) {
					return false;
				}
				readyRun++;
				if (readyRun < READY_PAYOUTS) {
					return false;
				}
				played = true;
				armed = false;
				return true;
			}
		}
	}
}

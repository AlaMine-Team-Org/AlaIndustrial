package dev.alaindustrial.core.structure;

/**
 * The reactor's arithmetic (MOD-468, stage 2) — output, heat and the neighbour bonus, with no
 * Minecraft type in sight so the balance can be exercised by L1 tests rather than by playing.
 *
 * <p><b>Output and heat come from the same number.</b> Every knob that raises power raises heat by the
 * same factor: more rods, tighter packing, deeper insertion. That is the whole design — there is no
 * setting that is simply better, only a choice of how close to the edge to run.
 *
 * <p><b>Depth is a throttle, not a discount.</b> Half depth means half the output, half the heat and
 * half the fuel burnt. Running a reactor gently stretches the uranium instead of wasting it, so the
 * player is never punished for being careful.
 */
public final class ReactorCore {

	private ReactorCore() {
	}

	/** Depth is carried in thousandths so a slider can be precise without floating point. */
	public static final int FULL_DEPTH = 1000;

	/**
	 * EU per tick a reactor produces.
	 *
	 * @param rods            total rods currently racked and burning
	 * @param neighbourPairs  how many adjacent loaded-assembly pairs exist in the room; each pair is
	 *                        counted once, and both racks in it get the bonus
	 * @param euPerRod        base output of one rod at full depth
	 * @param bonusPercent    extra output per adjacency, in percent
	 * @param depthPermille   0…{@link #FULL_DEPTH}
	 */
	public static long output(int rods, int neighbourPairs, int euPerRod, int bonusPercent,
			int depthPermille) {
		if (rods <= 0 || depthPermille <= 0) {
			return 0;
		}
		long base = (long) rods * euPerRod;
		// Each adjacency benefits both of its racks, hence x2 — the reason a tight block of assemblies
		// is worth so much more than the same rods spread out.
		long bonus = base * 2L * neighbourPairs * bonusPercent / 100L;
		return (base + bonus) * clampDepth(depthPermille) / FULL_DEPTH;
	}

	/**
	 * Heat produced this tick, before cooling. Same shape as {@link #output}, but the caller passes a
	 * <em>larger</em> {@code bonusPercent} — see {@code Config.reactorHeatNeighbourBonusPercent}. An
	 * arrangement that doubles the power more than doubles the heat, so density is a trade rather than
	 * a free win: a packed core reaches the tier ceiling on shallower rods, and pays for it in water.
	 */
	public static long heatProduced(int rods, int neighbourPairs, int heatPerRod, int bonusPercent,
			int depthPermille) {
		if (rods <= 0 || depthPermille <= 0) {
			return 0;
		}
		long base = (long) rods * heatPerRod;
		long bonus = base * 2L * neighbourPairs * bonusPercent / 100L;
		return (base + bonus) * clampDepth(depthPermille) / FULL_DEPTH;
	}

	/**
	 * Heat the shell sheds this tick on its own: a flat floor plus a share of the current temperature.
	 *
	 * <p>The proportional term is the whole point. A flat loss gives a reactor exactly two states —
	 * below it the gauge never leaves zero, above it the gauge climbs to the top and stays — and the
	 * first of those is what a player building their first reactor sees. With loss rising alongside
	 * temperature every core settles where production and losses meet, and the gauge becomes a reading
	 * of how hard this particular reactor is working.
	 */
	public static long naturalCooling(long heat, int passiveCooling, int lossPermille) {
		long base = Math.max(0, passiveCooling);
		long proportional = heat <= 0 || lossPermille <= 0 ? 0 : heat * lossPermille / 1000;
		return base + proportional;
	}

	/**
	 * Heat after one tick of production and cooling, clamped to the scale.
	 *
	 * <p>Cooling applies whether or not the reactor is running — a shut-down core still sheds heat,
	 * which is what makes "scram and wait" a real recovery rather than a permanent loss.
	 */
	public static long settleHeat(long heat, long produced, long cooling, int capacity) {
		long next = heat + produced - Math.max(0, cooling);
		if (next < 0) {
			return 0;
		}
		return Math.min(next, Math.max(0, capacity));
	}

	/** Heat as a percentage of the scale — what the gauge and the status line read. */
	public static int heatPercent(long heat, int capacity) {
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(100, Math.max(0, heat * 100 / capacity));
	}

	/**
	 * Total energy in one uranium rod. A rod is a quantity of EU, and packing columns together changes
	 * how FAST it comes out, never how much there is.
	 *
	 * <p>That framing replaced a depth-scaled one that did not survive scale. Fuel and heat used to be
	 * driven by an "effective depth" — the fraction of its potential the core was actually being asked
	 * for — carried as an integer per mille. On a core big enough to overshoot the tier ceiling a
	 * hundredfold that fraction truncated to its 1‰ floor, and two absurdities followed from the same
	 * line: a rod lasted about a thousand real days, and heat became {@code fullHeat / 1000}, which
	 * grows without bound as the room grows. A 12-block room came out at roughly 105 000 heat a tick —
	 * more coolant than the shell has surface to admit.
	 */
	public static long rodEnergy(int euPerRod, int burnTicks) {
		return Math.max(0L, (long) euPerRod * Math.max(0, burnTicks));
	}

	/**
	 * Heat for the energy actually produced.
	 *
	 * <p>One division at the end, against the potential at full depth. Because both terms scale with
	 * the same rods and the same adjacencies, the ratio converges as a core grows instead of diverging:
	 * heat per EU approaches {@code heatPerRod x heatBonus / (euPerRod x energyBonus)}, so a room at the
	 * tier ceiling settles near a fixed temperature however many columns are packed into it.
	 */
	public static long heatForOutput(long heatAtFullDepth, long output, long outputAtFullDepth) {
		if (heatAtFullDepth <= 0 || output <= 0 || outputAtFullDepth <= 0) {
			return 0;
		}
		return heatAtFullDepth * output / outputAtFullDepth;
	}

	/**
	 * Water needed to carry {@code heat} away, in mB, rounded UP.
	 *
	 * <p>Rounding up rather than down is what stops a trickle of heat from being cooled for free: the
	 * truncating version returned 0 for anything below the exchange rate, and a reactor idling just
	 * under it would have held its temperature on no water at all.
	 */
	public static long waterForHeat(long heat, int heatPerWater) {
		if (heat <= 0 || heatPerWater <= 0) {
			return 0;
		}
		return (heat + heatPerWater - 1) / heatPerWater;
	}

	/** The inverse: heat that {@code water} mB actually carries away once it boils. */
	public static long heatRemovedByWater(long water, int heatPerWater) {
		return water <= 0 || heatPerWater <= 0 ? 0 : water * heatPerWater;
	}

	/**
	 * Whether the overheat alarm should sound this tick, given whether it has already sounded (MOD-472).
	 *
	 * <p><b>Why this needs a deadband and cannot be a plain {@code heat >= warn} test.</b> A reactor with
	 * no plumbing settles at its own equilibrium, and for two adjacent columns that equilibrium is 66 %
	 * of the scale — three points under the 70 % warning line. A level test on a core hovering there
	 * fires, clears and fires again as the temperature wobbles by a single unit, which is a siren
	 * stuttering several times a second rather than a warning.
	 *
	 * <p>So the alarm is an edge with a rearm floor: it sounds once when the core crosses
	 * {@code warnPercent} going up, and it cannot sound again until the core has come back down below
	 * {@code rearmPercent}. Pointing the floor at {@code Config.reactorCoolantTargetPercent} (60) rather
	 * than inventing a number gives the deadband a meaning the player can act on — the alarm re-arms
	 * exactly when the coolant loop has done its job, because that target is the temperature the loop
	 * holds the core at.
	 *
	 * @param heatPercent   where the core is now, 0…100
	 * @param warnPercent   the line that fires the alarm going up
	 * @param rearmPercent  the line the core must fall below before it can fire again
	 * @param alreadyWarned whether the alarm is currently latched (the caller's persisted flag)
	 * @return true only on the rising crossing, i.e. exactly the ticks the siren should play
	 */
	public static boolean shouldSoundAlarm(int heatPercent, int warnPercent, int rearmPercent,
			boolean alreadyWarned) {
		return !alreadyWarned && heatPercent >= warnPercent;
	}

	/**
	 * The latch that goes with {@link #shouldSoundAlarm}: whether the alarm stays armed after this tick.
	 *
	 * <p>Kept as its own function rather than folded into the caller so the pair is testable as a pair —
	 * the failure this guards against is not "does it fire" but "does it fire twice", and that only shows
	 * up in the sequence.
	 *
	 * <p>The rearm floor is clamped to <em>at most</em> the warning line. That stops a nonsensical
	 * configuration (floor above the line) from unlatching the alarm on the very tick it fired; what it
	 * does not do is invent a deadband where the file asks for none — cross the two and the gap collapses
	 * to zero, so a core wobbling across the line will re-sound. The shipped pair puts the floor ten
	 * points below the line precisely so that cannot happen.
	 */
	public static boolean alarmStaysLatched(int heatPercent, int warnPercent, int rearmPercent,
			boolean alreadyWarned) {
		int floor = Math.min(rearmPercent, warnPercent);
		if (alreadyWarned) {
			return heatPercent >= floor;
		}
		return heatPercent >= warnPercent;
	}

	/** The top of the heat scale, where the warning stops being a warning. */
	public static final int CRITICAL_PERCENT = 100;

	/** Shortest gap between two blasts of the critical alarm — three seconds. */
	public static final int CRITICAL_ALARM_MIN_TICKS = 60;

	/** Longest gap between two blasts of the critical alarm — five seconds. */
	public static final int CRITICAL_ALARM_MAX_TICKS = 100;

	/**
	 * Whether the core is pinned at the top of the scale (MOD-472).
	 *
	 * <p><b>A different state from "warning", and it needs a different sound behaviour.</b> The
	 * threshold alarm is an edge: it fires once when the core crosses the warning line and then latches,
	 * which is right for "look at this soon". It is wrong for a core sitting at a hundred percent —
	 * there the single blast has long since played and the reactor then sits in its worst state in
	 * silence. So the top of the scale re-sounds the siren on a jittered three-to-five second cycle for
	 * as long as it stays there, and stops the moment the temperature comes down at all.
	 *
	 * <p>The interval is jittered rather than fixed because a perfectly regular repeat reads as a
	 * background loop and stops being heard; an irregular one keeps announcing itself.
	 */
	public static boolean isCritical(int heatPercent) {
		return heatPercent >= CRITICAL_PERCENT;
	}

	/**
	 * EU per tick a reactor makes with no sealed room around it (MOD-469).
	 *
	 * <p><b>A share of the room figure, then a flat ceiling — in that order, and both are load-bearing.</b>
	 * The share is what makes a bare core weaker than the same rods properly housed; the ceiling is what
	 * stops a player from answering that by simply piling on more rods. Without the ceiling the share
	 * alone would leave a bare cluster scaling for ever, which turns the dangerous shortcut into the
	 * cheapest power in the game — no walls to build, no coolant to plumb, no shell to craft.
	 *
	 * <p>{@code cap <= 0} means no ceiling. That is not a supported balance, only a configuration the
	 * arithmetic must not divide by.
	 *
	 * @param roomOutput what {@link #output} gives for these rods in a sealed room, before the tier cap
	 * @param percent    share the bare core keeps, in percent
	 * @param cap        hard ceiling in EU/t, independent of how big the cluster grows
	 */
	public static long bareOutput(long roomOutput, int percent, int cap) {
		if (roomOutput <= 0 || percent <= 0) {
			return 0;
		}
		long scaled = roomOutput * percent / 100;
		return cap <= 0 ? scaled : Math.min(scaled, cap);
	}

	/**
	 * Ticks between two blocks melting under a working bare reactor (MOD-469).
	 *
	 * <p>The gap shrinks as the cluster grows, so one rod forgotten in an open rack is a nuisance and a
	 * serious bare station is genuinely frightening — the design's own words. It is a division rather
	 * than a subtraction so the danger keeps rising with every rod instead of bottoming out at some
	 * cluster size and staying there; {@code minTicks} is the floor that keeps a huge cluster from
	 * melting the world faster than the server can tick it.
	 */
	public static int meltInterval(int rods, int baseTicks, int minTicks) {
		int floor = Math.max(1, minTicks);
		if (baseTicks <= 0) {
			return floor;
		}
		return Math.max(floor, baseTicks / Math.max(1, rods));
	}

	/**
	 * Whether a sealed room is hot enough for its own contents to start melting (MOD-469).
	 *
	 * <p>Deliberately a plain level test with no deadband, unlike {@link #shouldSoundAlarm}. The alarm
	 * needed one because it is an EDGE — firing twice is the failure. Melting is a rate: it already runs
	 * on its own interval, and every melted block sheds heat, so a core wobbling across the line simply
	 * melts a little more slowly. There is nothing here for a deadband to protect.
	 *
	 * <p>The threshold sits between the warning line and the top of the scale on purpose: the warning is
	 * "look at this", the meltdown is "you are losing the room's contents", and a hundred percent belongs
	 * to MOD-471's explosion.
	 */
	public static boolean isMeltingDown(int heatPercent, int startPercent) {
		return startPercent > 0 && heatPercent >= startPercent;
	}

	/**
	 * Heat left after a block of the room's contents has melted (MOD-469).
	 *
	 * <p><b>This is what stops a meltdown being a one-way trip.</b> Each melted block carries heat away
	 * with it, so a room that overheats eats its own guts and cools down as it does — the player loses
	 * the columns and the plumbing, keeps the shell, and gets a reactor they can rebuild. Without the
	 * relief the core would sit above the line for ever and melt the interior to the last cell, which is
	 * indistinguishable from the explosion MOD-471 owns.
	 */
	public static long heatAfterMelt(long heat, int relief) {
		return Math.max(0, heat - Math.max(0, relief));
	}

	// ── MOD-471: the accident at the top of the scale ──

	/**
	 * How hard a reactor blows up, given the rods it was carrying.
	 *
	 * <p><b>The ceiling is the whole balance, and it is a number with a physical meaning.</b> A reactor
	 * shell has an explosion resistance of 30, and vanilla drains {@code (resistance + 0.3) x 0.3} per
	 * 0.3-block step — so one cell of wall eats 28 to 37 power. Anything under about 24 is therefore
	 * held completely by a sealed room: the interior is gutted, the wall is holed, and not one block
	 * outside changes. Past that the containment starts to leak, and at the shipped ceiling a station
	 * throws debris twenty-odd blocks past its own wall. That progression is the point — a small
	 * reactor errs cheaply, a large one catastrophically — and it is why the cap is not merely a
	 * safety valve.
	 *
	 * <p>Below roughly 8 the blast cannot even scratch the shell: a ray must have power left AFTER the
	 * resistance is subtracted for the block to break at all. So the smallest possible accident wrecks
	 * what is inside the room and leaves the room standing.
	 *
	 * <p>Carried per TEN rods so the configuration stays integer; the caller divides once, here.
	 *
	 * @param rods        rods burning when the countdown ran out
	 * @param base        power of an empty core
	 * @param perTenRods  added power per ten rods
	 * @param cap         hard ceiling; {@code <= 0} means none
	 */
	public static float blastPower(int rods, int base, int perTenRods, int cap) {
		float raw = Math.max(0, base) + Math.max(0, perTenRods) * Math.max(0, rods) / 10.0f;
		return cap <= 0 ? raw : Math.min(raw, cap);
	}

	/**
	 * How long this particular accident takes to arrive, in ticks.
	 *
	 * <p><b>Rolled per accident rather than fixed, and that is a design requirement rather than
	 * flavour.</b> A constant delay becomes a memorised norm within a week — players learn "half a
	 * minute" and treat the countdown as a chore with a known length. A spread keeps the edge blurred:
	 * every alarm is a real question about how much time is left.
	 *
	 * <p>Takes the raw sample rather than a random source so the whole rule stays testable on the
	 * Minecraft-free lane. A reversed pair is clamped rather than rejected — a configuration where
	 * max &lt; min should still produce a working reactor, just an unsurprising one.
	 */
	public static int blastCountdown(int minTicks, int maxTicks, int sample) {
		int lo = Math.max(1, minTicks);
		int hi = Math.max(lo, maxTicks);
		return lo + Math.floorMod(sample, hi - lo + 1);
	}

	/**
	 * The accident timer: how long is left, what it started from, and how long the core has been back
	 * under the line.
	 *
	 * @param remaining   ticks to the explosion; 0 means no accident is under way
	 * @param total       what {@code remaining} started from, so a panel can draw a share
	 * @param belowTicks  consecutive ticks the scale has spent under a hundred percent
	 */
	public record BlastTimer(int remaining, int total, int belowTicks) {

		public static final BlastTimer IDLE = new BlastTimer(0, 0, 0);

		public boolean armed() {
			return remaining > 0;
		}
	}

	/**
	 * One tick of the accident timer (MOD-471).
	 *
	 * <p><b>Cancelling costs time, not a blink — and that is the whole reason this is a state machine
	 * rather than a level test.</b> The first version cleared the countdown the moment the gauge left a
	 * hundred percent, which sounded like the promise the design made ("it can be cancelled up to the
	 * last tick") and was in fact an exploit a player found within an hour: cutting the redstone for a
	 * SINGLE tick drops the gauge — the heat is clamped at the top, so one tick of cooling is enough to
	 * read 99 % — and a fresh countdown is rolled the tick after. A repeater clock with one tick off in
	 * twenty therefore ran the reactor at ninety-five percent duty and made it permanently immune.
	 *
	 * <p>So the rule is now: the countdown <em>pauses</em> while the core is under the line and only
	 * clears once it has STAYED under it for {@code releaseTicks}. Every honest way out still works —
	 * water, the scram lever, a breached wall all hold the core down for far longer than that — and
	 * every dishonest one stops working, because a duty cycle short enough to keep the gauge pinned is
	 * by definition too short to ever finish the release window.
	 *
	 * <p>Pausing rather than continuing to drain is what keeps the promise honest in the other
	 * direction: a player who fixes the coolant with two seconds left does not get punished for the two
	 * seconds it takes the temperature to fall.
	 *
	 * @param rolledDuration the duration to use if this tick ARMS the timer; ignored otherwise
	 */
	public static BlastTimer tickBlast(BlastTimer timer, boolean critical, int releaseTicks,
			int rolledDuration) {
		if (critical) {
			if (!timer.armed()) {
				int duration = Math.max(1, rolledDuration);
				return new BlastTimer(duration, duration, 0);
			}
			// Back at the line: whatever release the player had banked is gone, and the clock runs again.
			return new BlastTimer(timer.remaining() - 1, timer.total(), 0);
		}
		if (!timer.armed()) {
			return BlastTimer.IDLE;
		}
		int below = timer.belowTicks() + 1;
		if (below >= Math.max(1, releaseTicks)) {
			return BlastTimer.IDLE;
		}
		return new BlastTimer(timer.remaining(), timer.total(), below);
	}

	/**
	 * Instability a bare core adds per tick — the scale a reactor with no room runs on (MOD-471).
	 *
	 * <p><b>Why a bare reactor needs a second scale at all.</b> Heat belongs to the room: it is made by
	 * the energy actually sold, carried away by water, and read off a gauge the shell makes meaningful.
	 * A bare core has none of that and deliberately produces no heat whatsoever. Yet the player's own
	 * ruling is that a bare cluster must have a limit — small enough to farm lava from for ever, large
	 * enough to become a bomb. So the danger is measured by what a bare core actually has: the size of
	 * the pile.
	 *
	 * <p>Paired with a decay proportional to the current value ({@link #naturalCooling} with no flat
	 * floor), this gives the same equilibrium curve the room's heat has — and with it the property the
	 * whole feature rests on: a small cluster settles below the top and stays there indefinitely, a
	 * large one has an equilibrium above the ceiling and therefore runs away. The threshold is a
	 * rack count the player can count on their fingers, not a number in a config file.
	 */
	public static long instabilityGain(int rods, int perRod) {
		return (long) Math.max(0, rods) * Math.max(0, perRod);
	}

	/**
	 * Instability a bare core sheds this tick — a share of what it holds, plus one.
	 *
	 * <p><b>The "plus one" is not tuning, it is what stops the scale sticking.</b> The share is integer
	 * arithmetic: at the shipped 8 per mille, anything under 125 sheds {@code value * 8 / 1000 == 0} and
	 * a scrammed pile parks there for ever, showing one percent on a panel that will never reach zero.
	 * The room's heat scale never had the problem because it carries a flat floor of its own
	 * ({@code reactorPassiveCooling}); this is the same floor, at the smallest value that closes the
	 * gap. A unit test drives a full pile down to nothing precisely to keep it closed.
	 *
	 * <p>Not a config key, deliberately: it is a correctness floor rather than a balance number, and an
	 * operator who set it to zero would get a reactor whose scram silently stops working.
	 */
	public static long instabilityDecay(long instability, int permille) {
		return instability <= 0 ? 0 : naturalCooling(instability, 1, permille);
	}

	/**
	 * Dose a patch of fallout delivers per sweep, before distance and line of sight (MOD-471).
	 *
	 * <p><b>The cap is not tuning, it is what stops the crater being a death sentence.</b> Strength
	 * linear in the block count would make a forty-cell scar instantly lethal at any distance the
	 * radius allows, which is the exact trap MOD-474 had to fix for containers. Counting at most a
	 * handful of cells keeps a fallout field dangerous to stand in and survivable to walk past.
	 *
	 * <p>The per-block figure has a floor it must clear that has nothing to do with balance: a dose is
	 * stored as the remaining duration of an effect that vanilla ticks down every tick, so a source
	 * contributing less than {@code radiationTickInterval} per sweep contributes nothing at all.
	 */
	public static int falloutDose(int blocks, int perBlock, int maxCounted) {
		if (blocks <= 0 || perBlock <= 0) {
			return 0;
		}
		int counted = maxCounted > 0 ? Math.min(blocks, maxCounted) : blocks;
		return counted * perBlock;
	}

	private static int clampDepth(int depthPermille) {
		return Math.min(FULL_DEPTH, Math.max(0, depthPermille));
	}
}

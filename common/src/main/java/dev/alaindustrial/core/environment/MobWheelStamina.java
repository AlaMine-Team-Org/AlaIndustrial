package dev.alaindustrial.core.environment;

/**
 * How tired the mob in a wheel is (MOD-763, decisions D4 and D5) — the only thing about it that changes
 * while it works. Minecraft-free state with explicit transitions, so the L1 lane can walk it.
 *
 * <p>Stamina is counted in running ticks: a fresh mob holds {@link #max()} and spends one per tick it
 * actually runs. At zero it is <b>exhausted</b> and stops. An exhausted mob rests on its own — slowly, at its
 * species' rate, faster with hay — and runs again only once it is <b>full</b>: a half-rested mob does not set
 * off and tire again a moment later. Food skips the rest: one portion makes an exhausted mob full at once,
 * and food offered to a mob that is not exhausted is refused, so nothing is eaten ahead of time and the
 * effect never stacks.
 *
 * <p>A witch ({@link MobWheelProfile.Trait#SELF_RESTORE}) once per run gets part of its stamina back on its own
 * ({@link #selfRestore}); a run ends when the mob is exhausted, so the next run may do it again.
 */
public final class MobWheelStamina {
	private int max;
	private int remaining;
	private boolean exhausted;
	/** Fractional rest progress, so a slow rest rate loses nothing to integer division. */
	private long restAccumulator;
	/** Whether this run's one {@link #selfRestore} is spent. */
	private boolean selfRestored;

	private MobWheelStamina(int max, int remaining, boolean exhausted, long restAccumulator) {
		this.max = Math.max(1, max);
		this.remaining = Math.max(0, Math.min(this.max, remaining));
		this.exhausted = exhausted || this.remaining == 0;
		this.restAccumulator = Math.max(0L, restAccumulator);
	}

	/** A fresh mob: full and ready to run. */
	public static MobWheelStamina full(int max) {
		return new MobWheelStamina(max, max, false, 0L);
	}

	/** State read back from a save; out-of-range values are clamped rather than trusted. */
	public static MobWheelStamina restore(int max, int remaining, boolean exhausted, long restAccumulator) {
		return new MobWheelStamina(max, remaining, exhausted, restAccumulator);
	}

	/** State read back from a save, with this run's self-restore flag. */
	public static MobWheelStamina restore(int max, int remaining, boolean exhausted, long restAccumulator,
			boolean selfRestored) {
		MobWheelStamina stamina = new MobWheelStamina(max, remaining, exhausted, restAccumulator);
		stamina.selfRestored = selfRestored && !stamina.exhausted;
		return stamina;
	}

	/** Whether this run's one self-restore has already happened. */
	public boolean selfRestored() {
		return selfRestored;
	}

	/**
	 * Once per run, when no more than {@code atPercent} percent is left of a running mob's stamina, give back
	 * {@code percent} percent of the maximum. Returns whether it happened now — the caller plays the sound.
	 * Exhaustion ends the run, so the next run may restore again.
	 */
	public boolean selfRestore(int atPercent, int percent) {
		if (selfRestored || !canRun() || (long) remaining * 100 > (long) max * atPercent) {
			return false;
		}
		remaining = (int) Math.min(max, remaining + (long) max * Math.max(0, percent) / 100);
		selfRestored = true;
		return true;
	}

	public int max() {
		return max;
	}

	public int remaining() {
		return remaining;
	}

	public boolean exhausted() {
		return exhausted;
	}

	public long restAccumulator() {
		return restAccumulator;
	}

	/** Whether the mob may run this tick. */
	public boolean canRun() {
		return !exhausted && remaining > 0;
	}

	/** Stamina left, {@code 0..1}. */
	public double fraction() {
		return (double) remaining / max;
	}

	/** Stamina left in thousandths — what the GUI channel carries (a channel is a short, not a double). */
	public int permille() {
		return (int) ((long) remaining * 1000 / max);
	}

	/** One tick of running; at zero the mob is exhausted. A no-op for a mob that cannot run. */
	public void runTick() {
		if (!canRun()) {
			return;
		}
		remaining--;
		if (remaining == 0) {
			exhausted = true;
			restAccumulator = 0L;
			selfRestored = false;
		}
	}

	/**
	 * One tick of rest for an exhausted mob; a no-op otherwise. A full rest takes
	 * {@code restTicks × 1000 / restPermille / hayMultiplier} ticks, after which the mob may run again.
	 *
	 * @param restPermille  the species' rest rate in thousandths ({@link MobWheelProfile#restPermille()})
	 * @param hayMultiplier how many times faster hay makes it rest; 1 without hay (below 1 counts as 1)
	 * @param restTicks     ticks of a full rest at rate 1000 without hay ({@code GeneratorConfig.mobWheelRestTicks})
	 */
	public void restTick(int restPermille, int hayMultiplier, int restTicks) {
		if (!exhausted) {
			return;
		}
		long denominator = (long) Math.max(1, restTicks) * 1000L;
		restAccumulator += (long) max * Math.max(0, restPermille) * Math.max(1, hayMultiplier);
		long gained = restAccumulator / denominator;
		restAccumulator %= denominator;
		remaining = (int) Math.min(max, remaining + gained);
		if (remaining >= max) {
			exhausted = false;
			restAccumulator = 0L;
		}
	}

	/**
	 * One portion of food: an exhausted mob becomes full at once. Refused (returns {@code false}, nothing
	 * changes) while the mob is not exhausted, so the caller spends the portion only when this says yes.
	 */
	public boolean feed() {
		if (!exhausted) {
			return false;
		}
		remaining = max;
		exhausted = false;
		restAccumulator = 0L;
		return true;
	}
}

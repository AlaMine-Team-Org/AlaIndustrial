package dev.alaindustrial.core.environment;

import dev.alaindustrial.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Per-panel sampler that caches the {@link SolarSky.Access} and {@link SolarSky.Weather} verdicts for
 * {@link Config#solarSkySampleTicks} ticks, so a solar farm does not re-run the upward column scan
 * ({@link SolarSky#classify}) and the biome-precipitation lookup ({@link SolarSky#classifyWeather})
 * every tick. Mirrors the sampling cadence already used by {@code WindMillBlockEntity}.
 *
 * <p>One instance lives inside each solar panel block entity. The first call always samples (so a
 * freshly placed panel reacts immediately); subsequent calls return the cached verdict until the
 * counter wraps to a sample tick, then refresh both values atomically.
 *
 * <p>The cache is intentionally per-panel rather than per-level: a column scan is local to one
 * {@link BlockPos}, two panels side by side see different columns (one may be under a leaf, the
 * other open), and a global cache would have to key by position anyway. Per-panel also keeps the
 * invalidation story trivial — there is no shared state to invalidate on neighbour change, the
 * next periodic sample picks it up within {@code solarSkySampleTicks} ticks (≤2 s by default).
 *
 * <p>Thread-safety: this holder is only touched from the server tick that owns the panel block
 * entity, the same single-threaded contract as the rest of the per-tick machine state.
 */
public final class SolarSkyCache {
	private int sampleCounter;
	private SolarSky.Access cachedSky = SolarSky.Access.BLOCKED;
	private SolarSky.Weather cachedWeather = SolarSky.Weather.NONE;
	private boolean initialised;

	/**
	 * Refresh the verdict if the sample cadence says so and return whether this call sampled fresh
	 * state (always true the first time, then once every {@link Config#solarSkySampleTicks} ticks).
	 * After this call {@link #sky()} and {@link #weather()} return the freshly cached values.
	 */
	public void sample(Level level, BlockPos pos) {
		if (!initialised || sampleCounter % Math.max(1, Config.solarSkySampleTicks) == 0) {
			cachedSky = SolarSky.classify(level, pos);
			cachedWeather = SolarSky.classifyWeather(level, pos);
			initialised = true;
		}
		sampleCounter++;
	}

	/** The cached sky-access verdict; meaningful only after {@link #sample}. */
	public SolarSky.Access sky() {
		return cachedSky;
	}

	/** The cached weather verdict; meaningful only after {@link #sample}. */
	public SolarSky.Weather weather() {
		return cachedWeather;
	}

	/**
	 * Force a fresh sample on the very next {@link #sample} call. Used when a panel knows its world
	 * changed out from under the cadence (e.g. it just evolved into a new block entity that took over
	 * the position) — the new instance starts un-initialised anyway, so this is mostly a self-check
	 * helper for tests.
	 */
	public void invalidate() {
		initialised = false;
		sampleCounter = 0;
	}
}

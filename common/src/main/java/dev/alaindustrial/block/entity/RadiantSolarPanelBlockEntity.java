package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.RadiantSolarPanelBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.SolarSkyCache;
import dev.alaindustrial.menu.RadiantSolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mirror Concentrator — the third rung of the day branch (MOD-602). Base output
 * {@link Config#radiantEuPerTick}, lifted by half over the two thousand ticks around noon; rain,
 * thunder and snow all stop it dead.
 *
 * <p><b>Why snow is a blackout here and a trickle on its ancestors.</b> The two panels below spread
 * their cells flat and a dusting still lets some light through. This one focuses light with mirrors,
 * and a mirror under snow reflects nothing at all. It is also what the folding wings are for: the
 * block visibly shuts its optics away exactly when the weather would ruin them.
 *
 * <p>Sync channels: {@code progress} = production (EU/t); {@code maxProgress} = mode, one of the
 * {@code MODE_*} codes below, which the screen turns into a line of text.
 */
public class RadiantSolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	/**
	 * Cap on what leaves the block per tick, comfortably above the 12 EU/t the machine makes at noon.
	 *
	 * <p>Worth keeping above the production figure rather than equal to it: a cap at or below what
	 * the generator makes turns the surplus into energy the readout promises and the player can never
	 * spend, because the buffer fills once and everything after that is thrown away.
	 */
	private static final int MAX_EXTRACT = 20;
	/** No machine slots: this is the top of the branch, there is nothing further to evolve into. */
	public static final int SLOT_COUNT = 0;

	/**
	 * Half of a Minecraft day is 12 000 ticks and noon falls in the middle of it. The peak window is
	 * the two thousand ticks around noon; the arithmetic behind the daily average in the task
	 * (8 EU/t for 10 000 ticks plus 12 EU/t for 2 000) is this window and nothing else.
	 */
	private static final long NOON = 6000L;
	private static final long PEAK_HALF_WIDTH = 1000L;
	private static final long DAY_LENGTH = 24000L;

	/** Caches the sky/weather verdict for {@link Config#solarSkySampleTicks} ticks. */
	private final SolarSkyCache skyCache = new SolarSkyCache();

	/** Mode codes shared with the screen. */
	public static final int MODE_NIGHT = 0;
	public static final int MODE_DAY = 1;
	public static final int MODE_DAY_WEATHER = 2;
	public static final int MODE_DAY_PARTIAL = 3;
	public static final int MODE_DAY_SNOW = 4;
	/** Noon: the two-thousand-tick window where output is half again as high. */
	public static final int MODE_DAY_PEAK = 5;

	public RadiantSolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.RADIANT_SOLAR_PANEL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.radiantBuffer, MAX_EXTRACT);
	}

	/** Top face is the working surface and emits no EU; the other five are generator outputs (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	/** True inside the noon window. */
	private static boolean isNoon(Level level) {
		long timeOfDay = Math.floorMod(level.getOverworldClockTime(), DAY_LENGTH);
		return Math.abs(timeOfDay - NOON) < PEAK_HALF_WIDTH;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		// Assembled, the machine is asked about the sky one block up: its own top cell sits directly
		// over the core, and counting that as shade would halve the output of a machine standing in
		// the open. The other three columns are checked separately below.
		skyCache.sample(level, RadiantSolarPanelBlock.skyProbe(pos, state));
		SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? skyCache.sky()
				: SolarSky.Access.BLOCKED;
		boolean overworldSky = sky != SolarSky.Access.BLOCKED;

		int production = 0;
		int mode = MODE_NIGHT;

		if (!RadiantSolarPanelBlock.coveredColumnsAreClear(level, pos, state)) {
			// Assembled, a roof over ANY of the four columns stops the machine — the collector spans
			// the whole footprint. The one-block form always passes this.
			overworldSky = false;
		}
		if (overworldSky && level.isBrightOutside()) {
			// The structure earns its bigger number by its footprint, not by its block count: assembled
			// or not, this is one machine reading one column of sky, and the four-times figure is the
			// four blocks of ground it now covers.
			production = state.getValue(RadiantSolarPanelBlock.ASSEMBLED)
					? Config.radiantAssembledEuPerTick
					: Config.radiantEuPerTick;
			mode = isNoon(level) ? MODE_DAY_PEAK : MODE_DAY;
			if (mode == MODE_DAY_PEAK) {
				// Half again as much, rounded — the peak is a window, not a curve, so the number the
				// player reads is stable for the whole two thousand ticks.
				production = Math.round(production * 1.5f);
			}
			switch (skyCache.weather()) {
				case RAIN -> {
					production = 0;
					mode = MODE_DAY_WEATHER;
				}
				case SNOW -> {
					// A snowed-over mirror reflects nothing: full blackout, unlike the floored trickle
					// its two ancestors keep.
					production = 0;
					mode = MODE_DAY_SNOW;
				}
				case NONE -> {
					if (sky == SolarSky.Access.PARTIAL) {
						// Light through leaves or cobweb still reaches the collector, just less of it
						// (MOD-004). Applied after the noon lift so the two stack the way a player expects.
						production = Math.round(production * Config.solarTransparentFactor);
						mode = MODE_DAY_PARTIAL;
					}
				}
			}
		} else if (overworldSky && SolarSky.isClockDaytime(level) && level.isThundering()) {
			// Daytime thunderstorm: the sky is too dark for isBrightOutside(), output is honestly zero,
			// but calling it "night" at noon is a lie the player can see out of the window.
			mode = MODE_DAY_WEATHER;
		}

		this.maxProgress = mode;
		return production;
	}

	/** Written after the global multiplier so the GUI shows the rate the buffer actually gains (MOD-356). */
	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.progress = effectiveEuPerTick;
	}

	// --- Folding wings: a client-side clock, no packet of its own ---

	/**
	 * How long the wings take to fold, in ticks. The designer's keyframes run 1.2 s, and this is that
	 * at twenty ticks a second.
	 */
	private static final int FOLD_TICKS = 24;
	private static final long NO_TRANSITION = Long.MIN_VALUE;

	private boolean wingsOpen;
	private boolean wingsKnown;
	private long transitionStart = NO_TRANSITION;
	private long lastSampledTick = Long.MIN_VALUE;

	/**
	 * Whether the optics should be out, sampled at most once a tick.
	 *
	 * <p>Derived, not synced. {@link SolarSky#isConcentratorActive} is a pure function of world state
	 * the client already has, exactly as the ambient hum is (pattern C), so the wings need no block
	 * state property and a solar farm costs no block updates at dawn. The once-a-tick guard matters:
	 * the verdict costs a column scan, and a renderer asks its block entity once per FRAME.
	 */
	private boolean sampleWingsOpen(Level level, long gameTime) {
		if (!wingsKnown || gameTime != lastSampledTick) {
			lastSampledTick = gameTime;
			wingsOpen = SolarSky.isConcentratorActive(level, getBlockPos());
			wingsKnown = true;
		}
		return wingsOpen;
	}

	/**
	 * Fold state for the renderer: {@code 0} fully open, {@code 1} fully folded over the collector.
	 *
	 * <p>Same shape as the workstation's fold-out clock — the block entity notices the edge on its own
	 * and remembers when, so nothing extra crosses the network. Eased with smoothstep so the wings
	 * settle instead of stopping dead.
	 */
	public float foldProgress(long gameTime, float partialTicks) {
		Level level = getLevel();
		if (level == null) {
			return 0.0f;
		}
		boolean open = wingsOpen;
		boolean known = wingsKnown;
		boolean now = sampleWingsOpen(level, gameTime);
		if (known && now != open) {
			transitionStart = gameTime;
		}
		if (transitionStart == NO_TRANSITION) {
			return now ? 0.0f : 1.0f;
		}
		// The subtraction happens between two longs: a world tens of millions of ticks old cannot hold
		// consecutive ticks in a float, and the wings would step and then stop.
		float elapsed = (float) (gameTime - transitionStart) + partialTicks;
		float t = Mth.clamp(elapsed / FOLD_TICKS, 0.0f, 1.0f);
		float eased = t * t * (3.0f - 2.0f * t);
		return now ? 1.0f - eased : eased;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.radiant_solar_panel");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new RadiantSolarPanelMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}

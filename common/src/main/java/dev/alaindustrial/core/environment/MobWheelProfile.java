package dev.alaindustrial.core.environment;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The species a mob wheel accepts (MOD-763, decision D8) and the one profile each of them runs with. A profile
 * may cover more than one entity type: a zombie villager runs as a zombie.
 *
 * <p><b>One profile per species, never per animal.</b> Every pig runs like every other pig: power, stamina,
 * how fast it rests, how steady its pace is and its one special trait are properties of the species. What
 * changes while a mob works is only how tired it is ({@link MobWheelStamina}), never what it is born with.
 *
 * <p><b>The table is the balance.</b> The numbers live here, not in per-species knobs; the knobs of
 * {@link GeneratorConfig} scale the whole roster at once ({@code mobWheelEuMultiplierPercent},
 * {@code mobWheelStaminaMultiplierPercent}) and size the one shared bonus ({@code mobWheelBonusPercent}).
 * The table is not shown to players (D7): the drive's tooltip names no species and no number.
 *
 * <p>Minecraft-free on purpose — the L1 lane has no game on its classpath — so a species is named by its
 * entity type id as a string ({@code "minecraft:pig"}); {@code MobWheelRoster} maps the live entity types.
 * Only adults that fit the wheel's passage are listed: a hoglin (1.4 wide) and a wither skeleton (2.4 tall)
 * do not, and the game test {@code mob_wheel_every_runner_fits} checks every listed type's box.
 */
public enum MobWheelProfile {
	/** The weakest and the most enduring. */
	CHICKEN(1, 24000, 1000, Pace.STEADY, Bonus.NONE, Trait.NONE, "minecraft:chicken"),
	PIG(5, 6000, 1000, Pace.STEADY, Bonus.NONE, Trait.NONE, "minecraft:pig"),
	SHEEP(3, 12000, 1000, Pace.STEADY, Bonus.NONE, Trait.NONE, "minecraft:sheep"),
	COW(4, 9000, 1500, Pace.STEADY, Bonus.NONE, Trait.NONE, "minecraft:cow"),
	/** The fastest: the wheel spins twice as fast, but the goat tires soon. */
	GOAT(8, 2400, 2000, Pace.FAST, Bonus.NONE, Trait.NONE, "minecraft:goat"),
	VILLAGER(7, 6000, 3000, Pace.PAUSING, Bonus.NONE, Trait.NONE, "minecraft:villager"),
	/** Stronger at night. A zombie villager runs as one. */
	ZOMBIE(7, 4800, 500, Pace.RAGGED, Bonus.NIGHT, Trait.NONE, "minecraft:zombie", "minecraft:zombie_villager"),
	/** A zombie that does not burn by day, and gets no night bonus either. */
	HUSK(7, 6000, 500, Pace.RAGGED, Bonus.NONE, Trait.NONE, "minecraft:husk"),
	DROWNED(6, 4800, 500, Pace.RAGGED, Bonus.RAIN, Trait.NONE, "minecraft:drowned"),
	SKELETON(6, 6000, 1000, Pace.STEADY, Bonus.NONE, Trait.NONE, "minecraft:skeleton"),
	STRAY(6, 6000, 1000, Pace.STEADY, Bonus.SNOWY_BIOME, Trait.NONE, "minecraft:stray"),
	BOGGED(6, 6000, 1000, Pace.STEADY, Bonus.RAIN, Trait.NONE, "minecraft:bogged"),
	PARCHED(6, 6000, 1000, Pace.STEADY, Bonus.HOT_DRY_BIOME, Trait.NONE, "minecraft:parched"),
	/** Once per run drinks a potion and gets a quarter of its stamina back. */
	WITCH(5, 7200, 1000, Pace.STEADY, Bonus.NONE, Trait.SELF_RESTORE, "minecraft:witch"),
	VINDICATOR(8, 3600, 1000, Pace.RAGGED, Bonus.NONE, Trait.NONE, "minecraft:vindicator"),
	PILLAGER(6, 6000, 1000, Pace.STEADY, Bonus.NONE, Trait.NONE, "minecraft:pillager"),
	EVOKER(9, 4800, 1000, Pace.PAUSING, Bonus.NONE, Trait.NONE, "minecraft:evoker"),
	/** Does not turn into a zombified piglin while the wheel holds it. */
	PIGLIN(6, 6000, 1000, Pace.STEADY, Bonus.NONE, Trait.NO_ZOMBIFICATION, "minecraft:piglin"),
	PIGLIN_BRUTE(9, 4800, 500, Pace.RAGGED, Bonus.NONE, Trait.NO_ZOMBIFICATION, "minecraft:piglin_brute"),
	ZOMBIFIED_PIGLIN(6, 7200, 500, Pace.RAGGED, Bonus.NONE, Trait.NONE, "minecraft:zombified_piglin"),
	/** The strongest runner; does not swell while it runs. */
	CREEPER(10, 12000, 2000, Pace.STEADY, Bonus.NONE, Trait.NO_SWELL, "minecraft:creeper");

	/**
	 * Every entity type the wheel takes, in profile order then each profile's own order. A runner's index in
	 * this list is what the drive's screen is sent, so it can name the occupant's real species.
	 */
	public static final List<String> RUNNER_TYPE_IDS = Arrays.stream(values())
			.flatMap(profile -> profile.entityTypeIds.stream()).toList();

	/** A pausing runner runs this many ticks of each pace cycle... */
	public static final int PAUSING_RUN_TICKS = 160;
	/** ...and stands still for this many. */
	public static final int PAUSING_PAUSE_TICKS = 40;
	/** A ragged runner picks a new pace multiplier every this many running ticks. */
	public static final int RAGGED_PACE_STEP_TICKS = 20;
	/** Lowest ragged pace multiplier. */
	public static final double RAGGED_PACE_MIN = 0.8;
	/** Highest ragged pace multiplier; with {@link #RAGGED_PACE_MIN} the average is exactly 1. */
	public static final double RAGGED_PACE_MAX = 1.2;
	/** The ragged pace is drawn from this many evenly spaced values between the two bounds (0.05 apart). */
	static final int RAGGED_PACE_STEPS = 9;
	/** How many times faster the wheel turns under a {@link Pace#FAST} runner; the output pace stays 1. */
	public static final int FAST_SPIN_FACTOR = 2;
	/** Percent of its stamina a {@link Trait#SELF_RESTORE} runner gets back, once per run. */
	public static final int SELF_RESTORE_PERCENT = 25;

	/** How evenly a species runs. */
	public enum Pace {
		/** Always pace 1. */
		STEADY,
		/** Pace 1, with a {@link #PAUSING_PAUSE_TICKS} stop after every {@link #PAUSING_RUN_TICKS}. */
		PAUSING,
		/** 0.8..1.2, a new value every {@link #RAGGED_PACE_STEP_TICKS}. */
		RAGGED,
		/** Pace 1 for the output, but the wheel spins {@link #FAST_SPIN_FACTOR} times as fast. */
		FAST
	}

	/** When a species gets the shared {@code mobWheelBonusPercent}; at most one condition per species. */
	public enum Bonus {
		NONE,
		/** At night where the wheel stands. */
		NIGHT,
		/** While rain (or a thunderstorm) falls on the wheel. */
		RAIN,
		/** In a biome where precipitation falls as snow. */
		SNOWY_BIOME,
		/** In a hot biome without precipitation (desert, savanna, badlands, the Nether). */
		HOT_DRY_BIOME
	}

	/** A species' one special behaviour in the wheel. */
	public enum Trait {
		NONE,
		/** Once per run, at a quarter stamina left, it restores {@link #SELF_RESTORE_PERCENT} percent. */
		SELF_RESTORE,
		/** Held in the wheel, it does not turn into a zombified piglin. */
		NO_ZOMBIFICATION,
		/** Running, it does not swell; stopped with a player near, it swells as in vanilla. */
		NO_SWELL
	}

	/** What the world around the wheel is like this tick: the inputs of {@link #bonusApplies}. */
	public record Surroundings(boolean night, boolean rainOnWheel, boolean snowyBiome, boolean hotDryBiome) {
		/** Daylight, dry, a temperate biome: no bonus applies. */
		public static final Surroundings PLAIN = new Surroundings(false, false, false, false);
	}

	private final List<String> entityTypeIds;
	private final int power;
	private final int stamina;
	private final int restPermille;
	private final Pace pace;
	private final Bonus bonus;
	private final Trait trait;

	MobWheelProfile(int power, int stamina, int restPermille, Pace pace, Bonus bonus, Trait trait,
			String... entityTypeIds) {
		this.power = power;
		this.stamina = stamina;
		this.restPermille = restPermille;
		this.pace = pace;
		this.bonus = bonus;
		this.trait = trait;
		this.entityTypeIds = List.of(entityTypeIds);
	}

	/** The species' own entity type id, e.g. {@code "minecraft:pig"} — the first of {@link #entityTypeIds()}. */
	public String entityTypeId() {
		return entityTypeIds.get(0);
	}

	/** Every entity type that runs with this profile. */
	public List<String> entityTypeIds() {
		return entityTypeIds;
	}

	/** Output in EU/t of a fresh mob of this species at pace 1 with no bonus, before the EU multiplier knob. */
	public int basePowerEuPerTick() {
		return power;
	}

	/** Running ticks a fresh mob has in it before the stamina multiplier knob. */
	public int baseStaminaTicks() {
		return stamina;
	}

	/** Running ticks a fresh mob of this species has in it, after {@code mobWheelStaminaMultiplierPercent}. */
	public int staminaTicks() {
		return staminaTicks(GeneratorConfig.mobWheelStaminaMultiplierPercent);
	}

	/** Running ticks of a fresh mob at {@code multiplierPercent} of the table stamina; never below 1. */
	public int staminaTicks(int multiplierPercent) {
		long scaled = (long) stamina * Math.max(0, multiplierPercent) / 100L;
		return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
	}

	/**
	 * How fast this species rests, in thousandths of the base rate: 1000 rests in
	 * {@link GeneratorConfig#mobWheelRestTicks}, 3000 three times faster, 500 twice as slowly.
	 */
	public int restPermille() {
		return restPermille;
	}

	public Pace paceKind() {
		return pace;
	}

	public Bonus bonus() {
		return bonus;
	}

	public Trait trait() {
		return trait;
	}

	/** Whether this species' bonus condition holds in {@code around}; never for a species without a bonus. */
	public boolean bonusApplies(Surroundings around) {
		return switch (bonus) {
			case NONE -> false;
			case NIGHT -> around.night();
			case RAIN -> around.rainOnWheel();
			case SNOWY_BIOME -> around.snowyBiome();
			case HOT_DRY_BIOME -> around.hotDryBiome();
		};
	}

	/** How many times faster than its pace the wheel is drawn turning: {@link #FAST_SPIN_FACTOR} for a goat. */
	public int spinFactor() {
		return pace == Pace.FAST ? FAST_SPIN_FACTOR : 1;
	}

	/** The highest pace multiplier this species ever reaches — what sizes the wheel's one-tick buffer. */
	public double maxPace() {
		return pace == Pace.RAGGED ? RAGGED_PACE_MAX : 1.0;
	}

	/**
	 * Pace multiplier for the given running tick: 1 for a steady or fast runner; 1 or 0 (a pause) for a pausing
	 * one; one of {@link #RAGGED_PACE_STEPS} values between 0.8 and 1.2 for a ragged one, changing every
	 * {@link #RAGGED_PACE_STEP_TICKS}. Zero means the mob stands still: no EU, and no stamina spent.
	 *
	 * <p>Deterministic in its arguments, so a test can walk a whole cycle: {@code seed} makes two zombies in
	 * two wheels stumble differently without either of them needing a random source.
	 *
	 * @param runTick running ticks counted by the wheel since the mob started (any non-negative value)
	 * @param seed    a per-wheel or per-mob number mixed into the ragged draw
	 */
	public double pace(int runTick, long seed) {
		int tick = Math.max(0, runTick);
		return switch (pace) {
			case STEADY, FAST -> 1.0;
			case PAUSING -> tick % (PAUSING_RUN_TICKS + PAUSING_PAUSE_TICKS) < PAUSING_RUN_TICKS ? 1.0 : 0.0;
			case RAGGED -> {
				long step = tick / RAGGED_PACE_STEP_TICKS;
				int index = (int) Long.remainderUnsigned(mix(seed + step * 0x9E3779B97F4A7C15L), RAGGED_PACE_STEPS);
				yield RAGGED_PACE_MIN + (RAGGED_PACE_MAX - RAGGED_PACE_MIN) * index / (RAGGED_PACE_STEPS - 1);
			}
		};
	}

	/** SplitMix64's finaliser: spreads consecutive steps across the whole range. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** The profile for an entity type id, or {@code null} when the wheel does not take that species. */
	@Nullable
	public static MobWheelProfile byEntityTypeId(String entityTypeId) {
		for (MobWheelProfile profile : values()) {
			if (profile.entityTypeIds.contains(entityTypeId)) {
				return profile;
			}
		}
		return null;
	}

	/** The profile named {@code name} (its enum constant), or {@code null} — how a save names the species. */
	@Nullable
	public static MobWheelProfile byName(String name) {
		for (MobWheelProfile profile : values()) {
			if (profile.name().equals(name)) {
				return profile;
			}
		}
		return null;
	}

	/** Index of {@code entityTypeId} in {@link #RUNNER_TYPE_IDS}, or {@code -1} when the wheel does not take it. */
	public static int runnerIndex(@Nullable String entityTypeId) {
		return entityTypeId == null ? -1 : RUNNER_TYPE_IDS.indexOf(entityTypeId);
	}

	/** The runner type at {@code index} of {@link #RUNNER_TYPE_IDS}, or {@code null} when out of range. */
	@Nullable
	public static String runnerTypeId(int index) {
		return index >= 0 && index < RUNNER_TYPE_IDS.size() ? RUNNER_TYPE_IDS.get(index) : null;
	}
}

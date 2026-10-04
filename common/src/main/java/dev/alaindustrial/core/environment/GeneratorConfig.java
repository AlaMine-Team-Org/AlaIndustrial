package dev.alaindustrial.core.environment;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Generator knobs moved out of {@code Config} by {@code docs/tools/authoring/config_holder_codemod.py}
 * (MOD-710, ADR-034). The json key of each knob is still its field name and its section is still
 * declared on the field, so the operator's file is unchanged; {@code Config.REGISTRY} scans this
 * class next to {@code Config}.
 */
public final class GeneratorConfig {

	private GeneratorConfig() {
	}

	// --- Generators (EU/tick) ---
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Solar panel output in EU/t under clear daytime sky. The energy system's baseline (1).")
	public static int solarEuPerTick = 1;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Evolved daylight-panel output in EU/t during the day.")
	public static int daylightEuPerTick = 4;
	/**
	 * Mirror Concentrator output in EU/t by day, before the noon window lifts it by half (MOD-602): twice the
	 * daylight panel, under the water mill over a full day.
	 */
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Mirror Concentrator output in EU/t during the day, before the noon peak.")
	public static int radiantEuPerTick = 8;
	/**
	 * Assembled Mirror Concentrator output in EU/t (MOD-603). Equal to the one-block form by design — the
	 * structure buys shape, not power; a separate knob so a balance pass can change that alone.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Assembled Mirror Concentrator output in EU/t during the day, before the noon peak.")
	public static int radiantAssembledEuPerTick = 8;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Evolved moonlit-panel output in EU/t at night under clear sky.")
	public static int moonlitEuPerTick = 3;
	/** Flat EU/t the moonlit panel still produces at night during rain/thunder (a weather trickle). */
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Moonlit-panel EU/t at night during rain/thunder (a weather trickle).")
	public static int moonlitWeatherEuPerTick = 1;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Fuel (solid-burnable) generator output in EU/t while burning.")
	public static int fuelEuPerTick = 8;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Geothermal (lava) generator output in EU/t while burning lava.")
	public static int geothermalEuPerTick = 16;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "Ticks of burn a geothermal generator gets per bucket of lava (20 ticks = 1 second).")
	public static int geothermalBurnTicks = 1000;
	/**
	 * EU/t per adjacent vanilla-water block on the mill's four horizontal sides (0..4 EU/t, no fuel). Reads
	 * the world directly, never the fluid system.
	 */
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Water mill EU/t per adjacent water block on its four sides (0..4 EU/t total).")
	public static int waterMillEuPerTick = 1;
	// --- Wind altitude profile (MOD-347) — shared by all three mills AND the Wind Gauge ---
	/**
	 * The cloud deck: the windiest height. Wind climbs to here and collapses above it; 192 is the vanilla
	 * cloud layer, so the rule is visible.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Cloud deck Y: the windiest height. Wind climbs to here and collapses above it (shared by all wind"
					+ " mills and the Wind Gauge).")
	public static int windCloudY = 192;
	/** Height above which only {@link #windTraceFactor} remains — too little to turn any rotor. */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Y above which only a trace of wind remains — too little to turn any rotor.")
	public static int windDeadY = 248;
	/**
	 * Fraction of full wind strength at a branch's ridge; the rest is gained over the shoulder up to {@link
	 * #windCloudY}. Lower = a narrower full-output band (MOD-347).
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Fraction of full wind strength reached at a mill branch's ridge; the rest is gained over the"
					+ " shoulder up to windCloudY.")
	public static float windRidgeFactor = 0.45f;
	/** Fraction of full strength left above {@link #windDeadY}: readable on the gauge, 0 EU/t for mills. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Fraction of full wind strength left above windDeadY (readable on the gauge, 0 EU/t for mills).")
	public static float windTraceFactor = 0.06f;
	/** Clear-weather wind speed in km/h at the cloud deck — the Wind Gauge's full-scale reading. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Clear-weather wind speed in km/h at the cloud deck — the Wind Gauge's full-scale reading.")
	public static float windGaugePeakKmh = 62.5f;
	// --- Wind mill (LV) — needs open sky; base scales with height, boosted by weather ---
	/** Base EU/t at the cloud deck (MOD-347); the height profile scales this. 0 at/below sea level. */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Wind mill base EU/t at the cloud deck; the altitude profile scales it (MOD-347).")
	public static int windMillMaxBaseEuPerTick = 4;
	/** Hard cap on wind-mill EU/t after the weather multiplier (thunder can otherwise push past base). */
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 0,
			doc = "Hard cap on wind mill EU/t after the weather multiplier.")
	public static int windMillMaxEuPerTick = 8;
	/** Weather multiplier applied to the height base when it is raining (not thundering). */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Wind mill output multiplier while it is raining (not thundering).")
	public static float windMillRainFactor = 1.5f;
	/** Weather multiplier applied to the height base when it is thundering. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Wind mill output multiplier while it is thundering.")
	public static float windMillThunderFactor = 2.0f;
	/** How often (ticks) the wind mill re-samples height/sky/weather; the rate is cached between samples. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "How often (ticks) a wind mill re-samples height/sky/weather; rate is cached in between.")
	public static int windMillSampleTicks = 40;
	/**
	 * Active open-sky ticks (rotor + evolution chip) to evolve a base wind mill into its T2 branch. Mirrors
	 * {@link #solarEvolveTicks}.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Active open-sky ticks (with rotor + evolution chip) to evolve a base wind mill into its T2 branch.")
	public static int windMillEvolveTicks = 33_600;
	// --- High-altitude wind mill (T2, LV) — boosted by height ---
	/** Clear-sky height cap for the high-altitude variant: base EU/t = min((y − seaLevel) / blocksPerBase, this). */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "High-altitude wind mill (T2) clear-sky height cap in EU/t.")
	public static int highAltWindMillMaxBaseEuPerTick = 8;
	/** Blocks of height above sea level needed for +1 EU/t of base on the high-altitude variant (half the T1 16). */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Blocks of height above sea level per +1 EU/t of base on the high-altitude T2 variant.")
	public static int highAltWindMillBlocksPerBase = 8;
	/**
	 * High-altitude branch weather multipliers (MOD-345): weaker than T1's so the branch is the steady
	 * clear-sky earner and concedes storms to the storm mill.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "High-altitude T2 wind mill output multiplier while it is raining.")
	public static float highAltWindMillRainFactor = 1.25f;
	/** @see #highAltWindMillRainFactor */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "High-altitude T2 wind mill output multiplier while it is thundering.")
	public static float highAltWindMillThunderFactor = 1.5f;
	/**
	 * Cap on high-altitude wind-mill EU/t after weather: the reachable peak (8 x 1.5), leaving 16+ to the
	 * storm mill.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Hard cap on high-altitude T2 wind mill EU/t after the weather multiplier.")
	public static int highAltWindMillMaxEuPerTick = 12;
	// --- Storm wind mill (T2, LV) — boosted by weather ---
	/**
	 * Clear-sky height cap of the storm branch: T1's step, raised so the thunder multiplier pays off; low on
	 * purpose, it is not a clear-weather earner.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Storm wind mill (T2) clear-sky height cap in EU/t before the weather multiplier.")
	public static int stormWindMillMaxBaseEuPerTick = 6;
	/** Weather multiplier for the storm variant when it is raining (not thundering). */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Storm T2 wind mill output multiplier while it is raining.")
	public static float stormWindMillRainFactor = 2.0f;
	/**
	 * Storm branch multiplier while thundering (MOD-345): the branch's identity, the highest LV burst, paid
	 * for by the least clear-sky output.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Storm T2 wind mill output multiplier while it is thundering.")
	public static float stormWindMillThunderFactor = 3.5f;
	/**
	 * Cap on storm wind-mill EU/t after weather (MOD-345). Headroom, not a target: the reachable peak is 6 x
	 * 3.5 = 21.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Hard cap on storm T2 wind mill EU/t after the weather multiplier.")
	public static int stormWindMillMaxEuPerTick = 24;
	// --- Rotor / wheel wear (MOD-189) — the wind mill rotor and water mill wheel are consumables ---
	/**
	 * Rotor max durability; life = this x {@link #windMillRotorEuPerDamage} EU. Baked at item registration
	 * (restart, new rotors only); tune life via the live EU-per-damage rate.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Wind mill rotor max durability (bar). Applies at registration (restart); tune life via the"
					+ " EU-per-damage rate. Shared by all three wind mills.")
	public static int windMillRotorMaxDamage = 1000;
	/**
	 * EU produced per rotor durability point (480: about 5 in-game days at 4 EU/t). Wear follows output; read
	 * live every tick.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "EU of production per 1 durability point of the wind mill rotor (life = maxDamage × this). Read live"
					+ " every tick.")
	public static int windMillRotorEuPerDamage = 480;
	/**
	 * Output scale of the plain wooden rotor, the 1.0 baseline of the MOD-385 ladder; a field so all grades
	 * read alike in {@link dev.alaindustrial.core.machine.ComponentTier}.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: output scale of the plain wooden rotor — the ladder's 1.0 baseline. Raising it buffs every"
					+ " wind mill, not just upgraded ones.")
	public static float windMillRotorOutputMultiplier = 1.0f;
	/** Extra rotor wear in rain or thunder on top of the storm output; 1.0 disables it. All three wind mills. */
	@Knob(section = Section.GENERATORS, min = 1.0, exclusive = true,
			doc = "Extra rotor wear multiplier while running in rain/thunder (1.0 = off). Applies to all three wind"
					+ " mills.")
	public static float windMillStormWearFactor = 1.5f;
	// --- Reinforced / advanced rotor and wheel (MOD-385) — the second and third grades. ---
	/** Reinforced rotor durability: ×3 the wooden one. Registration-time like every max_damage. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: reinforced rotor max durability (×3 the wooden one). Applies at registration (restart).")
	public static int windMillRotorReinforcedMaxDamage = 3000;
	/**
	 * EU per durability point of the reinforced rotor, scaled by its x1.25 output so the stronger rotor keeps
	 * its promised life (wear is charged on EU).
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: EU per 1 durability point of the reinforced rotor. Scaled by its ×1.25 output so the life"
					+ " gain is purely the durability gain.")
	public static int windMillRotorReinforcedEuPerDamage = 600;
	/** Reinforced rotor output scale. Applied before the mill's cap, so it cannot raise the ceiling. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: reinforced rotor output scale. Applied before the mill's cap, so it cannot raise"
					+ " windMillMaxEuPerTick.")
	public static float windMillRotorReinforcedOutputMultiplier = 1.25f;
	/** Advanced rotor durability: ×6 the wooden one, ×2 the reinforced one. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: advanced rotor max durability (×6 the wooden one). Applies at registration (restart).")
	public static int windMillRotorAdvancedMaxDamage = 6000;
	/** EU per durability point of the advanced rotor — ×1.5, matching its output scale. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: EU per 1 durability point of the advanced rotor, scaled by its ×1.5 output.")
	public static int windMillRotorAdvancedEuPerDamage = 720;
	/** Advanced rotor output scale. Applied before the mill's cap, so it cannot raise the ceiling. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: advanced rotor output scale. Applied before the mill's cap.")
	public static float windMillRotorAdvancedOutputMultiplier = 1.5f;
	/**
	 * Water wheel max durability; life = this x {@link #waterMillWheelEuPerDamage} EU. Registration-time like
	 * the rotor (restart); tune life via the rate.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Water mill wheel max durability (bar). Applies at registration (restart); tune life via the"
					+ " EU-per-damage rate.")
	public static int waterMillWheelMaxDamage = 1000;
	/** EU produced per wheel durability point (320: about 6-7 in-game days at 2 EU/t). Read live every tick. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "EU of production per 1 durability point of the water mill wheel (life = maxDamage × this). Read live"
					+ " every tick.")
	public static int waterMillWheelEuPerDamage = 320;
	/**
	 * Output scale of the plain wooden wheel, the 1.0 baseline of the MOD-385 ladder (see {@link
	 * #windMillRotorOutputMultiplier}).
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: output scale of the plain wooden wheel — the ladder's 1.0 baseline.")
	public static float waterMillWheelOutputMultiplier = 1.0f;
	/** Reinforced wheel durability: ×3 the wooden one. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: reinforced wheel max durability (×3 the wooden one). Applies at registration (restart).")
	public static int waterMillWheelReinforcedMaxDamage = 3000;
	/** EU per durability point of the reinforced wheel — ×1.25, matching its output scale. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: EU per 1 durability point of the reinforced wheel, scaled by its ×1.25 output.")
	public static int waterMillWheelReinforcedEuPerDamage = 400;
	/**
	 * Reinforced wheel output scale. No cap needed: the mill's ceiling is structural (4 cells x {@link
	 * #waterMillEuPerTick}), far under LV and a copper cable.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: reinforced wheel output scale. The water mill has no EU/t cap of its own — its ceiling is 4"
					+ " wheel cells × waterMillEuPerTick.")
	public static float waterMillWheelReinforcedOutputMultiplier = 1.25f;
	/** Advanced wheel durability: ×6 the wooden one, ×2 the reinforced one. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: advanced wheel max durability (×6 the wooden one). Applies at registration (restart).")
	public static int waterMillWheelAdvancedMaxDamage = 6000;
	/** EU per durability point of the advanced wheel — ×1.5, matching its output scale. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: EU per 1 durability point of the advanced wheel, scaled by its ×1.5 output.")
	public static int waterMillWheelAdvancedEuPerDamage = 480;
	/** Advanced wheel output scale. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: advanced wheel output scale.")
	public static float waterMillWheelAdvancedOutputMultiplier = 1.5f;

	// --- Lightning rod generator (MOD-386) ---------------------------------------------------------
	// The rod banks a whole strike in the conductor tip's capacitor and bleeds it into the network at a
	// flat rate, so the burst never has to fit in one tick. See LightningRodOutput.
	/** Internal EU buffer of the rod block itself — the same 4000 every other LV generator carries. */
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "MOD-386: internal EU buffer of the lightning rod generator block.")
	public static int lightningRodBuffer = 4000;
	/**
	 * EU one lightning strike puts into the tip's capacitor — more than a lava bucket, as a rare event should.
	 * Capped by the tip's free capacity; the surplus is lost.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "MOD-386: EU one lightning strike delivers into the conductor tip's capacitor. Surplus over the tip's"
					+ " free room is lost, never banked.")
	public static int lightningRodStrikeEu = 20_000;
	/**
	 * T1 conductor tip capacitor before its grade multiplier. One {@link #lightningRodStrikeEu} fits an EMPTY
	 * T1 tip exactly — keep the two in step.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: capacitor size of the T1 conductor tip before its grade multiplier. Keep in step with"
					+ " lightningRodStrikeEu — one strike is meant to fit an empty T1 tip exactly.")
	public static int lightningRodBaseCapacitorEu = 20_000;
	/** Hard ceiling on a tip's capacitor after its grade multiplier. Headroom, not a target. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: ceiling on a conductor tip's capacitor after its grade multiplier. No multiplier can lift"
					+ " it.")
	public static int lightningRodMaxCapacitorEu = 32_000;
	/** EU/t the T1 tip bleeds from its capacitor into the buffer, before its grade multiplier. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: EU/t the T1 tip bleeds from its capacitor into the buffer, before its grade multiplier.")
	public static int lightningRodBaseBleedEuPerTick = 16;
	/** Ceiling on the bleed rate after the grade multiplier — the LV tier voltage, never breached. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: ceiling on the bleed rate after the grade multiplier (LV tier voltage). No multiplier can"
					+ " lift it.")
	public static int lightningRodMaxBleedEuPerTick = 32;
	/**
	 * One-in-N chance per tick of a strike while THUNDERING (900: about one per 45 s). Random, so two rods do
	 * not fire in lockstep.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: one-in-N chance per tick of a strike while thundering over the rod (900 ~ one strike every"
					+ " 45 s of storm). Higher = rarer.")
	public static int lightningRodThunderStrikeChanceDivisor = 900;
	/**
	 * One-in-N chance per tick of a strike in plain rain; ours, not vanilla's, so the rod earns something
	 * between storms. Much rarer than thunder.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: one-in-N chance per tick of a strike in plain rain without thunder (vanilla never does"
					+ " this; the rod does). Higher = rarer.")
	public static int lightningRodRainStrikeChanceDivisor = 4800;
	/**
	 * Extra tip wear when a strike is wasted on a full capacitor. 2.0 so an overload costs two strikes' worth
	 * and it takes two to kill the cheapest tip.
	 */
	@Knob(section = Section.GENERATORS, min = 1.0, exclusive = true,
			doc = "MOD-386: extra tip wear charged when a strike is wasted on a full capacitor (1.0 = no extra"
					+ " penalty).")
	public static float lightningRodOverloadWearFactor = 2.0f;
	/**
	 * How often the rod re-reads "is it storming over open sky here"; cached between samples like the wind
	 * mill's read. 40 ticks: roofing stops the rod within 2 s.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: how often the rod re-reads storm/open-sky conditions, in ticks. Cached in between, like the"
					+ " wind mill's height/sky sample.")
	public static int lightningRodSampleTicks = 40;
	/**
	 * T1 conductor tip durability, sized in STRIKES: 100 / (20 000 / 1000) = 5 caught strikes — a running
	 * cost, not a fit-and-forget part. Registration-time.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: T1 conductor tip max durability (bar). Applies at registration (restart); tune life via the"
					+ " EU-per-damage rate.")
	public static int lightningRodTipMaxDamage = 100;
	/** EU banked per one durability point of the T1 tip — 100 × 1000 = 100 000 EU = 5 caught strikes. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: EU banked per 1 durability point of the T1 conductor tip (life = maxDamage × this).")
	public static int lightningRodTipEuPerDamage = 1000;
	/** T1 tip capacity/bleed scale — the ladder's 1.0 baseline (a field for uniformity, as with the rotor). */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-386: capacity/bleed scale of the T1 conductor tip — the ladder's 1.0 baseline.")
	public static float lightningRodTipOutputMultiplier = 1.0f;
	/** Reinforced tip durability: 320 × 1250 = 400 000 EU = 20 caught strikes (×4 the copper one). */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: reinforced conductor tip max durability (×3 the copper one). Applies at registration"
					+ " (restart).")
	public static int lightningRodTipReinforcedMaxDamage = 320;
	/** EU per durability point of the reinforced tip — ×1.25, matching its output scale. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: EU per 1 durability point of the reinforced tip, scaled by its ×1.25 output so the life"
					+ " gain is purely the durability gain.")
	public static int lightningRodTipReinforcedEuPerDamage = 1250;
	/** Reinforced tip capacity/bleed scale. Applied before the caps, so it cannot raise them. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-386: reinforced tip capacity/bleed scale. Applied before the caps, so it cannot raise them.")
	public static float lightningRodTipReinforcedOutputMultiplier = 1.25f;
	/** Advanced tip durability: 800 × 1500 = 1 200 000 EU = 60 caught strikes (×12 the copper one). */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: advanced conductor tip max durability (×6 the copper one). Applies at registration"
					+ " (restart).")
	public static int lightningRodTipAdvancedMaxDamage = 800;
	/** EU per durability point of the advanced tip — ×1.5, matching its output scale. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: EU per 1 durability point of the advanced tip, scaled by its ×1.5 output.")
	public static int lightningRodTipAdvancedEuPerDamage = 1500;
	/** Advanced tip capacity/bleed scale — reaches both ceilings, which is the top grade's whole point. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-386: advanced tip capacity/bleed scale — reaches both ceilings, which is the top grade's point.")
	public static float lightningRodTipAdvancedOutputMultiplier = 1.5f;
	/** Output multiplier when a solar panel sees the sky through a translucent block (leaves, cobweb). MOD-004. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Output multiplier when a solar panel sees sky through a translucent block (leaves, cobweb).")
	public static float solarTransparentFactor = 0.5f;
	/** Output multiplier under snow: a snow layer above the panel, or snowfall in a cold biome — MODE_SNOW. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Output multiplier under snow (a snow layer above, or snowfall in a cold biome).")
	public static float solarSnowFactor = 0.2f;
	/**
	 * Active sky-time ticks (only in the chip's half of the day) to evolve a base panel into its T2 branch:
	 * about 2.8 active half-days, about 3 in-game days.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Active sky-time ticks needed to evolve a base solar panel into its T2 branch.")
	public static int solarEvolveTicks = 33_600;
	/**
	 * How often a panel re-samples sky access and weather; cached between samples (mirrors {@link
	 * #windMillSampleTicks}). 40 ticks is imperceptible and cuts the column scans.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "How often (ticks) a solar panel re-samples sky access + weather; verdict is cached between samples.")
	public static int solarSkySampleTicks = 40;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "Fuel generator EU buffer. Applies to newly placed blocks.")
	public static int generatorBuffer = 4000;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "Geothermal generator EU buffer. Applies to newly placed blocks.")
	public static int geothermalBuffer = 4000;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "Water mill EU buffer. Applies to newly placed blocks.")
	public static int waterMillBuffer = 4000;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "Wind mill (T1) EU buffer. Applies to newly placed blocks.")
	public static int windMillBuffer = 4000;
	/** Shared buffer for both T2 wind mills (high-altitude + storm). */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Shared EU buffer for both T2 wind mills (high-altitude + storm). Applies to newly placed blocks.")
	public static int t2WindMillBuffer = 8000;
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "Solar panel EU buffer. Applies to newly placed blocks.")
	public static int solarBuffer = 8000;
	/** Mirror Concentrator buffer (MOD-602) — twice the panels below it. */
	@Knob(section = Section.GENERATORS, clientVisible = true, min = 1,
			doc = "Mirror Concentrator EU buffer. Applies to newly placed blocks.")
	public static int radiantBuffer = 16000;
}

package dev.alaindustrial;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tunable balance knobs (v0.2 defaults), loaded from {@code config/alaindustrial.json}.
 * Generators/machines/storage/cables read these at runtime, so a server can rebalance without a
 * code change. Missing keys fall back to the v0.2 default, so the file is forward/backward safe.
 *
 * <p>The Java API is a flat set of {@code public static} fields and stays that way — hundreds of
 * call sites, every gametest and every command read {@code Config.<field>} directly. Only the FILE
 * is structured: since MOD-402 it carries a {@link #SCHEMA_VERSION} and groups its keys into
 * {@link Section}s, and the load layer migrates an older file into the current shape before reading
 * it. Adding a knob therefore never needs a migration; changing the file's shape does.
 *
 * <p>The balance fields and the pure file read/write ({@link #loadFrom(Path)}) are loader-neutral
 * and live in {@code common}. Resolving the per-loader config directory and hooking the
 * datapack-reload event is a platform seam: Fabric wires it in
 * {@code dev.alaindustrial.FabricConfigLoader}; NeoForge will do the same on its side (MOD-022).
 */
public final class Config {
	private Config() {
	}

	// --- Global multipliers (v0.2-neutral defaults) ---
	/** Scales every generator's EU/t output. Applied once in AbstractGeneratorBlockEntity.serverTick. */
	@Knob(section = Section.GLOBAL, min = 0.0, exclusive = true,
			doc = "Multiplier on EVERY generator's EU/t output. 1.0 = unchanged; 2.0 = twice the generation server-wide.")
	public static float globalEuRateMultiplier = 1.0f;
	/** Scales machine speed (E_op-invariant): EU/t up, duration down by the same factor. */
	@Knob(section = Section.GLOBAL, min = 0.0, exclusive = true,
			doc = "Machine speed multiplier (energy-neutral): higher = machines draw more EU/t but finish proportionally faster. 1.0 = unchanged.")
	public static float globalMachineSpeedMultiplier = 1.0f;

	// --- Generators (EU/tick) ---
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Solar panel output in EU/t under clear daytime sky. The energy system's baseline (1).")
	public static int solarEuPerTick = 1;
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Evolved daylight-panel output in EU/t during the day.")
	public static int daylightEuPerTick = 4;
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Evolved moonlit-panel output in EU/t at night under clear sky.")
	public static int moonlitEuPerTick = 3;
	/** Flat EU/t the moonlit panel still produces at night during rain/thunder (a weather trickle). */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Moonlit-panel EU/t at night during rain/thunder (a weather trickle).")
	public static int moonlitWeatherEuPerTick = 1;
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Fuel (solid-burnable) generator output in EU/t while burning.")
	public static int fuelEuPerTick = 8;
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Geothermal (lava) generator output in EU/t while burning lava.")
	public static int geothermalEuPerTick = 16;
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Ticks of burn a geothermal generator gets per bucket of lava (20 ticks = 1 second).")
	public static int geothermalBurnTicks = 1000;
	/**
	 * EU/t a water mill produces per adjacent vanilla-water block (source or flowing) on its four
	 * horizontal sides — so 0..4 EU/t, continuous while water is present, no fuel. Reads the world
	 * directly; never touches the fluid/tank system (Phases 4–5).
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Water mill EU/t per adjacent water block on its four sides (0..4 EU/t total).")
	public static int waterMillEuPerTick = 1;
	// --- Wind altitude profile (MOD-347) — shared by all three mills AND the Wind Gauge ---
	/**
	 * The cloud deck: the windiest height in the world. Wind climbs to here and collapses above it.
	 * 192 is the vanilla Overworld cloud layer, so the rule the player learns ("the best wind is just
	 * under the clouds, above them there is nothing") is something they can see.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Cloud deck Y: the windiest height. Wind climbs to here and collapses above it (shared by all wind mills and the Wind Gauge).")
	public static int windCloudY = 192;
	/** Height above which only {@link #windTraceFactor} remains — too little to turn any rotor. */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Y above which only a trace of wind remains — too little to turn any rotor.")
	public static int windDeadY = 248;
	/**
	 * Fraction of full strength reached at a branch's ridge ({@code seaLevel + maxBase × blocksPerBase},
	 * where that branch used to hit its cap before MOD-347). The remaining {@code 1 − this} is gained
	 * over an accelerating shoulder up to {@link #windCloudY}. Deliberately well under 1: the lower it
	 * is, the narrower the band of heights that yields full output, and the more the Wind Gauge is
	 * worth carrying. At 0.45 a T1 mill runs at its cap over eleven blocks instead of sixty-seven.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Fraction of full wind strength reached at a mill branch's ridge; the rest is gained over the shoulder up to windCloudY.")
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
	@Knob(section = Section.GENERATORS, min = 0,
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
	 * Active open-sky ticks (with a rotor installed and an evolution chip in the slot) needed to evolve
	 * a base wind mill into its T2 branch (high-altitude / storm). Mirrors {@link #solarEvolveTicks}.
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
	 * Weather multipliers for the high-altitude variant (MOD-345). Deliberately weaker than T1's
	 * ×1.5/×2: this branch's identity is a steady income that barely cares about the sky, so it wins in
	 * clear weather and concedes the storm to the storm branch. Before MOD-345 it borrowed T1's factors
	 * and, with twice T1's height growth, beat the storm mill in almost every cell of the table — two
	 * equally priced evolution chips with only one correct answer.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "High-altitude T2 wind mill output multiplier while it is raining.")
	public static float highAltWindMillRainFactor = 1.25f;
	/** @see #highAltWindMillRainFactor */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "High-altitude T2 wind mill output multiplier while it is thundering.")
	public static float highAltWindMillThunderFactor = 1.5f;
	/**
	 * Hard cap on high-altitude wind-mill EU/t after the weather multiplier. Equals the reachable peak
	 * (base 8 × thunder 1.5), so the branch tops out at 12 and leaves the 16+ band to the storm mill.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Hard cap on high-altitude T2 wind mill EU/t after the weather multiplier.")
	public static int highAltWindMillMaxEuPerTick = 12;
	// --- Storm wind mill (T2, LV) — boosted by weather ---
	/**
	 * Clear-sky height cap for the storm variant: same height step as T1 (16 blocks/+1), but raised above T1
	 * so the thunder multiplier actually pays off. The base stays low on purpose — this branch is not
	 * supposed to earn a living in clear weather.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Storm wind mill (T2) clear-sky height cap in EU/t before the weather multiplier.")
	public static int stormWindMillMaxBaseEuPerTick = 6;
	/** Weather multiplier for the storm variant when it is raining (not thundering). */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Storm T2 wind mill output multiplier while it is raining.")
	public static float stormWindMillRainFactor = 2.0f;
	/**
	 * Weather multiplier for the storm variant when it is thundering (MOD-345: ×3 → ×3.5). This is the
	 * branch's whole identity — the highest burst EU/t in the mod's LV tier, paid for by producing the
	 * least of the three mills when the sky is clear.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "Storm T2 wind mill output multiplier while it is thundering.")
	public static float stormWindMillThunderFactor = 3.5f;
	/**
	 * Hard cap on storm wind-mill EU/t after the weather multiplier (MOD-345: 16 → 24). The old 16 clipped
	 * the thunder peak down to the high-altitude branch's ceiling, erasing the difference between the two
	 * chips. Headroom, not a target: the reachable peak is base 6 × 3.5 = 21.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "Hard cap on storm T2 wind mill EU/t after the weather multiplier.")
	public static int stormWindMillMaxEuPerTick = 24;
	// --- Rotor / wheel wear (MOD-189) — the wind mill rotor and water mill wheel are consumables ---
	/**
	 * Wind mill rotor max durability (wear shown as a vanilla durability bar). Total rotor life is
	 * {@code windMillRotorMaxDamage × windMillRotorEuPerDamage} EU of production. NOTE: the max_damage
	 * component is baked when the item is registered, so a change here takes effect only after a restart
	 * (and only on newly obtained rotors); tune the calendar life through the EU-per-damage rate below,
	 * which is read live every tick. Shared by all three wind mills (T1 + both T2 branches).
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Wind mill rotor max durability (bar). Applies at registration (restart); tune life via the EU-per-damage rate. Shared by all three wind mills.")
	public static int windMillRotorMaxDamage = 1000;
	/**
	 * EU of production per one durability point of the wind mill rotor. Default 480: with the 1000-point
	 * bar that is 480 000 EU of life ≈ 5 in-game days at a typical 4 EU/t T1 mill (faster on a stronger
	 * high-altitude/storm mill — wear is proportional to output). Read live every tick.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "EU of production per 1 durability point of the wind mill rotor (life = maxDamage × this). Read live every tick.")
	public static int windMillRotorEuPerDamage = 480;
	/**
	 * Output scale of the plain wooden rotor — the baseline of the MOD-385 ladder, hence 1.0. It is a
	 * field rather than a hardcoded constant so all three grades read the same way in
	 * {@link dev.alaindustrial.core.machine.ComponentTier}; raising it buffs every wind mill in the
	 * world, which is a legitimate server knob but NOT what the reinforced/advanced grades are for.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: output scale of the plain wooden rotor — the ladder's 1.0 baseline. Raising it buffs every wind mill, not just upgraded ones.")
	public static float windMillRotorOutputMultiplier = 1.0f;
	/**
	 * Extra rotor-wear multiplier while a wind mill runs in rain or thunder — mechanical stress on top of
	 * the already-higher storm output. 1.0 disables the weather bonus. Applies to all three wind mills.
	 */
	@Knob(section = Section.GENERATORS, min = 1.0, exclusive = true,
			doc = "Extra rotor wear multiplier while running in rain/thunder (1.0 = off). Applies to all three wind mills.")
	public static float windMillStormWearFactor = 1.5f;
	// --- Reinforced / advanced rotor and wheel (MOD-385) — the second and third grades. ---
	/** Reinforced rotor durability: ×3 the wooden one. Registration-time like every max_damage. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: reinforced rotor max durability (×3 the wooden one). Applies at registration (restart).")
	public static int windMillRotorReinforcedMaxDamage = 3000;
	/**
	 * EU per durability point of the reinforced rotor. Scaled by the same ×1.25 as its output on
	 * purpose: wear is charged on EU produced, so leaving this at 480 would make the stronger rotor
	 * wear 25 % faster and quietly eat a quarter of the promised life gain.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-385: EU per 1 durability point of the reinforced rotor. Scaled by its ×1.25 output so the life gain is purely the durability gain.")
	public static int windMillRotorReinforcedEuPerDamage = 600;
	/** Reinforced rotor output scale. Applied before the mill's cap, so it cannot raise the ceiling. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: reinforced rotor output scale. Applied before the mill's cap, so it cannot raise windMillMaxEuPerTick.")
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
	 * Water mill wheel max durability (durability bar). Total wheel life is
	 * {@code waterMillWheelMaxDamage × waterMillWheelEuPerDamage} EU. Like the rotor the max_damage
	 * component is registration-time (restart to change); tune life via the rate below.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Water mill wheel max durability (bar). Applies at registration (restart); tune life via the EU-per-damage rate.")
	public static int waterMillWheelMaxDamage = 1000;
	/**
	 * EU of production per one durability point of the water mill wheel. Default 320: 320 000 EU of life
	 * ≈ 6–7 in-game days at a typical 2 EU/t setup (the wheel runs 24/7 but at a lower rate than the
	 * weather-dependent rotor, so a slightly longer calendar life). Read live every tick.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "EU of production per 1 durability point of the water mill wheel (life = maxDamage × this). Read live every tick.")
	public static int waterMillWheelEuPerDamage = 320;
	/**
	 * Output scale of the plain wooden wheel — the baseline of the MOD-385 ladder, hence 1.0. Kept a
	 * field for the same reason as {@link #windMillRotorOutputMultiplier}: uniform grade definitions.
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
	 * Reinforced wheel output scale. The water mill has no {@code *MaxEuPerTick} of its own — its
	 * ceiling is structural (4 wheel-swept cells × {@link #waterMillEuPerTick}), and even at the
	 * advanced grade the result stays far under the LV tier voltage and under a copper cable's
	 * throughput, so no cap needed to be introduced for MOD-385.
	 */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-385: reinforced wheel output scale. The water mill has no EU/t cap of its own — its ceiling is 4 wheel cells × waterMillEuPerTick.")
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
	// flat rate, so the burst never has to fit in one tick. See core/environment/LightningRodOutput.

	/** Internal EU buffer of the rod block itself — the same 4000 every other LV generator carries. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: internal EU buffer of the lightning rod generator block.")
	public static int lightningRodBuffer = 4000;
	/**
	 * EU one lightning strike delivers into the conductor tip's capacitor. Deliberately larger than a
	 * lava bucket (16 000 EU): a strike is a rare, weather-gated event and has to feel like one. What
	 * actually lands is capped by the tip's remaining capacity — the surplus is lost, not banked.
	 */
	@Knob(section = Section.GENERATORS, min = 0,
			doc = "MOD-386: EU one lightning strike delivers into the conductor tip's capacitor. Surplus over the tip's free room is lost, never banked.")
	public static int lightningRodStrikeEu = 20_000;
	/**
	 * Capacitor size of the T1 conductor tip, before its grade multiplier. A strike of
	 * {@link #lightningRodStrikeEu} fits whole into an EMPTY T1 capacitor by design — that equality is
	 * the whole "discharge before the storm" game, so keep the two in step when retuning.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: capacitor size of the T1 conductor tip before its grade multiplier. Keep in step with lightningRodStrikeEu — one strike is meant to fit an empty T1 tip exactly.")
	public static int lightningRodBaseCapacitorEu = 20_000;
	/** Hard ceiling on a tip's capacitor after its grade multiplier. Headroom, not a target. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: ceiling on a conductor tip's capacitor after its grade multiplier. No multiplier can lift it.")
	public static int lightningRodMaxCapacitorEu = 32_000;
	/** EU/t the T1 tip bleeds from its capacitor into the buffer, before its grade multiplier. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: EU/t the T1 tip bleeds from its capacitor into the buffer, before its grade multiplier.")
	public static int lightningRodBaseBleedEuPerTick = 16;
	/** Ceiling on the bleed rate after the grade multiplier — the LV tier voltage, never breached. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: ceiling on the bleed rate after the grade multiplier (LV tier voltage). No multiplier can lift it.")
	public static int lightningRodMaxBleedEuPerTick = 32;
	/**
	 * One-in-N chance per tick of a strike while it is <b>thundering</b> over the rod. 900 ≈ one strike
	 * every 45 s of storm. Same "chance divisor" idiom as {@link #cottonRootingChanceDivisor}: a strike
	 * is random rather than a timer, so two rods side by side do not fire in lockstep.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: one-in-N chance per tick of a strike while thundering over the rod (900 ~ one strike every 45 s of storm). Higher = rarer.")
	public static int lightningRodThunderStrikeChanceDivisor = 900;
	/**
	 * One-in-N chance per tick of a strike in <b>plain rain</b> (no thunder). Vanilla never spawns
	 * lightning without a thunderstorm, so this branch is entirely ours: it keeps the rod earning
	 * something through ordinary rain instead of standing dead between storms. Much rarer than thunder.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: one-in-N chance per tick of a strike in plain rain without thunder (vanilla never does this; the rod does). Higher = rarer.")
	public static int lightningRodRainStrikeChanceDivisor = 4800;
	/**
	 * Extra wear charged to the tip when a strike is WASTED (capacitor already full). The strike's
	 * energy is lost either way; this makes an unprepared base pay for it in tip life as well.
	 *
	 * <p><b>2.0, not 4.0.</b> The ×4 was set while a tip lasted ~50 strikes; once durability came down
	 * to 5 / 20 / 60 the same factor spent 80 % of a copper tip on a single mistake — one catch plus
	 * one overload landed on exactly {@code maxDamage} and destroyed it, taking the banked 20 000 EU
	 * with it. At ×2 an overload costs two strikes' worth: the lesson still stings, and it takes two
	 * of them to kill the cheapest tip.
	 */
	@Knob(section = Section.GENERATORS, min = 1.0, exclusive = true,
			doc = "MOD-386: extra tip wear charged when a strike is wasted on a full capacitor (1.0 = no extra penalty).")
	public static float lightningRodOverloadWearFactor = 2.0f;
	/**
	 * How often the rod re-reads "is it storming over open sky here" ({@code isRainingAt}, a heightmap
	 * + biome lookup). Between samples the cached answer is reused, exactly as the wind mill caches
	 * its height/sky/weather read. 40 ticks ≈ 2 s: roofing a rod stops it within two seconds, which no
	 * player can perceive against a strike that happens once every 45 s at best.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: how often the rod re-reads storm/open-sky conditions, in ticks. Cached in between, like the wind mill's height/sky sample.")
	public static int lightningRodSampleTicks = 40;
	/**
	 * T1 conductor tip durability (bar). Registration-time, like every component; tune life via the rate.
	 *
	 * <p>Sized in STRIKES, not in EU: 100 points ÷ (20 000 EU strike ÷ 1000 EU per point) = <b>5 caught
	 * strikes</b>. The tip is meant to be a running cost of owning a rod — something you re-craft every
	 * storm or two — not a part you fit once and forget, which is what a five-figure durability made it.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: T1 conductor tip max durability (bar). Applies at registration (restart); tune life via the EU-per-damage rate.")
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
			doc = "MOD-386: reinforced conductor tip max durability (×3 the copper one). Applies at registration (restart).")
	public static int lightningRodTipReinforcedMaxDamage = 320;
	/** EU per durability point of the reinforced tip — ×1.25, matching its output scale. */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: EU per 1 durability point of the reinforced tip, scaled by its ×1.25 output so the life gain is purely the durability gain.")
	public static int lightningRodTipReinforcedEuPerDamage = 1250;
	/** Reinforced tip capacity/bleed scale. Applied before the caps, so it cannot raise them. */
	@Knob(section = Section.GENERATORS, min = 0.0, exclusive = true,
			doc = "MOD-386: reinforced tip capacity/bleed scale. Applied before the caps, so it cannot raise them.")
	public static float lightningRodTipReinforcedOutputMultiplier = 1.25f;
	/** Advanced tip durability: 800 × 1500 = 1 200 000 EU = 60 caught strikes (×12 the copper one). */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "MOD-386: advanced conductor tip max durability (×6 the copper one). Applies at registration (restart).")
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
	 * Active sky-time ticks (at the chip's time of day, i.e. only while its half of the day/night
	 * cycle is active) needed to evolve a base solar panel into its T2 branch. 33 600 = ~2.8
	 * active half-days (~12 000 ticks/half-day) of continuous clear-weather generation, ≈28 real
	 * minutes, ≈3 in-game days accounting for weather/night gaps.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Active sky-time ticks needed to evolve a base solar panel into its T2 branch.")
	public static int solarEvolveTicks = 33_600;
	/**
	 * How often (ticks) a solar panel re-samples sky access + weather; the verdict is cached between
	 * samples to avoid scanning the column above the panel every tick. Mirrors {@link #windMillSampleTicks};
	 * 40 ticks (2 s) is imperceptible against the day/night and weather transitions a panel reacts to,
	 * and at 100 panels cuts the per-tick column-scan cost from 100/tick to ~2/tick on average.
	 */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "How often (ticks) a solar panel re-samples sky access + weather; verdict is cached between samples.")
	public static int solarSkySampleTicks = 40;

	// --- Pump (LV, EU-powered fluid mover) ---
	/** EU spent per bucket of fluid the pump moves (extract + push). The pump is one of the most
	 * energy-hungry machines — at 1000 EU/bucket it is a noticeable consumer, while a bucket of lava
	 * still yields 16 000 EU in the geothermal generator (16× payback on the pump's own tax). */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "EU the pump spends per bucket of fluid it moves (extract + push).")
	public static int pumpEuPerBucket = 1000;
	/** How many ticks the pump waits after a BFS scan before scanning again. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "How many ticks the pump waits after a BFS scan before scanning again.")
	public static int pumpScanCooldownTicks = 20;
	/** Max Manhattan distance the pump BFS searches for a fluid source. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Max Manhattan distance the pump BFS searches for a fluid source.")
	public static int pumpScanMaxDistance = 32;
	/** Max blocks the pump BFS visits per scan, caps lag. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Max blocks the pump BFS visits per scan, caps lag.")
	public static int pumpScanMaxVisited = 512;
	/** MOD-143: how many ticks {@code lit} (and the working hum loop) stays on after the pump's last
	 * actual bucket transfer. A single pull is a one-tick atomic event — no {@code lit}, so no sound
	 * would ever have time to be heard without this hold. 60 ticks (3s) covers pump_hum.ogg's own loop
	 * length (~3.4s) and, at the pump's normal cadence under steady power, overlaps the next pull so
	 * sustained pumping reads as continuously lit rather than flickering. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "How many ticks lit (and the working hum) stays on after the pump's last bucket transfer.")
	public static int pumpLitHoldTicks = 60;

	/** Portable passive tank capacity (MOD-111): 8 buckets, intentionally below machine tanks (10). */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Portable fluid tank capacity in mB (1000 mB = 1 bucket). Applies to newly placed tanks.")
	public static int fluidTankCapacity = 8000;

	// --- Teleporter (HV anchor station, MOD-091) ---
	/** Teleporter station EU buffer. Oversized (×25 the battery box) because a jump is paid in one
	 * lump sum by the TARGET station: at ~10 000–20 000 EU for a typical "home from the mine" jump
	 * this holds ~25–50 jumps, which is what makes the station usable while its chunk is unloaded
	 * (an unloaded station does not recharge — see docs/blocks/advanced-machines/teleporter.md). */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Teleporter station EU buffer. Applies to newly placed stations.")
	public static int teleporterBuffer = 500_000;
	/** Flat part of a jump's price — the "even next door is not free" floor (~17 macerator cycles). */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Flat EU part of a jump's price (paid even for a short hop).")
	public static int teleporterBaseCost = 5000;
	/** Added per block of euclidean distance to the target station. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Added EU per block of straight-line distance to the target station.")
	public static int teleporterCostPerBlock = 5;
	/**
	 * Warmup before a jump fires (100 t = 5 s).
	 *
	 * <p>Short on purpose: the feature's whole job is "get me home from the mine", and the original
	 * fifteen seconds of standing still made that a chore. The anti-escape guarantee rests on the
	 * cancel-on-damage rule rather than on the clock — a player under fire still cannot leave, but a
	 * player who is simply done mining does not wait around. Five seconds is what the wind-up needs
	 * to read as a scene (particles → rising sound → the screen going dark), which is why it is not
	 * shorter. Raise it on a PvP server if you want the jump interruptible by reaction, not by a hit.
	 */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Warmup before a jump fires (20 ticks = 1 second). Cancelled by damage.")
	public static int teleporterWarmupTicks = 100;
	/** Anti-spam lockout after landing, per player. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Per-player anti-spam lockout after landing (ticks).")
	public static int teleporterCooldownTicks = 1200;
	/** Moving further than this from where the warmup started cancels it. A step aside is fine. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Moving further than this many blocks from where warmup started cancels the jump.")
	public static int teleporterWarmupCancelRadius = 2;
	/**
	 * Max stations one remote can hold (MOD-093). Bounds the data component (each point is a
	 * dimension + pos + a name up to 32 chars) and keeps the screen's list finite without paging.
	 * Enforced server-side at bind time — never by the screen.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Max stations one teleport remote can hold.")
	public static int teleporterMaxPoints = 16;
	/**
	 * Price of one random jump (MOD-116) — flat, unlike the targeted jump's distance formula.
	 *
	 * <p>Flat because the player has to be able to read the price off the button BEFORE pressing it,
	 * and where the dice will land is by definition unknown at that moment. A distance-derived price
	 * would only be knowable after the search had already run.
	 *
	 * <p>50 000 EU is a tenth of {@link #teleporterBuffer}, so a full station holds ten random jumps.
	 * That is deliberately dearer than the ~30 000 EU a targeted 5000-block jump costs an empty pack:
	 * the targeted jump needs a station built and charged at the far end, the random one needs nothing
	 * there at all, and that convenience is what the surcharge buys.
	 */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Flat EU a random jump costs, paid by the station the remote has selected.")
	public static int teleporterRtpCost = 50_000;
	/** How far a random jump may throw the player. The outer edge of the ring, in blocks. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Outer edge of the ring a random jump throws the player into, in blocks.")
	public static int teleporterRtpRadius = 5000;
	/**
	 * How close a random jump may leave the player — the inner edge of the ring.
	 *
	 * <p>Without a floor an honest draw can land the player where they already stood, having spent a
	 * full jump's EU. That reads as a broken feature rather than as bad luck, so the ring has a hole
	 * in the middle.
	 */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Inner edge of that ring: a random jump never leaves the player closer than this.")
	public static int teleporterRtpMinRadius = 500;
	/**
	 * How many candidate spots a random jump tries before giving up (giving up costs the player
	 * nothing).
	 *
	 * <p>Deliberately small. Each candidate that clears the cheap noise probe costs a real chunk load,
	 * and on ground nobody has visited that means the full worldgen pipeline running synchronously on
	 * the server thread. The cheap probe rejects ocean for free, so in practice one candidate is paid
	 * for; this number is the ceiling on how bad an unlucky run can get.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "How many candidate spots a random jump tries before giving up. Giving up costs nothing.")
	public static int teleporterRtpMaxAttempts = 8;

	// --- Storage / per-block buffers (EU) ---
	@Knob(section = Section.STORAGE, min = 1,
			doc = "Battery Box EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).")
	public static int batteryBoxBuffer = 20_000;
	/**
	 * Reinforced Energy Storage (MV) EU buffer — five times the Battery Box (MOD-350/MOD-351). Its own
	 * knob rather than the tier default: {@code tierMvCapacity} stays the default for MV
	 * <em>machines</em>, and a store is deliberately larger than one machine's buffer (the LV pair sits
	 * at the same ratio, 20 000 against 10 000). The transfer rate is NOT a knob here — it comes from
	 * {@code EnergyTier.MV.maxVoltage()} so retuning {@code tierMvVoltage} moves the store with the tier.
	 */
	@Knob(section = Section.STORAGE, min = 1,
			doc = "Reinforced Energy Storage (MV) EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).")
	public static int cesuBuffer = 100_000;

	/**
	 * EU/tick a store hands to a NON-CASCADE sink over cable (MOD-353) — the Teleporter and the Charging
	 * Station today. 0 disables the channel.
	 *
	 * <p>Named for the mechanism, not for the Teleporter, because the defect it fixes is a class: any
	 * block that is an {@code isEnergyStorageSink} but outside the cascade was unreachable from a store
	 * over cable. 12 is the copper cable's throughput, so the store feeds a sink no faster than the wire
	 * to it would carry anyway, and it is the number already printed in the Teleporter spec.
	 */
	@Knob(section = Section.STORAGE, min = 0,
			doc = "EU/tick a storage block feeds a non-cascade sink (teleporter, charging station) over cable. 0 disables the channel.")
	public static int storageFeedRate = 12;

	/**
	 * Share of its capacity a donor store keeps for itself on that channel, 0..1 (MOD-353).
	 *
	 * <p>This is the guarantee that replaces "the fund is simply excluded from the cascade" (MOD-314 R3):
	 * below this line the channel is shut, so a Teleporter can never drain the base's bank. At 0.5 the
	 * player always keeps half their stored EU for machines. Setting it to 0 restores drain-dry
	 * behaviour and is NOT recommended — it makes this channel a slow version of the MOD-314 bug.
	 */
	@Knob(section = Section.STORAGE, min = 0.0, floorTo = 1.0,
			doc = "Share of its capacity a storage block keeps for itself when feeding a non-cascade sink over cable (0..1). Below this line the channel is shut, so a teleporter can never drain the base's bank.")
	public static double storageFeedReserveFraction = 0.5;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Macerator EU buffer. Applies to newly placed blocks.")
	public static int maceratorBuffer = 800;
	/** Shared buffer for ordinary LV processing machines: electric furnace, compressor, extractor,
	 * sawmill, polymerizer and vulcanizer. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Shared EU buffer for ordinary LV processing machines: electric furnace, compressor, extractor, sawmill, polymerizer and vulcanizer. Applies to newly placed blocks.")
	public static int machineBuffer = 800;
	/** Electric Heater EU buffer. At the default 6 EU/t it holds one cold start (1200 EU) plus one
	 * complete 200-tick vulcanization (1200 EU) and smooths a thin LV supply without becoming bulk
	 * storage — a fully charged heater can light itself and serve one batch with the cable cut. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Electric Heater EU buffer. Applies to newly placed blocks.")
	public static int electricHeaterBuffer = 2400;
	/** Pump EU buffer. Sized to hold several buckets' worth of pump cost (pumpEuPerBucket = 1000) so the
	 * energy network can keep the pump fed without stalling just below the per-bucket threshold. NOTE
	 * (MOD-070): a single copper cable now carries {@link #cableBuffer} EU/tick (the segment buffer, e.g.
	 * 12), not the LV tier voltage (32) — so a pump fed through one thin cable refills ~2.7× slower than a
	 * directly-adjacent source. This large buffer smooths that out; feed a high-draw pump from an adjacent
	 * source or several parallel cables if intake speed matters. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Pump EU buffer. Applies to newly placed blocks.")
	public static int pumpBuffer = 4000;
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Fuel generator EU buffer. Applies to newly placed blocks.")
	public static int generatorBuffer = 4000;
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Geothermal generator EU buffer. Applies to newly placed blocks.")
	public static int geothermalBuffer = 4000;
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Water mill EU buffer. Applies to newly placed blocks.")
	public static int waterMillBuffer = 4000;
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Wind mill (T1) EU buffer. Applies to newly placed blocks.")
	public static int windMillBuffer = 4000;
	/** Shared buffer for both T2 wind mills (high-altitude + storm). */
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Shared EU buffer for both T2 wind mills (high-altitude + storm). Applies to newly placed blocks.")
	public static int t2WindMillBuffer = 8000;
	@Knob(section = Section.GENERATORS, min = 1,
			doc = "Solar panel EU buffer. Applies to newly placed blocks.")
	public static int solarBuffer = 8000;
	/**
	 * Per-cable working EU buffer (MOD-070). A cable is a real transport segment with a small live
	 * buffer: energy flows segment-to-segment through these buffers (inertia) instead of teleporting
	 * producer→consumer, and on a line break the remainder is retained in the source-side cables.
	 * Deliberately tiny so a wall of cables can never be used as bulk storage — the balance ceiling is
	 * {@code cableBuffer × realistic-network-size ≪ batteryBoxBuffer}: a 1000-cable network holds
	 * 12 000 EU &lt; one Battery Box ({@link #batteryBoxBuffer} = 20 000). Kept separate from
	 * {@link dev.alaindustrial.core.energy.EnergyTier#capacity()} (the machine buffer, 10 000 EU for LV) on
	 * purpose. Applies to newly placed cables ({@code EnergyBuffer.capacity} is final per block entity).
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-cable working EU buffer — the live transport-segment buffer (MOD-070). Tiny by design so a wall of cables can't be used as bulk storage. Applies to newly placed cables.")
	public static int cableBuffer = 12;

	// --- Item pipes (MOD-104, rebalanced in MOD-108) ---
	/**
	 * Items a pipe network moves per transfer, once every {@link #itemPipeTransferIntervalTicks}.
	 * Together they set the throughput: 2 items / 20 ticks = <b>2 items per second</b>.
	 *
	 * <p><b>Why this number.</b> MOD-104 shipped 1 item <i>every tick</i> — 20/s, which is 8× a vanilla
	 * hopper (1 item / 8 ticks = 2.5/s) and faster than the starter tier of every comparable mod:
	 * BuildCraft wooden pipe 1.0/s, Mekanism Basic Transporter 2.0/s, Thermal basic servo 2.67/s,
	 * AE2 import bus 4.0/s, EnderIO conduit 8.0/s. A player emptied a stack between chests in ~3
	 * seconds and, worse, there was nowhere left to upgrade to. 2/s matches Mekanism's Basic
	 * Logistical Transporter exactly — a passive starter tier sits slightly below the hopper, and earns
	 * its keep by routing and range rather than raw speed.
	 *
	 * <p><b>Why an interval instead of a smaller batch.</b> A batch cannot go below 1 item, so the only
	 * way down from 20/s is to stop moving every tick. Fixing the interval and growing the batch is
	 * also what MI, AE2, Mekanism and EnderIO all do, which leaves a clean ladder for later tiers
	 * (batch 2 → 8 → 32 → 64 at the same interval = 2 → 8 → 32 → 64 items/s).
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Items an item-pipe network moves per transfer. With the interval below this sets throughput.")
	public static int itemPipeItemsPerTransfer = 2;

	/**
	 * Server ticks between transfers on one pipe network (20 = once per second). See
	 * {@link #itemPipeItemsPerTransfer} for the balance rationale; upgrades are expected to raise the
	 * batch, not shorten this.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Server ticks between item-pipe transfers (20 = once per second).")
	public static int itemPipeTransferIntervalTicks = 20;

	// --- Fluid pipes (MOD-151) ---
	/**
	 * Working fluid buffer of one pipe segment, in mB. As with {@link #cableBuffer}, this doubles as the
	 * segment's real throughput: fluid physically flows THROUGH the buffer, one hop per tick, so a line
	 * carries at most this much per tick regardless of how much the endpoints want to move.
	 *
	 * <p><b>Why 50.</b> Anchored to what actually feeds a line: the pump costs
	 * {@link #pumpEuPerBucket} EU per bucket and draws at LV (32 EU/tick), so it produces about
	 * 32 mB/tick. A pipe at 50 mB/tick (1 bucket/s) keeps the pump as the bottleneck rather than
	 * becoming one itself, while staying deliberately modest — like the item pipe, the basic tier earns
	 * its keep by reaching across a base, not by raw speed. Later tiers should raise this number; the
	 * one-hop-per-tick rule stays.
	 *
	 * <p>Kept small on purpose for the same reason as the cable buffer: a wall of pipes must not become
	 * bulk storage. 1000 segments hold 50 buckets, well under a single portable tank.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Per-segment fluid buffer in mB — also the segment's throughput, since fluid flows through the buffer one hop per tick (MOD-151). Applies to newly placed pipes.")
	public static int fluidPipeSegmentBuffer = 50;

	/**
	 * Fluid networks processed per server tick; the remainder round-robins to later ticks. Mirrors
	 * {@link #networksPerTick} for energy — a base with hundreds of separate pipe runs must not be able
	 * to spike the tick.
	 */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Fluid networks processed per server tick; the rest round-robin to later ticks.")
	public static int fluidNetworksPerTick = 512;

	// --- Battery Pouch (MOD-052, powered item) ---
	/** Pouch storage capacity in weight units (vanilla-bundle math: one item weighs 64/maxStackSize).
	 * 128 = exactly twice a vanilla bundle, ≈ two stacks of ordinary items. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Battery Pouch item-storage capacity in weight units (one ordinary item = 1).")
	public static int lvPouchCapacity = 128;
	/** Pouch EU buffer. At the 1 EU/s passive drain this is ~33 min of carrying items — well past a
	 * single mining trip; charging at the LV ceiling (32 EU/t) refills it in ~63 ticks. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Battery Pouch EU buffer.")
	public static int lvPouchBuffer = 2000;
	/** EU drained per second while the pouch is in a player inventory AND holds items. At 0 EU the
	 * pouch locks (no insert, no extract) until recharged in the Battery Box slot. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "EU the pouch drains per second while carried and holding items (locks at 0 EU until recharged).")
	public static int lvPouchDrainPerSecond = 1;

	// --- Battery (MOD-083, the stackable EU carrier) ---
	/** EU one battery holds. Deliberately the same size as the pouch (a pouch's whole charge fits in one
	 * battery), so the battery reads as "the first EU you can carry" and does not compete with the
	 * 20 000 EU Energy Pack. Charge is stored PER ITEM: a full stack of 16 carries 32 000 EU. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Battery EU buffer, PER ITEM (a stack of 16 carries 16x this).")
	public static int batteryBuffer = 2000;
	/** Max EU/tick one battery accepts in a charge slot. At the LV ceiling (32) a whole stack of 16
	 * still divides evenly — 2 EU per item per tick — which is what keeps stack charging exact. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t one battery accepts while charging in a slot.")
	public static int batteryInputRate = 32;
	/** EU one right-click hands from the battery to the item in the other hand. A full battery empties
	 * into a tool in four clicks, so a manual top-up stays a deliberate act rather than a reflex. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU one right-click moves from the battery into the item in the other hand.")
	public static int batteryTransferPerUse = 500;

	// --- EU crystals (MOD-504) ---
	// Only the BLANK of each tier has a buffer; the finished crystal is a plain crafting material with
	// no energy at all. So these numbers are not storage capacities — they are the EU price of making
	// one crystal, and the priming time is that price divided by the charge rate.
	//
	// Every tier accepts the same 128 EU/t, and that is the ceiling of the hardware rather than a
	// balance choice: a charge slot moves min(EnergyTier.MV.maxVoltage(), inputRate) — see
	// CesuBlockEntity#chargeItem — and the Charging Station's own intake is chargePadInputRate = 128.
	// The mod has no HV item charger, so a bigger number here would be a dead letter.
	//
	// The ladder is 100 k / 500 k / 1.5 M rather than the round IC2 100 k / 1 M / 10 M, because each
	// blank now fills from EMPTY: nothing is carried over from the tier below, since the finished
	// crystal it is built from holds no charge to carry. At 128 EU/t that gives 39 s / 3 min 15 s /
	// 9 min 45 s. Ten million would have meant 65 minutes of staring at a slot.
	/** EU a blank Energy Crystal must absorb before it becomes an Energy Crystal. ~39 s at 128 EU/t. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU a blank Energy Crystal must absorb to become a crystal. Priming time is this divided by the charge rate below.")
	public static int energyCrystalBuffer = 100_000;
	/** Max EU/tick an Energy Crystal blank accepts in a charge slot; the MV ceiling, see the note above. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t an Energy Crystal accepts in a charge slot. A charge slot caps at 128 regardless, so higher values do nothing until an HV item charger exists.")
	public static int energyCrystalInputRate = 128;
	/** EU a blank Lapotron Crystal must absorb. ~3 min 15 s at 128 EU/t. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU a blank Lapotron Crystal must absorb. It fills from empty - the finished Energy Crystal it is built from carries no charge.")
	public static int lapotronCrystalBuffer = 500_000;
	/** Max EU/tick a Lapotron Crystal blank accepts in a charge slot. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t a Lapotron Crystal accepts in a charge slot.")
	public static int lapotronCrystalInputRate = 128;
	/** EU a blank Resonant Crystal must absorb — the end of the ladder. ~9 min 45 s at 128 EU/t. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU a blank Resonant Crystal must absorb. Sized against the 128 EU/t charge ceiling; raise it only together with an HV item charger.")
	public static int resonantCrystalBuffer = 1_500_000;
	/** Max EU/tick a Resonant Crystal blank accepts in a charge slot. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t a Resonant Crystal accepts in a charge slot.")
	public static int resonantCrystalInputRate = 128;

	// --- Energy Pack (MOD-065, worn LV buffer) ---
	/** Energy Pack EU buffer — 10 pouches' worth, the same size as the Battery Box (LV tier). Charging
	 * it from a Battery Box at the LV ceiling (32 EU/t) takes ~625 ticks (~31 s). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Energy Pack (worn) EU buffer.")
	public static int energyPackBuffer = 20_000;
	/** Max EU/tick the pack accepts while sitting in a charge slot. At the LV ceiling this is what a
	 * Battery Box can push anyway; the knob exists so a future MV charger can feed the pack faster. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the Energy Pack accepts while charging in a slot.")
	public static int energyPackInputRate = 32;
	/** Max EU/tick the worn pack hands out to powered items in the player's inventory. The transfer
	 * runs once per second in batches of {@code energyPackOutputRate × 20} EU (see EnergyPackItem). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the worn Energy Pack hands out to powered items in the inventory.")
	public static int energyPackOutputRate = 32;

	// --- Charging Station (MOD-274, the pad the player stands on) ---
	/**
	 * Charging Station EU buffer — a Battery Box's worth ({@link #batteryBoxBuffer}), and the single
	 * number that makes the station feel instant.
	 *
	 * <p>This is the one machine in the mod whose buffer is not sized to "one operation": it is sized to
	 * a <em>visit</em>. The station is a capacitor. It fills slowly from the grid while nobody is on it
	 * and empties fast into whoever steps up, which is the only way {@link #chargePadOutputRate} can ever
	 * be reached — the grid itself cannot deliver that much (see the rate's own note). Cut this to a
	 * machine-sized 800 and the station still works, but hands out its whole buffer in six ticks and
	 * then crawls at whatever the cable feeds it, which is exactly the "stand here for three minutes"
	 * experience the block exists to remove.
	 */
	@Knob(section = Section.STORAGE, min = 1,
			doc = "Charging Station EU buffer. Sized to a visit, not an operation: the station banks power while idle so it can charge a player's gear in one burst. Applies to newly placed blocks.")
	public static int chargePadBuffer = 20_000;
	/**
	 * Max EU/tick the station accepts from the grid. Set to the MV ceiling ({@link #tierMvVoltage}) on
	 * purpose, even though the block itself is LV: it is a ceiling, not a promise, and nothing in the
	 * mod can currently reach it. What actually arrives is set by the supply — 12 EU/t through a copper
	 * cable ({@link #cableBuffer}), 48 through a gold one ({@link #goldCableBuffer}), 32 from a Battery
	 * Box flush against it ({@code DirectAdjacencyDistributor} caps at the SOURCE's tier voltage). So
	 * the station simply scales with whatever grid the player has built instead of capping their good
	 * infrastructure at LV, and the MV supply it is ready for costs gold to lay.
	 */
	@Knob(section = Section.STORAGE, min = 1,
			doc = "Max EU/t the Charging Station accepts from the grid. A ceiling, not a promise - a copper cable delivers 12, a gold one 48, an adjacent Battery Box 32.")
	public static int chargePadInputRate = 128;
	/**
	 * Max EU/tick the station hands to the items on the player standing on it, drawn from its buffer.
	 * Every target is still clamped by its own {@link dev.alaindustrial.item.energy.ItemEnergy#inputRate}
	 * (32 for every powered item in the mod), so this is the station's total per-tick output shared
	 * across everything the player carries, not a per-item rate — a full set drains the buffer in
	 * roughly eight seconds and then continues at the speed of the incoming supply.
	 */
	@Knob(section = Section.STORAGE, min = 1,
			doc = "Max EU/t the Charging Station hands to the player standing on it, shared across every powered item they carry (each item still capped by its own input rate).")
	public static int chargePadOutputRate = 128;

	// --- Electric Drill (MOD-079, first powered hand tool) ---
	/** Electric Drill EU buffer — half an Energy Pack, five pouches' worth. At {@link #electricDrillEuPerBlock}
	 * per block this is ~200 blocks on a full charge. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Drill EU buffer.")
	public static int electricDrillBuffer = 10_000;
	/** EU drained per block the drill successfully mines while it has at least this much charge. Below it the
	 * drill still mines (and drops), but at hand speed and free — see ElectricDrillItem. Kept under the LV
	 * machine floor (200 EU/op): breaking a block is cheaper than smelting one. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the drill spends per block mined at powered speed (below this it mines at hand speed for free).")
	public static int electricDrillEuPerBlock = 50;
	/** Max EU/tick the drill accepts while sitting in a charge slot. At the LV ceiling a full charge from a
		Battery Box takes ~313 ticks (~16 s). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the drill accepts while charging in a slot.")
	public static int electricDrillInputRate = 32;
	/** EU drained when the drill places a torch from the inventory on right-click (MOD-089). Placing a
		torch is a comfort action, cheaper than mining a block ({@link #electricDrillEuPerBlock} = 50). Below
		this charge the drill refuses to place and notifies the player instead of giving a free torch
		(MOD-097) — the torch is powered, not free. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "EU the drill spends to place a torch on right-click.")
	public static int electricDrillTorchEuCost = 5;
	/** Netherite-Tipped Electric Drill EU buffer (MOD-534) — the only number the third tier does not share
	 * with the two below it. Half again the base 10 000, so at the unchanged {@link #electricDrillEuPerBlock}
	 * it is ~300 blocks on a full charge against their ~200. The per-block cost deliberately does NOT rise
	 * with it: the tier is paid for in its recipe (netherite), not by charging the player more EU for the
	 * same work. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Netherite-Tipped Electric Drill EU buffer (the two tiers below share electricDrillBuffer).")
	public static int electricDrillNetheriteTipBuffer = 15_000;

	// --- Electric Chainsaw (MOD-337, the drill's wood-side counterpart) ---
	/** Electric Chainsaw EU buffer — the same reservoir as the drill, so the two tools of the LV hand-tool
	 * line charge and last alike. At {@link #electricChainsawEuPerBlock} per block this is ~333 logs on a
	 * full charge. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Chainsaw EU buffer.")
	public static int electricChainsawBuffer = 10_000;
	/** EU drained per block the chainsaw successfully cuts while it has at least this much charge. Below it
	 * the chainsaw still cuts (and drops), but at hand speed and free — see ElectricChainsawItem. Cheaper
	 * than the drill's 50 because wood is softer than stone, and well under the LV machine floor
	 * (200 EU/op). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the chainsaw spends per block cut at powered speed (below this it cuts at hand speed for free).")
	public static int electricChainsawEuPerBlock = 30;
	/** Max EU/tick the chainsaw accepts while sitting in a charge slot — the LV ceiling, like the drill. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the chainsaw accepts while charging in a slot.")
	public static int electricChainsawInputRate = 32;

	// --- Electric Shovel (MOD-338, the earth-side member of the same hand-tool line) ---
	/** Electric Shovel EU buffer — the same reservoir as the drill and the chainsaw, so the whole LV
	 * hand-tool line charges and lasts alike. At {@link #electricShovelEuPerBlock} per block this is
	 * ~500 blocks of dirt on a full charge. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Shovel EU buffer.")
	public static int electricShovelBuffer = 10_000;
	/** EU drained per block the shovel successfully digs while it has at least this much charge. Below it
	 * the shovel still digs (and drops), but at hand speed and free — see ElectricShovelItem. Cheaper
	 * than the chainsaw's 30 because loose earth is softer than wood, and far under the LV machine floor
	 * (200 EU/op). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the shovel spends per block dug at powered speed (below this it digs at hand speed for free).")
	public static int electricShovelEuPerBlock = 20;
	/** Max EU/tick the shovel accepts while sitting in a charge slot — the LV ceiling, like its siblings. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the shovel accepts while charging in a slot.")
	public static int electricShovelInputRate = 32;

	// --- Electric Hoe (MOD-342, the farming member of the same hand-tool line) ---
	/** Electric Hoe EU buffer — the same reservoir as the rest of the line, so all four powered hand
	 * tools charge and last alike. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Hoe EU buffer.")
	public static int electricHoeBuffer = 10_000;
	/** EU drained per block the hoe successfully breaks while it has at least this much charge. Below it
	 * the hoe still breaks (and drops), but at hand speed and free — see ElectricHoeItem. Set to the
	 * drill's 50 by customer decision: the hoe's block set ({@code #minecraft:mineable/hoe} — hay,
	 * leaves, sponge, moss, nether wart block) is small, so a cheap rate would make the tool spend
	 * nothing at all. Tilling is billed separately, by {@link #electricHoeTillEuCost}. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the hoe spends per block broken at powered speed (below this it breaks at hand speed for free). Tilling is powered too and is billed separately by electricHoeTillEuCost.")
	public static int electricHoeEuPerBlock = 50;
	/** EU the hoe spends per successful right-click conversion (tilling soil, coarse dirt → dirt, …).
	 * Unlike the shovel's free dirt paths, tilling is powered: it is the hoe's whole job, and if it were
	 * free the tool would never spend a single EU in normal play. Below this the hoe tills nothing and
	 * says so, mirroring the drill's torch (`electricDrillTorchEuCost`). */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "EU the hoe spends per successful right-click conversion (tilling soil).")
	public static int electricHoeTillEuCost = 50;
	/** Max EU/tick the hoe accepts while sitting in a charge slot — the LV ceiling, like its siblings. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the hoe accepts while charging in a slot.")
	public static int electricHoeInputRate = 32;

	// --- Electric Saber (MOD-149, the line's first weapon) ---
	/** Electric Saber EU buffer — the same reservoir as the four hand tools, so the whole LV line
	 * charges and lasts alike. At {@link #electricSaberEuPerHit} per hit this is 100 powered swings. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Saber EU buffer.")
	public static int electricSaberBuffer = 10_000;
	/** EU drained per hit on a living target while the saber is charged and switched on. Below it the
	 * saber still hits, but as a plain sword and for free — see ElectricSaberItem. Twice the drill's
	 * per-block 50 and half the LV machine floor (200 EU/op): a swing that outdamages every vanilla
	 * sword should cost more than breaking a block. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the saber spends per powered hit (below this it hits as a plain sword for free).")
	public static int electricSaberEuPerHit = 100;
	/** Max EU/tick the saber accepts while sitting in a charge slot — the LV ceiling, like its siblings. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the saber accepts while charging in a slot.")
	public static int electricSaberInputRate = 32;
	/** Seconds of Slowness II the electric discharge leaves on a target struck by a live saber. Short on
	 * purpose: two seconds read as a jolt, and the same number lands on players in PvP. 0 disables the
	 * effect entirely, leaving the saber a pure damage weapon. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Seconds of Slowness II a powered saber hit leaves on the target (0 disables).")
	public static int electricSaberShockSeconds = 2;

	// --- Electromagnet (MOD-132, item-pull convenience) ---
	/** Electromagnet EU buffer (tier 1). A modest LV reservoir: at {@link #magnetEuPerItem} per pulled
	 * item·tick it reaps hundreds of drops before a recharge, and tops up in ~8 s at an LV charger. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electromagnet EU buffer.")
	public static int magnetBuffer = 5_000;
	/** Max EU/tick the magnet accepts while sitting in a charge slot (LV ceiling, like the drill). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the electromagnet accepts while charging in a slot.")
	public static int magnetInputRate = 32;
	/** Pull radius in blocks around the carrier (a sphere — up, down and sideways). Tier 1 covers 5
	 * blocks; higher tiers (larger radius) are a later task. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electromagnet pull radius in blocks around the carrier.")
	public static int magnetRange = 5;
	/** EU spent per item actually pulled, each tick it is being drawn in. An idle scan (nothing in range)
	 * is free, so the magnet is a consumable and not a free vacuum. Small next to the large buffer. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the electromagnet spends per item pulled each scan tick (an idle scan is free).")
	public static int magnetEuPerItem = 2;
	/** How often (ticks) the magnet scans for and pulls nearby drops. 1 = every tick, for a smooth, fast
	 * XP-orb-like pull that visibly flies items in (a coarser interval read as "barely pulling"). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "How often (ticks) the electromagnet scans for and pulls nearby drops.")
	public static int magnetScanIntervalTicks = 1;

	// --- Jetpack (MOD-148, worn EU flight) ---
	/** Jetpack EU buffer — 1.5 Energy Packs. At {@link #jetpackEuPerTick} per tick of thrust this is
	 * ~30 s of continuous flight; charging at the LV ceiling (32 EU/t) refills it in ~938 ticks (~47 s). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Jetpack EU buffer.")
	public static int jetpackBuffer = 30_000;
	// Fluxweave armour (MOD-127). Only EU numbers live here: defense/toughness/enchantability are built
	// into ArmorMaterial at item-registration time, BEFORE the config file is read, so exposing those
	// would be dead knobs. See ModArmorMaterials.
	/** EU buffer of each Fluxweave piece — between the drill (10k) and the Energy Pack (20k). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU buffer of each Fluxweave armour piece.")
	public static int fluxweaveBuffer = 15_000;
	/** Max EU/t a Fluxweave piece accepts while charging in a slot (LV ceiling). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t a Fluxweave piece accepts while charging in a slot.")
	public static int fluxweaveInputRate = 32;
	/** EU/second a charged, worn piece burns to keep its bonuses on. 1 EU/s = ~4 h per full buffer. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "EU/second a charged, worn Fluxweave piece burns to keep its bonuses on.")
	public static int fluxweaveUpkeepEuPerSecond = 1;
	/** Boots: percent of fall damage absorbed while charged. Clamped to 90 in code — never a full cancel. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Percent of fall damage Fluxweave boots absorb while charged (clamped to 90 in code).")
	public static int fluxweaveFallDamageReductionPercent = 50;
	/** Leggings: percent added to run speed while charged. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Percent added to run speed by charged Fluxweave leggings.")
	public static int fluxweaveRunSpeedPercent = 12;
	/** Helmet: OXYGEN_BONUS levels while charged (Respiration's mechanic: 3 = ~75 % of air ticks skipped). */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "OXYGEN_BONUS levels granted by a charged Fluxweave helmet.")
	public static int fluxweaveOxygenBonus = 3;
	/** Helmet: percent added to water movement efficiency while charged (attribute caps at 100). */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Percent of water movement efficiency granted by a charged Fluxweave helmet.")
	public static int fluxweaveSwimEfficiency = 50;
	/** Chestplate: extra armour toughness while charged. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Extra armour toughness on a charged Fluxweave chestplate.")
	public static int fluxweaveChargedToughness = 2;
	/** Chestplate: percent of knockback resisted while charged. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Percent of knockback resisted by a charged Fluxweave chestplate.")
	public static int fluxweaveKnockbackResistance = 10;
	/** Leggings: extra step height (in hundredths of a block) while charged AND the assist is toggled on.
	 * 60 = +0.6, which takes the player from the vanilla 0.6 to 1.2 — a full block step. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Extra step height (hundredths of a block) from charged Fluxweave leggings with the assist toggled on.")
	public static int fluxweaveStepHeightBonus = 60;
	/** Set bonus: EU the helmet spends per half-heart healed at 4/4 while below full health. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the Fluxweave helmet spends per half-heart healed by the 4/4 set bonus.")
	public static int fluxweaveRegenEuPerHeal = 200;
	/** EU burned per tick the jetpack engine actually thrusts (jump held while airborne, charge left).
	 * Matches the drill's per-block cost: a second of flight ≈ 20 mined blocks' worth of EU. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the jetpack burns per tick of thrust (jump held while airborne).")
	public static int jetpackEuPerTick = 50;
	/** Max EU/tick the jetpack accepts while sitting in a charge slot (LV ceiling, like the pack). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the jetpack accepts while charging in a slot.")
	public static int jetpackInputRate = 32;
	/** Altitude ceiling (block Y) above which the engine refuses to thrust — the jetpack glides
	 * instead. 320 = the overworld build limit; server owners can lower it. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Altitude ceiling (block Y) above which the jetpack engine refuses to thrust.")
	public static int jetpackMaxY = 320;
	/** Light level (0–15) of the torch-like glow a thrusting jetpack casts around the flyer — a
	 * moving {@code minecraft:light} block (see JetpackLight). 0 disables the effect entirely; 10 is
	 * a bit under a torch (14), a "small glow". Values above 15 are clamped. */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Light level (0-15) a thrusting jetpack casts around the flyer; 0 disables the glow.")
	public static int jetpackFlightLightLevel = 10;

	// --- Stock Display Frame (MOD-066, no energy) ---
	/** How often (ticks) a stock display frame rescans the container behind it. 20 = once a second;
	 * a 100-frame warehouse costs ~5 container sums per tick at the default. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "How often (ticks) a Stock Display Frame rescans the container behind it.")
	public static int stockFrameScanIntervalTicks = 20;

	// --- Scythe bonus seed drop (MOD-315) ---
	/**
	 * Global multiplier on the scythe's per-tier bonus-seed chance. The per-tier base lives in
	 * {@code ScytheTiers} together with the rest of the scythe's balance (area, cap, attack bias) —
	 * this is the one server-side knob over the whole ladder, so a server can soften or disable the
	 * mechanic without rebalancing eight tiers.
	 *
	 * <p>{@code 1.0} keeps the shipped ladder (wood 0 % → netherite 35 %); {@code 0.0} disables the
	 * bonus entirely; anything that pushes a tier past 1.0 is clamped, so a large value means "every
	 * ripe crop drops the extra seed". The multiplication and the clamp live in
	 * {@code ScytheItem.Profile.effectiveBonusChance}.
	 */
	@Knob(section = Section.TOOLS, min = 0.0, floorTo = 0.0,
			doc = "Global multiplier on the scythe's per-tier bonus-seed chance (1.0 = shipped ladder, 0.0 = mechanic off; a tier is clamped to 1.0).")
	public static double scytheBonusSeedMultiplier = 1.0;

	// --- Machines: shared EU/tick + per-machine duration (ticks) -> E_op = euPerTick × duration ---
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Base EU/t a processing machine draws while running (energy per operation = this x its duration).")
	public static int machineEuPerTick = 2;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a macerator takes per operation at 1.0 speed.")
	public static int maceratorDuration = 150;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an electric furnace takes per smelt at 1.0 speed.")
	public static int electricFurnaceDuration = 100;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a compressor takes per operation at 1.0 speed.")
	public static int compressorDuration = 130;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an extractor takes per operation at 1.0 speed.")
	public static int extractorDuration = 120;
	/** Sawmill (MOD-150): ticks per cut at 1.0 speed. 80 → 160 EU/op — the cheapest machine op (wood
	 * saws easier than ore mills): furnace 100, extractor 120, compressor 130, macerator 150. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a sawmill takes per cut at 1.0 speed (all four modes).")
	public static int sawmillDuration = 80;
	/** Polymerizer (MOD-019): ticks per bucket of oil at 1.0 speed. 200 → 400 EU/op — the most expensive
	 * op of the LV processing family, because rubber is the material gate into MV and a bucket of oil is
	 * a whole pumping cycle's worth of input, not a single ore. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a polymerizer takes to turn one bucket of oil into raw rubber at 1.0 speed.")
	public static int polymerizerDuration = 200;
	/** Vulcanizer (MOD-258): ticks per operation at 1.0 speed. The shipped recipe costs 400 EU, so at
	 * the ordinary-machine rate of 2 EU/t the operation takes 200 ticks at every heat tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fallback ticks a vulcanizer operation takes at 1.0 speed; shipped recipe energy 400 / machineEuPerTick 2 = 200.")
	public static int vulcanizerDuration = 200;
	/**
	 * Thermal Centrifuge (MOD-424): ticks per operation at 1.0 speed. The shipped recipe costs 800 EU at
	 * this machine's own 4 EU/t tariff, so one dust becomes two shavings in 200 ticks.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fallback ticks a thermal centrifuge operation takes at 1.0 speed; shipped recipe energy 800 / thermalCentrifugeEuPerTick 4 = 200.")
	public static int thermalCentrifugeDuration = 200;
	/**
	 * EU/t the Thermal Centrifuge spends while spinning up or processing. Double the ordinary machine
	 * rate: it is the second doubling step on the mod's rarest ore, and it runs on top of a heater, so
	 * the pair (4 + 6 = 10 EU/t) is deliberately close to the copper cable's 12 EU/t ceiling.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t a thermal centrifuge spends while spinning up or processing; with the heater below the pair draws 10 EU/t.")
	public static int thermalCentrifugeEuPerTick = 4;
	/**
	 * Ticks a stopped rotor needs to reach working speed once the redstone signal arrives.
	 *
	 * <p>Twice the heater's warm-up rather than equal to it (playtest, 2026-08-16): matching the two made
	 * the rotor feel weightless — it was always ready the moment the heat was, so the spin never
	 * registered as its own thing. At double, the heater finishes first and the machine visibly waits on
	 * its own mass, which is the whole point of a centrifuge.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a stopped centrifuge rotor needs to reach working speed after the redstone signal arrives; it sheds speed at half this rate.")
	public static int thermalCentrifugeSpinupTicks = 400;

	// ── MOD-468, stage 1: the reactor room ───────────────────────────────────────────────────────
	/**
	 * Ticks the airlock stays open after a redstone pulse. Two seconds: long enough to walk through,
	 * short enough that the room is never casually left open. Holding the signal does NOT extend it —
	 * the door closes on this timer and only a fresh rising edge opens it again.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the reactor airlock stays open after a redstone pulse before closing itself; holding the signal does not extend it.")
	public static int reactorDoorOpenTicks = 40;
	/**
	 * How long the door waits before re-testing a doorway someone is standing in. Small, because this
	 * is a politeness delay, not a timer the player should feel.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the airlock waits before re-testing a doorway that still has someone standing in it.")
	public static int reactorDoorOccupiedRecheckTicks = 10;
	/**
	 * Ticks the panel takes to travel its full two blocks, in either direction (MOD-493). Purely
	 * cosmetic: the {@code open} block state — and with it collision, pathfinding and the radiation
	 * trace — still flips in one tick, and this only says how long the client draws the panel on its
	 * way there.
	 *
	 * <p>A fifth of {@link #reactorDoorOpenTicks} on purpose. The travel has to read as machinery
	 * rather than as a teleport, but it is spent out of the same two seconds the player has to walk
	 * through: a slow, stately blast door would eat the window it is opening.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the airlock panel takes to slide its full two blocks; cosmetic only, the open state still flips in one tick.")
	public static int reactorDoorSlideTicks = 8;
	/**
	 * How often a controller re-scans its room. {@code neighborChanged} only sees the six blocks
	 * touching the controller, and a room is up to 14 across — this sweep is the only thing that
	 * notices a far wall being mined. Two seconds keeps a 1016-cell walk off the hot path while still
	 * reacting faster than a player can cross the room.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between full re-scans of a reactor room by its controller; catches shell changes out of neighbour range.")
	public static int reactorScanIntervalTicks = 40;
	/** Smallest interior edge a reactor room may have, in blocks (shell 5x5x5). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Smallest interior edge of a reactor room, in blocks.")
	public static int reactorRoomMinInner = 3;
	/**
	 * Largest interior edge a reactor room may have, in blocks (shell 14x14x14, 1016 shell blocks).
	 * A cap rather than free growth: it bounds both the scan cost and how much power one room can ever
	 * hold, which is what keeps later stages balanceable.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Largest interior edge of a reactor room, in blocks; bounds both the scan cost and how much one room can hold.")
	public static int reactorRoomMaxInner = 12;
	/**
	 * Largest share of a reactor shell, in percent, that may be glass. Glass counts as shell and costs
	 * the same to make, so without a cap the pretty option would also be the strictly correct one and
	 * every room would be a glass box. At 30 the player gets real windows on the side they care about
	 * while the structure still reads as containment.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Largest share of a reactor shell that may be glass, in percent; above it the room reports a weak structure.")
	public static int reactorRoomMaxGlassPercent = 30;

	// ── MOD-505: the crystal greenhouse. Shares the reactor's room scanner, nothing else. ──
	/**
	 * How often a crystal-farm controller re-scans its greenhouse. Same reasoning as
	 * {@link #reactorScanIntervalTicks}: {@code neighborChanged} only sees the six neighbouring
	 * blocks, so this sweep is the only thing that notices a far wall being mined. It also refreshes
	 * the seedbed list and the water check, which is why growth reads them rather than re-walking.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between full re-scans of a crystal greenhouse by its controller; also refreshes its seedbed list and water check.")
	public static int crystalFarmScanIntervalTicks = 40;
	/**
	 * Smallest interior volume that counts as a greenhouse, in blocks. A volume rather than an edge
	 * length, because the room is flood-filled and may be any shape at all (MOD-505, playtest four):
	 * "3 blocks across" means nothing to a dome.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Smallest interior volume of a crystal greenhouse, in blocks; the room may be any shape, so this is a volume rather than an edge length.")
	public static int crystalFarmRoomMinCells = 27;
	/**
	 * Largest interior volume, in blocks. This is the fill's budget: past it the room is reported as
	 * unsealed, because a fill that has run this far is either leaking or enclosing more than one
	 * greenhouse's worth of air. 4096 is a 16x16x16 hall — generous for any shape worth building.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Largest interior volume of a crystal greenhouse, in blocks; past it the room reads as unsealed.")
	public static int crystalFarmRoomMaxCells = 4096;
	/**
	 * How far from the controller the fill may reach, in blocks. A second, harder limit than the cell
	 * budget: it is what keeps a leaking room from reading blocks chunks away, which is both slow and
	 * a way to touch terrain that is not loaded.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "How far from its controller a greenhouse fill may reach, in blocks; keeps a leaking room from scanning into unloaded terrain.")
	public static int crystalFarmRoomMaxSpan = 24;
	/**
	 * Ticks between growth attempts. Deliberately far slower than the scan: a crystal takes over an
	 * hour unaided, so rolling for one more than a few times a minute would be wasted work on every
	 * seedbed in the room.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between growth attempts on every seedbed in a greenhouse.")
	public static int crystalFarmGrowthIntervalTicks = 100;
	/**
	 * The 1-in-this chance that one seedbed advances on one attempt, with no water and no power.
	 *
	 * <p>Sized against the design target of "an hour or two per crystal, unhelped": a crystal costs
	 * {@link dev.alaindustrial.core.crystal.CrystalGrowth#EVENTS_PER_CRYSTAL} events, so
	 * 4 x 270 x 100 ticks is about 90 minutes. <b>Lower this to a single digit to watch the farm work
	 * during a test</b> — at the shipped value nothing visible happens for many minutes.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "1-in-this chance a seedbed advances on one attempt with no water and no power; lower it to single digits to watch a farm work during a test.")
	public static int crystalFarmGrowthChanceDivisor = 270;
	/** How much water in the room cuts the growth divisor by. Free to supply, so the smaller bonus. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Factor water in the room cuts the crystal growth divisor by.")
	public static int crystalFarmWaterSpeedup = 3;
	/** How much a powered attempt cuts the growth divisor by, on top of water. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Factor a powered attempt cuts the crystal growth divisor by, on top of water.")
	public static int crystalFarmPowerSpeedup = 2;
	/**
	 * How much a sprinkler standing in the room cuts the growth divisor by, on top of the other two
	 * (MOD-525). The third and last axis: water is free, power is a cable, and this one is a whole
	 * production chain — fermented waste, distilled twice. Sized like the power bonus rather than the
	 * water one, so the full stack is 270 / 3 / 2 / 2 = 22 and a fully-served bed beats a bare one by
	 * roughly twelvefold, not by an order of magnitude.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Factor a sprinkler in the room cuts the crystal growth divisor by, on top of water and power.")
	public static int crystalFarmSprinklerSpeedup = 2;
	/**
	 * mB of nutrient solution one boosted growth event in a greenhouse costs. Charged on delivery like
	 * {@link #crystalFarmEuPerGrowth}: solution buys crystals, not dice rolls.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "mB of nutrient solution one sprinkler-boosted growth event costs; charged only when the event happens.")
	public static int crystalFarmSolutionPerGrowthMb = 50;
	/**
	 * EU one boosted growth event costs. Charged on delivery, not per roll: energy buys crystals, not
	 * dice. Zero makes the power bonus free, which is a legitimate way to switch it off as a cost.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "EU one boosted growth event costs; charged only when the event actually happens.")
	public static int crystalFarmEuPerGrowth = 64;
	/** Buffer of a farm controller, in EU. Power is optional here, so this only smooths the boost. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Energy buffer of a crystal farm controller, in EU.")
	public static int crystalFarmBuffer = 4000;
	/**
	 * Ticks a hand-opened greenhouse door stays open before it seals itself. The room only grows
	 * while it is closed, so a door left ajar is a silent way to switch the farm off — five seconds
	 * is long enough to walk through and short enough that nobody forgets. A door held open by
	 * redstone ignores this entirely.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a hand-opened greenhouse door stays open before sealing itself; a door held open by redstone ignores this.")
	public static int crystalFarmDoorAutoCloseTicks = 100;
	/** Ticks the door waits before re-testing a doorway that still has somebody standing in it. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the greenhouse door waits before re-testing a doorway that still has someone standing in it.")
	public static int crystalFarmDoorOccupiedRecheckTicks = 10;
	/**
	 * Buds one amethyst shard buys when fed to a seedbed. Shards go in ONE per click (playtest three),
	 * so this is the whole exchange rate of the feature.
	 *
	 * <p><b>One, so the trade reads itself: a shard in, a crystal out.</b> The profit is not this
	 * number — it is what the crystal drops, which vanilla puts at
	 * {@link dev.alaindustrial.core.crystal.CrystalGrowth#SHARDS_PER_RIPE_CRYSTAL} to a pickaxe. So a
	 * shard fed comes back fourfold, paid for by roughly ninety minutes of standing there. It briefly
	 * shipped at 3 (a twelvefold return) for no reason other than that nobody had multiplied it out.
	 *
	 * <p>An amethyst block counts as four shards, because that is what vanilla crafts it from.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Buds one amethyst shard buys when fed to a seedbed; shards go in one per click, so this is the exchange rate of the whole farm.")
	public static int crystalSeedbedChargesPerShard = 1;
	/**
	 * Ticks the reactor button stays pressed. Vanilla's stone button is 20; this matches it, which is
	 * comfortably longer than the airlock needs to see the rising edge even through a run of dust.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the reactor button stays pressed before releasing itself.")
	public static int reactorButtonPressTicks = 20;

	// ── MOD-468, stage 2: the reactor actually runs ───────────────────────────────────────────────
	/**
	 * EU/t one fully-lowered fuel rod contributes before neighbour bonuses. Four rods per assembly, so
	 * a single rack at full depth is 4x this. The mod's whole existing ceiling is 16 EU/t (geothermal),
	 * which is what makes even a small reactor worth the trip to the Nether.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t one fully lowered uranium rod contributes, before neighbour bonuses.")
	public static int reactorEuPerRod = 6;
	/**
	 * Extra output, in percent, each adjacent loaded assembly grants a rack. Packing racks together is
	 * how a reactor is made powerful — and it heats up on exactly the same curve, so density is the
	 * risk/reward dial rather than a free win.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Extra output in percent granted per adjacent loaded fuel assembly; heat scales with it too.")
	public static int reactorNeighbourBonusPercent = 25;
	/**
	 * Ticks of burn in one uranium rod at full depth — twenty minutes of real time.
	 *
	 * <p><b>This number is the whole uranium economy, and the first guess at it was wrong by an order
	 * of magnitude.</b> A 3x3x3 room holds nine assemblies of four rods; at two minutes per rod that
	 * came to several hundred uranium ore per hour, and uranium generates about seven times more
	 * rarely than diamond. At twenty minutes a full room costs roughly a hundred ore an hour — still a
	 * serious appetite, which is the point, but one a mining trip can actually feed. Running the
	 * reactor part-loaded or at reduced control-rod depth scales it down proportionally.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks of burn in one uranium fuel rod at full control-rod depth.")
	public static int reactorRodBurnTicks = 24000;
	/** Heat, in thousandths of the scale, added per active rod per tick at full depth. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Heat units added per active rod per tick at full depth.")
	public static int reactorHeatPerRod = 4;
	/**
	 * Extra <em>heat</em> per adjacency, in percent — deliberately larger than
	 * {@link #reactorNeighbourBonusPercent}, which is the energy figure.
	 *
	 * <p>While the two were one number, density was cosmetic. The tier ceiling caps output at 512 EU/t
	 * and both fuel and heat are scaled by the depth the reactor actually needed to reach it, so heat
	 * collapsed to a fixed fraction of output: every core, sparse or packed, ran at exactly the same
	 * temperature for the same power. Splitting them restores the trade the room is built around — a
	 * tight core reaches full power on shallower rods, but runs hotter doing it and therefore drinks
	 * more water.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Extra heat in percent per adjacent loaded reactor column; larger than the energy bonus, which is what makes a dense core hotter for the same power.")
	public static int reactorHeatNeighbourBonusPercent = 40;
	/**
	 * Heat units one millibucket of water carries away as it boils. This is the cooling exchange rate
	 * and therefore the reactor's thirst.
	 *
	 * <p>Heat is bounded above by the tier ceiling — a core at 512 EU/t cannot exceed 546 heat a tick
	 * however large it grows — so the loop's demand tops out at 247 mB/t, which no single
	 * {@link #reactorPortThroughput} inlet can deliver: five feed the water and five more carry the
	 * steam back out. Needing more than one is the point: "hook up an infinite source and forget" was
	 * the thing to design against.
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
	 * Fluid a single reactor inlet passes per tick, in mB. The shell's only crossing point, and on
	 * purpose the tightest one: the pipe itself carries {@link #fluidPipeSegmentBuffer}.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fluid a single reactor inlet passes per tick, in mB.")
	public static int reactorPortThroughput = 50;
	/**
	 * EU a single reactor outlet holds. One tick of HV: a socket, not a battery — the reactor's own
	 * buffer is where energy waits, and a big one here would just be storage the player did not build.
	 */
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
	/**
	 * Heat the shell sheds every tick no matter how cold it is — the floor of the cooling curve.
	 *
	 * <p>Small on purpose. It used to be 30, larger than everything a starter reactor produced, so a
	 * single column sat at exactly 0% forever: the temperature gauge on a working reactor never moved,
	 * which reads as a broken feature rather than as a safe one. See {@link #reactorHeatLossPermille}
	 * for the part that actually shapes the curve.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Heat units that bleed away on their own each tick regardless of temperature.")
	public static int reactorPassiveCooling = 4;
	/**
	 * Extra heat shed per tick as thousandths of the CURRENT temperature — a hot shell loses heat
	 * faster than a warm one, as any real one does.
	 *
	 * <p>This is what gives the reactor an equilibrium instead of a switch. With a flat loss the
	 * temperature had only two outcomes: production below it pinned the gauge at zero, production above
	 * it climbed to the top and stayed. Now every core settles at the temperature where its output and
	 * its losses balance — one column near 15%, two near two thirds, and anything larger climbing past
	 * the warning line and into the coolant loop.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Extra heat shed per tick, in thousandths of the current temperature.")
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
	 * Percentage of the scale the coolant loop holds a reactor at. Deliberately BELOW
	 * {@link #reactorHeatWarnPercent}.
	 *
	 * <p>The loop used to engage at the warning line itself, which meant it parked every reactor
	 * larger than two columns exactly there — a perfectly healthy installation showed an amber gauge
	 * for ever, and amber stopped meaning "look at me". Aiming lower gives the warning colour its job
	 * back: a working loop sits green, and amber now says the coolant is losing.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Percentage of the heat scale the coolant loop holds the reactor at; below the warning threshold on purpose.")
	public static int reactorCoolantTargetPercent = 60;
	/** EU the controller can bank. Sized to a few seconds of full output so the grid can lag behind. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU buffer of the reactor controller.")
	public static int reactorBuffer = 200000;

	// ── MOD-469: the meltdown and the bare reactor ────────────────────────────────────────────────
	/**
	 * Master switch for every block this feature turns into lava — the room's contents on an overheat
	 * AND the scenery around a bare core.
	 *
	 * <p>Neither hazard ever takes the reactor's own parts — shell, racks, controller, button. The racks
	 * are exempt even inside a meltdown: they are crafted around a shielding plate, and a part built to
	 * survive a reactor survives this one (player's call, 2026-08-26). A runaway room therefore eats its
	 * floor, its plumbing and whatever was carried inside.
	 *
	 * <p>Off, both hazards keep their sound, their particles and their status readout and change not a
	 * single block. Deliberately NOT tied to the vanilla {@code mobGriefing} rule: that rule describes
	 * mobs, and a machine quietly obeying it is a surprise to the operator who set it for creepers. One
	 * switch, one meaning.
	 */
	@Knob(section = Section.MACHINES,
			doc = "When true, an overheating sealed room melts its own contents and a working bare reactor melts the scenery around it. false keeps every cue and changes no block.")
	public static boolean reactorMeltdownMeltsBlocks = true;
	/**
	 * How far the bare-mode search may WALK from the controller before it gives up.
	 *
	 * <p><b>A bound on a connectivity walk, not a sphere.</b> The search steps outwards through blocks
	 * that can carry a reaction — fuel racks and the shielding-alloy shell — so a controller only ever
	 * drives racks it is physically joined to. It started life as a radius, and a playtest killed that
	 * immediately: a controller on bare earth lit up from a column standing three blocks away across
	 * open ground, which reads exactly like power teleporting.
	 *
	 * <p>Still WIDER than {@link #reactorBareMeltRadius} on purpose: a long cluster should work end to
	 * end without setting fire to everything that far away in every direction.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Blocks a controller with no sealed room reaches when looking for fuel racks; wider than the melt radius.")
	public static int reactorBareSearchRadius = 8;
	/**
	 * Blocks around EACH charged rack within which the scenery melts. Tighter than the rack search.
	 *
	 * <p>Measured from the racks, not from the controller. A controller stands in a wall, so a sphere
	 * centred on it has the reactor's own (exempt) body filling half of it — a leaky reactor put every
	 * melt on the one side its controller faced and left the ground behind it untouched. The fuel is
	 * what is dangerous, which is also how radiation already models it.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Blocks around a working bare reactor within which the scenery melts.")
	public static int reactorBareMeltRadius = 5;
	/**
	 * Share of the sealed-room figure a bare core keeps, in percent.
	 *
	 * <p>The bare reactor is a real early generator, not a punishment — but it has to be clearly worse
	 * than the room, or nobody would ever build the room.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Share of the sealed-room output a bare reactor keeps, in percent.")
	public static int reactorBarePowerPercent = 40;
	/**
	 * Hard ceiling on a bare core's output, in EU/t, however many rods are piled into it.
	 *
	 * <p>A quarter of the HV ceiling a sealed room can reach. Without it the percentage alone would let
	 * a big enough heap of rods out-earn a properly built reactor, which would make the shell, the
	 * coolant loop and the airlock decoration.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Hard ceiling on a bare reactor's output in EU/t, however many rods are piled into it.")
	public static int reactorBarePowerCap = 128;
	/**
	 * Ticks between melts under a bare core carrying ONE rod. Divided by the rod count, floored by
	 * {@link #reactorBareMeltMinIntervalTicks} — a forgotten rod is a nuisance, a bare station is not.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between melts under a bare reactor carrying one rod; divided by the rod count.")
	public static int reactorBareMeltIntervalTicks = 600;
	/** Shortest gap between two melts however large the cluster grows. Two seconds. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Shortest gap between two melts under a bare reactor however large the cluster grows.")
	public static int reactorBareMeltMinIntervalTicks = 40;
	/**
	 * Ticks between a block being marked for melting and actually turning to lava.
	 *
	 * <p>The warning is POINTED — particles and a hiss at the doomed block itself, not a general mood
	 * over the whole reactor. Two seconds is enough to step off it or to grab what is on it, which is
	 * the entire difference between a hazard and a punishment.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Ticks between a block being marked for melting and turning to lava; the pointed warning the player can act on.")
	public static int reactorMeltWarnTicks = 40;
	/**
	 * Percentage of the heat scale at which a sealed room starts melting its own contents.
	 *
	 * <p>Between {@link #reactorHeatWarnPercent} (70, "look at this") and the top of the scale, which
	 * belongs to MOD-471's explosion. The room is meant to lose its columns and its plumbing here and
	 * keep its shell — that containment is what the walls were built for.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Percentage of the heat scale at which a sealed room starts melting its own contents; between the warning line and the top.")
	public static int reactorMeltdownStartPercent = 85;
	/** Ticks between two blocks of the room's contents melting while the core is over the line. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between two blocks of the room's contents melting while the core is over the meltdown line.")
	public static int reactorMeltdownIntervalTicks = 60;
	/**
	 * Heat carried away by one melted block of the room's contents.
	 *
	 * <p>What makes a meltdown self-limiting: the room eats its own guts and cools as it does, so the
	 * player is left with a wrecked interior inside an intact shell rather than a core pinned at the top
	 * of the scale for ever.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Heat carried away by one melted block of the room's contents; what makes a meltdown self-limiting.")
	public static int reactorMeltdownHeatRelief = 400;

	// ── MOD-471: the accident at the top of the scale ─────────────────────────────────────────────
	/**
	 * Master switch for the accident: the countdown, the blast and everything it leaves behind.
	 *
	 * <p>Off, a core pinned at the top of its scale still sounds, still says so on the panel and still
	 * counts down — and then nothing happens. Same bargain as {@link #reactorMeltdownMeltsBlocks}: the
	 * switch protects the world, not the operator's right to know what their reactor is doing.
	 */
	@Knob(section = Section.MACHINES,
			doc = "When true, a core pinned at the top of its scale counts down and explodes. false keeps the countdown, the siren and the panel and changes no block.")
	public static boolean reactorBlastEnabled = true;
	/**
	 * Shortest countdown between "the gauge is pinned" and the explosion, in ticks.
	 *
	 * <p><b>A range, not a number, and the spread is the feature.</b> A fixed delay becomes a memorised
	 * norm — players learn it once and stop reading the panel. Rolled fresh per accident, the alarm is a
	 * real question every time. Two minutes is the floor because the warning line at
	 * {@link #reactorHeatWarnPercent} is worth less than three seconds on a runaway core: the countdown
	 * is the only warning that can actually be acted on.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Shortest countdown between a pinned gauge and the explosion, in ticks; rolled fresh per accident.")
	public static int reactorBlastCountdownMinTicks = 2400;
	/** Longest countdown, in ticks. Three minutes — time to run back from the far end of a base. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Longest countdown between a pinned gauge and the explosion, in ticks.")
	public static int reactorBlastCountdownMaxTicks = 3600;
	/**
	 * How long the core must stay UNDER a hundred percent before an armed countdown is called off.
	 *
	 * <p><b>This is what makes cancelling cost something.</b> Without it the countdown cleared on the
	 * first tick the gauge left the top — and because heat is clamped at the top, cutting the redstone
	 * for a single tick was enough. A clock with one tick off in twenty ran the reactor at ninety-five
	 * percent duty and never exploded; a player found it within an hour of the feature shipping.
	 *
	 * <p>Five seconds is short enough that every honest fix clears it comfortably — water, the scram
	 * lever and a breached wall all hold the core down for far longer — and long enough that no duty
	 * cycle capable of keeping the gauge pinned can ever finish the window.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "How long the core must stay under a hundred percent before an armed countdown is called off; stops a redstone clock resetting it.")
	public static int reactorBlastReleaseTicks = 100;
	/**
	 * Explosion power of a core carrying no rods at all.
	 *
	 * <p>Under the ~8 a ray needs to break a reactor wall, on purpose: the smallest possible accident
	 * wrecks the room's contents and leaves the shell standing.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Explosion power of a reactor carrying no rods; below what it takes to break a reactor wall.")
	public static int reactorBlastBasePower = 6;
	/**
	 * Power added per TEN rods — integer arithmetic for a fractional slope.
	 *
	 * <p>At the shipped 4 (0.4 per rod) a three-column core reaches 10.8, which a sealed room contains
	 * completely; twelve columns reach 25.2, which is where the containment starts to leak.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Explosion power added per ten rods burning when the countdown ran out.")
	public static int reactorBlastPowerPerTenRods = 4;
	/**
	 * Hard ceiling on explosion power.
	 *
	 * <p>For scale: TNT is 4, a charged creeper 6. A reactor shell absorbs 28–37 power per cell of
	 * wall, so everything up to about 24 is held by the room entirely and 45 throws debris twenty-odd
	 * blocks past it. Raising this is not free — the blast traces 1352 rays that force-load, and on an
	 * unexplored border generate, every chunk they cross.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Hard ceiling on explosion power; a sealed room contains everything up to about 24.")
	public static int reactorBlastMaxPower = 45;
	/** Whether the blast sets fire to what it touches, like TNT lit in the Nether does. */
	@Knob(section = Section.MACHINES,
			doc = "Whether the blast sets fire to what it touches.")
	public static boolean reactorBlastFire = true;
	/**
	 * Lava sources poured into the crater, at most.
	 *
	 * <p>Placed only in cells the explosion itself destroyed — see {@code ReactorBlast}. That rule is
	 * what keeps the aftermath from outrunning a land-claim mod that blocked the blast.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Lava sources poured into the crater, only into cells the explosion itself destroyed.")
	public static int reactorBlastLavaCells = 6;
	/**
	 * Instability a bare reactor gains per rod per tick (MOD-471).
	 *
	 * <p><b>This is the lava farm's speed limit, expressed as a rack count.</b> Against
	 * {@link #reactorBareSettlePermille} it gives an equilibrium of
	 * {@code rods x perRod x 1000 / permille}: on the shipped pair one rack settles at 30 % of the
	 * scale, two at 60, three at 90 — all stable for ever — and four have no equilibrium below the
	 * ceiling at all, so they run away and blow up. Players farm lava on three racks and lose the farm
	 * on the fourth, which is a limit they can see on the panel instead of reading in a wiki.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Instability a bare reactor gains per rod per tick; against the settle rate this sets how many racks a lava farm may carry.")
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
	 * Dose one fallout block delivers per radiation sweep, before distance and shielding.
	 *
	 * <p>Must clear {@link #radiationTickInterval} (20) or it does literally nothing: the dose is the
	 * remaining duration of an effect vanilla ticks down every tick, so anything at or under the
	 * interval is cancelled out by its own decay before the next sweep.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Dose one fallout block delivers per radiation sweep; must exceed radiationTickInterval or its own decay cancels it.")
	public static int reactorFalloutDosePerBlock = 30;
	/**
	 * How many fallout blocks one player ever counts, however large the scar.
	 *
	 * <p>Without it strength is linear in the block count and a crater is instantly lethal from its far
	 * edge — the same trap {@link #radiationContainerMaxItems} exists to close.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "How many fallout blocks one player ever counts, however large the scar.")
	public static int reactorFalloutMaxBlocksCounted = 8;
	/**
	 * Percent chance per random tick that a fallout block fades one step. Water on top doubles it.
	 *
	 * <p>Four steps at this rate is on the order of a Minecraft day of real decay for an untended
	 * crater, and a few minutes for one somebody bothered to flood. Contamination that never lifts
	 * would be a permanent hole in a player's world; contamination that lifts instantly is scenery.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Percent chance per random tick that a fallout block fades one step; water on top doubles it.")
	public static int reactorFalloutDecayChancePercent = 20;

	// ── MOD-470: radiation, dose and the shielding suit ───────────────────────────────────────────
	/**
	 * Master switch. Off, nothing irradiates anything — the "never break a player's world" rule
	 * applies to a hazard that can kill in a world built before it existed.
	 */
	@Knob(section = Section.SAFETY,
			doc = "When true, uranium and fuelled reactor rods irradiate players. false disables the entire mechanic, including suit wear.")
	public static boolean radiationEnabled = true;
	/**
	 * Depth of the dose scale, in ticks. Also the recovery time: dose is stored as the remaining
	 * duration of the radiation effect, so a player at the top of the scale is clean five minutes
	 * after walking away — and one who keeps walking into the room never gets there.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Depth of the radiation dose scale in ticks; also the time a player at the top of the scale needs to recover once clear of every source.")
	public static int radiationDoseCapacity = 6000;
	/**
	 * Ticks between exposure sweeps. Every tick would be honest and pointless; this is one per second.
	 *
	 * <p><b>This number is also the decay rate, and that is a trap worth naming.</b> The dose lives in
	 * the duration of the radiation effect, which vanilla ticks down once per tick — so between two
	 * sweeps a dose loses exactly {@code radiationTickInterval} of itself. A source contributing LESS
	 * than that per sweep therefore does nothing at all: the dose sits at zero for ever while the
	 * symptoms still fire, which is precisely what the second playtest reported (a villager took damage
	 * from a thrown ingot and never transformed, because one ingot added 20 against a decay of 20).
	 * Every per-source number below must clear this bar with room to spare.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between radiation exposure sweeps per player.")
	public static int radiationTickInterval = 20;
	/**
	 * Dose per sweep from a fuelled rod within {@link #radiationSourceRadius}, before shielding.
	 * Sized so an unprotected player at the rods crosses the lethal line in a handful of seconds:
	 * 900 per second fills the 6000-tick scale in under seven.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep from a fuelled reactor rod in line of sight, before shielding.")
	public static int radiationRodDosePerTick = 900;
	/**
	 * How far a rod reaches, in blocks — a hard edge, with the strength falling off as the square of
	 * the distance on the way there ({@code RadiationCore.attenuate}, half strength at 1.5 blocks).
	 *
	 * <p>There is no second, larger radius for "a reactor with no shell": the shell blocks the line of
	 * sight, and that one mechanism covers both cases (see {@code RadiationSources}).
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Blocks a fuelled rod irradiates through open air; a shell block in the way stops it entirely.")
	public static int radiationSourceRadius = 6;
	/**
	 * Dose per sweep per item of {@code #radioactive_low} — ore, dust, concentrate, depleted rods.
	 * Above {@link #radiationTickInterval} so a single piece of ore actually registers, but barely: it
	 * is also held under {@link #radiationLowDoseCapPercent}, so raw material never stops at more than
	 * queasiness.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep per carried item of tag radioactive_low (ore, dust, shavings, depleted rods).")
	public static int radiationDoseLowPerItem = 24;
	/** Dose per sweep per item of {@code #radioactive_medium} — uranium ingots and plates. */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep per carried item of tag radioactive_medium (uranium ingots and plates).")
	public static int radiationDoseMediumPerItem = 30;
	/**
	 * Dose per sweep per item of {@code #radioactive_high} — refined uranium, isotopes, fuel rods.
	 * Net of decay this is 60 a sweep, so one loose rod fills the scale in about a minute and a half and
	 * a stack does it at once: slow enough that the death reads as the player's own mistake, fast enough
	 * that carrying fuel loose is not an option.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep per carried item of tag radioactive_high (refined uranium, isotopes, fuel rods). Uncapped: a loose stack kills.")
	public static int radiationDoseHighPerItem = 80;
	/**
	 * Ceiling, in percent of the scale, that {@code #radioactive_low} alone may push a dose to.
	 * Deliberately inside level I: raw ore makes a miner queasy and never kills them. The medium and
	 * high tiers have no ceiling.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Percent of the dose scale that tag radioactive_low alone can reach; inside level I on purpose, so raw ore sickens but never kills.")
	public static int radiationLowDoseCapPercent = 20;
	/** Percent of a dose each worn shielding piece cuts. Four pieces = 100 % of ordinary exposure. */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Percent of incoming dose each worn shielding-suit piece blocks; four pieces block all ordinary exposure.")
	public static int radiationShieldPerPiecePercent = 25;
	/**
	 * Hard cap on shielding against a bare rod in the open, in percent. Below 100 on purpose: a full
	 * suit buys working time inside a live reactor, not immunity to it. Anything else turns the suit
	 * into a switch that ends the mechanic.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Ceiling on suit protection against a rod in line of sight; below 100 on purpose, so a full suit buys working time inside a live reactor rather than immunity.")
	public static int radiationRodShieldCapPercent = 95;
	/** Blocks around the player in which dropped radioactive items are counted. */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Blocks around the player in which dropped radioactive items are counted as a source.")
	public static int radiationGroundRadius = 6;
	/**
	 * How deep to look inside containers carried in the inventory. 1 = a shulker box of fuel rods
	 * irradiates its carrier, a shulker inside a shulker does not. Zero would make any container a free
	 * shield and the shielding chest pointless.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "How deep to look inside carried containers for radioactive contents; 1 = a shulker box of fuel rods irradiates its carrier.")
	public static int radiationContainerDepth = 1;
	/**
	 * How many items' worth of radiation ONE container in the world may leak, no matter how much is
	 * inside it (MOD-474). Multiplied by {@link #radiationDoseHighPerItem}; 0 stops containers
	 * radiating at all.
	 *
	 * <p><b>Without this the feature was a trap, not a hazard.</b> Strength was linear in the count,
	 * so a chest holding one stack of refined uranium killed an unprotected player in two seconds and
	 * a full one killed instantly anywhere in the radius — including through the radius, in a world
	 * that had stored uranium safely the day before the update. That also revives the death loop
	 * MOD-470 deliberately closed: its arithmetic ("a stack needs about a minute, so there is time to
	 * run back, grab your things and drink milk") assumed a minute, not a second.
	 *
	 * <p>Capping the leak is also the physically honest model: the box walls attenuate, and uranium
	 * buried in the middle of a pile is shielded by the uranium on top of it. At the shipped value a
	 * container is felt immediately (nausea within a second at point blank) and lethal only to someone
	 * who stands next to it for half a minute — a bad decision the player had time to undo, which is
	 * the rule MOD-470 set for every radiation death.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "How many items' worth of radiation one container in the world may leak regardless of how much it holds; 0 stops containers radiating entirely. Without a cap a chest of refined uranium killed instantly across the whole radius.")
	public static int radiationContainerMaxItems = 4;
	/** Ticks between re-applying the visible symptoms (nausea, weakness, hunger). */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between re-applying the visible radiation symptoms (nausea, weakness, hunger).")
	public static int radiationSymptomIntervalTicks = 40;
	/** Ticks between hits at dose level II / III / IV. */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between radiation hits at dose level II.")
	public static int radiationDamageIntervalLevel2 = 160;
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between radiation hits at dose level III.")
	public static int radiationDamageIntervalLevel3 = 100;
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between radiation hits at the top of the dose scale.")
	public static int radiationDamageIntervalLevel4 = 40;
	/** Damage per hit below the lethal line, in half-hearts. */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage per radiation hit below the lethal line, in half-hearts.")
	public static float radiationDamageSick = 1.0f;
	/**
	 * Damage per hit at the top of the scale. Halved and slowed after the first playtest (MOD-470):
	 * at 2 half-hearts every 2 s a healthy player has about twenty seconds at the top of the scale —
	 * enough to run for the door, which is the point. Death by radiation should be the end of a bad
	 * decision the player had time to reverse, not a stumble into a kill box.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage per radiation hit at the top of the dose scale, in half-hearts.")
	public static float radiationDamageLethal = 2.0f;
	/**
	 * Absorbed dose worth one point of suit durability — <b>at most one point per piece per sweep</b>,
	 * however fierce the source, and never zero while the suit is stopping anything at all.
	 *
	 * <p>It sets the PACE rather than the amount. Charging a point per this much absorbed dose came to
	 * seventeen points a second beside a single four-rod column and destroyed the helmet in ten seconds;
	 * a suit that cannot survive walking past the thing it exists for is not a suit. Making it a plain
	 * threshold went too far the other way — the suit wore only under a fierce field, so carrying
	 * uranium cost nothing at all. Now it is a ratio turned into an interval
	 * ({@code RadiationCore.wearInterval}): a live core spends a point a second, one refined-uranium
	 * item every two, a piece of ore every eight. Suit life is a TIME in every case.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Dose the shielding suit absorbs per point of durability spent; the suit is a consumable, not a permanent answer.")
	public static int radiationDosePerSuitDurability = 200;
	/**
	 * Whether radiation touches villagers, wandering traders and cows at all (MOD-470). This is the
	 * ONE exception to "mobs are never irradiated": nothing dies of it, the list is closed, and the
	 * sweep only runs where a player already stands in a radiation field. Off, the three
	 * transformations simply never happen.
	 */
	@Knob(section = Section.SAFETY,
			doc = "When true, radiation turns villagers and wandering traders into zombie villagers and cows into mooshrooms. false disables all three transformations.")
	public static boolean radiationMobsEnabled = true;
	/**
	 * Percent of the dose scale at which a villager becomes a zombie villager and a cow becomes a
	 * mooshroom. Below 100 so the transformation lands while the player is still watching, rather than
	 * after a full scale of standing around — half the scale is two loose rods for half a minute.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Percent of the dose scale at which an irradiated villager or cow transforms.")
	public static int radiationMobConvertPercent = 50;
	/** Ticks between the light hits a sickening villager takes. A cow takes none — it just changes. */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between the light hits an irradiated villager takes before it transforms.")
	public static int radiationMobDamageIntervalTicks = 60;
	/**
	 * EU/t a spun-up centrifuge spends with nothing to process — the cost of holding revolutions instead
	 * of coasting to a stop. Small on purpose: it should read as "standing by", not as a penalty for
	 * leaving the lever on. Below this, the rotor sheds speed at half the rate it gained it.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t a spun-up thermal centrifuge spends holding revolutions with nothing to process.")
	public static int thermalCentrifugeIdleEuPerTick = 1;
	/** Ticks the canning machine spends pressing one ration; × machineEuPerTick = 200 EU per ration. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the canning machine takes per ration at 1.0 speed; x machineEuPerTick 2 = 200 EU per ration.")
	public static int canningMachineDuration = 100;
	/**
	 * Food value, in tenths of a point, that one ration costs (MOD-383). Must stay above the ration's
	 * own value of 96 — that gap is the processing loss, and it is the only thing standing between
	 * this machine and a food duplicator.
	 */
	@Knob(section = Section.MACHINES, min = 97,
			doc = "Food value in tenths (nutrition + saturation) the canning machine consumes per ration. Must exceed the ration's own 96, or canning becomes a food duplicator.")
	public static int canningFoodValuePerCan = 120;
	/** Distillation Column (MOD-251): fallback ticks per distillation at 1.0 speed. The shipped recipe
	 * costs 400 EU, so at the ordinary-machine rate of 2 EU/t one run takes 200 ticks — deliberately
	 * the Polymerizer's exact tier: both turn one bucket of pumped crude into product. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fallback ticks one distillation takes at 1.0 speed; shipped recipe energy 400 / machineEuPerTick 2 = 200.")
	public static int distillationColumnDuration = 200;
	/** Distillation Column (MOD-251): ticks a cold column heats before it can distil. It draws the
	 * ordinary machine rate while heating, so one cold start costs ~warmup × machineEuPerTick EU
	 * (~400 EU at defaults — one distillation's worth). Cooling runs at half this rate. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a cold distillation column heats (at machineEuPerTick) before it can distil; cooling is twice as slow.")
	public static int distillationColumnWarmupTicks = 200;
	/** Galvanic Bath (MOD-127): fallback ticks per operation at 1.0 speed. The shipped recipe costs
	 * 1000 EU, so at the ordinary-machine rate of 2 EU/t the operation takes 500 ticks (25 s) — by far
	 * the slowest of the LV processing family, because plating silver onto fibre is the gate into the
	 * Fluxweave line and its armour. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fallback ticks a galvanic bath operation takes at 1.0 speed; shipped recipe energy 1000 / machineEuPerTick 2 = 500.")
	public static int galvanicBathDuration = 500;

	// --- The organic chain (MOD-146 / MOD-525): fermenter → biofuel → nutrient solution → sprinkler. ---
	/**
	 * Fermenter (MOD-146): fallback ticks per batch at 1.0 speed. The shipped recipes cost 600 EU, so
	 * at the ordinary-machine rate of 2 EU/t one batch takes 300 ticks (15 s). Slow for an LV machine,
	 * but not so slow that a bucket of biofuel out of garden waste becomes an evening's project: at
	 * the poor tier that is 50 batches, and a batch has to be watchable to be worth watching.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fallback ticks one fermenter batch takes at 1.0 speed; shipped recipe energy 600 / machineEuPerTick 2 = 300.")
	public static int fermenterDuration = 300;
	/**
	 * mB of water one fermenter batch drinks. Not a recipe field: no recipe family in this mod mixes
	 * items and fluids on one side, so — exactly like {@link #galvanicBathWaterPerOp} — the water is a
	 * fixed cost of the machine. A bucket therefore covers ten batches.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of water a fermenter batch consumes (not part of the recipe JSON).")
	public static int fermenterWaterPerOp = 100;
	/**
	 * mB of biofuel one batch of the CHEAPEST organic tier brews — seeds, grass, leaves, rot. The
	 * fluid output is the machine's, not the recipe's, for the same reason the water cost is (no
	 * recipe family here mixes items and fluids), so the tier is read from the input's tag.
	 *
	 * <p><b>The three tiers are the whole economy of the machine.</b> Every batch costs the same 600
	 * EU and the same 15 seconds, so what a player feeds it is the only lever they have: four pieces
	 * of waste for 20 mB, or one golden carrot for 150. Deliberately in tens rather than hundreds —
	 * a bucket is 1000 mB, and the cheap tier should take a while to fill one.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of biofuel a batch of the cheapest organic tier brews (seeds, grass, leaves, rot).")
	public static int fermenterBiofuelPoor = 20;
	/** mB per batch of ordinary harvest — wheat, carrots, melon slices, raw meat. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of biofuel a batch of ordinary harvest brews (wheat, carrots, melon slices, raw meat).")
	public static int fermenterBiofuelCommon = 60;
	/** mB per batch of processed or dense feedstock — golden carrots, cooked food, hay blocks. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of biofuel a batch of processed or dense feedstock brews (golden carrots, cooked food, hay blocks).")
	public static int fermenterBiofuelRich = 150;
	/**
	 * Sprinkler (MOD-525): radius in blocks it sprays. Four, matching {@link #gardenDroneRange} — the
	 * two blocks are meant to sit on the same 9×9 plot, one tending it and one speeding it up.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Sprinkler spray radius in blocks around the block.")
	public static int sprinklerRange = 4;
	/**
	 * Ticks between sprinkler attempts. Matches {@link #gardenDroneScanIntervalTicks} — the two blocks
	 * work the same plot, and a field block that acts five times more slowly than its neighbour reads
	 * as broken rather than as balanced.
	 *
	 * <p>It deliberately does NOT match {@link #crystalFarmGrowthIntervalTicks} any more. It once did,
	 * on the reasoning that a greenhouse and a field should be watered at one visible cadence — but a
	 * greenhouse never used this number at all: indoors the controller runs the growth roll on its own
	 * timer and only asks the sprinkler to pay. So the tie bought nothing and cost the field a 5×
	 * slower crop, which is exactly how it played.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between sprinkler spray attempts.")
	public static int sprinklerIntervalTicks = 20;
	/** mB of nutrient solution one successful spray on a vanilla crop costs. A bucket is 20 sprays. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of nutrient solution one successful spray on a vanilla crop costs.")
	public static int sprinklerSolutionPerActionMb = 50;
	/** Sprinkler tank size, in mB. Also its intake rate ceiling — a FluidTank has no separate rate. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Sprinkler tank size in mB; also caps how fast a pipe can fill it.")
	public static int sprinklerTankMb = 4000;

	// --- Overclocker chip (MOD-392): the per-chip speed/energy trade, applied per machine. ---
	/**
	 * Duration multiplier per installed overclocker chip: each chip shortens one operation to 80 % of
	 * its previous length. Deliberately paired with a HARSHER {@link #overclockerEuFactor}, so the
	 * chip is not a re-skin of {@link #globalMachineSpeedMultiplier} — that knob is energy-neutral by
	 * design (EU/t up, duration down, E_op unchanged), whereas a chip must make speed genuinely
	 * expensive: 0.8 × 2.0 means every chip raises the energy PER OPERATION by 60 %.
	 */
	@Knob(section = Section.MACHINES, min = 0.0, exclusive = true,
			doc = "MOD-392: duration multiplier per overclocker chip (0.8 = each chip cuts one operation to 80% of its length).")
	public static float overclockerSpeedFactor = 0.8f;
	/**
	 * EU/t multiplier per installed overclocker chip — each chip doubles the draw. Together with
	 * {@link #overclockerSpeedFactor} one chip buys 1.25× speed for 1.6× the energy per operation, and
	 * four chips buy 2.46× speed for 6.55×. Doubling is also what makes the tier ceiling bite at a
	 * round number: a 2 EU/t machine reaches exactly {@code tierLvVoltage} (32) on its fourth chip.
	 */
	@Knob(section = Section.MACHINES, min = 1.0, exclusive = true,
			doc = "MOD-392: EU/t multiplier per overclocker chip (2.0 = each chip doubles the draw). With 0.8 speed this makes every chip cost 60% more energy per operation.")
	public static float overclockerEuFactor = 2.0f;
	/**
	 * Highest overclocker tier that exists — three chips are crafted (I, II, III), so three steps.
	 * On top of this sits the per-machine tier cap, and that is usually the one that bites: a machine
	 * may run no more steps than its voltage tier can actually feed
	 * ({@code base × euFactor^n ≤ tier.maxVoltage()}), so an 8 EU/t LV machine stops at II even holding
	 * a tier-III chip, while a 2 EU/t one runs all three.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "MOD-392: absolute ceiling on overclocker chips in one machine; the tier cap (base EU/t x factor^n <= tier voltage) usually bites first.")
	public static int overclockerMaxPerMachine = 3;

	// --- Energy condenser (MOD-393): surplus grid power banked, then packed into an item. ---
	/**
	 * Ceiling of the condenser's bank. Equals the tier-III threshold on purpose: past it there is
	 * nothing left to reach, so the block stops drawing instead of hoarding energy no one can spend.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-393: ceiling of the energy condenser's bank; equals the tier-III clot threshold, so it stops drawing once nothing higher is reachable.")
	public static int condenserCapacity = 4_000_000;
	/**
	 * EU/t the condenser will accept — one MV packet ({@link #tierMvVoltage}), so a farm of ~32 basic
	 * solar panels saturates exactly one condenser and the ratio is something a player can eyeball.
	 *
	 * <p>It was copper scale (12) at first, on the belief that a narrow intake was the only thing
	 * keeping the condenser from outrunning a machine sitting further from the generator. That was
	 * simply wrong: {@code EnergyNetwork#tick} serves the two consumer classes in two separate passes,
	 * machines first, so a machine drinks from the line before any storage sink is offered a drop. The
	 * intake never was the guard — it was only a throttle, and at 12 EU/t it throttled the mechanic
	 * itself: an 85-panel farm producing 1580 EU/t still banked 12, so building more generation — the
	 * one thing this block is supposed to reward — changed nothing at all.
	 *
	 * <p>Scaling therefore belongs to the BANK of condensers, not to a hidden cap inside one: absorbing
	 * a bigger surplus means placing more of them.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-393: EU/t the energy condenser accepts — one MV packet, about 32 basic solar panels. Machines are protected by the network's serve order, not by this number; absorbing a bigger surplus means placing more condensers.")
	public static int condenserInputRate = 128;
	/** Banked EU at which the condenser can yield a tier-I clot; below this its output stays empty. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-393: banked EU needed before the condenser can yield a tier-I energy clot.")
	public static int clotThresholdI = 250_000;
	/** Banked EU for a tier-II clot — four times tier I. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-393: banked EU for a tier-II clot (four times tier I).")
	public static int clotThresholdII = 1_000_000;
	/** Banked EU for a tier-III clot — four times tier II, and the bank's ceiling. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-393: banked EU for a tier-III clot (four times tier II).")
	public static int clotThresholdIII = 4_000_000;

	// --- MOD-064 alloy smelter. Its own rate, like the incubator and the assembler: melting several
	// metals into one is a hotter job than milling a single ore. 8 EU/t x 150 ticks = 1200 EU per
	// operation (7.5 s) for every alloy — one price across the family, so the four alloys differ by what
	// they consume and yield, not by what the machine charges. 8 EU/t is exactly a coal generator's
	// output and 2/3 of a copper cable's throughput, so one smelter occupies a starter line by itself.
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t the alloy smelter draws while running (MOD-064). Four times the machine standard, like the incubator.")
	public static int alloySmelterEuPerTick = 8;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Fallback ticks one alloying operation takes at 1.0 speed (MOD-064); shipped recipe energy 1200 / alloySmelterEuPerTick 8 = 150.")
	public static int alloySmelterDuration = 150;

	// --- MOD-384 component repair bench. Restores a worn rotor/wheel instead of replacing it, at the
	// price of a permanently lower durability ceiling. Its own rate, like the alloy smelter above: at the
	// shared 2 EU/t a T3 repair would run 14 400 ticks (12 minutes) and read as broken rather than
	// expensive. At 8 EU/t the three grades take 1200 / 2400 / 3600 ticks — exactly 60 / 120 / 180 s
	// (MOD-465 raised these from 625/1250/2250: half a minute for a T1 repair read as an errand rather
	// than as a job, and the bench was finished before the player had walked back to it).
	/** EU/t the repair bench draws while repairing. Four times the machine standard. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t the component repair bench draws while repairing (MOD-384). Four times the machine standard, like the alloy smelter.")
	public static int repairBenchEuPerTick = 8;
	/**
	 * EU one repair of a T1 component costs (plain {@code windmill_rotor} / {@code water_mill_wheel}).
	 * The material side is deliberately cheap — ONE plate — so energy, not metal, is what a repair
	 * really spends; see {@code docs/PERFORMANCE.md} for the full economy.
	 *
	 * <p>9600 rather than a rounder 10 000 so the figure divides exactly by {@link #repairBenchEuPerTick}
	 * into a whole minute (1200 ticks). The grade ladder below is then a clean ×2 / ×3 of it.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-384: EU one repair of a T1 rotor/wheel costs (material: 1 iron plate). 9600 EU / 8 EU-t = 1200 ticks (60 s).")
	public static int repairBenchTier1EuCost = 9600;
	/** EU one repair of a reinforced (T2) component costs — double T1 (2400 ticks, 2 min). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-384: EU one repair of a reinforced rotor/wheel costs (material: 1 tempered iron plate). 19200 EU / 8 EU-t = 2400 ticks (120 s).")
	public static int repairBenchTier2EuCost = 19200;
	/** EU one repair of an advanced (T3) component costs — triple T1 (3600 ticks, 3 min). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-384: EU one repair of an advanced rotor/wheel costs (material: 1 electronic circuit). 28800 EU / 8 EU-t = 3600 ticks (180 s).")
	public static int repairBenchTier3EuCost = 28800;
	/**
	 * How much of the component's ORIGINAL durability ceiling one repair burns, in percent. Linear in
	 * the original, not compounding on the current value: at 20 % that is 1000 → 800 → 600 → 400 → 200.
	 * Integer percent keeps it exact for every grade (see {@code core.machine.ComponentRepair}).
	 *
	 * <p><b>This single knob also sets how many repairs a component gets</b> — four here, because a
	 * fifth would leave nothing to restore. There is deliberately no separate count: a second knob
	 * could disagree with this one, and a bench refusing a part that still had most of its ceiling
	 * read as an arbitrary rule rather than as wear. 0 disables the decay, which also makes repairs
	 * unlimited.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "MOD-384: percent of the ORIGINAL durability ceiling one repair burns, linear (1000 -> 800 -> 600 -> 400). Also sets how many repairs a part gets (four at 20%). 0 disables the decay and the limit with it.")
	public static int repairBenchMaxDamageDecayPercent = 20;

	// --- MOD-275 assembler. The first MV machine: six times the LV rate, but a short operation.
	// 12 EU/t x 40 ticks = 480 EU per craft — dearer than crafting by hand, cheaper than a processing
	// step, so the machine buys time rather than resources. Raised from 8 EU/t after the playtest:
	// automation was reading as too cheap for what it removes. The buffer follows the rate so it still
	// holds 25 operations.
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/tick the assembler draws while crafting (MOD-275). MV rate: six times an LV machine.")
	public static int assemblerEuPerTick = 12;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks one assembler craft takes at 1.0 speed (MOD-275). 40 = 2 seconds, the pace of the genre.")
	public static int assemblerDuration = 40;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU buffer of the assembler (MOD-275) — 25 operations at 480 EU each.")
	public static int assemblerBuffer = 12000;
	/** Galvanic Bath (MOD-127): mB of water one operation consumes from the internal tank. Deliberately
	 * NOT part of the recipe JSON (no recipe family in the mod takes items and a fluid at once — see
	 * GalvanicBathBlockEntity), so this is the one knob for the water price. 4000 mB = four buckets per
	 * thread: the bath is meant to be thirsty, so a full 10-bucket tank yields only two threads and the
	 * player is pushed to pipe water in from a pump rather than carry it. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of water a galvanic bath consumes per completed operation (not part of the recipe JSON).")
	public static int galvanicBathWaterPerOp = 4000;
	/**
	 * Electric Heater (MOD-258): EU/t it spends, both while warming up and while a Vulcanizer directly
	 * above it advances. Three times the ordinary machine tariff on purpose — heat is the block's whole
	 * product, and at the old 2 EU/t it was cheaper than the machine it served, which read as free.
	 *
	 * <p>The pair therefore draws {@code 6 + 2 = 8 EU/t}, still inside a copper cable's 12; the first
	 * overclocker chip is what pushes it onto gold, which is the intended progression rather than an
	 * accident.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t an Electric Heater spends while the Vulcanizer directly above it advances; idle heater draws nothing.")
	public static int electricHeaterEuPerTick = 6;
	/**
	 * Electric Heater (MOD-418): ticks a cold heater spends warming before it is a heat source at all.
	 *
	 * <p>Until it is hot it supplies NOTHING, so the machine above simply waits — the heater is a stove
	 * being lit, not a dial. Warming is its own idle draw at {@link #electricHeaterEuPerTick}; the block
	 * still costs nothing when there is no work waiting on it, because it only warms while a machine
	 * above is actually blocked on heat. Cooling runs at half this rate, so a brief pause in the input
	 * feed costs a slice of the ramp rather than all of it.
	 *
	 * <p>200 keeps the arithmetic memorable: warming costs 1200 EU, exactly what one vulcanization costs,
	 * so {@link #electricHeaterBuffer} is one cold start plus one batch.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-418: paid heat ticks a cold Electric Heater needs before it supplies tier-3 heat (x3 output); until then it supplies tier 2 (x2). Cooling is twice as slow.")
	public static int electricHeaterWarmupTicks = 200;

	// --- Incubator (MOD-118): the mod's most energy-hungry LV machine. ---
	/**
	 * EU/t the incubator draws while running — four times the machine standard. Irradiating matter is
	 * deliberately far pricier than grinding it: a routine macerator op costs 300 EU, the cheapest
	 * incubator op costs 2400.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t the incubator draws while running (4x the machine standard).")
	public static int incubatorEuPerTick = 8;
	/** Internal EU buffer; holds the costliest operation (create, 8000 EU) in full. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Incubator internal EU buffer.")
	public static int incubatorBuffer = 8000;
	/** Ticks per transform attempt at 1.0 speed (300 x 8 EU/t = 2400 EU). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an incubator transform attempt takes at 1.0 speed.")
	public static int mutationDurationTransform = 300;
	/** Ticks per duplicate attempt at 1.0 speed (500 x 8 EU/t = 4000 EU). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an incubator duplicate attempt takes at 1.0 speed.")
	public static int mutationDurationDuplicate = 500;
	/** Ticks per create attempt at 1.0 speed (1000 x 8 EU/t = 8000 EU). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an incubator create attempt takes at 1.0 speed.")
	public static int mutationDurationCreate = 1000;
	/** Uranium ingots are spent as a charge: one ingot powers this many attempts, then becomes ash. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mutation attempts one uranium ingot powers before it burns to ash.")
	public static int mutationAttemptsPerIngot = 3;
	/** Base success chance of a transform mutation. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Base success chance of a transform mutation (0..1).")
	public static double mutationChanceTransform = 0.75;
	/** Base success chance of a duplicate mutation. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Base success chance of a duplicate mutation (0..1).")
	public static double mutationChanceDuplicate = 0.45;
	/** Base success chance of a create mutation. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Base success chance of a create mutation (0..1).")
	public static double mutationChanceCreate = 0.25;
	/** Ceiling on the total success chance (base + gene bonus) — a mutation is never guaranteed. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Ceiling on the total mutation success chance (base + gene bonus).")
	public static double mutationChanceCap = 0.95;
	/** Share of attempts that yield irradiated slag; carved out of the failure share, not the success. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Share of attempts yielding irradiated slag instead of an empty miss.")
	public static double mutationSlagChance = 0.05;
	/** Share of successes that roll the rare grade. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Share of successful mutations rolling the rare grade.")
	public static double mutationGradeRare = 0.20;
	/** Share of successes that roll the epic grade. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Share of successful mutations rolling the epic grade.")
	public static double mutationGradeEpic = 0.08;
	/** Share of successes that roll the legendary grade. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Share of successful mutations rolling the legendary grade.")
	public static double mutationGradeLegendary = 0.02;

	// --- Garden Drone Station (MOD-277): zone-scan farm caretaker, one BER-drawn drone per station. ---
	/** Internal EU buffer. Sized like {@link #pumpBuffer}/{@link #magnetBuffer} — a modest LV
	 * reservoir for a per-action (not per-tick) consumer. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Garden Drone station internal EU buffer.")
	public static int gardenDroneBuffer = 4000;
	/** EU spent per completed action (till / plant / fertilize / harvest). Demand-driven: an idle
	 * station (nothing to do) spends nothing, same pattern as {@link #electricHeaterEuPerTick}. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU the Garden Drone spends per completed action (till/plant/fertilize/harvest); idle costs nothing.")
	public static int gardenDroneEuPerAction = 8;
	/**
	 * Scan radius in blocks around the station.
	 *
	 * <p>Four, not the nine an edge-placed 9x9 plot would need: this is the tier-1 drone, and a machine
	 * that tends a 9-wide field the moment it is crafted leaves nothing for a later tier to improve.
	 * A radius-4 zone is a comfortable plot around the dock and keeps the flights short enough to watch.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Garden Drone zone-scan radius in blocks around the station.")
	public static int gardenDroneRange = 4;
	/** Ticks between zone re-scans; same cadence as {@link #pumpScanCooldownTicks}. The scan result
	 * is cached and invalidated by block updates inside the zone, so this interval only bounds the
	 * cost of a full rebuild after cache invalidation, not every tick's work. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between Garden Drone zone rebuilds after cache invalidation.")
	public static int gardenDroneScanIntervalTicks = 20;
	/**
	 * Ticks the drone spends flying per block of distance to its target. The action lands when the
	 * drone arrives, not when the target is chosen — without this the whole farm is tended in a couple
	 * of ticks, which reads as teleportation rather than as a machine doing work.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Ticks the Garden Drone flies per block of distance before its action lands.")
	public static int gardenDroneFlightTicksPerBlock = 11;

	// --- Mob Repeller (MOD-278): tiered guard field that expels hostile mobs for EU. ---
	/** Zone radius in blocks around the LV block (a cube, matching the highlight dome). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller LV zone radius in blocks (a cube around the block).")
	public static int mobRepellerRange = 8;
	/** Zone radius of the MV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller MV zone radius in blocks.")
	public static int mobRepellerRangeMv = 16;
	/** Zone radius of the HV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller HV zone radius in blocks.")
	public static int mobRepellerRangeHv = 24;
	/**
	 * Field upkeep in EU/t while enabled — a constant drain, NOT a per-expulsion tariff. The field is
	 * the useful work (a base stays clean whether or not a mob wanders in tonight), so unlike the
	 * demand-driven heater this block pays around the clock; at 0 EU the field goes dark.
	 *
	 * <p>8 EU/t on the LV tier is four times an ordinary processing machine ({@link #machineEuPerTick}
	 * = 2) and eight times a bare solar panel: protection is meant to cost real generation — a fuel
	 * generator's whole output, or eight panels — rather than being a torch substitute you bolt on and
	 * forget. Retuned upward after the first playtest, where the original 2 EU/t read as free.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller LV field upkeep in EU per tick while enabled (constant drain, not per expulsion).")
	public static int mobRepellerEuPerTick = 8;
	/** MV field upkeep — ×4 of LV, the same step the voltage ladder takes. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller MV field upkeep in EU per tick.")
	public static int mobRepellerEuPerTickMv = 32;
	/**
	 * HV field upkeep — ×2 of MV rather than ×4. The top tier already demands an HV line and 32 000 EU
	 * of buffer; another fourfold jump (128 EU/t) would put it past a geothermal pair for a radius
	 * that is only half again as wide, and the tier would stop being worth reaching.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller HV field upkeep in EU per tick.")
	public static int mobRepellerEuPerTickHv = 64;
	/** Internal EU buffer of the LV block. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller LV internal EU buffer.")
	public static int mobRepellerBuffer = 2000;
	/** Internal EU buffer of the MV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller MV internal EU buffer.")
	public static int mobRepellerBufferMv = 8000;
	/** Internal EU buffer of the HV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller HV internal EU buffer.")
	public static int mobRepellerBufferHv = 32000;
	/** Ticks between zone sweeps. Ten (half a second) keeps the boundary dance tight without paying
	 * the entity scan every tick; the upkeep drain still applies every tick regardless. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between Mob Repeller zone sweeps (the upkeep drain still applies every tick).")
	public static int mobRepellerScanIntervalTicks = 10;
	/** Personal hostile kills a Soul Vessel must hold to evolve the LV block into the MV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Personal hostile kills a Soul Vessel needs to evolve the LV repeller into MV.")
	public static int mobRepellerEvolveKillsMv = 80;
	/** Personal hostile kills to evolve the MV block into the HV tier; also the vessel's hard cap. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Personal hostile kills to evolve the MV repeller into HV; also the vessel hard cap.")
	public static int mobRepellerEvolveKillsHv = 250;

	// --- Cotton trellis (MOD-280): the mod's first crop. ---
	/**
	 * Chance divisor for one rooting stage of the trellis: on each random tick of a moist, lit
	 * plant there is a 1-in-this chance of advancing. Growth is deliberately random rather than a timer —
	 * the plant carries no block entity, so a field of any size costs nothing to tick (MOD-280).
	 *
	 * <p>Rooting runs three stages and happens <b>once per plant</b>, so this is the slow knob: raising it
	 * lengthens the initial wait without touching how fast an established plant re-fruits.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Cotton trellis: 1-in-this chance of advancing one rooting stage per random tick (higher = longer initial growth).")
	public static int cottonRootingChanceDivisor = 12;
	/**
	 * Chance divisor for one fruiting stage — the two-stage cycle an established plant repeats forever
	 * after every harvest. Much smaller than the rooting divisor: waiting once is the price of the plant,
	 * waiting every harvest would be tedium.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Cotton trellis: 1-in-this chance of advancing one fruiting stage per random tick (the repeating harvest cycle).")
	public static int cottonFruitingChanceDivisor = 4;

	// --- Kok sagyz (MOD-537): the rubber dandelion — root rubber without an oil rig. ---
	/**
	 * Chance divisor for one flower stage of the kok sagyz (rosette → bud → flower → seed head):
	 * on each random tick of a lit plant there is a 1-in-this chance of advancing. Like the cotton
	 * trellis, growth is random rather than a timer — the plant carries no block entity, so a
	 * plantation of any size costs nothing to tick.
	 *
	 * <p>On farmland (or the plant's own root) this is the rate actually used; off tended ground the
	 * {@link #kokSagyzWildGrowthDivisor} multiplies it on top.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: 1-in-this chance of advancing one flower stage per random tick on tended ground (farmland or the plant's own root).")
	public static int kokSagyzGrowthChanceDivisor = 1;
	/**
	 * Chance divisor for one block of root growth: only a mature (seed-head) plant rolls this, and
	 * only while the column can still go deeper. Kept separate from the flower divisor because the
	 * two waits feel different — the flower is what the player watches, the root is what he waits
	 * for, and they should be tunable apart.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: 1-in-this chance of growing the root one block deeper per random tick of a mature plant.")
	public static int kokSagyzRootChanceDivisor = 1;
	/**
	 * Multiplier stacked onto both kok sagyz divisors when the ground below is neither farmland nor
	 * the plant's own root — a self-seeded roadside specimen grows this many times slower than the
	 * plantation it escaped from. Wild plants stay a curiosity rather than a free farm, without
	 * being impossible.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: multiplier on both growth divisors when the plant is NOT on farmland or its own root, so a wild specimen grows slower than a tended one.")
	public static int kokSagyzWildGrowthDivisor = 2;

	// --- Iron Furnace (fuel-based, MOD-115): ticks to smelt one item. Vanilla furnace = 200. ---
	/** Ticks the iron furnace needs to smelt one item on fuel. Between vanilla (200) and the
	 * electric furnace, so it reads as "a bit faster than stone" without devaluing the electric tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the (fuel-based) iron furnace takes to smelt one item. Vanilla furnace = 200.")
	public static int ironFurnaceCookTime = 150;

	// --- Player stats / mod XP (MOD-133). Starting values — calibrate after playtest. ---
	/** Useful EU (from completed machine operations) that equals one point of mod XP. Higher = slower. */
	@Knob(section = Section.PLAYER, min = 1, floorTo = 1,
			doc = "MOD-133 player profile: useful EU (from completed machine operations) per 1 point of mod XP. Higher = slower progression. Starting value, tune after playtest.")
	public static int euPerXp = 1000;
	/**
	 * Produced EU (actually credited into a generator's buffer) that equals one point of mod XP —
	 * deliberately far worse than {@link #euPerXp}, because a generator runs without the player.
	 * The token trickle keeps a big power farm from feeling unrewarded while leaving hands-on machine
	 * work the dominant source; idle production into a full buffer credits nothing at all.
	 */
	@Knob(section = Section.PLAYER, min = 1, floorTo = 1,
			doc = "MOD-133 player profile: produced EU (actually credited into a generator buffer, never idle overflow) per 1 point of mod XP. Much higher than euPerXp on purpose - a generator runs unattended, so it only trickles. Starting value, tune after playtest.")
	public static int euPerXpGenerated = 20_000;
	/** XP cost of the first level (1→2); each later level costs {@link #levelXpMultiplier}× the previous. */
	@Knob(section = Section.PLAYER, min = 1, floorTo = 1,
			doc = "MOD-133: XP cost of the first level (1->2); each later level costs levelXpMultiplier x the previous. Starting value.")
	public static int xpLevelOneCost = 80;
	/** Per-level XP cost multiplier — the exponential curve over 40 levels. Must be &gt; 1.0. */
	@Knob(section = Section.PLAYER, min = 1.0, exclusive = true,
			doc = "MOD-133: per-level XP cost multiplier (exponential curve over 40 levels). Must be > 1.0.")
	public static float levelXpMultiplier = 1.18f;
	/** How often (server ticks) in-memory player stats are folded into the attachment and synced. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-133: how often (server ticks) in-memory player stats fold into the attachment and sync. 100 = every 5s.")
	public static int statsFlushTicks = 100;

	// --- Energy tiers: per-tick voltage cap + default buffer capacity, configurable per tier ---
	/**
	 * Max packet voltage (EU) and per-tick transfer cap for the LV tier. Applies to every LV block
	 * (cable, generator, machine, storage) — i.e. the most-used tier in the mod. The other LV-rate
	 * fields (cableBuffer, generatorBuffer, …) are per-block overrides; this is the universal tier ceiling.
	 * Mirrored into {@link dev.alaindustrial.core.energy.EnergyTier#LV} at class init.
	 */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max packet voltage (EU) and per-tick transfer cap for the LV tier (cable, generator, machine, storage). Mirrored into EnergyTier.LV.")
	public static int tierLvVoltage = 32;
	/** Max packet voltage for the MV tier. 4× LV by convention. Mirrored into EnergyTier.MV. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max packet voltage for the MV tier (4x LV by convention). Mirrored into EnergyTier.MV.")
	public static int tierMvVoltage = 128;
	/** Max packet voltage for the HV tier. 4× MV by convention. Mirrored into EnergyTier.HV. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max packet voltage for the HV tier (4x MV by convention). Mirrored into EnergyTier.HV.")
	public static int tierHvVoltage = 512;
	/** Default internal buffer capacity for LV machines that do not override it. Mirrored into EnergyTier.LV. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Default internal buffer capacity for LV machines that do not override it. Mirrored into EnergyTier.LV.")
	public static int tierLvCapacity = 10_000;
	/** Default internal buffer capacity for MV machines. Mirrored into EnergyTier.MV. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Default internal buffer capacity for MV machines. Mirrored into EnergyTier.MV.")
	public static int tierMvCapacity = 40_000;
	/** Default internal buffer capacity for HV machines. Mirrored into EnergyTier.HV. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Default internal buffer capacity for HV machines. Mirrored into EnergyTier.HV.")
	public static int tierHvCapacity = 160_000;

	// --- Cable ---
	/**
	 * Fraction of throughput attenuated per cable block traversed (copper LV). The retained fraction
	 * compounds over cable-distance {@code d}: a consumer receives the gross flow minus
	 * {@code floor(gross × (1 - (1 - copperCableLossPerBlock)^d))}. Loss is capped below the whole packet,
	 * so a positive flow always delivers at least 1 EU and a small top-off packet still reaches exact
	 * capacity on a long line.
	 *
	 * <p>{@code 0.02} = 2%/block (finalized in MOD-073, source of truth PERFORMANCE.md): a full 32 EU
	 * LV packet loses 5 EU over 10 cables and 10 over 20; an 8 EU/t fuel-generator stream loses 1 EU
	 * over 10 cables, and a 1 EU trickle floors to zero over any distance. Tuned to penalize long runs
	 * across the base without ever turning a sufficiently long cable into a silent 100% cutoff,
	 * pushing players to keep a BatteryBox near the load — copper stays the only cable in the release,
	 * so 0.02 sits at the comfortable top of its band (0.025/0.03 are reserved for when glass fibre
	 * gives a real upgrade path).
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per copper cable block (0.02 = 2% of the remaining flow per block).")
	public static double copperCableLossPerBlock = 0.02;
	/** Master safety switch for contact damage and its particles/sound on energized bare cables. */
	@Knob(section = Section.SAFETY,
			doc = "When true, energized bare cables damage players on direct contact and emit shock feedback. false disables the entire mechanic.")
	public static boolean bareCableShockEnabled = true;
	/** Contact damage from an energized bare LV (tin/copper) cable, in half-hearts. */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage from direct contact with an energized bare LV cable, in half-hearts.")
	public static float bareCableShockLvDamage = 2.0f;
	/** Contact damage from an energized bare MV (gold) cable, in half-hearts. */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage from direct contact with an energized bare MV cable, in half-hearts.")
	public static float bareCableShockMvDamage = 6.0f;
	/**
	 * Contact damage from an energized bare HV (electrum) cable, in half-hearts. Continues the
	 * 2 → 6 → 10 ladder (MOD-358). HV shared the MV number until the electrum cable existed, because
	 * no HV cable did; the teleporter station is an HV consumer but not a cable, so it never touched
	 * this path.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage from direct contact with an energized bare HV cable, in half-hearts.")
	public static float bareCableShockHvDamage = 10.0f;
	/**
	 * Extra blocks the shock hazard reaches beyond the bare cable segment's own cell, in every
	 * direction (proximity check, on top of the direct-contact shape). {@code 0} keeps the original
	 * direct-touch-only behaviour.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Extra blocks the shock hazard reaches beyond a bare cable segment's own cell in every direction (0 = direct-touch only).")
	public static double bareCableShockProximityRadius = 0.5;
	/**
	 * Multiplier applied to the matching bare cable's attenuation when the whole governing cable grade
	 * is insulated. {@code 0.5} makes rubber insulation halve loss without changing tier, packet cap or
	 * segment throughput (MOD-259). Keeping this as one live knob preserves the exact relationship for
	 * tin and copper instead of duplicating two independently drifting rates.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Multiplier applied to bare-cable attenuation for rubber-insulated tin/copper cables (0.5 = half the loss; throughput and packet cap are unchanged).")
	public static double insulationLossMultiplier = 0.5;

	// --- Insulating stands under bare cable, see core.energy.ShockGuardMaterial (MOD-279) ---
	/**
	 * Probability (0..1) that a shock still lands on a player standing <b>on top of</b> a wood-stood
	 * segment. These chances only govern the from-above case: a stand blocks the side/below hazard
	 * outright, so they are deliberately mild — the stand's main value is that it makes a cable run
	 * safe to walk <em>past</em>, not safe to walk <em>on</em>. {@code 1.0} removes the from-above
	 * benefit entirely, {@code 0.0} makes the stand as good as rubber insulation.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) a shock still lands through a plank insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).")
	public static double shockGuardWoodHitChance = 0.7;
	/** Probability (0..1) a shock still lands from above through a <b>wool</b> stand — the weakest of the three. */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) a shock still lands through a wool insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).")
	public static double shockGuardWoolHitChance = 0.9;
	/** Probability (0..1) a shock still lands from above through a <b>glass</b> stand — the strongest of the three. */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) a shock still lands through a glass insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).")
	public static double shockGuardGlassHitChance = 0.5;
	/**
	 * Contact ticks a player is left alone for after a stand absorbs a shock. Without this the roll would
	 * repeat every tick the player stays in contact (20×/second), and even a strong stand would let a hit
	 * through almost immediately — the reduced chance would be per-tick rather than per-contact, which is
	 * not what a player reads it as. Matches vanilla's own post-hit invulnerability window
	 * ({@code LivingEntity.INVULNERABLE_DURATION}, 20 ticks), so a blocked shock and a landed one pace
	 * the same. That constant is {@code protected} and cannot be referenced from here, hence the literal.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Contact ticks a player is spared after an insulating stand absorbs a shock, so the reduced chance is per contact rather than re-rolled every tick.")
	public static int shockGuardGraceTicks = 20;
	/**
	 * Percent of a bare cable's shock one worn piece of insulating armour cuts (MOD-466). Four pieces
	 * at 25 make a full set immune; three cut 75 % and still take the rest.
	 *
	 * <p>Per piece rather than all-or-nothing on purpose: it is the same shape the shielding suit uses
	 * for radiation, so the mod has one rule for both worn defences, and a half-built set is worth
	 * something instead of nothing. Values above 25 are clamped to a whole set's 100 % by
	 * {@link dev.alaindustrial.core.energy.ShockInsulation#cutPercent} — without that ceiling a
	 * generous operator would push damage past zero and start healing the wearer. {@code 0} disables
	 * the set's protection without touching the hazard itself.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Percent of a bare cable's shock cut by one worn piece of insulating armour (25 = a full four-piece set is immune; 0 disables the set's protection).")
	public static int bareCableShockInsulationPerPiecePercent = 25;
	/**
	 * Absorbed shock damage that costs one point of durability on each worn insulating piece (MOD-466).
	 *
	 * <p><b>This knob is a rate, not a price, and the difference is the whole bug it fixes.</b> Contact
	 * is continuous — both hazard paths re-enter every tick and an absorbed hit opens only a
	 * {@link #shockGuardGraceTicks} window — so a player standing beside a live wire is shocked once a
	 * second. At the shipped 1.0 (full absorbed damage per point) an LV line cost 2 durability a second
	 * and destroyed a helmet in under half a minute of standing still.
	 *
	 * <p>At 4.0 the tier ladder survives but the clock is sane: a full set spends 1 point per LV shock,
	 * 2 per MV, 3 per HV, so a helmet takes roughly 4.5 minutes of unbroken LV contact, 2.5 of MV and
	 * 1.5 of HV. Raise it to make the set last longer, lower it to make voltage bite sooner; the floor
	 * of one point per absorbed hit is in the code and cannot be configured away.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Absorbed shock damage that costs one durability point on each worn insulating piece (higher = the set lasts longer; contact is once per second, so this is a rate).")
	public static float bareCableShockInsulationDamagePerDurability = 4.0f;

	// --- Cable grades: tin (cheap/narrow), gold (MV/wide) and electrum (HV/widest),
	// see core.energy.CableType (MOD-219, MOD-358) ---
	/**
	 * Per-segment buffer of a tin cable — and therefore its real throughput (MOD-070: a cable carries its
	 * buffer per tick). 8 EU/t is deliberately below copper's 12: tin is the cheap wire, narrower than
	 * copper but far cheaper to lose energy in. Comfortably above a solar farm's needs (one panel is
	 * {@link #solarEuPerTick} = 1 EU/t) and below a fuel generator's 8 EU/t burst, so the choice
	 * tin-vs-copper is a real one.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-segment working EU buffer of a tin cable = its real throughput (8 EU/t, narrower than copper's 12).")
	public static int tinCableBuffer = 8;
	/**
	 * Per-tick ceiling on EU drawn from one source through a tin cable. Matches its buffer (8): unlike
	 * copper — whose LV tier voltage (32) sits well above its 12 EU buffer — tin is capped by design, so
	 * it cannot be used as a cheap stand-in for a full LV line. There is no sub-LV entry in
	 * {@link dev.alaindustrial.core.energy.EnergyTier}; tin is an LV cable with its own lower cap.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-tick ceiling on EU drawn from one source through a tin cable (8 EU/t, below the LV tier voltage by design).")
	public static int tinCablePacketCap = 8;
	/**
	 * Fraction of throughput attenuated per tin cable block traversed. {@code 0.006} (research §3) is ~3.3×
	 * gentler than copper's 0.02 — this is tin's whole point. A 1 EU/t solar trickle loses nothing at
	 * any distance because the attenuation model always preserves at least 1 EU of a positive packet;
	 * at larger flows tin also attenuates more slowly than copper, which remains the wider choice for a
	 * dense, high-flow line.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per tin cable block (0.006 = 0.6% of the remaining flow per block; a 1 EU/t solar trickle floors to zero loss).")
	public static double tinCableLossPerBlock = 0.006;
	/**
	 * Per-segment buffer of a gold cable — its real throughput. 48 EU/t is 4× copper's 12, mirroring the
	 * ×4 LV→MV voltage step, so the MV cable is felt as a genuinely wider pipe rather than a recoloured
	 * copper one. Note the "no battery from wires" ceiling is a copper-scale invariant
	 * ({@link #cableBuffer} × 1000 &lt; {@link #batteryBoxBuffer}); gold's cost (gold ingots) is what keeps
	 * a 1000-segment gold grid out of reach rather than the buffer size.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-segment working EU buffer of a gold (MV) cable = its real throughput (48 EU/t, 4x copper).")
	public static int goldCableBuffer = 48;
	/**
	 * Fraction of throughput lost per gold cable block traversed. {@code 0.03} is deliberately WORSE than
	 * copper's 0.02 (research §3, IC2 canon): gold buys throughput (4× buffer, 128 EU/t packet cap) and
	 * pays for it in distance, making the choice "wide pipe up close" vs "thin pipe far away" instead of
	 * a strict upgrade. Its packet cap is the shared {@link #tierMvVoltage}, not a private knob.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per gold cable block (0.03 = 3% of the remaining flow per block; worse than copper by design - gold buys throughput, not distance).")
	public static double goldCableLossPerBlock = 0.03;
	/**
	 * Per-segment buffer of an electrum cable — its real throughput. 192 EU/t is 4× gold's 48, the same
	 * ×4 step every previous rung of the ladder takes, so HV is the next rung rather than a leap. Its
	 * packet cap is the shared {@link #tierHvVoltage} (512), not a private knob — same arrangement as
	 * copper and gold; only tin owns a cap below its tier.
	 *
	 * <p><b>The "no battery from wires" invariant does not scale here</b> and is not meant to: that
	 * ceiling ({@link #cableBuffer} × 1000 &lt; {@link #batteryBoxBuffer}) is a copper-scale rule about
	 * the wire players run by the thousand. A 1000-segment electrum grid would bank 192 000 EU, but it
	 * costs ~1 667 electrum ingots and ~1 333 diamond dust to build — the craft, not the buffer, is what
	 * keeps it out of reach. Gold already sits the same way (48 000 EU per 1000 segments).
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-segment working EU buffer of an electrum (HV) cable = its real throughput (192 EU/t, 4x gold).")
	public static int electrumCableBuffer = 192;
	/**
	 * Fraction of throughput lost per electrum cable block traversed. {@code 0.005} is the lowest in the
	 * mod — below even tin's 0.006 — and is a deliberate exception to the "wider pipe pays in distance"
	 * rule that gold follows (research §3): electrum beats every other grade on every axis at once, and
	 * pays for it purely in craft cost. Halved again by {@link #insulationLossMultiplier} on the
	 * insulated variant, giving 0.0025.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per electrum cable block (0.005 = 0.5% of the remaining flow per block; the lowest in the mod - electrum pays in craft cost, not in distance).")
	public static double electrumCableLossPerBlock = 0.005;

	// --- Energy network ---
	/** Max awake energy networks processed per server tick; the rest are deferred round-robin. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max awake energy networks processed per server tick; the rest are deferred round-robin.")
	public static int networksPerTick = 512;
	/**
	 * Cap on how many distinct {@code EnergyNetwork}s the Network Analyzer's Traverse mode (MOD-047)
	 * will walk through storage sinks before stopping. Visualization-only — never affects energy
	 * distribution. Generous default so realistic factories stitch fully; absurd megabases cap out
	 * with an actionbar warning instead of freezing the client.
	 */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Cap on networks the Network Analyzer's Traverse mode walks (visualization only, never affects energy).")
	public static int networkAnalyzerMaxTraversedNetworks = 32;

	// --- World gen ---
	/**
	 * MOD-119: when {@code true} and the player creates a world with the vanilla "Bonus Chest" option on,
	 * the mod injects a pool of starter items into {@code minecraft:chests/spawn_bonus_chest} (vanilla loot
	 * is kept). Set {@code false} to leave the bonus chest purely vanilla. Read by the Fabric
	 * {@code LootTableEvents.MODIFY} handler and the NeoForge {@code alaindustrial:bonus_chest_enabled}
	 * loot condition.
	 */
	@Knob(section = Section.WORLD,
			doc = "When true, mod starter items are injected into the vanilla bonus chest at world creation (vanilla loot kept). false = purely vanilla bonus chest.")
	public static boolean bonusChestEnabled = true;

	/**
	 * MOD-238: when {@code true}, oil blocks ignite from adjacent fire/soul fire/lava and the burn
	 * spreads across the pool (see {@code OilLiquidBlock}). Set {@code false} to make oil inert.
	 */
	@Knob(section = Section.WORLD,
			doc = "When true, oil ignites from adjacent fire or flint-and-steel and the burn spreads across the pool; lava alone does not ignite it. false = oil is inert.")
	public static boolean oilBurns = true;

	// ---------------------------------------------------------------------------------------------
	// MOD-402: file layout — schema version, thematic sections and the migration ladder.
	// NOTE: none of this touches the Java API above. Every consumer keeps reading `Config.<field>`;
	// only the shape of `config/alaindustrial.json` and the load/save layer changed.
	// ---------------------------------------------------------------------------------------------

	/**
	 * Layout version of {@code config/alaindustrial.json}, written into the file as
	 * {@code schemaVersion} and bumped whenever the file's SHAPE or the MEANING of a key changes —
	 * never for adding a knob (a new key is absent-safe and needs no migration).
	 *
	 * <p><b>How to bump it</b> (the whole recipe, deliberately one entry):
	 * <ol>
	 *   <li>Write a {@code private static void migrateVNtoVN1(JsonObject file)} that rewrites the
	 *       document IN PLACE from version {@code N} to {@code N+1} — rename a key, rescale a value,
	 *       move a knob between sections. It receives the document as it stands after every earlier
	 *       step, so each migration only has to know about its own hop.</li>
	 *   <li>Append {@code new Migration(N, Config::migrateVNtoVN1)} to {@link #MIGRATIONS}.</li>
	 *   <li>Raise this constant to {@code N+1}.</li>
	 * </ol>
	 * {@code ConfigSchemaTest} pins the ladder: {@code MIGRATIONS} must hold exactly one step per
	 * version hop, in ascending order, so a bump without a migration (or a migration without a bump)
	 * fails the build rather than silently skipping a file.
	 *
	 * <p>An OLDER file is migrated step by step and then rewritten in the current shape. A NEWER file
	 * is refused outright — see {@link #loadFrom}.
	 */
	static final int SCHEMA_VERSION = 2;

	/** Json key holding {@link #SCHEMA_VERSION}. Absent = a pre-MOD-402 flat file, i.e. version 0. */
	private static final String SCHEMA_VERSION_KEY = "schemaVersion";

	/** Inline doc written above {@link #SCHEMA_VERSION_KEY}, same {@code _comment_} idiom as a field. */
	private static final String SCHEMA_VERSION_DOC =
			"Layout version of this file. Written by the mod - do not raise it by hand. An older file is"
					+ " migrated automatically on load; a file from a NEWER mod version is refused (the"
					+ " server logs a warning and runs on built-in defaults instead of guessing).";

	/**
	 * Json key of the machine-written block that records, per knob, the built-in default this file was
	 * last written against (MOD-553). It is what turns "the number in the file" into "the number the
	 * operator chose": a value equal to the default recorded beside it was never chosen by anybody, so
	 * a later build is free to replace it with ITS default.
	 *
	 * <p>One flat block rather than a sibling key next to every knob, on purpose. The operator's own
	 * editing area — the sections — keeps exactly the shape it had, the bookkeeping is quarantined in
	 * one clearly-labelled place with one explanation instead of 380 repetitions, and the block is
	 * keyed by knob name alone, so a future migration that moves a knob between sections does not have
	 * to move its recorded default as well.
	 */
	private static final String BUILTIN_DEFAULTS_KEY = "builtinDefaults";

	/**
	 * Inline doc written above {@link #BUILTIN_DEFAULTS_KEY}. It has to state the limitation, because
	 * the limitation is invisible from the data: see {@link #migrateAddBuiltinDefaults}.
	 */
	private static final String BUILTIN_DEFAULTS_DOC =
			"Written by the mod - do not edit. For each knob, the mod's own default at the moment this"
					+ " file was last saved. On load, a knob still holding exactly that number counts as"
					+ " untouched, so a mod update that changes the default applies it here too (the"
					+ " change is logged). A knob you edited to anything else is left alone forever."
					+ " NOTE: a value you deliberately set to the same number as the default cannot be"
					+ " told apart from one you never touched, and will follow the default when it"
					+ " changes. NOTE: for files created before this block existed, the reference point"
					+ " is the moment it was added - defaults that drifted before that are not"
					+ " recoverable.";

	/**
	 * Thematic group a tunable belongs to — and, on disk, the JSON object its key lives inside.
	 *
	 * <p>The section is declared ON THE FIELD's own {@code FIELDS} entry, right next to its doc string.
	 * That is the point: a second "key → section" table would be a parallel list to keep in sync by
	 * hand, which is the exact drift class this repo has already had to build gates against.
	 *
	 * <p>Declaration order below is the order the sections render in the file.
	 */
	enum Section {
		GLOBAL("global", "Server-wide multipliers applied on top of everything else."),
		GENERATORS("generators", "Generator output and the environment that scales it (sky, height, weather),"
				+ " plus rotor/wheel consumables and generator EU buffers."),
		MACHINES("machines", "Processing machines: EU/t, operation durations, internal buffers, overclocker"
				+ " chips, the incubator mutation table, the condenser and the drone station."),
		STORAGE("storage", "EU stores: battery box, reinforced storage, charging station, and the channel"
				+ " that feeds a non-cascade sink from a store."),
		// NB: keep equals signs and apostrophes OUT of a section doc. Gson html-escapes both, so they
		// land in the written file as bare unicode escapes and read as noise to the operator. The older
		// field docs already carry that scar; new text does not have to.
		CABLES("cables", "Cable grades: per-segment buffer (which is also the segment throughput), packet"
				+ " caps and per-block loss."),
		SAFETY("safety", "Bare-cable shock hazard and the insulating stands that soften it."),
		NETWORK("network", "Voltage tiers, buffer capacities per tier, and per-tick network budgets."),
		TOOLS("tools", "Powered items the player carries or wears: buffers, charge rates and running costs."),
		LOGISTICS("logistics", "Moving things around: item and fluid pipes, the pump, portable tanks,"
				+ " the teleporter and the stock display frame."),
		PLAYER("player", "Mod XP and the player profile curve."),
		WORLD("world", "World behaviour: bonus chest injection, burning oil, crop growth.");

		/** Json key of the section object. */
		final String id;
		/** One-line note written as {@code _comment_<id>} above the section. */
		final String doc;

		Section(String id, String doc) {
			this.id = id;
			this.doc = doc;
		}
	}

	/**
	 * One rung of the schema ladder: "a document stored at {@code fromVersion} becomes a document at
	 * {@code fromVersion + 1} after {@code apply} has rewritten it in place".
	 *
	 * <p>Kept as data rather than a chain of {@code if}s so adding the next hop is one list entry —
	 * see {@link #SCHEMA_VERSION} for the three-step recipe.
	 */
	private record Migration(int fromVersion, Consumer<JsonObject> apply) {
	}

	/**
	 * The ladder, ascending, one entry per version hop ({@code MIGRATIONS.get(i).fromVersion() == i},
	 * {@code MIGRATIONS.size() == SCHEMA_VERSION}). Pinned by {@code ConfigSchemaTest}.
	 */
	private static final List<Migration> MIGRATIONS = List.of(
			new Migration(0, Config::migrateFlatToSections),
			new Migration(1, Config::migrateAddBuiltinDefaults));

	/**
	 * v0 → v1: the pre-MOD-402 flat file, where all 220 knobs sat at the top level, becomes the
	 * sectioned one. Every registered key found at the root is moved into its declared section, so a
	 * server that has been running since before MOD-402 keeps every value it had edited.
	 *
	 * <p>This step is load-bearing, not decorative: {@link #loadFrom} stages each field from its
	 * section object only. Remove this migration and every old install silently reverts to defaults.
	 */
	private static void migrateFlatToSections(JsonObject file) {
		for (ConfigField field : FIELDS) {
			JsonElement value = file.remove(field.key);
			if (value == null) {
				continue;
			}
			JsonElement existing = file.get(field.section.id);
			JsonObject body;
			if (existing != null && existing.isJsonObject()) {
				body = existing.getAsJsonObject();
			} else {
				body = new JsonObject();
				file.add(field.section.id, body);
			}
			body.add(field.key, value);
		}
	}

	/**
	 * v1 → v2: give an existing file the {@link #BUILTIN_DEFAULTS_KEY} block it never had, filled with
	 * the defaults compiled into THIS build.
	 *
	 * <p><b>The limitation this step creates, stated plainly.</b> The block answers "was this knob ever
	 * changed by hand?" by comparing the stored value against the default it was stored with — and for a
	 * file written before the block existed, that default was never recorded. The only honest reference
	 * point left is the moment of migration: from here on, a knob equal to this build's default counts
	 * as untouched, and a knob holding anything else counts as operator-edited and is preserved forever.
	 * A knob that still holds a default from three releases ago is therefore frozen at that number — the
	 * drift accumulated before this step is <b>not recoverable</b>, and no later code can recover it,
	 * because the information was never written down. This is also said in {@link #BUILTIN_DEFAULTS_DOC}
	 * (so the operator reads it in their own file) and in {@code docs/SERVER_CONFIG.md}.
	 *
	 * <p>The alternative — recording each knob's CURRENT file value as its default — was rejected: it
	 * would mark every knob as untouched, and the next default change would silently overwrite real
	 * operator edits. Freezing an unknown-provenance value is a stale number; overwriting an edit is
	 * data loss.
	 *
	 * <p><b>No test can observe this step's output, and that is expected.</b> A block filled with
	 * exactly the compiled defaults can never trigger an adoption on the load that installs it —
	 * adoption needs the file's value to differ from the compiled default while equalling the recorded
	 * one, which is a contradiction here — and the self-heal then rewrites the block from
	 * {@link #snapshot()} anyway. The step exists to keep {@link #migrate}'s postcondition honest ("the
	 * rest of the load only ever sees a current-shape document") and because the ladder demands exactly
	 * one rung per version hop. Do not "simplify" it away on the grounds that nothing goes red: the
	 * reader after the next bump would inherit a ladder with a hole in it.
	 */
	private static void migrateAddBuiltinDefaults(JsonObject file) {
		JsonObject defaults = new JsonObject();
		for (ConfigField field : FIELDS) {
			field.writeDefault(defaults);
		}
		file.add(BUILTIN_DEFAULTS_KEY, defaults);
	}

	/**
	 * Declares a {@code public static} field of this class as a tunable: which section of the file it
	 * lives in, the inline documentation written above it, and the range the loader accepts (MOD-553).
	 *
	 * <p><b>This annotation IS the registration.</b> There is no second list to keep in step — that
	 * mirror list is what MOD-553 deleted. A field carrying this annotation is loaded, validated,
	 * written and reset; a field without one is an ordinary static that the config file never sees.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface Knob {
		/** Json object this key lives in, and the heading an operator reads it under. */
		Section section();

		/** The {@code _comment_<key>} line written above the value, in the operator's own file. */
		String doc();

		/**
		 * Lowest value the loader accepts. The default means "no lower bound at all", which is what a
		 * boolean knob wants; every numeric knob in this class declares one.
		 */
		double min() default Double.NEGATIVE_INFINITY;

		/** When true the bound is exclusive: the value must be strictly GREATER than {@link #min()}. */
		boolean exclusive() default false;

		/**
		 * Value substituted for an out-of-range one. {@link Double#NaN} — the default — means "restore
		 * this build's compiled default", which is what a knob wants when its default is also its
		 * recovery value. A knob that deliberately clamps to a range BOUNDARY instead says so here, so
		 * the exception is visible rather than implied: {@code euPerXp}, {@code euPerXpGenerated} and
		 * {@code xpLevelOneCost} floor at 1 because they guard a division, and every double knob names
		 * its floor because "no loss / no reserve" is a legal answer their default is not.
		 */
		double floorTo() default Double.NaN;
	}

	/**
	 * Every tunable declared above, collected once at class-init time by reading the {@link Knob}
	 * annotation each field carries. {@link #loadFrom} and {@link #snapshot} walk this list instead of
	 * repeating each field five times (declaration, staged read, clamp, commit, serialize).
	 *
	 * <p><b>Why reflection and not a hand-written list (MOD-553).</b> The same 380 knobs used to be
	 * spelled out twice — once as a field, once as a descriptor in a mirror list — and a third time as a
	 * sentinel in {@code ConfigSnapshotTest}. Adding a knob meant three edits, and forgetting the second
	 * one made the knob silently unsaveable. The declaration is now the single source: a field carrying
	 * a {@link Knob} annotation IS a tunable, and a field without one is not.
	 *
	 * <p><b>Order is deterministic and does NOT come from reflection.</b> {@code getDeclaredFields()}
	 * returns fields in no specified order — the JLS gives no guarantee, HotSpot merely happens to return
	 * class-file order — so relying on it would make the canonical file's key order a property of the JVM
	 * it was written on, and a JVM that reordered them would rewrite every operator's file with the same
	 * values in a different sequence. The registry is therefore sorted by section (enum order) and then by
	 * key name: a total order derived only from data this class declares, identical everywhere. The cost
	 * is that the file no longer mirrors the source's feature-by-feature grouping — the section objects
	 * and each key's own {@code _comment_} carry that context instead.
	 *
	 * <p><b>It must stay textually BELOW the declarations:</b> each entry captures the compiled default by
	 * reading its field, and Java runs static initializers in source order, so a registry built above them
	 * would capture zeroes.
	 */
	private static final List<ConfigField> FIELDS = buildRegistry();

	/**
	 * Read every {@link Knob}-annotated field on this class into a {@link ConfigField}. Runs once, at
	 * class initialization, before any file can be loaded — which is what makes each entry's captured
	 * fallback the value COMPILED into this build rather than whatever a file last applied.
	 *
	 * <p>A field annotated in a way the loader cannot honour (non-public, final, or of an unsupported
	 * type) fails here instead of being skipped. A silently ignored knob — neither loaded nor saved — is
	 * exactly the failure this registry exists to prevent.
	 */
	private static List<ConfigField> buildRegistry() {
		List<ConfigField> out = new ArrayList<>();
		for (Field field : Config.class.getDeclaredFields()) {
			Knob knob = field.getAnnotation(Knob.class);
			if (knob == null) {
				continue;
			}
			int mods = field.getModifiers();
			if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
				throw new IllegalStateException("@Knob field " + field.getName()
						+ " must be public, static and non-final");
			}
			out.add(ConfigField.of(field, knob));
		}
		out.sort(Comparator.comparingInt((ConfigField f) -> f.section.ordinal())
				.thenComparing((ConfigField f) -> f.key));
		return List.copyOf(out);
	}

	/** Effective machine drain per tick after the speed multiplier (E_op stays ~constant). */
	public static int machineEuPerTickEffective() {
		return Math.max(1, Math.round(machineEuPerTick * globalMachineSpeedMultiplier));
	}

	/** Effective Electric Heater drain after the machine speed multiplier (heater E_op stays ~constant). */
	public static int electricHeaterEuPerTickEffective() {
		return Math.max(1, Math.round(electricHeaterEuPerTick * globalMachineSpeedMultiplier));
	}

	/** Scale a base duration (ticks) by the speed multiplier: faster machine -> fewer ticks. */
	public static int scaledDuration(int baseTicks) {
		return Math.max(1, Math.round(baseTicks / globalMachineSpeedMultiplier));
	}

	/**
	 * EU the electric furnace spends on one vanilla smelt — its scaled duration times its effective
	 * per-tick drain, i.e. exactly what {@code ElectricFurnaceBlockEntity} ticks away.
	 *
	 * <p>Lives here so the recipe-viewer mirrors (MOD-086) quote the same number the machine spends
	 * under any {@link #globalMachineSpeedMultiplier}: both factors round separately, so multiplying
	 * the raw fields would only agree at the default 1.0 (e.g. x3 really costs
	 * round(100/3) x round(2*3) = 198 EU, not 200).
	 */
	public static int electricFurnaceVanillaSmeltEu() {
		return Math.max(1, scaledDuration(electricFurnaceDuration) * machineEuPerTickEffective());
	}

	/**
	 * Per-loader path to {@code config/alaindustrial.json}. Each loader binds this in its config-loader
	 * {@code register()} (Fabric via {@code FabricLoader.getConfigDir()}, NeoForge via {@code FMLPaths.CONFIGDIR}),
	 * so loader-neutral callers in {@code common} — notably the {@code /ala config reload} command — can reload
	 * without knowing which loader they run on. Same set-once-supplier idiom as {@code ModSounds}: the default
	 * throws loudly if read before a loader bound it, catching an ordering regression instead of a silent NPE.
	 */
	public static Supplier<Path> configPath = () -> {
		throw new IllegalStateException("Config.configPath read before its loader bound it");
	};

	/** Outcome of {@link #loadFrom(Path)} so callers (e.g. {@code /ala config reload}) can report precisely. */
	public enum LoadResult {
		/** File existed and was parsed; live balance now reflects it. */
		LOADED,
		/** File was absent; the current defaults were written to it. */
		DEFAULTS_WRITTEN,
		/**
		 * File existed but could not be parsed (bad JSON, or a value of the wrong type). The apply is
		 * atomic, so the live balance is left exactly as it was.
		 */
		ERROR,
		/**
		 * The file declares a {@code schemaVersion} newer than {@link #SCHEMA_VERSION} (MOD-402): this
		 * build cannot know what its keys mean. Nothing from it is applied, the file on disk is left
		 * untouched, and the live balance is reset to the mod's built-in defaults.
		 *
		 * <p><b>Why this is its own outcome and not {@link #ERROR}.</b> The two differ in the one fact an
		 * admin needs: {@code ERROR} leaves the running balance alone, this one replaces it. Folding them
		 * together made {@code /ala config reload} report "live balance unchanged" while the balance had in
		 * fact just been reset — the reader would go looking for a syntax error instead of for the version
		 * mismatch that actually happened.
		 */
		SCHEMA_TOO_NEW
	}

	/** Reload from the loader-bound {@link #configPath}. Thin wrapper for the reload command + reload listeners. */
	public static LoadResult reload() {
		return loadFrom(configPath.get());
	}

	/**
	 * Load the config file at {@code path}, or write the current defaults if it does not exist yet.
	 *
	 * <p><b>Versioned (MOD-402):</b> the file carries {@code schemaVersion}. An older one (including a
	 * pre-MOD-402 file, which has no such key and counts as version 0) is walked up the
	 * {@link #MIGRATIONS} ladder before anything is read, so an existing server keeps every value it had
	 * edited. A file from a NEWER mod build is refused: this build cannot know what its keys mean, so
	 * nothing is applied, the balance falls back to the compiled defaults, the file on disk is left
	 * alone, and the reason goes to the log at WARN. Silently reading a newer format is exactly the
	 * "config shadow" failure this task existed to end.
	 *
	 * <p><b>Atomic:</b> every field is parsed into locals first (a wrong-type value throws before anything is
	 * applied), then committed to the static fields in one block — a single typo in the file can never leave the
	 * live balance half-updated. <b>Self-healing on load:</b> after a successful parse the file is re-serialized in
	 * canonical form (sections + field comments + any newly added mod fields) and rewritten only when its content
	 * actually differs, so existing installs gain the inline comments and the write is idempotent (no churn on
	 * {@code /reload}).
	 * <b>Minecraft-free:</b> uses plain Gson (not {@code net.minecraft.GsonHelper}) so the loader-neutral L1 test
	 * suite, which runs without the Minecraft jar, can exercise the file logic directly.
	 */
	public static LoadResult loadFrom(Path path) {
		try {
			if (!Files.exists(path)) {
				Files.writeString(path, canonicalJson());
				Industrialization.LOGGER.info("[config] wrote defaults to {}", path);
				return LoadResult.DEFAULTS_WRITTEN;
			}
			String raw = Files.readString(path);
			JsonObject o = JsonParser.parseString(raw).getAsJsonObject();

			int fileVersion = readSchemaVersion(o);
			if (fileVersion > SCHEMA_VERSION) {
				Industrialization.LOGGER.warn("[config] {} declares schemaVersion {}, but this build only"
						+ " understands {}. NOTHING from the file was applied — the balance is now the mod's"
						+ " built-in defaults, and the file is left untouched. Downgrade the file or upgrade"
						+ " the mod.", path, fileVersion, SCHEMA_VERSION);
				resetToDefaults();
				return LoadResult.SCHEMA_TOO_NEW;
			}
			migrate(o, fileVersion, path);

			// --- staging: parse + validate every field into pending commits; a present-but-wrong-type
			//     key throws here, before any static field is touched (atomic all-or-nothing apply below).
			//     Each field is read out of its own section object, resolved once up front so a section
			//     holding something other than an object also fails before any commit. ---
			JsonObject[] bodies = new JsonObject[Section.values().length];
			for (Section section : Section.values()) {
				bodies[section.ordinal()] = sectionBody(o, section);
			}
			JsonObject recordedDefaults = builtinDefaults(o);
			List<String> adopted = new ArrayList<>();
			List<Runnable> pending = new ArrayList<>(FIELDS.size());
			for (ConfigField field : FIELDS) {
				pending.add(field.stage(bodies[field.section.ordinal()], recordedDefaults, adopted));
			}

			// --- commit: apply all staged values at once (nothing above threw, so this is all-or-nothing). ---
			for (Runnable commit : pending) {
				commit.run();
			}
			Industrialization.LOGGER.info("[config] loaded {}", path);
			if (!adopted.isEmpty()) {
				// One line, not one per knob: a rebalance can move dozens at once, and an admin reading
				// the boot log needs the list, not a wall. Says old -> new so the change is auditable.
				Industrialization.LOGGER.info("[config] {} knob(s) were still at the default this file"
						+ " recorded and now follow this build's new default: {}", adopted.size(),
						String.join(", ", adopted));
			}

			// --- self-heal: rewrite in canonical form (comments + new fields) only when content differs. This
			//     is how an existing comment-less file gains its inline docs, and it stays idempotent on /reload.
			//     A write failure must not fail the (already successful) load, so it is caught separately. ---
			String canonical = canonicalJson();
			if (!normalize(raw).equals(normalize(canonical))) {
				try {
					Files.writeString(path, canonical);
					Industrialization.LOGGER.info("[config] canonicalized {}", path);
				} catch (Exception writeError) {
					Industrialization.LOGGER.error("[config] canonicalize write failed {}: {}", path, writeError.toString());
				}
			}
			return LoadResult.LOADED;
		} catch (Exception e) {
			Industrialization.LOGGER.error("[config] failed to load {}: {}", path, e.toString());
			return LoadResult.ERROR;
		}
	}

	/** Pretty-printed canonical form of the current values, including the {@code _comment_*} field docs. */
	private static String canonicalJson() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(snapshot());
	}

	/**
	 * Layout version recorded in {@code file}. Absent means "written before MOD-402", i.e. the flat
	 * layout, which is version 0 — the one case where a missing key is information rather than a
	 * default. A present-but-not-a-number value throws, exactly like any other wrong-type key.
	 */
	private static int readSchemaVersion(JsonObject file) {
		JsonElement e = file.get(SCHEMA_VERSION_KEY);
		if (e == null || e.isJsonNull()) {
			return 0;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsInt();
		}
		throw new IllegalArgumentException("config key '" + SCHEMA_VERSION_KEY + "' must be a number, got " + e);
	}

	/**
	 * Walk {@code file} up the {@link #MIGRATIONS} ladder from {@code fileVersion} to
	 * {@link #SCHEMA_VERSION}, rewriting it in place. Called before staging, so the rest of the load
	 * only ever sees a current-shape document. Nothing is written back here — what lands on disk is
	 * {@link #canonicalJson()}, rebuilt from the live values by the self-heal step.
	 */
	private static void migrate(JsonObject file, int fileVersion, Path path) {
		if (fileVersion >= SCHEMA_VERSION) {
			return;
		}
		for (Migration step : MIGRATIONS) {
			if (step.fromVersion() >= fileVersion) {
				step.apply().accept(file);
			}
		}
		Industrialization.LOGGER.info("[config] migrated {} from schemaVersion {} to {}",
				path, fileVersion, SCHEMA_VERSION);
	}

	/**
	 * The section's object inside {@code file}, or an empty one when the section is absent (every key
	 * in it then falls back to its live value, the same contract a missing key has always had). A
	 * section key present with a non-object value is a typo in the operator's file and throws, so the
	 * load aborts before any commit instead of silently ignoring a whole group of knobs.
	 */
	private static JsonObject sectionBody(JsonObject file, Section section) {
		JsonElement e = file.get(section.id);
		if (e == null || e.isJsonNull()) {
			return new JsonObject();
		}
		if (e.isJsonObject()) {
			return e.getAsJsonObject();
		}
		throw new IllegalArgumentException("config section '" + section.id + "' must be an object, got " + e);
	}

	/**
	 * The {@link #BUILTIN_DEFAULTS_KEY} block inside {@code file}, or an empty object when it is absent
	 * or damaged.
	 *
	 * <p><b>Why this one tolerates junk while {@link #sectionBody} throws.</b> A malformed section is
	 * the operator's own data and hiding it would drop a whole group of their edits, so it aborts the
	 * load. This block is the mod's bookkeeping, and the safe reading of a damaged one is "assume every
	 * knob was edited by hand" — which changes nothing at all: every value in the file is kept exactly
	 * as written, and the block is rebuilt by the self-heal. Throwing here would instead refuse the
	 * whole file over a line the operator was told not to touch.
	 */
	private static JsonObject builtinDefaults(JsonObject file) {
		JsonElement e = file.get(BUILTIN_DEFAULTS_KEY);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	/** True when {@code key} carries an actual value in {@code o} (a json null counts as absent). */
	private static boolean present(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && !e.isJsonNull();
	}

	/**
	 * The recorded built-in default for {@code key} as a raw primitive, or {@code null} when it is
	 * absent or not a number. Read at the knob's OWN precision by the caller: widening a recorded
	 * {@code 0.45} to double and comparing it against a {@code float} knob's {@code 0.45f} would never
	 * match, and every float knob would look hand-edited forever.
	 */
	private static JsonPrimitive recordedNumber(JsonObject recorded, String key) {
		JsonElement e = recorded.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
				? e.getAsJsonPrimitive() : null;
	}

	/** Boolean counterpart of {@link #recordedNumber}. */
	private static JsonPrimitive recordedBoolean(JsonObject recorded, String key) {
		JsonElement e = recorded.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()
				? e.getAsJsonPrimitive() : null;
	}

	/**
	 * Restore every tunable to the value compiled into this build — the state a fresh install starts
	 * from. Used by {@link #loadFrom} when the file's schema is from the future: leaving whatever the
	 * previous load happened to apply would mean the server keeps running on a file nobody can read
	 * any more, which is the silent-corruption case this guard exists to prevent.
	 */
	private static void resetToDefaults() {
		for (ConfigField field : FIELDS) {
			field.resetToDefault();
		}
	}

	/** Ignore line-ending + surrounding-whitespace differences when deciding whether to rewrite the file. */
	private static String normalize(String s) {
		return s.replace("\r\n", "\n").strip();
	}

	/**
	 * Read an int by key from a section body with the {@link net.minecraft.util.GsonHelper}-equivalent contract,
	 * but on plain Gson so {@link Config} carries no {@code net.minecraft} dependency: return {@code def} if the
	 * key is absent/null, else the number — and <b>throw</b> if the key is present but not a number (this is what
	 * makes a typo abort the whole load instead of silently applying a partial file). {@code _comment_*} keys are
	 * never requested.
	 */
	private static int getInt(JsonObject o, String key, int def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsInt();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/** Float counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. */
	private static float getFloat(JsonObject o, String key, float def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsFloat();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/**
	 * Double counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. Reads at
	 * double precision on purpose: the previous code funnelled the one double knob through
	 * {@link #getFloat}, so a file holding {@code 0.02} was widened back as {@code 0.019999999552965164}
	 * and the self-heal then rewrote the file with that noise.
	 */
	private static double getDouble(JsonObject o, String key, double def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsDouble();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/** Boolean counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. */
	private static boolean getBool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a boolean, got " + e);
	}

	/**
	 * The whole current balance as the canonical document: {@code schemaVersion} first, then one object
	 * per {@link Section} in enum order, each holding its fields in {@code FIELDS} order with their
	 * inline {@code _comment_} doc above them, and finally the machine-owned
	 * {@link #BUILTIN_DEFAULTS_KEY} block.
	 *
	 * <p>The defaults block always records THIS build's compiled defaults, never the live values: the
	 * file is being written by this build, so "the default this value was saved against" is this
	 * build's default by definition. That is the whole mechanism — after any write, an untouched knob
	 * equals its recorded default, and the next build that changes that default gets to apply it.
	 */
	private static JsonObject snapshot() {
		JsonObject root = new JsonObject();
		root.addProperty("_comment_" + SCHEMA_VERSION_KEY, SCHEMA_VERSION_DOC);
		root.addProperty(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
		for (Section section : Section.values()) {
			JsonObject body = new JsonObject();
			for (ConfigField field : FIELDS) {
				if (field.section == section) {
					field.write(body);
				}
			}
			root.addProperty("_comment_" + section.id, section.doc);
			root.add(section.id, body);
		}
		JsonObject defaults = new JsonObject();
		for (ConfigField field : FIELDS) {
			field.writeDefault(defaults);
		}
		root.addProperty("_comment_" + BUILTIN_DEFAULTS_KEY, BUILTIN_DEFAULTS_DOC);
		root.add(BUILTIN_DEFAULTS_KEY, defaults);
		return root;
	}

	/** Add an inline {@code _comment_<field>} doc string immediately before its field, inside the field's
	 * section object (Gson keeps insertion order, so the comment renders on the line above). Ignored on
	 * read — {@link #getInt}/{@link #getFloat} only ever request the real field keys. */
	private static void c(JsonObject o, String field, String text) {
		o.addProperty("_comment_" + field, text);
	}

	/**
	 * One tunable's read/validate/write behaviour, bound to its declared {@link Field} by reflection.
	 *
	 * <p>Subclasses exist per primitive type for two reasons that outlive the lambdas they replaced: the
	 * serialized json must keep the exact numeric form the knob has (an int must not start rendering as
	 * a double), and each type has to compare the recorded built-in default at its OWN precision.
	 *
	 * <p>The range lives on the base class as plain {@code double}s. An absent bound is
	 * {@link Double#NEGATIVE_INFINITY}, which fails every comparison on its own — so "no bound" needs no
	 * null check and no separate code path.
	 */
	private abstract static class ConfigField {
		/** Json key of the knob — always the java field's own name, so the two cannot drift. */
		final String key;
		/** Which JSON object this key lives in (MOD-402); declared on the field, never in a side table. */
		final Section section;
		final String doc;
		/** The declared field itself; read and written reflectively. See {@link #buildRegistry()}. */
		final Field field;
		/** Lowest accepted value, or {@code -inf} when the knob declares no bound. */
		final double min;
		/** True when {@link #min} is exclusive (the value must be strictly greater). */
		final boolean exclusive;
		/** Replacement for a rejected value, or {@link Double#NaN} for "restore the compiled default". */
		final double floorTo;

		ConfigField(Field field, Knob knob) {
			this.key = field.getName();
			this.section = knob.section();
			this.doc = knob.doc();
			this.field = field;
			this.min = knob.min();
			this.exclusive = knob.exclusive();
			this.floorTo = knob.floorTo();
		}

		/**
		 * The entry for {@code field}'s primitive type. A type with no reader fails here rather than being
		 * skipped: a knob the loader cannot handle must not become a knob the loader silently ignores.
		 */
		static ConfigField of(Field field, Knob knob) {
			Class<?> type = field.getType();
			if (type == int.class) {
				return new IntField(field, knob);
			}
			if (type == float.class) {
				return new FloatField(field, knob);
			}
			if (type == double.class) {
				return new DoubleField(field, knob);
			}
			if (type == boolean.class) {
				return new BoolField(field, knob);
			}
			throw new IllegalStateException("@Knob field " + field.getName()
					+ " has type " + type.getName() + ", which the config loader cannot read");
		}

		/**
		 * The live value, boxed. The field is public, static and non-final on this very class (checked in
		 * {@link #buildRegistry()}), so an {@link IllegalAccessException} here cannot be a runtime state —
		 * only a programming error, which is why it is rethrown rather than handled.
		 */
		Object live() {
			try {
				return field.get(null);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("config field " + key + " is not readable", e);
			}
		}

		/** Write {@code value} into the live field. Same accessibility contract as {@link #live()}. */
		void assign(Object value) {
			try {
				field.set(null, value);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("config field " + key + " is not writable", e);
			}
		}

		/** True when {@code v} is below this knob's declared bound and must be replaced. */
		boolean rejects(double v) {
			return exclusive ? v <= min : v < min;
		}

		/**
		 * Parse and validate this field out of {@code body} — its own section's object — returning the
		 * action that commits it. Throws if the key is present with a wrong type: that is what aborts
		 * the whole load before any field is applied, keeping {@link #loadFrom} all-or-nothing.
		 *
		 * <p>{@code recordedDefaults} is the file's {@link #BUILTIN_DEFAULTS_KEY} block (MOD-553). When
		 * the file's value for this key is exactly the default recorded beside it AND this build ships a
		 * different default, the build's default wins and the swap is appended to {@code adopted} for
		 * the load log. That is the end of the "config shadow": before it, {@code snapshot()} wrote every
		 * key on the first run, so every knob was pinned by the file and a changed default in the code
		 * could never reach an existing install.
		 */
		abstract Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted);

		/**
		 * Append this knob's COMPILED default (the value captured when the registry was built, never the
		 * live one) to the machine-owned defaults block. See {@link #snapshot()}.
		 */
		abstract void writeDefault(JsonObject out);

		/** Record an adopted default for the one-line load report: {@code key: old -> new}. */
		static void recordAdoption(List<String> adopted, String key, Object from, Object to) {
			adopted.add(key + ": " + from + " -> " + to);
		}

		/**
		 * Report an out-of-range value being replaced by a safe one. Shared by all three numeric
		 * field types so the three call sites cannot drift apart in wording, and so the player sees
		 * the same line whichever key they got wrong. Says what was rejected AND what replaced it —
		 * "value ignored" alone would leave the player guessing what the mod is actually running on.
		 */
		static void warnOutOfRange(String key, Number rejected, Number replacement) {
			Industrialization.LOGGER.warn(
					"[config] key '{}' has out-of-range value {} — using {} instead. The file will be "
							+ "rewritten with the replacement, so your edit will not survive this run.",
					key, rejected, replacement);
		}

		/** Append the current live value (and its doc comment) to its section in the canonical snapshot. */
		abstract void write(JsonObject out);

		/**
		 * Put the field back to the value compiled into this build (captured when the registry was
		 * built, before any file could touch it). Used by {@link #resetToDefaults()}.
		 */
		abstract void resetToDefault();
	}

	private static final class IntField extends ConfigField {
		private final int fallback;

		IntField(Field field, Knob knob) {
			super(field, knob);
			this.fallback = liveValue();
		}

		private int liveValue() {
			return ((Integer) live()).intValue();
		}

		@Override
		Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
			int v = getInt(body, key, liveValue());
			if (present(body, key) && v != fallback) {
				JsonPrimitive was = recordedNumber(recordedDefaults, key);
				if (was != null && was.getAsInt() == v) {
					recordAdoption(adopted, key, v, fallback);
					v = fallback;
				}
			}
			if (rejects(v)) {
				int replacement = Double.isNaN(floorTo) ? fallback : (int) floorTo;
				// The substitution itself is deliberate and load-bearing -- machineEuPerTick: 0 used to
				// crash the world on a divide-by-zero (MOD-169), and ConfigFileTest pins this behaviour.
				// What was wrong is that it happened in total silence while the canonicalizing self-heal
				// then rewrote the file, so the player's edit was not merely ignored, it was erased from
				// disk with nothing in the log. A wrong TYPE is already reported loudly (loading fails);
				// a wrong VALUE being silent was an unexplained asymmetry. Behaviour unchanged -- only
				// the reporting.
				warnOutOfRange(key, v, replacement);
				v = replacement;
			}
			int applied = v;
			return () -> assign(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, liveValue());
		}

		@Override
		void writeDefault(JsonObject out) {
			out.addProperty(key, fallback);
		}

		@Override
		void resetToDefault() {
			assign(fallback);
		}
	}

	private static final class FloatField extends ConfigField {
		private final float fallback;

		FloatField(Field field, Knob knob) {
			super(field, knob);
			this.fallback = liveValue();
		}

		private float liveValue() {
			return ((Float) live()).floatValue();
		}

		@Override
		Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
			float v = getFloat(body, key, liveValue());
			if (present(body, key) && Float.compare(v, fallback) != 0) {
				JsonPrimitive was = recordedNumber(recordedDefaults, key);
				// Compared as a FLOAT: the file's 0.45 read as a double is 0.4500000000000000111,
				// while the knob's 0.45f widens to 0.4499999880790710 — every float knob would look
				// hand-edited forever if the two were compared at double precision.
				if (was != null && Float.compare(was.getAsFloat(), v) == 0) {
					recordAdoption(adopted, key, v, fallback);
					v = fallback;
				}
			}
			if (rejects(v)) {
				float replacement = Double.isNaN(floorTo) ? fallback : (float) floorTo;
				warnOutOfRange(key, v, replacement);
				v = replacement;
			}
			float applied = v;
			return () -> assign(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, liveValue());
		}

		@Override
		void writeDefault(JsonObject out) {
			out.addProperty(key, fallback);
		}

		@Override
		void resetToDefault() {
			assign(fallback);
		}
	}

	private static final class DoubleField extends ConfigField {
		private final double fallback;

		DoubleField(Field field, Knob knob) {
			super(field, knob);
			this.fallback = liveValue();
		}

		private double liveValue() {
			return ((Double) live()).doubleValue();
		}

		@Override
		Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
			double v = getDouble(body, key, liveValue());
			if (present(body, key) && Double.compare(v, fallback) != 0) {
				JsonPrimitive was = recordedNumber(recordedDefaults, key);
				if (was != null && Double.compare(was.getAsDouble(), v) == 0) {
					recordAdoption(adopted, key, v, fallback);
					v = fallback;
				}
			}
			if (rejects(v)) {
				double replacement = Double.isNaN(floorTo) ? fallback : floorTo;
				warnOutOfRange(key, v, replacement);
				v = replacement;
			}
			double applied = v;
			return () -> assign(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, liveValue());
		}

		@Override
		void writeDefault(JsonObject out) {
			out.addProperty(key, fallback);
		}

		@Override
		void resetToDefault() {
			assign(fallback);
		}
	}

	private static final class BoolField extends ConfigField {
		private final boolean fallback;

		BoolField(Field field, Knob knob) {
			super(field, knob);
			this.fallback = liveValue();
		}

		private boolean liveValue() {
			return ((Boolean) live()).booleanValue();
		}

		@Override
		Runnable stage(JsonObject body, JsonObject recordedDefaults, List<String> adopted) {
			boolean v = getBool(body, key, liveValue());
			if (present(body, key) && v != fallback) {
				JsonPrimitive was = recordedBoolean(recordedDefaults, key);
				if (was != null && was.getAsBoolean() == v) {
					recordAdoption(adopted, key, v, fallback);
					v = fallback;
				}
			}
			boolean applied = v;
			return () -> assign(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, liveValue());
		}

		@Override
		void writeDefault(JsonObject out) {
			out.addProperty(key, fallback);
		}

		@Override
		void resetToDefault() {
			assign(fallback);
		}
	}

}

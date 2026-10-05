package dev.alaindustrial.core.radiation;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Radiation and Geiger counter knobs moved out of {@code Config} by
 * {@code docs/tools/authoring/config_holder_codemod.py}
 * (MOD-710, ADR-034). The json key of each knob is still its field name and its section is still
 * declared on the field, so the operator's file is unchanged; {@code Config.REGISTRY} scans this
 * class next to {@code Config}.
 */
public final class RadiationConfig {

	private RadiationConfig() {
	}

	// ── MOD-470: radiation, dose and the shielding suit ───────────────────────────────────────────
	/** Radiation master switch: off, nothing irradiates anything. */
	@Knob(section = Section.SAFETY,
			doc = "When true, uranium and fuelled reactor rods irradiate players. false disables the entire mechanic,"
					+ " including suit wear.")
	public static boolean radiationEnabled = true;
	/**
	 * Depth of the dose scale in ticks, which is also the recovery time: dose is the remaining duration of the
	 * radiation effect.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Depth of the radiation dose scale in ticks; also the time a player at the top of the scale needs to"
					+ " recover once clear of every source.")
	public static int radiationDoseCapacity = 6000;
	/**
	 * Ticks between exposure sweeps — and the decay per sweep, since the effect ticks down every tick: a
	 * source adding less than this does nothing. Every per-source dose must clear it.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between radiation exposure sweeps per player.")
	public static int radiationTickInterval = 20;
	/**
	 * Dose per sweep from a fuelled rod within {@link #radiationSourceRadius}, before shielding: an
	 * unprotected player crosses the lethal line in seconds.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep from a fuelled reactor rod in line of sight, before shielding.")
	public static int radiationRodDosePerTick = 900;
	/**
	 * Rod reach in blocks, a hard edge with inverse-square falloff ({@code RadiationCore.attenuate}); a
	 * missing shell is covered by line of sight ({@code RadiationSources}).
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Blocks a fuelled rod irradiates through open air; a shell block in the way stops it entirely.")
	public static int radiationSourceRadius = 6;
	/**
	 * Dose per sweep per {@code #radioactive_low} item: above the decay so ore registers, capped by {@link
	 * #radiationLowDoseCapPercent}.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep per carried item of tag radioactive_low (ore, dust, shavings, depleted rods).")
	public static int radiationDoseLowPerItem = 24;
	/** Dose per sweep per item of {@code #radioactive_medium} — uranium ingots and plates. */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep per carried item of tag radioactive_medium (uranium ingots and plates).")
	public static int radiationDoseMediumPerItem = 30;
	/**
	 * Dose per sweep per {@code #radioactive_high} item: one loose rod fills the scale in about 90 s, a stack
	 * at once.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Dose per sweep per carried item of tag radioactive_high (refined uranium, isotopes, fuel rods)."
					+ " Uncapped: a loose stack kills.")
	public static int radiationDoseHighPerItem = 80;
	/**
	 * Ceiling, in percent of the scale, that {@code #radioactive_low} alone may reach: raw ore makes a miner
	 * queasy, never dead.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Percent of the dose scale that tag radioactive_low alone can reach; inside level I on purpose, so"
					+ " raw ore sickens but never kills.")
	public static int radiationLowDoseCapPercent = 20;
	/** Percent of a dose each worn shielding piece cuts. Four pieces = 100 % of ordinary exposure. */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Percent of incoming dose each worn shielding-suit piece blocks; four pieces block all ordinary"
					+ " exposure.")
	public static int radiationShieldPerPiecePercent = 25;
	/** Cap on shielding against a bare rod in the open, in percent: a full suit buys working time, not immunity. */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Ceiling on suit protection against a rod in line of sight; below 100 on purpose, so a full suit buys"
					+ " working time inside a live reactor rather than immunity.")
	public static int radiationRodShieldCapPercent = 95;
	/** Blocks around the player in which dropped radioactive items are counted. */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Blocks around the player in which dropped radioactive items are counted as a source.")
	public static int radiationGroundRadius = 6;
	/**
	 * How deep to look inside carried containers: 1 = a shulker of rods irradiates, a shulker in a shulker
	 * does not. Zero would make any container a shield.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "How deep to look inside carried containers for radioactive contents; 1 = a shulker box of fuel rods"
					+ " irradiates its carrier.")
	public static int radiationContainerDepth = 1;
	/**
	 * Items' worth of radiation one placed container may leak whatever it holds (MOD-474), times {@link
	 * #radiationDoseHighPerItem}; 0 stops containers radiating. Keeps deaths reversible (MOD-470).
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "How many items' worth of radiation one container in the world may leak regardless of how much it"
					+ " holds; 0 stops containers radiating entirely. Without a cap a chest of refined uranium killed"
					+ " instantly across the whole radius.")
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
	/** Damage per hit at the top of the scale (MOD-470): about 20 s for a healthy player to reach the door. */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage per radiation hit at the top of the dose scale, in half-hearts.")
	public static float radiationDamageLethal = 2.0f;
	/**
	 * Absorbed dose per point of suit wear, at most one point per piece per sweep ({@code
	 * RadiationCore.wearInterval}): suit life is a TIME — a point a second beside a live core.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Dose the shielding suit absorbs per point of durability spent; the suit is a consumable, not a"
					+ " permanent answer.")
	public static int radiationDosePerSuitDurability = 200;
	/**
	 * Geiger counter reach, deliberately beyond {@link #radiationSourceRadius} (MOD-475): past the hazard
	 * radius everything it says is pure warning.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "How far a Geiger counter hears radiation. Larger than the radius radiation actually "
					+ "reaches, on purpose: the counter has to warn before the dose starts.")
	public static int geigerRadius = 16;
	/**
	 * How far the counter hears ore in the rock; 0 disables it (MOD-475). Ore in the wall adds no dose; its
	 * grade comes from distance bands, not the field.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "How far a Geiger counter hears uranium ore still in the rock. Ore in the wall gives "
					+ "no dose at all — this only moves the needle. 0 disables the ore scan.")
	public static int geigerOreRadius = 16;
	/**
	 * Field at which the counter stops being silent (MOD-475). Silence means nothing here at all; keep it at 1
	 * or ore goes unheard.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Field at which the Geiger counter starts clicking. Below it the instrument is silent.")
	public static int geigerFaintThreshold = 1;
	/** Second step of the counter: from occasional clicks to an audible rattle. */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Field at which the counter moves from occasional clicks to an audible rattle.")
	public static int geigerBusyThreshold = 20;
	/** Third step: a dense rattle. Irradiated soil and a leaking chest live here. */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Field at which the counter's rattle becomes dense.")
	public static int geigerLoudThreshold = 100;
	/** Field at which the counter saturates and roars (MOD-475); the dosimeter reads above it. */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Field above which the Geiger counter stops telling levels apart and just roars.")
	public static int geigerOffScaleThreshold = 400;
	/**
	 * Click volume in percent (MOD-475): the server's ceiling on top of the player's own PLAYERS slider. 0
	 * silences the counter.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Loudness of the Geiger counter's clicks, in percent. 0 silences it; players also "
					+ "have the vanilla Players volume slider.")
	public static int geigerVolumePercent = 60;
	/**
	 * Promille of a source's field the DETECTOR hears through a solid block (MOD-579); the dose is unchanged.
	 * 0 = all-or-nothing.
	 */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Promille of a shielded source the Geiger counter still hears (dose is unaffected).")
	public static int geigerWallPermille = 150;
	/**
	 * Whether radiation transforms villagers, wandering traders and cows (MOD-470), the one exception to "mobs
	 * are never irradiated"; nothing dies of it.
	 */
	@Knob(section = Section.SAFETY,
			doc = "When true, radiation turns villagers and wandering traders into zombie villagers and cows into"
					+ " mooshrooms. false disables all three transformations.")
	public static boolean radiationMobsEnabled = true;
	/**
	 * Dose percent at which a villager becomes a zombie villager and a cow a mooshroom: while the player is
	 * still watching.
	 */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Percent of the dose scale at which an irradiated villager or cow transforms.")
	public static int radiationMobConvertPercent = 50;
	/** Ticks between the light hits a sickening villager takes. A cow takes none — it just changes. */
	@Knob(section = Section.SAFETY, min = 1,
			doc = "Ticks between the light hits an irradiated villager takes before it transforms.")
	public static int radiationMobDamageIntervalTicks = 60;
}

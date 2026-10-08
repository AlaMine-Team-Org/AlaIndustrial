package dev.alaindustrial.item;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Powered tool knobs moved out of {@code Config} by {@code docs/tools/authoring/config_holder_codemod.py}
 * (MOD-710, ADR-034). The json key of each knob is still its field name and its section is still
 * declared on the field, so the operator's file is unchanged; {@code Config.REGISTRY} scans this
 * class next to {@code Config}.
 */
public final class ToolConfig {

	private ToolConfig() {
	}

	// --- Shielding Pouch (MOD-545, radiation-proof carrier) ---
	/** Shielding Pouch storage capacity in weight units, same bundle math as the Battery Pouch.
	 * 128 = two stacks of ordinary items, which is the mining trip the pouch exists for: enough for
	 * the ore a player digs out in one run without turning into bulk uranium logistics. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "Shielding Pouch item-storage capacity in weight units (one ordinary item = 1).")
	public static int shieldingPouchCapacity = 128;
	// --- Battery Pouch (MOD-052, powered item) ---
	/** Pouch storage capacity in weight units (vanilla-bundle math: one item weighs 64/maxStackSize).
	 * 128 = exactly twice a vanilla bundle, ≈ two stacks of ordinary items. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
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
			doc = "EU a blank Energy Crystal must absorb to become a crystal. Priming time is this divided by the"
					+ " charge rate below.")
	public static int energyCrystalBuffer = 100_000;
	/** Max EU/tick an Energy Crystal blank accepts in a charge slot; the MV ceiling, see the note above. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t an Energy Crystal accepts in a charge slot. A charge slot caps at 128 regardless, so higher"
					+ " values do nothing until an HV item charger exists.")
	public static int energyCrystalInputRate = 128;
	/** EU a blank Lapotron Crystal must absorb. ~3 min 15 s at 128 EU/t. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU a blank Lapotron Crystal must absorb. It fills from empty - the finished Energy Crystal it is"
					+ " built from carries no charge.")
	public static int lapotronCrystalBuffer = 500_000;
	/** Max EU/tick a Lapotron Crystal blank accepts in a charge slot. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t a Lapotron Crystal accepts in a charge slot.")
	public static int lapotronCrystalInputRate = 128;
	/** EU a blank Resonant Crystal must absorb — the end of the ladder. ~9 min 45 s at 128 EU/t. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU a blank Resonant Crystal must absorb. Sized against the 128 EU/t charge ceiling; raise it only"
					+ " together with an HV item charger.")
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
	// --- Electric Drill (MOD-079, first powered hand tool) ---
	/** Electric Drill EU buffer — half an Energy Pack, five pouches' worth. At {@link #electricDrillEuPerBlock}
	 * per block this is ~200 blocks on a full charge. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Drill EU buffer.")
	public static int electricDrillBuffer = 10_000;
	/** EU drained per block the drill successfully mines while it has at least this much charge. Below it the
	 * drill still mines (and drops), but at hand speed and free — see ElectricDrillItem. Kept under the LV
	 * machine floor (200 EU/op): breaking a block is cheaper than smelting one. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "EU the drill spends per block mined at powered speed (below this it mines at hand speed for free).")
	public static int electricDrillEuPerBlock = 50;
	/** Max EU/tick the drill accepts while sitting in a charge slot. At the LV ceiling a full charge from a
		Battery Box takes ~313 ticks (~16 s). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the drill accepts while charging in a slot.")
	public static int electricDrillInputRate = 32;
	/**
	 * EU the drill spends placing a torch on right-click (MOD-089), cheaper than a block; below it the drill
	 * refuses instead of placing for free (MOD-097).
	 */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "EU the drill spends to place a torch on right-click.")
	public static int electricDrillTorchEuCost = 5;
	/**
	 * Netherite-tipped drill buffer (MOD-534), the third tier's only own number: about 300 blocks a charge.
	 * The per-block cost stays; the tier is paid in its recipe.
	 */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Netherite-Tipped Electric Drill EU buffer (the two tiers below share electricDrillBuffer).")
	public static int electricDrillNetheriteTipBuffer = 15_000;
	/**
	 * EU per EXTRA block a column-bore drill breaks above and below (MOD-482): 1.5x the base, so a stroke
	 * costs 200 for three blocks. A drill short of the whole column breaks one.
	 */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "EU the drill spends per extra block of a column bore (the block above and below the one hit).")
	public static int electricDrillColumnEuPerBlock = 75;
	// --- Electric Chainsaw (MOD-337, the drill's wood-side counterpart) ---
	/** Electric Chainsaw EU buffer — the same reservoir as the drill, so the two tools of the LV hand-tool
	 * line charge and last alike. At {@link #electricChainsawEuPerBlock} per block this is ~333 logs on a
	 * full charge. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Chainsaw EU buffer.")
	public static int electricChainsawBuffer = 10_000;
	/**
	 * EU per block the chainsaw cuts while charged; below it the chainsaw cuts at hand speed for free
	 * (ElectricChainsawItem). Cheaper than the drill: wood is softer.
	 */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
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
	/**
	 * EU per block the shovel digs while charged; below it the shovel digs at hand speed for free
	 * (ElectricShovelItem). Cheaper than the chainsaw.
	 */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
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
	/**
	 * EU per block the hoe breaks while charged; below it, hand speed and free (ElectricHoeItem). The drill's
	 * 50 by customer decision; tilling is {@link #electricHoeTillEuCost}.
	 */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "EU the hoe spends per block broken at powered speed (below this it breaks at hand speed for free)."
					+ " Tilling is powered too and is billed separately by electricHoeTillEuCost.")
	public static int electricHoeEuPerBlock = 50;
	/**
	 * EU per right-click conversion (tilling, coarse dirt to dirt): the hoe's job is powered. Below it the hoe
	 * tills nothing and says so, like the drill's torch.
	 */
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
	/**
	 * EU per hit on a living target while the saber is on; below it the saber hits as a plain sword for free
	 * (ElectricSaberItem). Twice a drill block.
	 */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
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
	// --- Electric Bow (MOD-363, the line's ranged weapon) ---
	/** Electric Bow EU buffer — the line's shared reservoir. At {@link #electricBowEuPerShot} per shot
	 * this is 66 powered shots. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Electric Bow EU buffer.")
	public static int electricBowBuffer = 10_000;
	/** EU spent per powered shot. Below it the bow still shoots, but as a plain bow and for free — see
	 * ElectricBowItem. Arrows are still consumed either way: EU buys a better shot, not free ammunition.
	 * One and a half times the saber's hit: a shot lands from forty blocks away. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "EU the bow spends per powered shot (below this it shoots as a plain bow for free).")
	public static int electricBowEuPerShot = 150;
	/** Max EU/tick the bow accepts while sitting in a charge slot — the LV ceiling, like its siblings. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the bow accepts while charging in a slot.")
	public static int electricBowInputRate = 32;
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
	 * blocks; the advanced grade has its own {@link #magnetAdvancedRange} (MOD-580). */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "Electromagnet pull radius in blocks around the carrier.")
	public static int magnetRange = 5;
	/** EU spent per item actually pulled, each tick it is being drawn in. An idle scan (nothing in range)
	 * is free, so the magnet is a consumable and not a free vacuum. Small next to the large buffer. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "EU the electromagnet spends per item pulled each scan tick (an idle scan is free).")
	public static int magnetEuPerItem = 2;
	/** How often (ticks) the magnet scans for and pulls nearby drops. 1 = every tick, for a smooth, fast
	 * XP-orb-like pull that visibly flies items in (a coarser interval read as "barely pulling"). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "How often (ticks) the electromagnet scans for and pulls nearby drops.")
	public static int magnetScanIntervalTicks = 1;
	// --- Advanced Electromagnet (MOD-580, tier 2) ---
	/** Advanced magnet EU buffer. Four times the basic grade: it reaches further, so it works more. */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Advanced electromagnet EU buffer.")
	public static int magnetAdvancedBuffer = 20_000;
	/** Max EU/tick the advanced magnet accepts while charging (MV ceiling — it is an MV-tier item). */
	@Knob(section = Section.TOOLS, min = 1,
			doc = "Max EU/t the advanced electromagnet accepts while charging in a slot.")
	public static int magnetAdvancedInputRate = 128;
	/** Pull radius of the advanced magnet: 9 against the basic 5, still leaving pipes and the sorter a job. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "Advanced electromagnet pull radius in blocks around the carrier.")
	public static int magnetAdvancedRange = 9;
	/** EU per item pulled by the advanced grade. Same tariff as the basic one: reach is what you bought. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "EU the advanced electromagnet spends per item pulled each scan tick.")
	public static int magnetAdvancedEuPerItem = 2;
	/** EU per experience orb pulled; above an item on purpose, so a mob farm is not a free ride. */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 1,
			doc = "EU the advanced electromagnet spends per experience orb pulled.")
	public static int magnetAdvancedEuPerOrb = 4;
	/**
	 * Below this distance the magnet leaves orbs to vanilla, which already collects them within 8 blocks
	 * ({@code ExperienceOrb.followNearbyPlayer}).
	 */
	@Knob(section = Section.TOOLS, min = 0,
			doc = "Distance under which the magnet leaves experience orbs to vanilla's own pull.")
	public static int magnetVanillaOrbReach = 8;
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
			doc = "Extra step height (hundredths of a block) from charged Fluxweave leggings with the assist toggled"
					+ " on.")
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
	// --- Scythe bonus seed drop (MOD-315) ---
	/**
	 * Global multiplier on the scythe's per-tier bonus-seed chance ({@code ScytheTiers}); 0 disables it,
	 * results clamp at 1 ({@code ScytheItem.Profile.effectiveBonusChance}).
	 */
	@Knob(section = Section.TOOLS, clientVisible = true, min = 0.0, floorTo = 0.0,
			doc = "Global multiplier on the scythe's per-tier bonus-seed chance (1.0 = shipped ladder, 0.0 = mechanic"
					+ " off; a tier is clamped to 1.0).")
	public static double scytheBonusSeedMultiplier = 1.0;
}

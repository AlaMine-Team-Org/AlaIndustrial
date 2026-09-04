package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Loader-neutral {@link TagKey} constants for the mod's own datapack tags. The backing JSON lives in
 * {@code common/src/main/resources/data/alaindustrial/tags/}; both loaders load it from the shared
 * {@code common} resources, so one Java constant + one JSON file cover Fabric and NeoForge.
 */
public final class ModTags {
	private ModTags() {
	}

	/** Block tags. */
	public static final class Blocks {
		private Blocks() {
		}

		/**
		 * Foliage the scythe (MOD-068) clears in its <b>decor</b> mode (plain right-click, MOD-098):
		 * leaves, flowers, saplings, grasses/ferns, mushrooms, vines and roots. Backed by
		 * {@code data/alaindustrial/tags/block/scythe_harvestable.json}. Deliberately excludes crops,
		 * logs, terrain blocks and the over-broad vanilla {@code #replaceable_by_trees} /
		 * {@code #sword_efficient} tags (water/seagrass/melons) — see the OKF spec.
		 */
		public static final TagKey<Block> SCYTHE_HARVESTABLE = key("scythe_harvestable");

		/** Glass the incubator accepts as its dome (MOD-118); any modded glass joins via c:glass_blocks. */
		public static final TagKey<Block> INCUBATOR_DOME_GLASS = key("incubator_dome_glass");

		/**
		 * Glass a crystal greenhouse may be walled with (MOD-505). Its own tag rather than a share of
		 * {@link #INCUBATOR_DOME_GLASS}: the two structures have no reason to move together, and a pack
		 * that widens one should not silently widen the other.
		 */
		public static final TagKey<Block> CRYSTAL_FARM_GLASS = key("crystal_farm_glass");

		/**
		 * Doors a greenhouse shell accepts (MOD-505). Vanilla doors, so the player can walk into the
		 * room with something they already have — the shell only has to be sealed, and a themed farm
		 * door can join this tag later without touching the scanner.
		 */
		public static final TagKey<Block> CRYSTAL_FARM_DOOR = key("crystal_farm_door");

		/**
		 * Crops the scythe (MOD-068) harvests in its <b>crop</b> mode (shift + right-click, MOD-098):
		 * {@code #minecraft:crops} plus {@code sweet_berry_bush}, {@code cactus} and {@code sugar_cane}
		 * — none of those three is in the vanilla {@code #minecraft:crops} tag, so the mod keeps the
		 * whole harvest list in one place instead of pulling {@code #minecraft:crops} into the item
		 * directly. Backed by {@code data/alaindustrial/tags/block/scythe_crops.json}. The scythe only
		 * ever breaks blocks from this tag when they are mature (see {@code ScytheItem}).
		 */
		public static final TagKey<Block> SCYTHE_CROPS = key("scythe_crops");
		/**
		 * Uranium ore as it sits in the rock — the source a Geiger counter is carried into a mine for
		 * (MOD-475).
		 *
		 * <p><b>This tag is about the BLOCK, and it is not the item tag.</b> Mined uranium is already
		 * covered by {@code #alaindustrial:radioactive_low} as an item; ore in the wall was not a
		 * source at all until this task, because nothing scanned for it.
		 *
		 * <p><b>Membership makes a block audible, never harmful.</b> The scan feeds the counter only
		 * ({@code geigerOreRadius}); no member of this tag adds anything to a player's dose
		 * while it is still in the wall, which is what lets the scan skip the line-of-sight test that
		 * every real source has to pass. Backed by
		 * {@code data/alaindustrial/tags/block/radioactive_ore.json}.
		 */
		public static final TagKey<Block> RADIOACTIVE_ORE = key("radioactive_ore");


		/**
		 * Blocks a reactor's lava cannot take (MOD-469).
		 *
		 * <p><b>Membership is "built out of shielding alloy", not "belongs to the reactor".</b> The rule
		 * started as a list of reactor classes and a playtest immediately found the hole: a shielding
		 * chest standing beside a bare core melted, though it is crafted from the very plate the reactor
		 * room is built from. A player who has paid for shielding expects it to shield, and which mod
		 * class the block happens to extend is not something they can see.
		 *
		 * <p>A tag rather than an {@code instanceof} chain so a datapack can extend it, and so adding a
		 * shielded block later is one JSON line instead of an edit to the hazard.
		 */
		public static final TagKey<Block> MELTPROOF = key("meltproof");

		/**
		 * Ground a reactor's fallout may settle on (MOD-471).
		 *
		 * <p>Soil, sand, gravel, stone — the surfaces a crater actually exposes. Kept as a tag rather
		 * than a hardcoded family for the same reason {@link #MELTPROOF} is: a modpack that adds its own
		 * dirt should be able to say so, and a server that wants fallout to stop eating its terrain can
		 * empty the tag instead of turning the whole feature off.
		 */
		public static final TagKey<Block> FALLOUT_REPLACEABLE = key("fallout_replaceable");

		private static TagKey<Block> key(String path) {
			return TagKey.create(Registries.BLOCK, Industrialization.id(path));
		}
	}

	/** Item tags. */
	public static final class Items {
		private Items() {
		}

		/**
		 * Items the worn Energy Pack (MOD-065) must never hand EU to, even though they advertise an energy
		 * capability. Backed by {@code data/alaindustrial/tags/item/no_auto_charge.json}.
		 *
		 * <p>Exists because MOD-084 opened the pack up to <i>other mods'</i> items: the old rule ("skip
		 * other packs") was an {@code instanceof} check and cannot see a foreign charger. Two chargers
		 * that charge each other ping-pong energy and drain the wearer for nothing — a bug shipped and
		 * fixed by other mods (TechReborn #2297). A denylist keeps the default permissive (charge anything
		 * that takes a charge) while letting packs exclude a foreign charger without a code change.
		 */
		public static final TagKey<Item> NO_AUTO_CHARGE = key("no_auto_charge");

		/** What the incubator accepts as irradiation fuel (MOD-118) — uranium ingots today. */
		public static final TagKey<Item> INCUBATOR_FUEL = key("incubator_fuel");

		/**
		 * Everything a machine's upgrade panel accepts anywhere (MOD-392). A tag rather than a hardcoded
		 * item list: before this the slot tested {@code stack.is(MUTE_CHIP)} in three separate places, so
		 * every new upgrade meant hunting all three down. Used for the coarse "is this an upgrade at all"
		 * question (shift-click routing); WHICH arm takes it is decided by the per-kind tags below.
		 * Backed by {@code data/alaindustrial/tags/item/machine_upgrade.json}.
		 */
		public static final TagKey<Item> MACHINE_UPGRADE = key("machine_upgrade");

		/** Upgrades that belong in the mute arm of the panel (MOD-393). */
		public static final TagKey<Item> UPGRADE_MUTE = key("upgrade/mute");

		/** Upgrades that belong in the overclocker arm — the three tiers of the chip (MOD-393). */
		public static final TagKey<Item> UPGRADE_OVERCLOCK = key("upgrade/overclock");

		/** Upgrades that belong in the statistics arm — the statistics chip (MOD-125). */
		public static final TagKey<Item> UPGRADE_STATS = key("upgrade/stats");

		/**
		 * Fibre the Galvanic Bath plates with silver to make flux thread (MOD-127): vanilla string and
		 * our own cotton fibre (MOD-280). A tag rather than two recipes because the two are meant to be
		 * interchangeable by design — string comes from a spider farm, cotton from a trellis, and the
		 * player picks whichever playstyle they already have. Backed by
		 * {@code data/alaindustrial/tags/item/fiber.json}.
		 */
		/**
		 * What the Fermenter accepts at all (MOD-146) — the union of the three price tiers below.
		 * Used for the slot filter; the recipes match the tiers themselves.
		 */
		public static final TagKey<Item> FERMENTER_INPUT =
				TagKey.create(Registries.ITEM, Industrialization.id("fermenter_input"));

		/** Cheapest organic tier: seeds, grass, leaves, saplings, flowers, rot. */
		public static final TagKey<Item> FERMENTER_INPUT_POOR =
				TagKey.create(Registries.ITEM, Industrialization.id("fermenter_input_poor"));

		/** Ordinary harvest: wheat, carrots, melon slices, raw meat, eggs. */
		public static final TagKey<Item> FERMENTER_INPUT_COMMON =
				TagKey.create(Registries.ITEM, Industrialization.id("fermenter_input_common"));

		/** Processed or dense feedstock: golden carrots, cooked food, hay blocks, honey. */
		public static final TagKey<Item> FERMENTER_INPUT_RICH =
				TagKey.create(Registries.ITEM, Industrialization.id("fermenter_input_rich"));

		public static final TagKey<Item> FIBER = key("fiber");

		/**
		 * The conventional umbrella ingot tag ({@code c:ingots}), aggregating every metal ingot in the
		 * pack — ours and other mods'.
		 *
		 * <p>Used by the Alloy Smelter's menu (MOD-064) as the <i>client-side</i> approximation of "this
		 * could be an alloy component". The authoritative rule needs the recipe manager and therefore the
		 * server; without something for the client to predict with, every drag into an input slot would
		 * flicker. Deliberately not used server-side, where the real recipe check runs.
		 */
		public static final TagKey<Item> C_INGOTS =
				TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots"));

		/**
		 * Every grade of wind mill rotor (MOD-385) — what the three wind mills' rotor slot accepts.
		 *
		 * <p>A tag rather than a list of {@code ModContent} comparisons: the check lives in
		 * <b>three</b> block entities (T1 plus both T2 branches), so an explicit list would have to
		 * name all three grades in all three files and would drift the moment a fourth grade appears.
		 * The slot rule itself is unchanged — anything outside the tag is still rejected, both by hand
		 * and through a face (MOD-179).
		 */
		public static final TagKey<Item> WINDMILL_ROTORS = key("windmill_rotors");

		/** Every grade of water mill wheel (MOD-385) — the wheel slot's filter. Twin of {@link #WINDMILL_ROTORS}. */
		public static final TagKey<Item> WATER_MILL_WHEELS = key("water_mill_wheels");

		/**
		 * Every grade of lightning rod conductor tip (MOD-386) — the rod's tip slot filter, and the third
		 * family the repair bench accepts. Same reasoning as the two above: a tag rather than a list, so a
		 * later grade does not have to be named again in the block entity and in the bench.
		 */
		public static final TagKey<Item> CONDUCTOR_TIPS = key("conductor_tips");

		/**
		 * Radioactivity by degree of refinement (MOD-470). Three tags rather than one because a tag
		 * cannot carry a number, and the difference is not cosmetic: {@link #RADIOACTIVE_LOW} is ore and
		 * dust, capped so it can nauseate but never kill — a player mining uranium has not been told
		 * radiation exists yet. {@link #RADIOACTIVE_HIGH} is refined uranium and fuel rods, uncapped: a
		 * stack in the pockets is a death sentence, and that is what makes the shielding chest a
		 * requirement rather than a convenience.
		 */
		public static final TagKey<Item> RADIOACTIVE_LOW = key("radioactive_low");
		public static final TagKey<Item> RADIOACTIVE_MEDIUM = key("radioactive_medium");
		public static final TagKey<Item> RADIOACTIVE_HIGH = key("radioactive_high");

		/** Worn pieces that shield against radiation (MOD-470) — the four parts of the shielding suit. */
		public static final TagKey<Item> RADIATION_SHIELDING = key("radiation_shielding");

		/**
		 * Worn pieces that insulate against a bare cable's shock (MOD-466) — the insulated set, and the
		 * shielding suit alongside it.
		 *
		 * <p><b>The two suits overlap here on purpose, and only here.</b> The shielding suit is sealed
		 * leather and the mod's own rubber (it is even repaired with rubber), so a player who has one on
		 * and still gets electrocuted by a wire is being told the material does not do what the material
		 * obviously does. The reverse does not hold: the insulated set carries no {@link
		 * #RADIATION_SHIELDING}, because rubber stops a current and does nothing at all about a gamma
		 * ray. So the insulated set stays the cheap, early answer to cables, and the shielding suit is the
		 * later one that happens to also cover them.
		 */
		public static final TagKey<Item> SHOCK_INSULATING = key("shock_insulating");

		private static TagKey<Item> key(String path) {
			return TagKey.create(Registries.ITEM, Industrialization.id(path));
		}
	}

	/** Fluid tags. */
	public static final class Fluids {
		private Fluids() {
		}

		/**
		 * The conventional cross-mod oil tag ({@code c:oil}, the de-facto standard used by Ad Astra /
		 * GregTech-style mods). Backed by {@code data/c/tags/fluid/oil.json}, which lists both the
		 * still and the flowing variant (MOD-238). Future recipes consume oil by this tag, not by id,
		 * so another mod's oil is accepted for free.
		 */
		public static final TagKey<Fluid> C_OIL =
				TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "oil"));

		/** Fuel oil (MOD-251) — {@code c:fuel_oil}, still + flowing, same convention as {@link #C_OIL}. */
		public static final TagKey<Fluid> C_FUEL_OIL =
				TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "fuel_oil"));

		/** Biofuel (MOD-146) — {@code c:biofuel}, still + flowing, same convention as {@link #C_OIL}. */
		public static final TagKey<Fluid> C_BIOFUEL =
				TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "biofuel"));
	}
}

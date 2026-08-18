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
		 * Crops the scythe (MOD-068) harvests in its <b>crop</b> mode (shift + right-click, MOD-098):
		 * {@code #minecraft:crops} plus {@code sweet_berry_bush}, {@code cactus} and {@code sugar_cane}
		 * — none of those three is in the vanilla {@code #minecraft:crops} tag, so the mod keeps the
		 * whole harvest list in one place instead of pulling {@code #minecraft:crops} into the item
		 * directly. Backed by {@code data/alaindustrial/tags/block/scythe_crops.json}. The scythe only
		 * ever breaks blocks from this tag when they are mature (see {@code ScytheItem}).
		 */
		public static final TagKey<Block> SCYTHE_CROPS = key("scythe_crops");

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

		/** The three mutation chips that select the incubator's mode (MOD-118). */
		public static final TagKey<Item> MUTATION_CHIP = key("mutation_chip");

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

		/** Diesel (MOD-251) — {@code c:diesel}, still + flowing, same convention as {@link #C_OIL}. */
		public static final TagKey<Fluid> C_DIESEL =
				TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "diesel"));

		/** Fuel oil (MOD-251) — {@code c:fuel_oil}, still + flowing. */
		public static final TagKey<Fluid> C_FUEL_OIL =
				TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "fuel_oil"));

		/**
		 * Every fluid the liquid fuel generator burns (MOD-251 ships the tag, MOD-261 reads it):
		 * diesel + fuel oil + vanilla lava. Lava is the one member the mod does not own — it is in the
		 * tag so one filter covers the whole family.
		 */
		public static final TagKey<Fluid> C_COMBUSTION_FUELS =
				TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "combustion_fuels"));
	}
}

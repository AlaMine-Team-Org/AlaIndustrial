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
	}
}

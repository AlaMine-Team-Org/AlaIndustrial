package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

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

		private static TagKey<Item> key(String path) {
			return TagKey.create(Registries.ITEM, Industrialization.id(path));
		}
	}
}

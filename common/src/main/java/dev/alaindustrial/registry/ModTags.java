package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
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
		 * Foliage the scythe (MOD-068) is allowed to clear in its AOE: leaves, flowers, saplings,
		 * grasses/ferns, mushrooms, vines and roots. Backed by
		 * {@code data/alaindustrial/tags/block/scythe_harvestable.json}. Deliberately excludes crops,
		 * logs, terrain blocks and the over-broad vanilla {@code #replaceable_by_trees} /
		 * {@code #sword_efficient} tags (water/seagrass/melons) — see the OKF spec.
		 */
		public static final TagKey<Block> SCYTHE_HARVESTABLE = key("scythe_harvestable");

		private static TagKey<Block> key(String path) {
			return TagKey.create(Registries.BLOCK, Industrialization.id(path));
		}
	}
}

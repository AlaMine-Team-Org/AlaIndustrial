package dev.alaindustrial.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Stable vanilla creative-tab keys. In MC 26.2 the matching fields on
 * {@code CreativeModeTabs} are private, so mods must address them by registry id.
 */
public final class VanillaCreativeTabs {
	public static final ResourceKey<CreativeModeTab> BUILDING_BLOCKS = key("building_blocks");
	public static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS = key("natural_blocks");
	public static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS = key("functional_blocks");
	public static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES = key("tools_and_utilities");
	public static final ResourceKey<CreativeModeTab> COMBAT = key("combat");
	public static final ResourceKey<CreativeModeTab> INGREDIENTS = key("ingredients");

	private VanillaCreativeTabs() {
	}

	private static ResourceKey<CreativeModeTab> key(String path) {
		return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(path));
	}
}

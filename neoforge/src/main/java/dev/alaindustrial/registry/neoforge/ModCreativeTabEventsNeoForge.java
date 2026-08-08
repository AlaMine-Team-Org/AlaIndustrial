package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.registry.CreativeTabContent;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.VanillaCreativeTabs;
import java.util.Collection;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Adds public Ala Industrial content to the matching vanilla creative tabs on NeoForge. */
public final class ModCreativeTabEventsNeoForge {
	private ModCreativeTabEventsNeoForge() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(ModCreativeTabEventsNeoForge::buildCreativeTabContents);
	}

	/** Package-private rather than private so the MOD-349 regression test can drive the real chain. */
	static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(VanillaCreativeTabs.COMBAT)) {
			insertAfter(event, Items.IRON_SWORD.getDefaultInstance(), ModContent.TEMPERED_IRON_SWORD.get().getDefaultInstance());
			ItemStack helmet = ModContent.TEMPERED_IRON_HELMET.get().getDefaultInstance();
			ItemStack chestplate = ModContent.TEMPERED_IRON_CHESTPLATE.get().getDefaultInstance();
			ItemStack leggings = ModContent.TEMPERED_IRON_LEGGINGS.get().getDefaultInstance();
			ItemStack boots = ModContent.TEMPERED_IRON_BOOTS.get().getDefaultInstance();
			insertAfter(event, Items.IRON_BOOTS.getDefaultInstance(), helmet);
			insertAfter(event, helmet, chestplate);
			insertAfter(event, chestplate, leggings);
			insertAfter(event, leggings, boots);
			// The Energy Pack is worn in the chest slot, so a player looking for chest gear finds it here
			// too — it also sits with the other powered items under Tools & Utilities below.
			insertAfter(event, boots, ModContent.ENERGY_PACK.get().getDefaultInstance());
			// The Jetpack is chest gear too (MOD-148) — it sits right after the pack here.
			insertAfter(event, ModContent.ENERGY_PACK.get().getDefaultInstance(),
					ModContent.JETPACK.get().getDefaultInstance());
		} else if (event.getTabKey().equals(VanillaCreativeTabs.TOOLS_AND_UTILITIES)) {
			ItemStack pickaxe = ModContent.TEMPERED_IRON_PICKAXE.get().getDefaultInstance();
			ItemStack axe = ModContent.TEMPERED_IRON_AXE.get().getDefaultInstance();
			ItemStack shovel = ModContent.TEMPERED_IRON_SHOVEL.get().getDefaultInstance();
			ItemStack hoe = ModContent.TEMPERED_IRON_HOE.get().getDefaultInstance();
			// Each scythe sits right after the matching vanilla hoe tier. The iron scythe follows the
			// vanilla iron hoe; the tempered-iron scythe follows the mod's tempered-iron hoe.
			insertAfter(event, Items.WOODEN_HOE.getDefaultInstance(), ModContent.SCYTHE_WOOD.get().getDefaultInstance());
			insertAfter(event, Items.STONE_HOE.getDefaultInstance(), ModContent.SCYTHE_STONE.get().getDefaultInstance());
			insertAfter(event, Items.COPPER_HOE.getDefaultInstance(), ModContent.SCYTHE_COPPER.get().getDefaultInstance());
			ItemStack ironScythe = ModContent.SCYTHE_IRON.get().getDefaultInstance();
			insertAfter(event, Items.IRON_HOE.getDefaultInstance(), ironScythe);
			// The tempered-iron tool set is the mod's tier between iron and gold, so it follows the iron
			// scythe right after the vanilla iron hoe.
			insertAfter(event, ironScythe, pickaxe);
			insertAfter(event, pickaxe, axe);
			insertAfter(event, axe, shovel);
			insertAfter(event, shovel, hoe);
			insertAfter(event, hoe, ModContent.SCYTHE_TEMPERED_IRON.get().getDefaultInstance());
			insertAfter(event, Items.GOLDEN_HOE.getDefaultInstance(), ModContent.SCYTHE_GOLD.get().getDefaultInstance());
			insertAfter(event, Items.DIAMOND_HOE.getDefaultInstance(), ModContent.SCYTHE_DIAMOND.get().getDefaultInstance());
			insertAfter(event, Items.NETHERITE_HOE.getDefaultInstance(), ModContent.SCYTHE_NETHERITE.get().getDefaultInstance());
			insertAfter(event, Items.COMPASS.getDefaultInstance(), ModContent.NETWORK_ANALYZER.get().getDefaultInstance());
			event.accept(ModContent.WRENCH.get());
			event.accept(ModContent.BATTERY_POUCH.get());
			event.accept(ModContent.ENERGY_PACK.get());
			event.accept(ModContent.ELECTRIC_DRILL.get());
			event.accept(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get());
			// MOD-374/MOD-378: mirrors the Fabric list, which already accepts both upgrades here. (The
			// base chainsaw, shovel and hoe are still Fabric-only in this vanilla tab — a pre-existing
			// asymmetry left alone deliberately; the mod's own tab carries all of them on both loaders.)
			event.accept(ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get());
			event.accept(ModContent.ELECTRIC_HOE_DIAMOND_TIP.get());
			event.accept(ModContent.ELECTROMAGNET.get());
			event.accept(ModContent.JETPACK.get());
		} else if (event.getTabKey().equals(VanillaCreativeTabs.INGREDIENTS)) {
			CreativeTabContent.ingredients(event::accept);
		} else if (event.getTabKey().equals(VanillaCreativeTabs.BUILDING_BLOCKS)) {
			CreativeTabContent.buildingBlocks(event::accept);
		} else if (event.getTabKey().equals(VanillaCreativeTabs.NATURAL_BLOCKS)) {
			CreativeTabContent.naturalBlocks(event::accept);
		} else if (event.getTabKey().equals(VanillaCreativeTabs.FUNCTIONAL_BLOCKS)) {
			CreativeTabContent.functionalBlocks(event::accept);
		}
	}

	/**
	 * Places {@code stack} directly after {@code anchor}, appending it at the end of the tab instead when
	 * the anchor is absent.
	 *
	 * <p><b>Why the guard.</b> {@link BuildCreativeModeTabContentsEvent#insertAfter} asserts the anchor is
	 * already in the tab and throws {@link IllegalArgumentException} when it is not — NeoForge documents
	 * that throw as intended behaviour, so it will not soften upstream. Every anchor this class positions
	 * against is foreign content (vanilla swords, boots, hoes, a compass), and any other mod may take those
	 * out of the vanilla tabs. When one does, the throw escapes tab construction and takes the client down
	 * before the player reaches a world. MOD-349 is exactly that report, against a mod that strips vanilla
	 * weapons; the same crash is open against several other mods, so it is a live cross-mod hazard rather
	 * than a theoretical one.
	 *
	 * <p><b>Why it cannot change existing placement.</b> The check evaluates the same predicate the assert
	 * does: NeoForge builds both entry sets with {@code ItemStackLinkedSet.TYPE_AND_TAG} (see
	 * {@code EventHooks#onCreativeModeTabBuildContents}), a strategy keyed on item type plus components, so
	 * a freshly built anchor stack matches the entry already in the set. While the anchor is present the
	 * call is byte-for-byte the old one; only its absence now degrades to an append — which is what
	 * Fabric's creative-tab API already does on its own, so the two loaders now fail the same way.
	 *
	 * <p><b>Why the two sets are guarded separately.</b> {@code insertAfter} asserts against the parent and
	 * search sets independently, and the sets legitimately differ — vanilla contributes some entries as
	 * search-only. A single combined check passes on one set and still throws on the other.
	 *
	 * <p>Deliberately NOT done here: swallowing {@link RuntimeException} around the whole call, and skipping
	 * a stack that is already in the tab. Both would also mask the duplicate-entry throw, which is a
	 * genuine defect detector for our own content — it is how the double-registration in MOD-280 was
	 * caught. This guard covers the missing anchor only.
	 */
	static void insertAfter(BuildCreativeModeTabContentsEvent event, ItemStack anchor, ItemStack stack) {
		placeOrAppend(event, event.getParentEntries(), anchor, stack,
				CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
		placeOrAppend(event, event.getSearchEntries(), anchor, stack,
				CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
	}

	/**
	 * {@code entries} is the unmodifiable view over the very set {@code insertAfter} asserts against, so
	 * {@code contains} here and the assert there can never disagree.
	 */
	private static void placeOrAppend(BuildCreativeModeTabContentsEvent event, Collection<ItemStack> entries,
			ItemStack anchor, ItemStack stack, CreativeModeTab.TabVisibility visibility) {
		if (entries.contains(anchor)) {
			event.insertAfter(anchor, stack, visibility);
		} else {
			event.accept(stack, visibility);
		}
	}
}

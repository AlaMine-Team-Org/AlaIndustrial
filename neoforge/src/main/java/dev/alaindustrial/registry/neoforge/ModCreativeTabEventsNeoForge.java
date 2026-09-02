package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.registry.CreativeTabContent;
import dev.alaindustrial.registry.VanillaCreativeTabs;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Adds public Ala Industrial content to the matching vanilla creative tabs on NeoForge.
 *
 * <p><b>What lives here since MOD-555:</b> the NeoForge placement MECHANISM, and only that. Which items
 * go into which vanilla tab, in which order, and against which anchor is one shared list in
 * {@link CreativeTabContent}; this file supplies the {@link CreativeTabContent.AnchoredSink} that knows
 * how to say "after this one" on NeoForge — a chain of single-stack inserts, each guarded against a
 * missing anchor (see {@link #insertAfter}). Before that, the Combat and Tools &amp; Utilities lists were
 * written out here AND in Fabric's {@code ModItems}, and they drifted: MOD-478 is two releases in which
 * the base chainsaw, shovel and hoe reached the vanilla tab on Fabric only.
 */
public final class ModCreativeTabEventsNeoForge {
	private ModCreativeTabEventsNeoForge() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(ModCreativeTabEventsNeoForge::buildCreativeTabContents);
	}

	/** Package-private rather than private so the MOD-349 regression test can drive the real chain. */
	static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(VanillaCreativeTabs.COMBAT)) {
			CreativeTabContent.combat(anchored(event));
		} else if (event.getTabKey().equals(VanillaCreativeTabs.TOOLS_AND_UTILITIES)) {
			CreativeTabContent.toolsAndUtilities(anchored(event));
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
	 * The tab-contents event, seen as the loader-neutral {@link CreativeTabContent.AnchoredSink} the two
	 * vanilla-tab lists are written against (MOD-555).
	 *
	 * <p><b>Why a chain.</b> NeoForge's {@code insertAfter} takes ONE stack, so a group of four goes in as
	 * anchor → first, first → second, second → third, third → fourth. Fabric's output takes the whole
	 * group at once. Both land the same order; the difference is the loader's, not the list's.
	 */
	private static CreativeTabContent.AnchoredSink anchored(BuildCreativeModeTabContentsEvent event) {
		return new CreativeTabContent.AnchoredSink() {
			@Override
			public void accept(ItemLike item) {
				event.accept(item);
			}

			@Override
			public void insertAfter(ItemLike anchor, List<ItemLike> items) {
				chainAfter(event, anchor, items);
			}
		};
	}

	/**
	 * The chain itself, outside the anonymous class on purpose: in there the neutral
	 * {@code insertAfter(ItemLike, List)} shadows this class's guarded {@code insertAfter(event, …)} by
	 * name, so the call would have to be qualified — and a qualified call is indistinguishable from the
	 * raw {@code event.insertAfter(…)} the {@code creative-tab-inserts-guarded} rule bans.
	 */
	private static void chainAfter(BuildCreativeModeTabContentsEvent event, ItemLike anchor,
			List<ItemLike> items) {
		ItemStack previous = anchor.asItem().getDefaultInstance();
		for (ItemLike item : items) {
			ItemStack stack = item.asItem().getDefaultInstance();
			insertAfter(event, previous, stack);
			previous = stack;
		}
	}

	/**
	 * Places {@code stack} directly after {@code anchor}, appending it at the end of the tab instead when
	 * the anchor is absent.
	 *
	 * <p><b>Why the guard.</b> {@link BuildCreativeModeTabContentsEvent#insertAfter} asserts the anchor is
	 * already in the tab and throws {@link IllegalArgumentException} when it is not — NeoForge documents
	 * that throw as intended behaviour, so it will not soften upstream. Every vanilla anchor the shared
	 * list positions against is foreign content (swords, boots, hoes, a compass), and any other mod may
	 * take those out of the vanilla tabs. When one does, the throw escapes tab construction and takes the
	 * client down before the player reaches a world. MOD-349 is exactly that report, against a mod that
	 * strips vanilla weapons; the same crash is open against several other mods, so it is a live cross-mod
	 * hazard rather than a theoretical one.
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

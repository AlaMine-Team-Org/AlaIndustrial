package dev.alaindustrial.registry.neoforge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.VanillaCreativeTabs;
import it.unimi.dsi.fastutil.Hash;
import java.lang.reflect.Field;
import java.util.List;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-349 — the mod must survive a third-party mod removing the vanilla items we position against.
 *
 * <p><b>The defect.</b> {@link ModCreativeTabEventsNeoForge} places mod gear relative to vanilla anchors
 * ({@code IRON_SWORD}, {@code IRON_BOOTS}, the hoe tiers, {@code COMPASS}).
 * {@link BuildCreativeModeTabContentsEvent#insertAfter} throws {@link IllegalArgumentException} when the
 * anchor is not in the tab, and NeoForge documents that throw as intended. A mod that strips vanilla
 * weapons therefore turned OUR tab contribution into a hard crash during creative-tab construction —
 * reported from the field, and the same crash is open against several other mods.
 *
 * <p><b>Why the negative control matters.</b> A test that only calls the guarded helper and sees no
 * exception is green whether or not the guard does anything — the crash needs a specific, easily-lost
 * precondition (an entry set that genuinely lacks the anchor). {@link #rawInsertAfterStillThrows} asserts
 * the UNGUARDED call still blows up on the very sets the other tests use. If it ever stops throwing, the
 * harness has stopped reproducing the hazard and the other assertions have quietly become decoration.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class CreativeTabAnchorSafetyTest {

	/**
	 * The exact hash strategy NeoForge builds the tab's entry sets with (see
	 * {@code EventHooks#onCreativeModeTabBuildContents}). It is keyed on item type plus components, which
	 * is what lets a freshly built anchor stack match an entry already in the set — the property the
	 * guard's {@code contains} check rests on. The field is private, so the test reads it reflectively
	 * rather than substituting a different strategy: a weaker one would change what {@code contains}
	 * means and quietly stop testing the real thing.
	 */
	@SuppressWarnings("unchecked")
	private static Hash.Strategy<? super ItemStack> typeAndTagStrategy() {
		try {
			Field field = ItemStackLinkedSet.class.getDeclaredField("TYPE_AND_TAG");
			field.setAccessible(true);
			return (Hash.Strategy<? super ItemStack>) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("ItemStackLinkedSet.TYPE_AND_TAG is no longer reachable — this test "
					+ "can no longer build the entry sets the way NeoForge does, so it would stop "
					+ "exercising the real insert path. Re-check the field against the current mappings.", e);
		}
	}

	/** An event over a COMBAT tab containing exactly {@code existing} — nothing else. */
	private static BuildCreativeModeTabContentsEvent combatTabContaining(MinecraftServer server,
			ItemStack... existing) {
		return tabContaining(server, VanillaCreativeTabs.COMBAT, existing);
	}

	/** An event over the vanilla tab {@code tabKey} containing exactly {@code existing} — nothing else. */
	private static BuildCreativeModeTabContentsEvent tabContaining(MinecraftServer server,
			ResourceKey<CreativeModeTab> tabKey, ItemStack... existing) {
		Hash.Strategy<? super ItemStack> strategy = typeAndTagStrategy();
		InsertableLinkedOpenCustomHashSet<ItemStack> parent = new InsertableLinkedOpenCustomHashSet<>(strategy);
		InsertableLinkedOpenCustomHashSet<ItemStack> search = new InsertableLinkedOpenCustomHashSet<>(strategy);
		for (ItemStack stack : existing) {
			parent.add(stack);
			search.add(stack);
		}
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(tabKey);
		CreativeModeTab.ItemDisplayParameters params =
				new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, false, server.registryAccess());
		return new BuildCreativeModeTabContentsEvent(tab, tabKey, params, parent, search);
	}

	/**
	 * MOD-478 — the powered hand tools reach the vanilla Tools &amp; Utilities tab on NeoForge too.
	 *
	 * <p><b>The defect.</b> Fabric put the electric chainsaw, shovel and hoe into that tab; NeoForge put
	 * only their diamond-tipped upgrades there (MOD-374/MOD-378 added those and left the base tools
	 * behind). Nothing was unobtainable — the mod's own tab carried all of them on both loaders — but a
	 * NeoForge player looking for a powered tool beside the vanilla ones did not find it, and the two
	 * loaders showed different vanilla tabs for the same mod version.
	 *
	 * <p><b>What this pins.</b> The full set the listener contributes, not just the three that were
	 * missing: an assertion naming only the repaired items goes green again the moment some OTHER entry
	 * is dropped, which is the same class of regression. The list is the vanilla-tab contribution as of
	 * MOD-478 and is deliberately spelled out — it is the NeoForge half of a parity that no gate checks
	 * (the two loaders keep these lists by hand; see MOD-477 for why), so it has to be pinned somewhere.
	 * A deliberate addition updates this list; an accidental deletion trips it.
	 */
	@Test
	void toolsTabCarriesThePoweredToolsOnNeoForge(MinecraftServer server) {
		// Seeded with the vanilla anchors the listener positions against, so the anchored inserts take
		// their real path rather than silently falling back to an append.
		BuildCreativeModeTabContentsEvent event = tabContaining(server, VanillaCreativeTabs.TOOLS_AND_UTILITIES,
				Items.WOODEN_HOE.getDefaultInstance(), Items.STONE_HOE.getDefaultInstance(),
				Items.COPPER_HOE.getDefaultInstance(), Items.IRON_HOE.getDefaultInstance(),
				Items.GOLDEN_HOE.getDefaultInstance(), Items.DIAMOND_HOE.getDefaultInstance(),
				Items.NETHERITE_HOE.getDefaultInstance(), Items.COMPASS.getDefaultInstance());

		assertDoesNotThrow(() -> ModCreativeTabEventsNeoForge.buildCreativeTabContents(event),
				"building the vanilla Tools & Utilities tab must not throw");

		for (ItemStack expected : List.of(
				// The three MOD-478 restored — the reason this test exists.
				ModContent.ELECTRIC_CHAINSAW.get().getDefaultInstance(),
				ModContent.ELECTRIC_SHOVEL.get().getDefaultInstance(),
				ModContent.ELECTRIC_HOE.get().getDefaultInstance(),
				// Everything else the listener contributes, so a different entry going missing is caught
				// by this test rather than by a player.
				ModContent.SCYTHE_WOOD.get().getDefaultInstance(),
				ModContent.SCYTHE_STONE.get().getDefaultInstance(),
				ModContent.SCYTHE_COPPER.get().getDefaultInstance(),
				ModContent.SCYTHE_IRON.get().getDefaultInstance(),
				ModContent.SCYTHE_TEMPERED_IRON.get().getDefaultInstance(),
				ModContent.SCYTHE_GOLD.get().getDefaultInstance(),
				ModContent.SCYTHE_DIAMOND.get().getDefaultInstance(),
				ModContent.SCYTHE_NETHERITE.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_PICKAXE.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_AXE.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_SHOVEL.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_HOE.get().getDefaultInstance(),
				ModContent.NETWORK_ANALYZER.get().getDefaultInstance(),
				ModContent.WRENCH.get().getDefaultInstance(),
				ModContent.BATTERY_POUCH.get().getDefaultInstance(),
				ModContent.ENERGY_PACK.get().getDefaultInstance(),
				ModContent.ELECTRIC_DRILL.get().getDefaultInstance(),
				ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get().getDefaultInstance(),
				ModContent.ELECTRIC_DRILL_NETHERITE_TIP.get().getDefaultInstance(),
				ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get().getDefaultInstance(),
				ModContent.ELECTRIC_HOE_DIAMOND_TIP.get().getDefaultInstance(),
				ModContent.ELECTROMAGNET.get().getDefaultInstance(),
				ModContent.JETPACK.get().getDefaultInstance())) {
			assertTrue(event.getParentEntries().contains(expected),
					() -> "missing from the vanilla Tools & Utilities tab on NeoForge: " + expected);
			assertTrue(event.getSearchEntries().contains(expected),
					() -> "missing from the search half of Tools & Utilities on NeoForge: " + expected);
		}
	}

	private static ItemStack ourSword() {
		return ModContent.TEMPERED_IRON_SWORD.get().getDefaultInstance();
	}

	/**
	 * NEGATIVE CONTROL — proves the hazard is real in this harness. Must fail loudly if NeoForge ever
	 * makes {@code insertAfter} lenient, because then the guard is dead weight and this file is lying
	 * about what it protects.
	 */
	@Test
	void rawInsertAfterStillThrows(MinecraftServer server) {
		// A tab with boots but no sword — exactly what a mod that strips vanilla weapons leaves behind.
		BuildCreativeModeTabContentsEvent event =
				combatTabContaining(server, Items.IRON_BOOTS.getDefaultInstance());
		assertThrows(IllegalArgumentException.class,
				() -> event.insertAfter(Items.IRON_SWORD.getDefaultInstance(), ourSword(),
						CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS),
				"the UNGUARDED call must still throw on a missing anchor — if it does not, this test no "
						+ "longer reproduces the crash and every other assertion here is vacuous");
	}

	/** The fix: a missing anchor degrades to an append instead of taking the game down. */
	@Test
	void guardedInsertAppendsWhenAnchorRemoved(MinecraftServer server) {
		BuildCreativeModeTabContentsEvent event =
				combatTabContaining(server, Items.IRON_BOOTS.getDefaultInstance());
		ItemStack ours = ourSword();

		assertDoesNotThrow(
				() -> ModCreativeTabEventsNeoForge.insertAfter(event, Items.IRON_SWORD.getDefaultInstance(), ours),
				"a third-party mod removing the vanilla anchor must not crash creative-tab construction");

		// Asserting membership alone would not distinguish "appended" from "inserted after the only
		// entry" — with one pre-existing element both look identical. Pin the position instead.
		List<ItemStack> parent = List.copyOf(event.getParentEntries());
		assertEquals(2, parent.size(), "one entry added, none replaced");
		assertTrue(ItemStack.isSameItemSameComponents(parent.get(parent.size() - 1), ours),
				"with the anchor gone the item must land at the END of the tab, not at an arbitrary spot");
		assertTrue(event.getSearchEntries().contains(ours),
				"the search half of the tab must be handled too — insertAfter asserts against it separately");
	}

	/**
	 * The field scenario end to end: a mod strips vanilla weapons AND boots, then the real listener runs
	 * over the COMBAT tab. This is the case the crash actually traversed, and it is the one the unit-level
	 * tests above cannot reach — most COMBAT inserts anchor on the mod's OWN previously-placed stacks
	 * (helmet → chestplate → leggings → boots → energy pack → jetpack), so the interesting question is
	 * whether the chain survives when its first two links fell back to an append.
	 */
	@Test
	void wholeCombatTabSurvivesStrippedVanillaGear(MinecraftServer server) {
		// Neither IRON_SWORD nor IRON_BOOTS present: both COMBAT anchors are gone at once.
		BuildCreativeModeTabContentsEvent event =
				combatTabContaining(server, Items.SHIELD.getDefaultInstance());

		assertDoesNotThrow(() -> ModCreativeTabEventsNeoForge.buildCreativeTabContents(event),
				"stripping the vanilla anchors must not crash the whole tab build");

		for (ItemStack expected : List.of(
				ModContent.TEMPERED_IRON_SWORD.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_HELMET.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_CHESTPLATE.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_LEGGINGS.get().getDefaultInstance(),
				ModContent.TEMPERED_IRON_BOOTS.get().getDefaultInstance(),
				ModContent.ENERGY_PACK.get().getDefaultInstance(),
				ModContent.JETPACK.get().getDefaultInstance())) {
			assertTrue(event.getParentEntries().contains(expected),
					() -> "missing from the parent tab after the fallback: " + expected);
			assertTrue(event.getSearchEntries().contains(expected),
					() -> "missing from the search tab after the fallback: " + expected);
		}
	}

	/**
	 * The guard must not cost anything in the normal case. This also pins the property the whole design
	 * rests on: a FRESHLY built anchor stack matches the entry already in the set, so {@code contains}
	 * answers exactly what {@code insertAfter}'s own assert would. If that ever stopped holding, the guard
	 * would silently downgrade every placement in the mod to an append and nothing else would notice.
	 */
	@Test
	void guardedInsertKeepsPositionWhenAnchorPresent(MinecraftServer server) {
		BuildCreativeModeTabContentsEvent event = combatTabContaining(server,
				Items.IRON_SWORD.getDefaultInstance(), Items.IRON_BOOTS.getDefaultInstance());
		ItemStack ours = ourSword();

		ModCreativeTabEventsNeoForge.insertAfter(event, Items.IRON_SWORD.getDefaultInstance(), ours);

		List<ItemStack> parent = List.copyOf(event.getParentEntries());
		assertEquals(3, parent.size(), "one entry added, none replaced");
		assertTrue(ItemStack.isSameItemSameComponents(parent.get(1), ours),
				"our sword must sit directly after the iron sword, not at the end — the guard is only "
						+ "allowed to change behaviour when the anchor is absent");
	}

	/**
	 * The two entry sets are asserted independently by {@code insertAfter}, and they legitimately differ
	 * (vanilla contributes some entries search-only). A guard that checked only the parent set would still
	 * crash here — this is the case that makes the split necessary rather than merely tidy.
	 */
	@Test
	void guardedInsertHandlesAnchorPresentInOnlyOneSet(MinecraftServer server) {
		Hash.Strategy<? super ItemStack> strategy = typeAndTagStrategy();
		InsertableLinkedOpenCustomHashSet<ItemStack> parent = new InsertableLinkedOpenCustomHashSet<>(strategy);
		InsertableLinkedOpenCustomHashSet<ItemStack> search = new InsertableLinkedOpenCustomHashSet<>(strategy);
		parent.add(Items.IRON_SWORD.getDefaultInstance());   // anchor present here…
		search.add(Items.IRON_BOOTS.getDefaultInstance());   // …but absent here
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(VanillaCreativeTabs.COMBAT);
		CreativeModeTab.ItemDisplayParameters params =
				new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, false, server.registryAccess());
		BuildCreativeModeTabContentsEvent event =
				new BuildCreativeModeTabContentsEvent(tab, VanillaCreativeTabs.COMBAT, params, parent, search);
		ItemStack ours = ourSword();

		assertDoesNotThrow(
				() -> ModCreativeTabEventsNeoForge.insertAfter(event, Items.IRON_SWORD.getDefaultInstance(), ours),
				"a per-set guard is required: checking only the parent set leaves the search set to throw");

		List<ItemStack> parentEntries = List.copyOf(event.getParentEntries());
		assertTrue(ItemStack.isSameItemSameComponents(parentEntries.get(1), ours),
				"where the anchor was present the item keeps its position");
		assertTrue(event.getSearchEntries().contains(ours),
				"where the anchor was missing the item is appended rather than lost");
	}
}

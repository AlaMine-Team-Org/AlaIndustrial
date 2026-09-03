package dev.alaindustrial.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Loader-neutral gametest bodies for the enchantability of the WHOLE item roster (suite TC-ENCH-001).
 * Wrapped by the Fabric {@code EnchantableRosterGameTest} suite and registered on the NeoForge
 * {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders run the SAME checks.
 *
 * <p><b>Why a roster-wide test and not another per-item one.</b> This defect has now recurred three
 * times — MOD-057 (tempered iron tools), MOD-389 and MOD-364 (electric tools), MOD-565 (three armour
 * sets plus the diamond-tipped electric shovel). The shape is identical every time, because vanilla
 * splits the answer in two: {@code Item.Properties.humanoidArmor(...)} / {@code .pickaxe(...)} attach
 * the {@code ENCHANTABLE} data component and nothing else, while WHICH enchantments an item may take is
 * decided by membership in {@code #minecraft:*} tags. Miss the tag and the item still passes
 * {@code ItemStack.isEnchantable()}, so the enchanting table quotes a price and lists levels — and then
 * offers nothing, because the candidate list filtered through {@code supported_items} came back empty.
 * The player sees "30 levels" above a blank line, and the anvil silently refuses the book.
 *
 * <p>Every previous guard named the items it knew about, so each new item re-opened the hole. This one
 * names none: it walks the item registry itself, keeps everything in this mod's namespace, and asserts a
 * property that must hold for all of them — an item that declares itself enchantable has to be
 * enchantable with SOMETHING. A future armour set or tool is covered the day it is registered, with no
 * list to remember, and the walk sees items registered outside the manifest too.
 *
 * <p>The assertion is deliberately {@link Enchantment#canEnchant}, not {@code isEnchantable()}: the
 * latter reads only the component and is exactly the check that stayed green through all three
 * recurrences.
 */
public final class EnchantableRosterScenarios {

	private EnchantableRosterScenarios() {}

	/**
	 * TC-ENCH-001-FUN01 — every item carrying the {@code ENCHANTABLE} component accepts at least one
	 * enchantment. Removing an item from its membership tag reddens this.
	 *
	 * <p>Mirrors: EnchantableRosterGameTest.tcEnch001Fun01_everyEnchantableItemHasCandidates
	 */
	public static void fun01EveryEnchantableItemHasCandidates(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Holder.Reference<Enchantment>> all =
				level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList();

		if (all.isEmpty()) {
			helper.fail("enchantment registry is empty — the test cannot prove anything");
			return;
		}

		List<String> orphans = new ArrayList<>();
		int checked = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (id == null || !id.getNamespace().equals("alaindustrial")) {
				continue;
			}
			ItemStack stack = new ItemStack(item);
			if (!stack.has(DataComponents.ENCHANTABLE)) {
				continue;   // not meant to be enchanted at all — a consistent, honest state
			}
			checked++;
			boolean any = all.stream().anyMatch(e -> e.value().canEnchant(stack));
			if (!any) {
				orphans.add(id.getPath());
			}
		}

		if (checked == 0) {
			helper.fail("no enchantable items found in the manifest — the walk is broken, not the roster");
			return;
		}
		if (!orphans.isEmpty()) {
			helper.fail("these items declare themselves enchantable but no enchantment accepts them — "
					+ "the enchanting table will show levels and offer nothing, and the anvil will refuse "
					+ "books. Missing membership in a #minecraft:* tag: " + String.join(", ", orphans));
			return;
		}
		helper.succeed();
	}
}

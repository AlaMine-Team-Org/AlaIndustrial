package dev.alaindustrial.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Stands in for a third-party tech mod that adds tin (MOD-290), so the promise the common-tag work
 * made to players — "our machines take other mods' materials" — is actually asserted instead of
 * merely argued.
 *
 * <p><b>Why a stand-in is the honest test here.</b> No third-party tech mod exists for MC 26.2 at
 * all, so the criterion "verified on a build with a foreign tech mod adding tin" cannot be met by
 * installing one. It can be met by <em>being</em> one: this lives in the gametest source set, which
 * is its own Fabric mod ({@code alaindustrial-gametest}, see its {@code fabric.mod.json}) and never
 * ships in the release jar — the same construction {@link ForeignEnergyItemMod} uses for the energy
 * bridge. The items are registered under the {@code foreignmod} namespace and put into
 * {@code #c:ingots/tin} and {@code #c:ores/tin} by this mod's own datapack, exactly as a real mod
 * would; nothing in {@code alaindustrial} knows they exist, and the recipes under test resolve them
 * through the stock {@link net.minecraft.world.item.crafting.Ingredient} without a line of code
 * being aware of the difference.
 *
 * <p>Deliberately <b>not</b> vanilla items borrowed for the purpose: the whole point is a namespace
 * the mod does not own, reaching the mod's recipes through nothing but a convention tag.
 *
 * <p>The NeoForge lane registers the same two ids from
 * {@code dev.alaindustrial.gametest.neoforge.ForeignMaterialStandIn}, so the shared scenario bodies
 * in {@code ForeignMaterialScenarios} look the items up by id and run unchanged on both loaders.
 */
public class ForeignMaterialMod implements ModInitializer {

	/** Namespace of the imaginary third-party mod. Anything but {@code alaindustrial}. */
	public static final String NAMESPACE = "foreignmod";

	/** The foreign ingot, put into {@code #c:ingots/tin} by the test datapack. */
	public static final Identifier TIN_INGOT = Identifier.fromNamespaceAndPath(NAMESPACE, "tin_ingot");

	/** The foreign ore, put into {@code #c:ores/tin} by the test datapack. */
	public static final Identifier TIN_ORE = Identifier.fromNamespaceAndPath(NAMESPACE, "tin_ore");

	@Override
	public void onInitialize() {
		register(TIN_INGOT);
		register(TIN_ORE);
	}

	private static void register(Identifier id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}
}

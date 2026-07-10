package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge creative-tab registration (MOD-022 gap fix). The mod's own tab ({@code alaindustrial:main},
 * title {@code itemGroup.alaindustrial}) is registered on Fabric via {@code FabricCreativeModeTab} in
 * {@code ModItems#init()} — the NeoForge side had no equivalent, so on NeoForge the items were registered
 * but appeared in NO creative tab (invisible in the inventory). This mirrors the Fabric tab 1:1: same id,
 * title, Macerator icon and item list, so both loaders show the identical "Ala Industrial" tab.
 *
 * <p>Release-hidden blocks (non-copper cables — MOD-010) are intentionally omitted here too, matching
 * the Fabric {@code displayItems} list. The water mill + high-altitude/storm windmills are likewise
 * temporarily hidden from the player (no tab entry; the {@code water_mill} recipe is removed).
 * The pump is now public (creative tab + recipe restored).
 */
public final class ModCreativeTabNeoForge {
	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Industrialization.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.alaindustrial"))
					.icon(() -> new ItemStack(ModItemsNeoForge.MACERATOR_ITEM.get()))
					.displayItems((params, output) -> {
						// Generators
						output.accept(ModItemsNeoForge.SOLAR_PANEL_ITEM.get());
						output.accept(ModItemsNeoForge.DAYLIGHT_SOLAR_PANEL_ITEM.get());
						output.accept(ModItemsNeoForge.MOONLIT_SOLAR_PANEL_ITEM.get());
						output.accept(ModItemsNeoForge.GENERATOR_ITEM.get());
						output.accept(ModItemsNeoForge.GEOTHERMAL_GENERATOR_ITEM.get());
						output.accept(ModItemsNeoForge.WIND_MILL_ITEM.get());
						// Machines
						output.accept(ModItemsNeoForge.MACERATOR_ITEM.get());
						output.accept(ModItemsNeoForge.ELECTRIC_FURNACE_ITEM.get());
						output.accept(ModItemsNeoForge.EXTRACTOR_ITEM.get());
						output.accept(ModItemsNeoForge.COMPRESSOR_ITEM.get());
						output.accept(ModItemsNeoForge.PUMP_ITEM.get());
						// Storage + cables
						output.accept(ModItemsNeoForge.BATTERY_BOX_ITEM.get());
						output.accept(ModItemsNeoForge.IRON_CHEST_ITEM.get());
						output.accept(ModItemsNeoForge.COPPER_CABLE_ITEM.get());
						// Ores + materials
						output.accept(ModItemsNeoForge.TIN_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.DEEPSLATE_TIN_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.RAW_TIN.get());
						output.accept(ModItemsNeoForge.TIN_DUST.get());
						output.accept(ModItemsNeoForge.TIN_INGOT.get());
						output.accept(ModItemsNeoForge.SILVER_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.DEEPSLATE_SILVER_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.RAW_SILVER.get());
						output.accept(ModItemsNeoForge.SILVER_DUST.get());
						output.accept(ModItemsNeoForge.SILVER_INGOT.get());
						output.accept(ModItemsNeoForge.NICKEL_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.DEEPSLATE_NICKEL_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.RAW_NICKEL.get());
						output.accept(ModItemsNeoForge.NICKEL_DUST.get());
						output.accept(ModItemsNeoForge.NICKEL_INGOT.get());
						output.accept(ModItemsNeoForge.URANIUM_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.DEEPSLATE_URANIUM_ORE_ITEM.get());
						output.accept(ModItemsNeoForge.RAW_URANIUM.get());
						output.accept(ModItemsNeoForge.URANIUM_DUST.get());
						output.accept(ModItemsNeoForge.URANIUM_INGOT.get());
						// Components
						output.accept(ModItemsNeoForge.ELECTRONIC_CIRCUIT.get());
						output.accept(ModItemsNeoForge.ALIGNMENT_CHIP_DAY.get());
						output.accept(ModItemsNeoForge.ALIGNMENT_CHIP_NIGHT.get());
						output.accept(ModItemsNeoForge.WINDMILL_ROTOR.get());
						output.accept(ModItemsNeoForge.IRON_DUST.get());
						output.accept(ModItemsNeoForge.COPPER_DUST.get());
						output.accept(ModItemsNeoForge.GOLD_DUST.get());
						output.accept(ModItemsNeoForge.COAL_DUST.get());
						output.accept(ModItemsNeoForge.DIAMOND_DUST.get());
						output.accept(ModItemsNeoForge.EMERALD_DUST.get());
						output.accept(ModItemsNeoForge.LAPIS_DUST.get());
						output.accept(ModItemsNeoForge.NETWORK_ANALYZER.get());
					})
					.build());

	private ModCreativeTabNeoForge() {
	}
}

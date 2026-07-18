package dev.alaindustrial.registry;

import net.minecraft.world.level.ItemLike;

/**
 * Loader-neutral source of truth for public creative-inventory visibility.
 *
 * <p>This deliberately lists only player-visible MVP content. Registered-hidden entries
 * such as non-copper cables and deferred wind/water variants stay out of both the mod tab
 * and the vanilla tabs until their progression path is restored.
 */
public final class CreativeTabContent {
	private CreativeTabContent() {
	}

	@FunctionalInterface
	public interface Sink {
		void accept(ItemLike item);
	}

	public static void main(Sink out) {
		generators(out);
		machines(out);
		storageAndCables(out);
		oresAndMaterials(out);
		components(out);
		utility(out);
		temperedIron(out);
		scythes(out);
	}

	public static void combat(Sink out) {
		out.accept(ModContent.TEMPERED_IRON_SWORD.get());
		out.accept(ModContent.TEMPERED_IRON_HELMET.get());
		out.accept(ModContent.TEMPERED_IRON_CHESTPLATE.get());
		out.accept(ModContent.TEMPERED_IRON_LEGGINGS.get());
		out.accept(ModContent.TEMPERED_IRON_BOOTS.get());
	}

	public static void toolsAndUtilities(Sink out) {
		out.accept(ModContent.TEMPERED_IRON_PICKAXE.get());
		out.accept(ModContent.TEMPERED_IRON_AXE.get());
		out.accept(ModContent.TEMPERED_IRON_SHOVEL.get());
		out.accept(ModContent.TEMPERED_IRON_HOE.get());
		scythes(out);
		out.accept(ModContent.NETWORK_ANALYZER.get());
		out.accept(ModContent.TELEPORTER_REMOTE.get());
		out.accept(ModContent.WRENCH.get());
	}

	/** The six scythe tiers (MOD-068), wood → netherite, as one continuous row. */
	public static void scythes(Sink out) {
		out.accept(ModContent.SCYTHE_WOOD.get());
		out.accept(ModContent.SCYTHE_STONE.get());
		out.accept(ModContent.SCYTHE_COPPER.get());
		out.accept(ModContent.SCYTHE_IRON.get());
		out.accept(ModContent.SCYTHE_GOLD.get());
		out.accept(ModContent.SCYTHE_TEMPERED_IRON.get());
		out.accept(ModContent.SCYTHE_DIAMOND.get());
		out.accept(ModContent.SCYTHE_NETHERITE.get());
	}

	public static void ingredients(Sink out) {
		out.accept(ModContent.TEMPERED_IRON.get());
		out.accept(ModContent.TIN_INGOT.get());
		out.accept(ModContent.SILVER_INGOT.get());
		out.accept(ModContent.NICKEL_INGOT.get());
		out.accept(ModContent.URANIUM_INGOT.get());
		out.accept(ModContent.RAW_TIN.get());
		out.accept(ModContent.RAW_SILVER.get());
		out.accept(ModContent.RAW_NICKEL.get());
		out.accept(ModContent.RAW_URANIUM.get());
		out.accept(ModContent.IRON_DUST.get());
		out.accept(ModContent.COPPER_DUST.get());
		out.accept(ModContent.GOLD_DUST.get());
		out.accept(ModContent.COAL_DUST.get());
		out.accept(ModContent.DIAMOND_DUST.get());
		out.accept(ModContent.EMERALD_DUST.get());
		out.accept(ModContent.LAPIS_DUST.get());
		out.accept(ModContent.TIN_DUST.get());
		out.accept(ModContent.SILVER_DUST.get());
		out.accept(ModContent.NICKEL_DUST.get());
		out.accept(ModContent.URANIUM_DUST.get());
		out.accept(ModContent.ELECTRONIC_CIRCUIT.get());
		out.accept(ModContent.COPPER_COIL.get());
		out.accept(ModContent.ALIGNMENT_CHIP_DAY.get());
		out.accept(ModContent.ALIGNMENT_CHIP_NIGHT.get());
		out.accept(ModContent.EMPTY_CHIP.get());
		out.accept(ModContent.MUTE_CHIP.get());
		out.accept(ModContent.WINDMILL_ROTOR.get());
		out.accept(ModContent.WOODEN_GEAR.get());
		out.accept(ModContent.STONE_GEAR.get());
		out.accept(ModContent.IRON_GEAR.get());
		out.accept(ModContent.GOLD_GEAR.get());
		out.accept(ModContent.SILVER_GEAR.get());
	}

	public static void buildingBlocks(Sink out) {
		out.accept(ModContent.TEMPERED_IRON_BLOCK_ITEM.get());
	}

	public static void naturalBlocks(Sink out) {
		out.accept(ModContent.TIN_ORE_ITEM.get());
		out.accept(ModContent.DEEPSLATE_TIN_ORE_ITEM.get());
		out.accept(ModContent.SILVER_ORE_ITEM.get());
		out.accept(ModContent.DEEPSLATE_SILVER_ORE_ITEM.get());
		out.accept(ModContent.NICKEL_ORE_ITEM.get());
		out.accept(ModContent.DEEPSLATE_NICKEL_ORE_ITEM.get());
		out.accept(ModContent.URANIUM_ORE_ITEM.get());
		out.accept(ModContent.DEEPSLATE_URANIUM_ORE_ITEM.get());
	}

	public static void functionalBlocks(Sink out) {
		generators(out);
		machines(out);
		storageAndCables(out);
		utility(out);
	}

	/** Decorative/utility blocks that live in vanilla Functional Blocks — the Enriched Uranium Torch (MOD-085). */
	private static void utility(Sink out) {
		out.accept(ModContent.ENRICHED_URANIUM_TORCH_ITEM.get());
	}

	private static void generators(Sink out) {
		out.accept(ModContent.SOLAR_PANEL_ITEM.get());
		out.accept(ModContent.DAYLIGHT_SOLAR_PANEL_ITEM.get());
		out.accept(ModContent.MOONLIT_SOLAR_PANEL_ITEM.get());
		out.accept(ModContent.GENERATOR_ITEM.get());
		out.accept(ModContent.GEOTHERMAL_GENERATOR_ITEM.get());
		out.accept(ModContent.WIND_MILL_ITEM.get());
	}

	private static void machines(Sink out) {
		out.accept(ModContent.MACERATOR_ITEM.get());
		out.accept(ModContent.ELECTRIC_FURNACE_ITEM.get());
		out.accept(ModContent.IRON_FURNACE_ITEM.get());
		out.accept(ModContent.EXTRACTOR_ITEM.get());
		out.accept(ModContent.COMPRESSOR_ITEM.get());
		out.accept(ModContent.PUMP_ITEM.get());
	}

	private static void storageAndCables(Sink out) {
		out.accept(ModContent.BATTERY_BOX_ITEM.get());
		out.accept(ModContent.FLUID_TANK_ITEM.get());
		// Teleporter (MOD-091/092/093): hidden until the feature was whole — the station banks EU with
		// no way to spend it until the remote existed, and the remote had no list until the screen did.
		// All three shipped, so it is visible now.
		out.accept(ModContent.TELEPORTER_ITEM.get());
		// The chest tiers, in upgrade order (36 → 45 → 54). Silver and Gold were missing here while
		// present in the Fabric list, so NeoForge players saw neither in any tab — MOD-102.
		out.accept(ModContent.IRON_CHEST_ITEM.get());
		out.accept(ModContent.SILVER_CHEST_ITEM.get());
		out.accept(ModContent.GOLD_CHEST_ITEM.get());
		out.accept(ModContent.STOCK_DISPLAY_FRAME_ITEM.get());
		out.accept(ModContent.COPPER_CABLE_ITEM.get());
		out.accept(ModContent.ITEM_PIPE_ITEM.get());
	}

	private static void oresAndMaterials(Sink out) {
		naturalBlocks(out);
		out.accept(ModContent.RAW_TIN.get());
		out.accept(ModContent.TIN_DUST.get());
		out.accept(ModContent.TIN_INGOT.get());
		out.accept(ModContent.RAW_SILVER.get());
		out.accept(ModContent.SILVER_DUST.get());
		out.accept(ModContent.SILVER_INGOT.get());
		out.accept(ModContent.RAW_NICKEL.get());
		out.accept(ModContent.NICKEL_DUST.get());
		out.accept(ModContent.NICKEL_INGOT.get());
		out.accept(ModContent.RAW_URANIUM.get());
		out.accept(ModContent.URANIUM_DUST.get());
		out.accept(ModContent.URANIUM_INGOT.get());
	}

	private static void components(Sink out) {
		out.accept(ModContent.ELECTRONIC_CIRCUIT.get());
		out.accept(ModContent.COPPER_COIL.get());
		out.accept(ModContent.ALIGNMENT_CHIP_DAY.get());
		out.accept(ModContent.ALIGNMENT_CHIP_NIGHT.get());
		out.accept(ModContent.EMPTY_CHIP.get());
		out.accept(ModContent.MUTE_CHIP.get());
		out.accept(ModContent.WINDMILL_ROTOR.get());
		out.accept(ModContent.WOODEN_GEAR.get());
		out.accept(ModContent.STONE_GEAR.get());
		out.accept(ModContent.IRON_GEAR.get());
		out.accept(ModContent.GOLD_GEAR.get());
		out.accept(ModContent.SILVER_GEAR.get());
		out.accept(ModContent.IRON_DUST.get());
		out.accept(ModContent.COPPER_DUST.get());
		out.accept(ModContent.GOLD_DUST.get());
		out.accept(ModContent.COAL_DUST.get());
		out.accept(ModContent.DIAMOND_DUST.get());
		out.accept(ModContent.EMERALD_DUST.get());
		out.accept(ModContent.LAPIS_DUST.get());
		out.accept(ModContent.NETWORK_ANALYZER.get());
		out.accept(ModContent.WRENCH.get());
		out.accept(ModContent.BATTERY_POUCH.get());
		out.accept(ModContent.BATTERY.get());
		out.accept(ModContent.ENERGY_PACK.get());
		out.accept(ModContent.ELECTRIC_DRILL.get());
		out.accept(ModContent.TELEPORTER_REMOTE.get());
		// Empty capsule only — the filled form (MOD-063) is obtained by using it on a fluid.
		out.accept(ModContent.VACUUM_CAPSULE.get());
	}

	private static void temperedIron(Sink out) {
		out.accept(ModContent.TEMPERED_IRON.get());
		out.accept(ModContent.TEMPERED_IRON_BLOCK_ITEM.get());
		out.accept(ModContent.TEMPERED_IRON_PICKAXE.get());
		out.accept(ModContent.TEMPERED_IRON_AXE.get());
		out.accept(ModContent.TEMPERED_IRON_SHOVEL.get());
		out.accept(ModContent.TEMPERED_IRON_HOE.get());
		out.accept(ModContent.TEMPERED_IRON_SWORD.get());
		out.accept(ModContent.TEMPERED_IRON_HELMET.get());
		out.accept(ModContent.TEMPERED_IRON_CHESTPLATE.get());
		out.accept(ModContent.TEMPERED_IRON_LEGGINGS.get());
		out.accept(ModContent.TEMPERED_IRON_BOOTS.get());
	}
}

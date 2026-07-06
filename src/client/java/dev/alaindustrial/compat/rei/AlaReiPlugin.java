package dev.alaindustrial.compat.rei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModRecipes;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.level.block.Block;

/**
 * Client half of the AlaIndustrial REI integration (MOD-018). Registers one category per processing
 * machine ({@link ModRecipes.Kind}) with its icon, and the workstation link so clicking the machine
 * block opens its recipes.
 *
 * <p>The actual recipe → display filling is done server-side by {@link AlaReiCommonPlugin} and synced
 * to the client (MC 26.2 no longer ships full recipes to the client — see that class). This plugin
 * therefore only supplies the client-only category widgets; it does not fill displays itself.
 *
 * <p>Optional dependency: this class is only loaded when REI itself invokes the {@code rei_client}
 * entrypoint, so the mod runs fine without REI installed.
 */
public class AlaReiPlugin implements REIClientPlugin {

	/** One machine family: its recipe {@link ModRecipes.Kind}, icon/workstation block and REI id. */
	private record Machine(ModRecipes.Kind kind, Block block, CategoryIdentifier<AlaProcessingDisplay> id) {
	}

	private static Machine machine(ModRecipes.Kind kind, Block block) {
		return new Machine(kind, block, CategoryIdentifier.of(Industrialization.id(kind.id())));
	}

	private static final Machine[] MACHINES = {
			machine(ModRecipes.MACERATION, ModBlocks.MACERATOR),
			machine(ModRecipes.SMELTING, ModBlocks.ELECTRIC_FURNACE),
			machine(ModRecipes.COMPRESSING, ModBlocks.COMPRESSOR),
			machine(ModRecipes.EXTRACTING, ModBlocks.EXTRACTOR),
	};

	@Override
	public void registerCategories(CategoryRegistry registry) {
		for (Machine m : MACHINES) {
			registry.add(new AlaProcessingCategory(m.id(), m.block()));
			// Clicking the machine block in REI opens its recipes.
			registry.addWorkstations(m.id(), EntryStacks.of(m.block()));
		}
	}
}

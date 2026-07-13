package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.registry.CreativeTabContent;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.VanillaCreativeTabs;
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

	private static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
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
			event.accept(ModContent.BATTERY_POUCH.get());
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

	private static void insertAfter(BuildCreativeModeTabContentsEvent event, ItemStack anchor, ItemStack stack) {
		event.insertAfter(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
	}
}

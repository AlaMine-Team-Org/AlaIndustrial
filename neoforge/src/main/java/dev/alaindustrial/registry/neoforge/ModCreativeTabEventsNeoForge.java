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
			insertAfter(event, Items.IRON_HOE.getDefaultInstance(), pickaxe);
			insertAfter(event, pickaxe, axe);
			insertAfter(event, axe, shovel);
			insertAfter(event, shovel, hoe);
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

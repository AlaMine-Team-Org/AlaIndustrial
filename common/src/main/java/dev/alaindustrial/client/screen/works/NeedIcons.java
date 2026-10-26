package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.works.NeedIcon;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Draws the picture of one of the town's needs on the core's tab (MOD-890): what {@link NeedIcon} words — the energy
 * picture (a battery), a building's picture from the «Construction» cards' atlas, or an item found by the translation
 * key the need names it with. An item is drawn at its size (16) or at half (8), on a whole pixel.
 */
public final class NeedIcons {

	/** Items by translation key, found once: the registries do not change while the game runs. */
	private static final Map<String, ItemStack> ITEMS = new HashMap<>();

	private NeedIcons() {
	}

	/** Draws {@code icon} in a {@code size} square at ({@code x}, {@code y}); nothing for an empty or unknown one. */
	public static void draw(GuiGraphicsExtractor graphics, String icon, int x, int y, int size) {
		if (icon.isEmpty()) {
			return;
		}
		if (icon.equals(NeedIcon.ENERGY)) {
			item(graphics, new ItemStack(ModContent.BATTERY.get()), x, y, size);
		} else if (icon.startsWith(NeedIcon.BUILDING)) {
			String building = icon.substring(NeedIcon.BUILDING.length());
			if (TownBuildingIcons.has(building)) {
				TownBuildingIcons.draw(graphics, building, x, y, size);
			} else {
				item(graphics, new ItemStack(Items.BRICKS), x, y, size);
			}
		} else if (icon.startsWith(NeedIcon.ITEM)) {
			ItemStack stack = itemFor(icon.substring(NeedIcon.ITEM.length()));
			if (!stack.isEmpty()) {
				item(graphics, stack, x, y, size);
			}
		}
	}

	private static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int size) {
		if (size >= 16) {
			graphics.item(stack, x + (size - 16) / 2, y + (size - 16) / 2);
			return;
		}
		WorksPaint.smallIcon(graphics, stack, x + (size - 8) / 2, y + (size - 8) / 2);
	}

	/** The item whose translation key, or whose block's, is {@code key}; empty when none is. */
	static ItemStack itemFor(String key) {
		return ITEMS.computeIfAbsent(key, NeedIcons::find).copy();
	}

	private static ItemStack find(String key) {
		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR && item.getDescriptionId().equals(key)) {
				return new ItemStack(item);
			}
		}
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block.getDescriptionId().equals(key) && block.asItem() != Items.AIR) {
				return new ItemStack(block.asItem());
			}
		}
		return ItemStack.EMPTY;
	}
}

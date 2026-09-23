package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.IndustrialWorkbenchBlockEntity;
import dev.alaindustrial.block.entity.WorkbenchCraftDetector;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The Industrial Workbench's crafting grid (MOD-656): vanilla's 3x3 crafting menu, plus a memory.
 *
 * <p><b>Why a subclass of {@link CraftingMenu} and not a menu of our own.</b> The super constructor keeps
 * {@code MenuType.CRAFTING}, so the client opens vanilla's own crafting screen with vanilla's own client
 * menu: the recipe book, shift-click, the ghost recipe and the recipe viewers' transfer buttons all
 * behave exactly as on a crafting table, and there is no new screen for the mod to keep in step. That
 * is also why it lives next to its block and not in {@code menu/}: every menu there is a registered type.
 * This
 * class exists only on the server, for two things vanilla hard-codes to its own block:
 * <ul>
 *   <li>{@link #stillValid} — vanilla closes the menu unless the block is {@code minecraft:crafting_table};</li>
 *   <li>{@link #clicked} — the one place a craft is taken (plain, shift, drop, number-key swap all pass
 *       through it), so it is where the bench learns the recipe to remember.</li>
 * </ul>
 */
public class IndustrialWorkbenchMenu extends CraftingMenu {

	private final ContainerLevelAccess access;

	public IndustrialWorkbenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(containerId, inventory, access);
		this.access = access;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ModContent.INDUSTRIAL_WORKBENCH.get());
	}

	/**
	 * Take the craft exactly as vanilla does, then remember the recipe if one was really crafted.
	 *
	 * <p>The recipe is read BEFORE the click: afterwards the grid may be empty and the result slot cleared.
	 * {@code getRecipeUsed()} alone is not proof of a craft — vanilla leaves the last matched recipe there
	 * after the grid stops matching — so the grid snapshot decides ({@link WorkbenchCraftDetector}).
	 */
	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		RecipeHolder<?> recipe = slotId == RESULT_SLOT ? resultSlots.getRecipeUsed() : null;
		int[] before = recipe != null ? gridCounts() : null;
		super.clicked(slotId, button, input, player);
		if (recipe != null && WorkbenchCraftDetector.craftedBetween(before, gridCounts())) {
			access.execute((level, pos) -> {
				if (level.getBlockEntity(pos) instanceof IndustrialWorkbenchBlockEntity bench) {
					bench.remember(recipe.id());
				}
			});
		}
	}

	private int[] gridCounts() {
		int size = craftSlots.getContainerSize();
		int[] counts = new int[size];
		for (int i = 0; i < size; i++) {
			counts[i] = craftSlots.getItem(i).getCount();
		}
		return counts;
	}
}

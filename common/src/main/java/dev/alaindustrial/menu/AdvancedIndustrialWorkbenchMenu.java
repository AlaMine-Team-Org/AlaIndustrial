package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.AdvancedIndustrialWorkbenchBlockEntity;
import dev.alaindustrial.block.entity.AdvancedWorkbenchGrid;
import dev.alaindustrial.block.entity.WorkbenchCraftDetector;
import dev.alaindustrial.compat.ServerDrops;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

/**
 * The Advanced Industrial Workbench's window (MOD-810): the crafting grid of a crafting table, laid out the same
 * way, over a grid that belongs to the BLOCK and not to the window.
 *
 * <p>Built on vanilla's {@link AbstractCraftingMenu} so the recipe book, the recipe-viewer transfer buttons and
 * the ghost recipe work as on a crafting table. What differs is the grid: its nine slots are backed straight by
 * the bench's block entity (through {@link AdvancedWorkbenchGrid}), so closing the window keeps the items and
 * two players with the window open see and change the same nine stacks. The result slot is per window, since
 * it is only a view of the grid.
 *
 * <p><b>The result is recomputed on demand, not on every slot event.</b> Vanilla's crafting container tells its
 * own menu when a slot changes; a shared grid cannot, because it has many menus. Each menu instead compares the
 * block entity's change counter with the one it last computed for, and recomputes when they differ - at the start
 * of every click (so a result left stale by the OTHER player's craft can never be taken: that would be free
 * items), every tick, and after every craft inside a shift-click, which is what lets the shift-click repeat
 * until the grid runs dry.
 */
public class AdvancedIndustrialWorkbenchMenu extends AbstractCraftingMenu {

	public static final int RESULT_SLOT = 0;
	private static final int GRID_START = 1;
	private static final int GRID_END = 10;
	private static final int INV_START = 10;
	private static final int HOTBAR_START = 37;
	private static final int INV_END = 46;

	/** Menu-button id of the "Clear grid" button. */
	public static final int BUTTON_CLEAR = 0;

	private final ContainerLevelAccess access;
	private final Player player;
	private final AdvancedWorkbenchGrid grid;
	private final @Nullable AdvancedIndustrialWorkbenchBlockEntity bench;
	private int seenRevision = -1;

	/** Server side: the grid is the bench itself. */
	public AdvancedIndustrialWorkbenchMenu(int containerId, Inventory inventory,
			AdvancedIndustrialWorkbenchBlockEntity bench, ContainerLevelAccess access) {
		this(containerId, inventory, bench, bench, access);
	}

	/** Client side: a stand-in grid the server's slot updates fill. */
	public AdvancedIndustrialWorkbenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(AdvancedIndustrialWorkbenchBlockEntity.SLOT_COUNT), null,
				ContainerLevelAccess.NULL);
	}

	private AdvancedIndustrialWorkbenchMenu(int containerId, Inventory inventory, Container backing,
			@Nullable AdvancedIndustrialWorkbenchBlockEntity bench, ContainerLevelAccess access) {
		super(ModContent.ADVANCED_INDUSTRIAL_WORKBENCH_MENU.get(), containerId, 3, 3);
		this.access = access;
		this.player = inventory.player;
		this.grid = new AdvancedWorkbenchGrid(backing);
		this.bench = bench;
		addSlot(new ResultSlot(player, grid, resultSlots, 0, 124, 35));
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				addSlot(new Slot(backing, x + y * 3, 30 + x * 18, 17 + y * 18));
			}
		}
		addStandardInventorySlots(inventory, 8, 84);
		refreshResult(false);
	}

	// --- the result: a view of the shared grid ---

	/** Recomputes the result if the bench changed since this window last looked. */
	private void syncResult() {
		if (bench != null && seenRevision != bench.revision()) {
			refreshResult(true);
		}
	}

	/** The result of the grid as it is now; {@code send} pushes it to the player the way vanilla's craft does. */
	@SuppressWarnings("unchecked")
	private void refreshResult(boolean send) {
		if (bench == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		seenRevision = bench.revision();
		ServerLevel level = serverPlayer.level();
		CraftingInput input = grid.asCraftInput();
		ItemStack result = ItemStack.EMPTY;
		Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(
				RecipeType.CRAFTING, input, level, (RecipeHolder<CraftingRecipe>) resultSlots.getRecipeUsed());
		if (found.isPresent() && resultSlots.setRecipeUsed(serverPlayer, found.get())) {
			ItemStack assembled = found.get().value().assemble(input);
			if (assembled.isItemEnabled(level.enabledFeatures())) {
				result = assembled;
			}
		}
		resultSlots.setItem(0, result);
		if (send) {
			setRemoteSlot(0, result);
			serverPlayer.connection.send(
					new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
		}
	}

	@Override
	public void broadcastChanges() {
		syncResult();
		super.broadcastChanges();
	}

	/**
	 * Take the click exactly as vanilla does, then remember the recipe if one was really crafted - the first
	 * tier's rule ({@link WorkbenchCraftDetector}): the recipe is read BEFORE the click and the grid snapshot
	 * decides whether a craft happened.
	 */
	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
		syncResult();
		RecipeHolder<?> recipe = slotId == RESULT_SLOT && bench != null ? resultSlots.getRecipeUsed() : null;
		int[] before = recipe != null ? gridCounts() : null;
		super.clicked(slotId, button, input, clicker);
		if (recipe != null && bench != null && WorkbenchCraftDetector.craftedBetween(before, gridCounts())) {
			bench.remember(recipe.id());
		}
	}

	private int[] gridCounts() {
		int[] counts = new int[AdvancedWorkbenchGrid.SIZE];
		for (int i = 0; i < counts.length; i++) {
			counts[i] = grid.getItem(i).getCount();
		}
		return counts;
	}

	// --- shift-click ---

	@Override
	public ItemStack quickMoveStack(Player mover, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		if (index == RESULT_SLOT) {
			syncResult();
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == RESULT_SLOT) {
			stack.getItem().onCraftedBy(stack, mover);
			if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (index >= INV_START) {
			if (!moveItemStackTo(stack, GRID_START, GRID_END, false)) {
				if (index < HOTBAR_START) {
					if (!moveItemStackTo(stack, HOTBAR_START, INV_END, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
					return ItemStack.EMPTY;
				}
			}
		} else if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(mover, stack);
		if (index == RESULT_SLOT) {
			if (!stack.isEmpty()) {
				ServerDrops.drop(mover, stack);
			}
			// The craft just consumed ingredients: look at the grid again NOW, so the repeat vanilla's
			// shift-click loop makes sees the real next result - or none once the materials run out.
			refreshResult(true);
		}
		return original;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
	}

	// --- the Clear button ---

	@Override
	public boolean clickMenuButton(Player clicker, int buttonId) {
		if (buttonId != BUTTON_CLEAR || bench == null || !(clicker instanceof ServerPlayer)) {
			return false;
		}
		boolean moved = false;
		for (int i = 0; i < AdvancedWorkbenchGrid.SIZE; i++) {
			ItemStack held = grid.getItem(i);
			if (held.isEmpty()) {
				continue;
			}
			int before = held.getCount();
			clicker.getInventory().add(held);
			moved |= held.getCount() != before;
			grid.setItem(i, held.isEmpty() ? ItemStack.EMPTY : held);
		}
		return moved;
	}

	// --- recipe book ---

	@Override
	public RecipeBookMenu.PostPlaceAction handlePlacement(boolean useMaxItems, boolean creative,
			RecipeHolder<?> recipe, ServerLevel level, Inventory inventory) {
		@SuppressWarnings("unchecked")
		RecipeHolder<CraftingRecipe> holder = (RecipeHolder<CraftingRecipe>) recipe;
		beginPlacingRecipe();
		try {
			List<Slot> inputs = getInputGridSlots();
			return ServerPlaceRecipe.placeRecipe(new ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe>() {
				@Override
				public void fillCraftSlotsStackedContents(StackedItemContents contents) {
					AdvancedIndustrialWorkbenchMenu.this.fillCraftSlotsStackedContents(contents);
				}

				@Override
				public void clearCraftingContent() {
					resultSlots.clearContent();
					grid.clearContent();
				}

				@Override
				public boolean recipeMatches(RecipeHolder<CraftingRecipe> candidate) {
					return candidate.value().matches(grid.asCraftInput(), player.level());
				}
			}, 3, 3, inputs, inputs, inventory, holder, useMaxItems, creative);
		} finally {
			finishPlacingRecipe(level, holder);
		}
	}

	@Override
	protected void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
		refreshResult(true);
	}

	@Override
	public void fillCraftSlotsStackedContents(StackedItemContents contents) {
		grid.fillStackedContents(contents);
	}

	@Override
	public Slot getResultSlot() {
		return slots.get(RESULT_SLOT);
	}

	@Override
	public List<Slot> getInputGridSlots() {
		return slots.subList(GRID_START, GRID_END);
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return RecipeBookType.CRAFTING;
	}

	@Override
	protected Player owner() {
		return player;
	}

	@Override
	public boolean stillValid(Player viewer) {
		return stillValid(access, viewer, ModContent.ADVANCED_INDUSTRIAL_WORKBENCH.get());
	}
}

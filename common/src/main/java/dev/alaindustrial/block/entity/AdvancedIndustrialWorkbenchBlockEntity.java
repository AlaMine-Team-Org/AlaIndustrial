package dev.alaindustrial.block.entity;

import dev.alaindustrial.menu.AdvancedIndustrialWorkbenchMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Advanced Industrial Workbench's memory and hands (MOD-810): nine grid slots that keep what is in them,
 * a remembered last recipe, and one output slot that lets a pipe or hopper use the bench as an auto-crafter.
 *
 * <p><b>Why {@link BaseContainerBlockEntity} and not the mod's energy base.</b> The bench draws no energy, has
 * no tick work and no upgrade slots; it is a chest with a recipe on top. The chests are built on the same vanilla
 * base for the same reason (persistence, display name, menu provider, lock key come for free), and the item
 * capability on both loaders is derived from {@link net.minecraft.world.Container} by
 * {@code BlockCapabilityRoster}, so nothing loader-specific is added. The face rules that make it an
 * auto-crafter are {@link WorldlyContainer}'s, which both loaders' item adapters already honour.
 *
 * <h2>The slots</h2>
 * Slots {@code 0..8} are the grid (row-major, as the menu draws it). Slot {@value #OUTPUT_SLOT} is a REAL,
 * saved slot - the bench's output buffer: a finished craft waits there for a pipe, and a block break drops it.
 * It is real on purpose: both transfer APIs snapshot every slot they touch and restore the snapshot when a
 * transaction aborts, so a virtual "preview" slot would leave a simulated extraction having eaten the ingredients.
 *
 * <h2>Faces</h2>
 * <ul>
 *   <li>Top and sides take items into the grid, but only into the cells the REMEMBERED recipe wants that item
 *       in, and only into the emptiest of them ({@link WorkbenchRecipePattern}). No remembered recipe, no intake.</li>
 *   <li>The bottom gives out the output slot, and nothing else. Asking for the bottom's slot list is what turns a
 *       complete recipe into a finished craft in the buffer ({@link #ensureOutput}) - the one moment before
 *       any transfer snapshot is taken, so the craft is inside every transaction's picture, never half of it.
 *       A handler cached across several transfers must ask again to see the next craft.</li>
 * </ul>
 */
public class AdvancedIndustrialWorkbenchBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

	public static final int GRID_SIZE = 9;
	public static final int OUTPUT_SLOT = 9;
	public static final int SLOT_COUNT = 10;

	private static final int[] GRID_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int[] OUTPUT_SLOTS = {OUTPUT_SLOT};
	private static final String KEY_LAST_RECIPE = "LastRecipe";
	private static final String KEY_PREVIEW = "Preview";

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
	private @Nullable ResourceKey<Recipe<?>> lastRecipe;
	/** The recipe the grid last matched; a hint that makes the next lookup one comparison. Not saved. */
	private @Nullable RecipeHolder<CraftingRecipe> matched;
	/** Bumped on every change, so a menu can tell its result slot has gone stale without comparing stacks. */
	private int revision;
	/** What the renderer shows above the bench: the buffered craft, else the craft the grid would make. */
	private ItemStack preview = ItemStack.EMPTY;
	private int syncedSignature = Integer.MIN_VALUE;
	private boolean includePreview;

	public AdvancedIndustrialWorkbenchBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ADVANCED_INDUSTRIAL_WORKBENCH_BE.get(), pos, state);
	}

	// --- container ---

	@Override
	protected Component getDefaultName() {
		// The window title is a short name of its own: the block's full name overflows the 176 px frame.
		return Component.translatable("container.alaindustrial.advanced_industrial_workbench");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	/** Players may put anything in the grid; the output slot is never a place to put things. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot < GRID_SIZE;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new AdvancedIndustrialWorkbenchMenu(containerId, inventory, this,
				ContainerLevelAccess.create(Objects.requireNonNull(level), worldPosition));
	}

	/** The nine grid stacks as a live list (not the output slot). */
	public List<ItemStack> grid() {
		return items.subList(0, GRID_SIZE);
	}

	public boolean isGridEmpty() {
		for (int i = 0; i < GRID_SIZE; i++) {
			if (!items.get(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public int revision() {
		return revision;
	}

	/** The comparator reading: how full the nine grid slots are, the way a chest's is read. */
	public int comparatorSignal() {
		float fill = 0.0F;
		for (int i = 0; i < GRID_SIZE; i++) {
			ItemStack stack = items.get(i);
			if (!stack.isEmpty()) {
				fill += (float) stack.getCount() / Math.min(getMaxStackSize(), stack.getMaxStackSize());
			}
		}
		return Mth.lerpDiscrete(fill / GRID_SIZE, 0, 15);
	}

	// --- the remembered recipe (as on the first tier) ---

	public Optional<ResourceKey<Recipe<?>>> lastRecipe() {
		return Optional.ofNullable(lastRecipe);
	}

	/** Remember {@code recipe} as the one to hint and to feed next time; a repeat writes nothing. */
	public void remember(ResourceKey<Recipe<?>> recipe) {
		if (!recipe.equals(lastRecipe)) {
			lastRecipe = recipe;
			setChanged();
		}
	}

	// --- faces: pipes and hoppers ---

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.DOWN) {
			ensureOutput();
			return OUTPUT_SLOTS;
		}
		return GRID_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side != Direction.DOWN && slot >= 0 && slot < GRID_SIZE && wantedByRememberedRecipe(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side == Direction.DOWN && slot == OUTPUT_SLOT;
	}

	private boolean wantedByRememberedRecipe(int slot, ItemStack stack) {
		if (lastRecipe == null || !(level instanceof ServerLevel server)) {
			return false;
		}
		return server.getServer().getRecipeManager().byKey(lastRecipe)
				.flatMap(holder -> WorkbenchRecipePattern.of(holder.value()))
				.map(pattern -> pattern.accepts(grid(), slot, stack))
				.orElse(false);
	}

	// --- the auto-crafter ---

	/**
	 * If the output buffer is empty and the grid holds a complete recipe, craft it now: the ingredients leave the
	 * grid, the result lands in the buffer. Called only when something asks for the bottom face.
	 */
	public void ensureOutput() {
		if (!(level instanceof ServerLevel server) || !items.get(OUTPUT_SLOT).isEmpty()) {
			return;
		}
		CraftingInput.Positioned positioned = CraftingInput.ofPositioned(3, 3, grid());
		CraftingInput input = positioned.input();
		if (input.isEmpty()) {
			return;
		}
		RecipeHolder<CraftingRecipe> holder = match(server, input);
		if (holder == null) {
			return;
		}
		ItemStack result = holder.value().assemble(input);
		if (result.isEmpty() || !result.isItemEnabled(server.enabledFeatures())) {
			return;
		}
		NonNullList<ItemStack> remaining = holder.value().getRemainingItems(input);
		for (int y = 0; y < input.height(); y++) {
			for (int x = 0; x < input.width(); x++) {
				int slot = x + positioned.left() + (y + positioned.top()) * 3;
				ItemStack in = items.get(slot);
				if (!in.isEmpty()) {
					in.shrink(1);
					if (in.isEmpty()) {
						items.set(slot, ItemStack.EMPTY);
					}
				}
				ItemStack left = remaining.get(x + y * input.width());
				if (left.isEmpty()) {
					continue;
				}
				ItemStack now = items.get(slot);
				if (now.isEmpty()) {
					items.set(slot, left);
				} else if (ItemStack.isSameItemSameComponents(now, left)) {
					now.grow(left.getCount());
				} else {
					Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
							worldPosition.getZ() + 0.5, left);
				}
			}
		}
		items.set(OUTPUT_SLOT, result);
		setChanged();
	}

	private @Nullable RecipeHolder<CraftingRecipe> match(ServerLevel server, CraftingInput input) {
		Optional<RecipeHolder<CraftingRecipe>> found = server.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, server, matched);
		matched = found.orElse(null);
		return matched;
	}

	/** What this bench shows above its grid: the buffered craft, else what the grid would craft. */
	private ItemStack computePreview() {
		if (!(level instanceof ServerLevel server)) {
			return ItemStack.EMPTY;
		}
		if (!items.get(OUTPUT_SLOT).isEmpty()) {
			return items.get(OUTPUT_SLOT).copy();
		}
		CraftingInput input = CraftingInput.of(3, 3, grid());
		if (input.isEmpty()) {
			return ItemStack.EMPTY;
		}
		RecipeHolder<CraftingRecipe> holder = match(server, input);
		if (holder == null) {
			return ItemStack.EMPTY;
		}
		ItemStack result = holder.value().assemble(input);
		return result.isItemEnabled(server.enabledFeatures()) ? result : ItemStack.EMPTY;
	}

	// --- change tracking and the client picture ---

	@Override
	public void setChanged() {
		super.setChanged();
		revision++;
		if (level != null && !level.isClientSide()) {
			syncToClients();
		}
	}

	/** Tell watching clients only when what is DRAWN changed: the item in each cell, or the result above. */
	private void syncToClients() {
		preview = computePreview();
		int signature = ItemStack.hashItemAndComponents(preview);
		for (int i = 0; i < GRID_SIZE; i++) {
			signature = signature * 31 + ItemStack.hashItemAndComponents(items.get(i));
		}
		if (signature == syncedSignature) {
			return;
		}
		syncedSignature = signature;
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
	}

	/** The result floating above the grid, as the server last told us (client) or computed (server). */
	public ItemStack previewResult() {
		return preview;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this,
				(be, access) -> ((AdvancedIndustrialWorkbenchBlockEntity) be).getUpdateTag(access));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		if (level != null && !level.isClientSide()) {
			preview = computePreview();
		}
		includePreview = true;
		try {
			return saveCustomOnly(provider);
		} finally {
			includePreview = false;
		}
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		if (lastRecipe != null) {
			output.store(KEY_LAST_RECIPE, Recipe.KEY_CODEC, lastRecipe);
		}
		if (includePreview && !preview.isEmpty()) {
			output.store(KEY_PREVIEW, ItemStack.CODEC, preview);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		lastRecipe = input.read(KEY_LAST_RECIPE, Recipe.KEY_CODEC).orElse(null);
		preview = input.read(KEY_PREVIEW, ItemStack.CODEC).orElse(ItemStack.EMPTY);
		revision++;
	}

	/**
	 * Everything in the bench - the grid and the buffered craft - falls out when the block goes, whatever
	 * removed it. This hook runs while the block entity is still attached; the block's own removal hook runs
	 * after the entity has been detached and would find nothing to drop.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide()) {
			Containers.dropContents(level, pos, this);
		}
		super.preRemoveSideEffects(pos, state);
	}
}

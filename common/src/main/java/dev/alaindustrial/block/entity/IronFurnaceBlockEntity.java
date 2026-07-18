package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.IronFurnaceBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Iron Furnace block entity — a fuel-burning smelter that runs every vanilla {@code minecraft:smelting}
 * recipe (food, ores, sand→glass, raw→ingot, cobble→stone…) but faster than the stone furnace:
 * {@link Config#ironFurnaceCookTime} ticks per item (default 150 vs vanilla 200), and slower than the
 * mod's EU-powered {@link ElectricFurnaceBlockEntity}. It is intentionally an early-game upgrade — one
 * iron craft, no electricity — sitting between {@code minecraft:furnace} and the electric furnace.
 *
 * <p>Why a hand-rolled smelter instead of extending vanilla {@code AbstractFurnaceBlockEntity}: on
 * 26.2 the cook-time hook ({@code getTotalCookTime}) is {@code private static} and the progress
 * fields are {@code private}, so a subclass cannot change the smelting speed without an
 * AccessWidener (Fabric) plus an AccessTransformer (NeoForge) — neither of which the loader-neutral
 * {@code common} module can carry. Re-implementing the small burn/cook loop here (mechanics, not
 * copied code) keeps the speed fully configurable and works identically on both loaders. The GUI is
 * still 100 % vanilla: {@link #createMenu} returns a stock {@link FurnaceMenu} (vanilla
 * {@code MenuType.FURNACE}), so the vanilla {@code FurnaceScreen} opens with no client registration —
 * only the title differs (from {@link #getDefaultName()}).
 *
 * <p>Known v1 limitation (see task MOD-115): no smelting XP is awarded on collection. The stone and
 * electric furnaces differ here too (the electric furnace also grants none); tracked as a follow-up.
 */
public class IronFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INPUT_SLOT = 0;
	public static final int FUEL_SLOT = 1;
	public static final int RESULT_SLOT = 2;
	public static final int CONTAINER_SIZE = 3;

	/** Vanilla furnace cools an idle-but-partial cook bar by 2 ticks/tick. */
	private static final int COOL_SPEED = 2;
	private static final int OUTPUT_MAX = 64;
	/** Vanilla reference smelt time (ticks). Fuel burn is scaled by {@code cookTotal / this} so the
	 * iron furnace keeps vanilla fuel economy (e.g. 8 smelts/coal) — it is a SPEED upgrade only, not a
	 * fuel-efficiency upgrade (MOD-115 design, question 3). */
	private static final int VANILLA_SMELT_TIME = 200;

	private static final int[] SLOTS_UP = { INPUT_SLOT };
	private static final int[] SLOTS_DOWN = { RESULT_SLOT, FUEL_SLOT };
	private static final int[] SLOTS_SIDE = { FUEL_SLOT };

	private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

	/** Remaining burn ticks of the fuel currently on fire (0 = not lit). */
	private int litTime;
	/** Total burn ticks the current fuel item provided (for the GUI flame gauge). */
	private int litDuration;
	/** Ticks the current item has been cooking. */
	private int cookProgress;
	/** Ticks needed to finish one item; kept in sync with {@link Config#ironFurnaceCookTime}. */
	private int cookTotal = Config.ironFurnaceCookTime;

	/** Vanilla smelting lookup (same call the electric furnace uses for its vanilla fallback). */
	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> quickCheck =
			RecipeManager.createCheck(RecipeType.SMELTING);

	/** The 4 values a vanilla {@link FurnaceMenu} reads: lit time, lit duration, cook progress, cook total. */
	private final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> IronFurnaceBlockEntity.this.litTime;
				case 1 -> IronFurnaceBlockEntity.this.litDuration;
				case 2 -> IronFurnaceBlockEntity.this.cookProgress;
				case 3 -> IronFurnaceBlockEntity.this.cookTotal;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> IronFurnaceBlockEntity.this.litTime = value;
				case 1 -> IronFurnaceBlockEntity.this.litDuration = value;
				case 2 -> IronFurnaceBlockEntity.this.cookProgress = value;
				case 3 -> IronFurnaceBlockEntity.this.cookTotal = value;
			}
		}

		@Override
		public int getCount() {
			return 4;
		}
	};

	public IronFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.IRON_FURNACE_BE.get(), pos, state);
	}

	// --- server smelting loop (hand-rolled; mechanics, not copied vanilla code) ---

	public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, IronFurnaceBlockEntity be) {
		boolean wasLit = be.litTime > 0;
		boolean changed = false;

		if (be.litTime > 0) {
			be.litTime--;
		}

		be.cookTotal = Math.max(1, Config.ironFurnaceCookTime);
		ItemStack input = be.items.get(INPUT_SLOT);
		ItemStack fuel = be.items.get(FUEL_SLOT);

		SmeltingRecipe recipe = input.isEmpty()
				? null
				: be.quickCheck.getRecipeFor(new SingleRecipeInput(input), level).map(RecipeHolder::value).orElse(null);
		boolean canCook = recipe != null && be.canAcceptResult(recipe);

		// Light fresh fuel only when there is something to cook and the fire has gone out.
		if (be.litTime <= 0 && canCook && !fuel.isEmpty()) {
			int burn = level.fuelValues().burnDuration(fuel);
			if (burn > 0) {
				// Scale burn ticks by the speed ratio (cookTotal/200) so smelts-per-fuel stays vanilla
				// (8/coal, 100/lava bucket): the iron furnace is faster but not more fuel-efficient.
				int scaledBurn = Math.max(1, (int) ((long) burn * be.cookTotal / VANILLA_SMELT_TIME));
				be.litTime = scaledBurn;
				be.litDuration = scaledBurn;
				Item fuelItem = fuel.getItem();
				fuel.shrink(1);
				if (fuel.isEmpty()) {
					// Fuels with a remainder (lava bucket → empty bucket) leave it; coal/wood have a NULL
					// remainder (Item.getCraftingRemainder is @Nullable) → leave the slot empty. Skipping
					// this null check crashes the server tick on the first coal smelt.
					ItemStackTemplate remainder = fuelItem.getCraftingRemainder();
					be.items.set(FUEL_SLOT, remainder != null ? remainder.create() : ItemStack.EMPTY);
				}
				changed = true;
			}
		}

		boolean lit = be.litTime > 0;
		if (lit && canCook) {
			be.cookProgress++;
			if (be.cookProgress >= be.cookTotal) {
				be.cookProgress = 0;
				be.smelt(recipe);
				changed = true;
			}
		} else if (be.cookProgress > 0) {
			be.cookProgress = Math.max(0, be.cookProgress - COOL_SPEED);
		}

		if (wasLit != lit) {
			state = state.setValue(IronFurnaceBlock.LIT, lit);
			level.setBlock(pos, state, Block.UPDATE_ALL);
			changed = true;
		}

		if (changed) {
			be.setChanged();
		}
	}

	/** True if this recipe's result fits in the output slot (empty, or same item under the stack cap). */
	private boolean canAcceptResult(SmeltingRecipe recipe) {
		ItemStack result = recipe.assemble(new SingleRecipeInput(items.get(INPUT_SLOT)));
		if (result.isEmpty()) {
			return false;
		}
		ItemStack out = items.get(RESULT_SLOT);
		if (out.isEmpty()) {
			return true;
		}
		return out.getItem() == result.getItem()
				&& out.getCount() + result.getCount() <= Math.min(OUTPUT_MAX, out.getMaxStackSize());
	}

	/** Consume one input and add one result to the output slot. */
	private void smelt(SmeltingRecipe recipe) {
		ItemStack input = items.get(INPUT_SLOT);
		ItemStack result = recipe.assemble(new SingleRecipeInput(input));
		ItemStack out = items.get(RESULT_SLOT);
		if (out.isEmpty()) {
			items.set(RESULT_SLOT, result.copy());
		} else {
			out.grow(result.getCount());
		}
		input.shrink(1);
	}

	// --- container / menu ---

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.alaindustrial.iron_furnace");
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
		return CONTAINER_SIZE;
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
		return new FurnaceMenu(syncId, playerInventory, this, dataAccess);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == RESULT_SLOT) {
			return false;
		}
		if (slot == FUEL_SLOT) {
			if (level == null) {
				return false;
			}
			// Fuel, or an empty bucket going in on top of a lava bucket (vanilla furnace parity).
			ItemStack current = items.get(FUEL_SLOT);
			return level.fuelValues().isFuel(stack) || (stack.is(Items.BUCKET) && !current.is(Items.BUCKET));
		}
		return true;
	}

	// --- WorldlyContainer: standard furnace hopper behaviour (top→input, side→fuel, bottom out) ---

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.DOWN) {
			return SLOTS_DOWN;
		}
		return side == Direction.UP ? SLOTS_UP : SLOTS_SIDE;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		// Pull finished results from the bottom; also let empty buckets be pulled out of the fuel slot.
		if (direction == Direction.DOWN && slot == FUEL_SLOT) {
			return stack.is(Items.BUCKET);
		}
		return slot == RESULT_SLOT;
	}

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		output.putInt("lit_time", litTime);
		output.putInt("lit_duration", litDuration);
		output.putInt("cook_progress", cookProgress);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		litTime = input.getIntOr("lit_time", 0);
		litDuration = input.getIntOr("lit_duration", 0);
		cookProgress = input.getIntOr("cook_progress", 0);
		cookTotal = Math.max(1, Config.ironFurnaceCookTime);
	}
}

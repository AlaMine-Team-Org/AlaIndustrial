package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV electric furnace — works exactly like a vanilla furnace (processes all vanilla smelting
 * recipes) but consumes EU instead of fuel, and is faster. Additionally accepts mod-specific
 * {@code alaindustrial:smelting} recipes (e.g. dusts → ingots) which take priority over vanilla
 * when both would match the same input.
 */
public class ElectricFurnaceBlockEntity extends MachineBlockEntity implements MenuProvider {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;

	private static final int OUTPUT_MAX = 64;

	/** Mod-specific smelting recipes (priority over vanilla). */
	private final RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> modRecipeCheck =
			ModRecipes.SMELTING.newCheck();

	/** Vanilla furnace recipes — used as fallback when no mod recipe matches. */
	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> vanillaRecipeCheck =
			RecipeManager.createCheck(RecipeType.SMELTING);

	public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELECTRIC_FURNACE_BE.get(), pos, state, EnergyTier.LV, 2,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.electricFurnaceDuration);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int euPerTick = Config.machineEuPerTickEffective();

		AlaProcessingRecipe modRecipe = level instanceof ServerLevel sl
				? ModRecipes.lookup(modRecipeCheck, sl, items.get(INPUT_SLOT)) : null;
		SmeltingRecipe vanillaRecipe = (modRecipe == null && level instanceof ServerLevel sl2)
				? lookupVanilla(sl2) : null;

		// Duration: mod recipe specifies EU cost; vanilla recipe uses default furnace duration.
		int baseDuration = modRecipe != null
				? Math.max(1, modRecipe.energy() / Config.machineEuPerTick)
				: Config.electricFurnaceDuration;
		this.maxProgress = Config.scaledDuration(baseDuration);

		ItemStack resultItem = resolveResult(modRecipe, vanillaRecipe);
		boolean hasRecipe = !resultItem.isEmpty();
		boolean canWork = hasRecipe && energy.amount >= euPerTick && canOutput(resultItem);

		updateLit(canWork);

		if (canWork) {
			energy.amount -= euPerTick;
			progress++;
			if (progress >= maxProgress) {
				progress = 0;
				items.get(INPUT_SLOT).shrink(1);
				addOutput(resultItem);
			}
			setChanged();
		} else if (!hasRecipe && progress != 0) {
			progress = 0;
			setChanged();
		}
		return canWork ? 0 : IDLE_SLEEP_TICKS;
	}

	/** Returns the output ItemStack from whichever recipe matched, or EMPTY if none. */
	private ItemStack resolveResult(AlaProcessingRecipe modRecipe, SmeltingRecipe vanillaRecipe) {
		if (modRecipe != null) return modRecipe.resultStack();
		if (vanillaRecipe != null) return vanillaRecipe.assemble(new SingleRecipeInput(items.get(INPUT_SLOT)));
		return ItemStack.EMPTY;
	}

	private SmeltingRecipe lookupVanilla(ServerLevel level) {
		ItemStack input = items.get(INPUT_SLOT);
		if (input.isEmpty()) return null;
		return vanillaRecipeCheck.getRecipeFor(new SingleRecipeInput(input), level)
				.map(RecipeHolder::value).orElse(null);
	}

	private boolean canOutput(ItemStack result) {
		ItemStack out = items.get(OUTPUT_SLOT);
		return out.isEmpty()
				|| (out.getItem() == result.getItem() && out.getCount() + result.getCount() <= OUTPUT_MAX);
	}

	private void addOutput(ItemStack result) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, result.copy());
		} else {
			out.grow(result.getCount());
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot != INPUT_SLOT || stack.isEmpty()) return false;
		if (!(level instanceof ServerLevel sl)) return false;
		// Accept if mod recipe OR vanilla smelting recipe matches.
		if (ModRecipes.lookup(modRecipeCheck, sl, stack) != null) return true;
		return vanillaRecipeCheck.getRecipeFor(new SingleRecipeInput(stack), sl).isPresent();
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.electric_furnace");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ElectricFurnaceMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}

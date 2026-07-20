package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.menu.ExtractorMenu;
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
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV extractor — extracts items (blaze rod → blaze powder, gravel → flint)
 * spending EU over the configured number of ticks. Recipes are real vanilla recipes
 * ({@link ModRecipes#EXTRACTING}) loaded from {@code data/<ns>/recipe/extracting/*.json}; the input
 * may be an item or a tag (R-14/R-15).
 */
public class ExtractorBlockEntity extends MachineBlockEntity implements MenuProvider {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;

	/** Output stack cap; per-recipe energy sets the duration. Shared machine buffer. */
	private static final int OUTPUT_MAX = 64;

	/** Per-machine cached recipe lookup (mirrors vanilla furnace quickCheck). */
	private final RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> recipeCheck =
			ModRecipes.EXTRACTING.newCheck();

	public ExtractorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.EXTRACTOR_BE.get(), pos, state, EnergyTier.LV, 2,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.extractorDuration);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int euPerTick = Config.machineEuPerTickEffective();
		AlaProcessingRecipe recipe = level instanceof ServerLevel sl
				? ModRecipes.lookup(recipeCheck, sl, items.get(INPUT_SLOT)) : null;
		int baseDuration = recipe != null
				? Math.max(1, recipe.energy() / Config.machineEuPerTick) : Config.extractorDuration;
		this.maxProgress = Config.scaledDuration(baseDuration);
		boolean canWork = recipe != null && energy.amount >= euPerTick && canOutput(recipe);

		updateLit(canWork);

		if (canWork) {
			energy.amount -= euPerTick;
			progress++;
			if (progress >= maxProgress) {
				progress = 0;
				items.get(INPUT_SLOT).shrink(1);
				addOutput(recipe);
				creditUsefulWork(level, (long) euPerTick * maxProgress); // MOD-133: completed op → XP
			}
			setChanged();
		} else if (recipe == null && progress != 0) {
			// Recipe gone (input removed/changed): reset progress. On mere power loss or a full output
			// (recipe still present) neither branch runs, so progress stays FROZEN and resumes when work
			// can continue (R-NRG-10).
			progress = 0;
			setChanged();
		}
		// Idle (no recipe / no power / output full) → sleep until input, energy or output changes (R-29).
		return canWork ? 0 : IDLE_SLEEP_TICKS;
	}

	private boolean canOutput(AlaProcessingRecipe recipe) {
		ItemStack out = items.get(OUTPUT_SLOT);
		ItemStack result = recipe.resultStack();
		return out.isEmpty()
				|| (out.getItem() == result.getItem() && out.getCount() + result.getCount() <= OUTPUT_MAX);
	}

	private void addOutput(AlaProcessingRecipe recipe) {
		ItemStack result = recipe.resultStack();
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, result.copy());
		} else {
			out.grow(result.getCount());
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == INPUT_SLOT && level instanceof ServerLevel sl
				&& ModRecipes.lookup(recipeCheck, sl, stack) != null;
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
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
		return Component.translatable("block.alaindustrial.extractor");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ExtractorMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}

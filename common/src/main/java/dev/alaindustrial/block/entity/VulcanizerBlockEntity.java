package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.core.heat.WorldHeatSources;
import dev.alaindustrial.menu.VulcanizerMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Two-input LV processing machine: raw rubber + sulfur dust + external heat → rubber. */
public final class VulcanizerBlockEntity extends MachineBlockEntity implements Overclockable, MenuProvider {
	public static final int RAW_RUBBER_SLOT = 0;
	public static final int SULFUR_SLOT = 1;
	public static final int OUTPUT_SLOT = 2;
	public static final int SLOT_COUNT = 3;
	public static final int DATA_COUNT = 6;
	private static final int OUTPUT_MAX = 64;
	private static final int[] NO_SLOTS = new int[0];

	private final RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> recipeCheck =
			ModRecipes.VULCANIZING.newCheck();
	private HeatSource heatSource = HeatSource.NONE;
	private VulcanizerStatus status = VulcanizerStatus.READY;
	/** Heat tier captured by the first paid tick; changing it restarts the batch without consuming inputs. */
	private int cycleHeatLevel;

	public VulcanizerBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.VULCANIZER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.vulcanizerDuration);
	}


	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int euPerTick = effectiveEuPerTick(Config.machineEuPerTick);
		ProcessingRecipeInput input = new ProcessingRecipeInput(items.get(RAW_RUBBER_SLOT), items.get(SULFUR_SLOT));
		AlaProcessingRecipe recipe = level instanceof ServerLevel server
				? ModRecipes.lookup(recipeCheck, server, input)
				: null;
		heatSource = WorldHeatSources.resolve(level, pos);
		resetCycleForHeatChange(heatSource);

		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Config.machineEuPerTick)
				: Config.vulcanizerDuration;
		maxProgress = effectiveDuration(baseDuration);

		if (recipe == null) {
			if (progress != 0 || cycleHeatLevel != 0) {
				progress = 0;
				cycleHeatLevel = 0;
				setChanged();
			}
			setStatus(diagnose(null, ItemStack.EMPTY));
			updateLit(false);
			return IDLE_SLEEP_TICKS;
		}

		int outputLevel = cycleHeatLevel > 0 ? cycleHeatLevel : heatSource.level();
		ItemStack result = scaledResult(recipe.resultStack(), outputLevel);
		boolean heatEnough = heatSource.level() > 0;
		// The batch price (MOD-271: four sulfur dust) must be on hand every tick, not just at the
		// start — pulling dust out mid-cycle stops the run instead of completing it underpaid.
		boolean canWork = heatEnough && recipe.hasEnough(input)
				&& energy.getAmount() >= euPerTick && canOutput(result);

		if (canWork && !WorldHeatSources.consumeForProgress(level, pos, heatSource, overclockerCount())) {
			canWork = false;
			heatSource = HeatSource.NONE;
		}

		setStatus(diagnose(recipe, result));
		updateLit(canWork);
		if (!canWork) {
			return IDLE_SLEEP_TICKS;
		}

		if (cycleHeatLevel == 0) {
			cycleHeatLevel = heatSource.level();
			result = scaledResult(recipe.resultStack(), cycleHeatLevel);
		}
		energy.drainInternal(euPerTick);
		progress++;
		if (progress >= maxProgress) {
			recipe.consume(List.of(items.get(RAW_RUBBER_SLOT), items.get(SULFUR_SLOT)));
			addOutput(result);
			progress = 0;
			cycleHeatLevel = 0;
			creditUsefulWork(level, (long) euPerTick * maxProgress);
		}
		setChanged();
		return 0;
	}

	private VulcanizerStatus diagnose(AlaProcessingRecipe recipe, ItemStack result) {
		// With a recipe in hand the shortfall is measured against its own per-input price, so half a
		// batch of sulfur reads as "need sulfur dust" rather than "no matching recipe".
		int rubberNeeded = recipe != null ? recipe.inputCount(RAW_RUBBER_SLOT) : 1;
		int sulfurNeeded = recipe != null ? recipe.inputCount(SULFUR_SLOT) : 1;
		if (items.get(RAW_RUBBER_SLOT).getCount() < rubberNeeded) {
			return VulcanizerStatus.NO_RAW_RUBBER;
		}
		if (items.get(SULFUR_SLOT).getCount() < sulfurNeeded) {
			return VulcanizerStatus.NO_SULFUR;
		}
		if (recipe == null) {
			return VulcanizerStatus.NO_RECIPE;
		}
		if (heatSource.level() == 0 || (cycleHeatLevel > 0 && heatSource.level() < cycleHeatLevel)) {
			return VulcanizerStatus.NO_HEAT;
		}
		if (!canOutput(result)) {
			return VulcanizerStatus.OUTPUT_BLOCKED;
		}
		return VulcanizerStatus.READY;
	}

	private void setStatus(VulcanizerStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	private static ItemStack scaledResult(ItemStack base, int multiplier) {
		if (base.isEmpty() || multiplier <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack result = base.copy();
		result.setCount(Math.min(result.getMaxStackSize(), base.getCount() * multiplier));
		return result;
	}

	private boolean canOutput(ItemStack result) {
		if (result.isEmpty()) {
			return false;
		}
		ItemStack out = items.get(OUTPUT_SLOT);
		return out.isEmpty() || (ItemStack.isSameItem(out, result)
				&& out.getCount() + result.getCount() <= Math.min(OUTPUT_MAX, out.getMaxStackSize()));
	}

	private void addOutput(ItemStack result) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, result.copy());
		} else {
			out.grow(result.getCount());
		}
	}

	public HeatSource heatSource() {
		return heatSource;
	}

	public VulcanizerStatus status() {
		return status;
	}

	public int cycleHeatLevel() {
		return cycleHeatLevel;
	}

	/**
	 * Restarts an in-flight batch when its heat tier changes. Inputs are committed only on completion,
	 * so resetting progress is lossless and prevents both a permanently paused cycle and a last-tick
	 * upgrade exploit.
	 */
	private void resetCycleForHeatChange(HeatSource currentHeat) {
		if (cycleHeatLevel > 0 && currentHeat.level() != cycleHeatLevel) {
			progress = 0;
			cycleHeatLevel = 0;
			setChanged();
		}
	}

	/** Called by the block's neighbour callback to bypass the base class's 40-tick idle sleep. */
	public void onHeatNeighbourChanged() {
		if (level != null) {
			heatSource = WorldHeatSources.resolve(level, worldPosition);
			resetCycleForHeatChange(heatSource);
			if (heatSource == HeatSource.NONE) {
				updateLit(false);
			}
			setChanged();
		}
		wake();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case RAW_RUBBER_SLOT -> stack.is(ModContent.RAW_RUBBER.get());
			case SULFUR_SLOT -> stack.is(ModContent.SULFUR_DUST.get());
			default -> false;
		};
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.DOWN) {
			return NO_SLOTS;
		}
		return super.getSlotsForFace(side);
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side != Direction.DOWN && super.canPlaceItemThroughFace(slot, stack, side);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side != Direction.DOWN && super.canTakeItemThroughFace(slot, stack, side);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if ((slot == RAW_RUBBER_SLOT || slot == SULFUR_SLOT)
				&& !ItemStack.isSameItem(items.get(slot), stack)) {
			progress = 0;
			cycleHeatLevel = 0;
		}
		super.setItem(slot, stack);
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	private final ContainerData vulcanizerData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> heatSource.ordinal();
				case 5 -> status.ordinal();
				default -> VulcanizerBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != 4 && index != 5) {
				VulcanizerBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return vulcanizerData;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("CycleHeatLevel", cycleHeatLevel);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		cycleHeatLevel = Math.max(0, Math.min(3, input.getIntOr("CycleHeatLevel", 0)));
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.vulcanizer");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new VulcanizerMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}

package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.SawmillMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV sawmill (spec: alaindustrial:sawmill, MOD-150) — saws vanilla wood into planks, sticks, slabs or
 * stairs with a higher yield than the hand recipe, spending EU. Unlike the other processing machines
 * it has <b>four switchable modes</b> ({@link SawmillMode}); each mode is its own
 * {@link dev.alaindustrial.registry.ModRecipes.Kind} recipe family, and the machine only saws in the
 * currently-selected mode. Recipes are real vanilla recipes loaded from
 * {@code data/<ns>/recipe/sawing_*}/*.json (item or tag input, R-14/R-15).
 *
 * <p>Behaviour lives in {@link AbstractProcessingMachineBlockEntity}; this class adds the four recipe
 * checks, the active-mode field (persisted in NBT, synced to the screen via a 5th ContainerData index),
 * and mode-aware input resolution. Audible while working since MOD-447: {@code SawmillBlock}
 * implements {@code MachineHumProvider} (pattern A, the vanilla {@code lit} blockstate).
 */
public final class SawmillBlockEntity extends AbstractProcessingMachineBlockEntity implements MenuProvider {
	/** Input slot index — re-export of the shared processing-machine slot 0. */
	public static final int INPUT_SLOT = 0;
	/** Output slot index — re-export of the shared processing-machine slot 1. */
	public static final int OUTPUT_SLOT = 1;

	/**
	 * ContainerData index carrying the active {@link SawmillMode} ordinal (0..3), appended after everything
	 * the family already publishes.
	 *
	 * <p><b>Offset from the family constant, never a literal.</b> Written as {@code 4} this collided head-on
	 * with {@link AbstractProcessingMachineBlockEntity#DATA_STATUS} the moment MOD-458 added it — and no gate
	 * would have caught it: {@code MenuDataWidthScenarios} compares channel counts, and both sides would have
	 * stayed five wide while the mode buttons quietly highlighted by status ordinal instead. As an expression
	 * the index simply moves when the family grows.
	 */
	public static final int DATA_MODE = AbstractProcessingMachineBlockEntity.DATA_COUNT;

	/**
	 * Six-wide data — hides {@link AbstractProcessingMachineBlockEntity#DATA_COUNT} so
	 * {@code SawmillBlockEntity.DATA_COUNT} names this machine's width for the bridge below and for
	 * {@link SawmillMenu}'s client stub (MOD-235).
	 */
	public static final int DATA_COUNT = AbstractProcessingMachineBlockEntity.DATA_COUNT + 1;

	// One cached lookup per mode: resolveInput uses the active mode's, canPlaceInput scans all four.
	private final RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe>[] checks = newChecks();

	private SawmillMode mode = SawmillMode.PLANKS;

	/**
	 * A 6-wide data bridge: everything below {@link #DATA_MODE} delegates to the family bridge
	 * (energy/capacity/progress/maxProgress plus the MOD-458 status); {@link #DATA_MODE} carries the active
	 * mode ordinal so the screen can highlight the selected button. The client menu binds a
	 * {@code SimpleContainerData(DATA_COUNT)} that vanilla fills from this on sync.
	 *
	 * <p>Delegation goes to {@code processingData}, not to {@code dataAccess}: routing straight to the base
	 * machine bridge would skip the status channel and hand the screen a permanent zero.
	 */
	private final ContainerData sawmillData = new ContainerData() {
		@Override
		public int get(int index) {
			return index == DATA_MODE ? mode.ordinal() : processingData.get(index);
		}

		@Override
		public void set(int index, int value) {
			if (index == DATA_MODE) {
				mode = SawmillMode.byOrdinal(value);
			} else {
				processingData.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public SawmillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SAWMILL_BE.get(), pos, state, EnergyTier.LV, Config.machineBuffer, Config.sawmillDuration);
	}

	@SuppressWarnings("unchecked")
	private static RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe>[] newChecks() {
		SawmillMode[] modes = SawmillMode.values();
		RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe>[] result =
				new RecipeManager.CachedCheck[modes.length];
		for (int i = 0; i < modes.length; i++) {
			result[i] = checkFor(modes[i].kind());
		}
		return result;
	}

	/** Resolve the input against the ACTIVE mode's recipe family only (the other three are inert). */
	@Override
	protected RecipeSolution resolveInput(ServerLevel level, ItemStack input) {
		AlaProcessingRecipe recipe = lookupKind(checks[mode.ordinal()], level, input);
		return recipe != null ? RecipeSolution.of(recipe) : RecipeSolution.empty();
	}

	/**
	 * Accept an input the instant ANY mode could saw it — not just the active one. Otherwise a log
	 * inserted while the wrong mode is selected would be rejected by hoppers / manual placement and the
	 * player would have to re-insert after every switch. When the active mode has no recipe for the held
	 * item the machine simply sleeps ({@link #resolveInput} returns empty) until the mode is switched.
	 */
	@Override
	protected boolean canPlaceInput(ItemStack stack) {
		if (stack.isEmpty() || !(level instanceof ServerLevel sl)) {
			return false;
		}
		for (RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> check : checks) {
			if (lookupKind(check, sl, stack) != null) {
				return true;
			}
		}
		return false;
	}

	/** The currently selected cutting mode. */
	public SawmillMode getMode() {
		return mode;
	}

	/**
	 * Set the active mode (server-authoritative, driven by the GUI button via
	 * {@link SawmillMenu#clickMenuButton}). Switching restarts any in-progress operation from zero — the
	 * new mode saws a different product, so carrying progress across would be wrong — and wakes the
	 * machine so it re-evaluates the input on the next tick.
	 */
	public void setMode(SawmillMode next) {
		if (next == null || next == mode) {
			return;
		}
		mode = next;
		progress = 0;
		setChanged();
		// No full block-entity resync: the open screen gets the mode via the ContainerData slot
		// (DATA_MODE) through broadcastChanges, and the block model keys off LIT, not the mode.
		wake();
	}

	@Override
	public ContainerData getDataAccess() {
		return sawmillData;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Mode", mode.ordinal());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		mode = SawmillMode.byOrdinal(input.getIntOr("Mode", 0));
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.sawmill");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new SawmillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}

package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.SawmillMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
 * and mode-aware input resolution. Sound is intentionally omitted: {@code SawmillBlock} does not
 * implement {@code MachineHumProvider}, so the machine is silent by contract (MOD-150 decision).
 */
public final class SawmillBlockEntity extends AbstractProcessingMachineBlockEntity implements MenuProvider {
	/** Input slot index — re-export of the shared processing-machine slot 0. */
	public static final int INPUT_SLOT = 0;
	/** Output slot index — re-export of the shared processing-machine slot 1. */
	public static final int OUTPUT_SLOT = 1;

	/** ContainerData index carrying the active {@link SawmillMode} ordinal (0..3), appended after the base 0..3. */
	public static final int DATA_MODE = 4;

	// One cached lookup per mode: resolveInput uses the active mode's, canPlaceInput scans all four.
	private final RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe>[] checks = newChecks();

	private SawmillMode mode = SawmillMode.PLANKS;

	/**
	 * A 5-wide data bridge: indices 0..3 delegate to the shared machine data (energy/capacity/progress/
	 * maxProgress); index {@link #DATA_MODE} carries the active mode ordinal so the screen can highlight
	 * the selected button. The client menu binds a {@code SimpleContainerData(5)} that vanilla fills from
	 * this on sync.
	 */
	private final ContainerData sawmillData = new ContainerData() {
		@Override
		public int get(int index) {
			return index == DATA_MODE ? mode.ordinal() : dataAccess.get(index);
		}

		@Override
		public void set(int index, int value) {
			if (index == DATA_MODE) {
				mode = SawmillMode.byOrdinal(value);
			} else {
				dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return 5;
		}
	};

	/**
	 * Fallback duration for a cut, in ticks at 1.0 speed.
	 *
	 * <p>Not a config knob (MOD-209): while the machine is hidden from players, a
	 * {@code sawmillDuration} key would appear in every player's {@code alaindustrial.json} — the last
	 * place the hidden machine was still named in player-reachable text, and its doc comment described
	 * the four modes on top of that. The knob was also inert: every shipped sawing recipe carries an
	 * explicit {@code energy}, and the core derives the duration from that, so this value is only ever
	 * the no-recipe fallback. Restore it to {@code Config} when the machine returns to players.
	 */
	public static final int DEFAULT_DURATION = 80;

	public SawmillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SAWMILL_BE.get(), pos, state, EnergyTier.LV, Config.machineBuffer, DEFAULT_DURATION);
	}

	@SuppressWarnings("unchecked")
	private static RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe>[] newChecks() {
		SawmillMode[] modes = SawmillMode.values();
		RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe>[] result =
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
		for (RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> check : checks) {
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

package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.heat.HeatConsumer;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.core.heat.WorldHeatSources;
import dev.alaindustrial.menu.ThermalCentrifugeMenu;
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

/**
 * LV centrifuge that doubles an ore dust a second time (MOD-424): one uranium dust becomes two
 * shavings, which smelt into refined uranium. Stacked on the macerator's own doubling, a single ore
 * block yields four.
 *
 * <p><b>Three gates, not one.</b> This is the mod's first machine that must be <em>switched on</em>:
 * <ul>
 *   <li><b>Redstone.</b> The signal is held, not pulsed. Without it the rotor is stopped and the block
 *       spends exactly nothing — see {@link #onServerTick} and the polarity note below.</li>
 *   <li><b>Spin-up.</b> A stopped rotor needs {@link Config#thermalCentrifugeSpinupTicks} paid ticks to
 *       reach working speed, and sheds it at half that rate when it cannot pay.</li>
 *   <li><b>Heat.</b> A hot {@link ElectricHeaterBlockEntity} directly below. Unlike the Vulcanizer, lava
 *       and campfires do <em>not</em> qualify: the output multiplier here is fixed, so a heat
 *       <em>tier</em> would be a dial with nothing to turn.</li>
 * </ul>
 *
 * <p><b>Redstone polarity is inverted relative to the mod's only precedent, deliberately.</b> The Mob
 * Repeller treats a signal as "pause", matching hoppers and the documented {@code
 * redstone_inverter_upgrade}. Here the signal is the motor's start button: a machine whose whole
 * identity is a spinning rotor cannot sensibly read "no signal" as "run". The exception is recorded in
 * the block's OKF spec.
 *
 * <p><b>Why the idle draw exists.</b> A spun-up rotor with nothing to process keeps turning at
 * {@link Config#thermalCentrifugeIdleEuPerTick}. That is not a tax for leaving the lever on — it is what
 * makes the rotor's state readable: standing by costs a trickle, and the alternative (free coasting) would
 * make the spin-up rule meaningless, because a player could park at full speed for nothing.
 */
public final class ThermalCentrifugeBlockEntity extends MachineBlockEntity
		implements Overclockable, MenuProvider, HeatConsumer {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;
	public static final int SLOT_COUNT = 2;

	/** Six-wide data: base 0..3 plus spin permille and status. */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 2;
	/** Rotor speed as permille of a full spin-up — the wire format for the screen and the renderer. */
	public static final int DATA_SPIN = 4;
	/** {@link ThermalCentrifugeStatus} ordinal. */
	public static final int DATA_STATUS = 5;

	private static final int[] NO_SLOTS = new int[0];

	/**
	 * Resync the rotor to watching clients when the spin crosses a tenth. Every tick would be a packet per
	 * tick for a purely cosmetic ramp; a tenth is finer than the eye follows on a ten-second spin-up.
	 */
	private static final int SPIN_SYNC_STEP_PERMILLE = 100;

	/** Rotor speed in paid ticks, 0..{@link Config#thermalCentrifugeSpinupTicks}. Persisted. */
	private int spin;

	/** Parity counter for the half-rate spin-down. Deliberately not persisted, like the heater's. */
	private int coolParity;

	/** Last spin permille pushed to clients, so the renderer's ramp does not cost a packet per tick. */
	private int syncedSpinPermille = -1;

	private final RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> recipeCheck =
			ModRecipes.CENTRIFUGING.newCheck();

	private ThermalCentrifugeStatus status = ThermalCentrifugeStatus.NO_SIGNAL;

	public ThermalCentrifugeBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.THERMAL_CENTRIFUGE_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.thermalCentrifugeDuration);
	}

	private static int spinupTicks() {
		return Math.max(1, Config.thermalCentrifugeSpinupTicks);
	}

	/** Rotor speed as permille, for the screen gauge and the renderer's angular rate. */
	public int spinPermille() {
		return spin <= 0 ? 0 : Math.max(1, Math.min(spin * 1000 / spinupTicks(), 1000));
	}

	public boolean isAtSpeed() {
		return spin >= spinupTicks();
	}

	public ThermalCentrifugeStatus status() {
		return status;
	}

	@Override
	public boolean isWaitingOnHeat() {
		return status.waitsOnHeat();
	}

	/** Called from the block's neighbour callback, for both the heater below and the redstone signal. */
	@Override
	public void onHeatNeighbourChanged() {
		wake();
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// A lowered config must not leave a rotor spinning faster than the new ceiling.
		spin = Math.min(spin, spinupTicks());

		if (!level.hasNeighborSignal(pos)) {
			return stopped(level);
		}

		int euPerTick = effectiveEuPerTick(Config.thermalCentrifugeEuPerTick);
		ProcessingRecipeInput input = new ProcessingRecipeInput(items.get(INPUT_SLOT));
		AlaProcessingRecipe recipe = level instanceof ServerLevel server
				? ModRecipes.lookup(recipeCheck, server, input)
				: null;

		// The duration follows the recipe's own price at THIS machine's tariff. Feeding the raw energy to
		// effectiveDuration would scale it twice — the overclock helper applies the config speed itself.
		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Math.max(1, Config.thermalCentrifugeEuPerTick))
				: Config.thermalCentrifugeDuration;
		this.maxProgress = effectiveDuration(baseDuration);

		ItemStack result = recipe != null ? recipe.resultStack() : ItemStack.EMPTY;
		HeatSource heat = WorldHeatSources.resolve(level, pos);
		setStatus(diagnose(level, pos, recipe, input, result, heat));

		// Spin-up and processing are mutually exclusive branches of one paid tick, the shape the
		// Distillation Column established: EU buys revolutions first, product second.
		if (!isAtSpeed()) {
			return spinUp(euPerTick);
		}

		boolean canWork = status == ThermalCentrifugeStatus.READY && energy.getAmount() >= euPerTick;
		// Discover, then commit: a competing draw can empty the heater between the two, and progress must
		// never advance on a heat tick nobody paid for.
		if (canWork && !WorldHeatSources.consumeForProgress(level, pos, heat, overclockerCount())) {
			canWork = false;
			setStatus(ThermalCentrifugeStatus.HEATER_COLD);
		}
		updateLit(canWork);

		if (!canWork) {
			return coast();
		}

		// MOD-125/MOD-440: every branch of this tick reports the draw it actually decided on — the
		// working rate here, the ramp rate in spinUp, the idle trickle in coast, 0 when stopped.
		recordEuRate(euPerTick);
		energy.drainInternal(euPerTick);
		progress++;
		if (progress >= maxProgress) {
			progress = 0;
			recipe.consume(List.of(items.get(INPUT_SLOT)));
			addOutput(OUTPUT_SLOT, result);
			recordItemProcessed();
			creditUsefulWork(level, (long) euPerTick * maxProgress);
		}
		setChanged();
		return 0;
	}

	/**
	 * No signal: the full stop. The rotor loses its speed outright rather than coasting down, because the
	 * lever is the motor switch — cutting power mid-spin and finding the rotor still at speed would make
	 * the signal look optional.
	 */
	private int stopped(Level level) {
		boolean changed = spin != 0 || progress != 0;
		spin = 0;
		progress = 0;
		coolParity = 0;
		setStatus(ThermalCentrifugeStatus.NO_SIGNAL);
		updateLit(false);
		recordEuRate(0);
		if (changed) {
			setChanged();
			pushSpinToClients();
		}
		return IDLE_SLEEP_TICKS;
	}

	/** One paid tick of the ramp. Out of energy the rotor simply holds — freeze, don't waste (R-NRG-10). */
	private int spinUp(int euPerTick) {
		updateLit(false);
		if (energy.getAmount() < euPerTick) {
			recordEuRate(0);
			return 0;
		}
		recordEuRate(euPerTick);
		energy.drainInternal(euPerTick);
		spin++;
		setChanged();
		pushSpinToClients();
		return 0;
	}

	/**
	 * Spun up, powered, but with nothing to process: hold the revolutions for the idle rate, and shed them
	 * at half the rate they were gained once even that cannot be paid.
	 */
	private int coast() {
		int idle = Math.max(1, Config.thermalCentrifugeIdleEuPerTick);
		if (energy.getAmount() >= idle) {
			recordEuRate(idle);
			energy.drainInternal(idle);
			setChanged();
			return 0;
		}
		recordEuRate(0);
		if (spin > 0 && ++coolParity >= 2) {
			coolParity = 0;
			spin--;
			setChanged();
			pushSpinToClients();
		}
		return 0;
	}

	/**
	 * Why the machine is idle, in the order the player should fix things.
	 *
	 * <p>The input, recipe and output checks come <em>before</em> {@link ThermalCentrifugeStatus#SPINNING_UP}
	 * on purpose: {@code SPINNING_UP} answers true to {@link #isWaitingOnHeat()}, so a rotor idling with an
	 * empty slot must not report it, or the heater below would warm itself for a machine with nothing to do.
	 */
	private ThermalCentrifugeStatus diagnose(Level level, BlockPos pos, AlaProcessingRecipe recipe,
			ProcessingRecipeInput input, ItemStack result, HeatSource heat) {
		int needed = recipe != null ? recipe.inputCount(INPUT_SLOT) : 1;
		if (items.get(INPUT_SLOT).getCount() < needed) {
			return ThermalCentrifugeStatus.NO_INPUT;
		}
		if (recipe == null || !recipe.hasEnough(input)) {
			return ThermalCentrifugeStatus.NO_RECIPE;
		}
		if (!canOutput(OUTPUT_SLOT, result)) {
			return ThermalCentrifugeStatus.OUTPUT_BLOCKED;
		}
		if (!isAtSpeed()) {
			return ThermalCentrifugeStatus.SPINNING_UP;
		}
		if (heat != HeatSource.ELECTRIC_HEATER) {
			// resolve() cannot tell "no heater" from "heater still warming" — both come back as something
			// other than ELECTRIC_HEATER — so ask the block below directly. The two need different fixes.
			return hasHeaterBelow(level, pos)
					? ThermalCentrifugeStatus.HEATER_COLD
					: ThermalCentrifugeStatus.NO_HEATER;
		}
		return ThermalCentrifugeStatus.READY;
	}

	private static boolean hasHeaterBelow(Level level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(ModContent.ELECTRIC_HEATER.get());
	}

	private void setStatus(ThermalCentrifugeStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	/**
	 * Push the rotor's speed to everyone watching, coarsely.
	 *
	 * <p>The renderer reads the client copy of this block entity, and {@code ContainerData} would not do:
	 * that only flows while a menu is open, so a closed GUI would freeze the rotor for every other player.
	 * The spin rides the block-entity update packet instead — but only when it crosses a tenth, because the
	 * sync is rate-limited and a per-tick call would spend the whole budget on a cosmetic ramp.
	 */
	private void pushSpinToClients() {
		int permille = spinPermille();
		if (syncedSpinPermille < 0
				|| permille == 0
				|| permille == 1000
				|| Math.abs(permille - syncedSpinPermille) >= SPIN_SYNC_STEP_PERMILLE) {
			syncedSpinPermille = permille;
			syncBlockEntityToClient();
		}
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == INPUT_SLOT;
	}

	/** The down face belongs to the heater; automation must not reach through it. */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? NO_SLOTS : super.getSlotsForFace(side);
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side != Direction.DOWN && canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side != Direction.DOWN && slot == OUTPUT_SLOT;
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Spin", spin);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		spin = Math.max(0, Math.min(input.getIntOr("Spin", 0), spinupTicks()));
	}

	private final ContainerData centrifugeData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_SPIN -> spinPermille();
				case DATA_STATUS -> status.ordinal();
				default -> ThermalCentrifugeBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			// Both readout channels are derived and server-authoritative.
			if (index < DATA_SPIN) {
				ThermalCentrifugeBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return centrifugeData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.thermal_centrifuge");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ThermalCentrifugeMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}

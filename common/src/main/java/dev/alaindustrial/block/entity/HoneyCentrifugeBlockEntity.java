package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.menu.HoneyCentrifugeMenu;
import dev.alaindustrial.recipe.ItemToFluidRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV Honey Centrifuge (MOD-924): spins honeycombs, honeycomb blocks, honey bottles and honey blocks into
 * liquid honey. The mirror image of the Polymerizer — an item goes in, a fluid volume comes out — and its
 * output tank works like the Fermenter's biofuel tank.
 *
 * <p><b>Recipes are data</b> ({@link ItemToFluidRecipe}, {@code data/<ns>/recipe/honey_centrifuging/*.json}):
 * the shipped four state the owner's yields (comb 100 mB, comb block 400, bottle 250 plus the empty
 * bottle, honey block 1000), and a datapack adds another mod's comb as one more file.
 *
 * <p><b>The tank only gives honey out.</b> It is filled by a completed operation alone; every face lets a
 * neighbour (a pipe set to extract, a pump, another mod's pipe) drain it, none fills it. A full tank stops
 * the machine before it consumes the input, so nothing is ever voided. Without pipes the player takes the
 * honey with an empty bucket in the container pair of slots.
 */
public class HoneyCentrifugeBlockEntity extends MachineBlockEntity
		implements Overclockable, FluidPortHost, MenuProvider, BatteryFed {
	/** What is spun: a comb, a comb block, a honey bottle or a honey block (anything a recipe accepts). */
	public static final int INPUT_SLOT = 0;
	/** What a recipe hands back for the consumed item — the empty bottle of a honey bottle. */
	public static final int REMAINDER_SLOT = 1;
	/** An empty bucket (or capsule, or a foreign container) placed here is filled from the tank. */
	public static final int BUCKET_INPUT_SLOT = 2;
	/** The filled container drops here. */
	public static final int BUCKET_OUTPUT_SLOT = 3;
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes from this. */
	public static final int SLOT_COUNT = 4;

	/** Output tank: 10 buckets, like every machine with a tank. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/** Sentinel for an empty tank on the fluid-id sync channel — see {@link FluidTank#FLUID_ID_NONE}. */
	public static final int FLUID_ID_NONE = FluidTank.FLUID_ID_NONE;

	/** Honey out. Filled only by a completed operation; neighbours may drain it, never fill it. */
	public final FluidTank honeyTank = new FluidTank(TANK_CAPACITY,
			fluid -> false,
			fluid -> true,
			() -> {
				setChanged();
				// A neighbour that just drained a full tank restarts the stalled machine next tick.
				wake();
			});

	private final RecipeManager.CachedCheck<SingleRecipeInput, ItemToFluidRecipe> recipeCheck =
			ModRecipes.HONEY_CENTRIFUGING.newCheck();

	private HoneyCentrifugeStatus status = HoneyCentrifugeStatus.NO_INPUT;

	/** The shared eight-step tick loop (MOD-557). */
	private final ProcessingCycle cycle = new ProcessingCycle(this);

	public HoneyCentrifugeBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0. Shares
		// machineBuffer with the other LV processing machines — its 200 EU operation fits four times over.
		super(ModContent.HONEY_CENTRIFUGE_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = MachineRates.duration(Config.honeyCentrifugeDuration,
				Config.globalMachineSpeedMultiplier);
	}

	/** Every face exposes the same tank, drain-only — the machine takes no fluid in. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return honeyTank;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// 1) Fill an empty container from the tank (no EU cost — manual handling).
		boolean bucketWork = ItemFluidBridge.get().fillSlotFromTank(this,
				BUCKET_INPUT_SLOT, BUCKET_OUTPUT_SLOT, honeyTank, FluidAmounts.BUCKET) > 0;

		ItemToFluidRecipe recipe = level instanceof ServerLevel server ? resolveRecipe(server) : null;
		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Config.machineEuPerTick)
				: Config.honeyCentrifugeDuration;
		ProcessingCycle.Job job = cycle.job(Config.machineEuPerTick, baseDuration);

		boolean readyExceptEnergy = recipe != null && canComplete(recipe);
		boolean canWork = readyExceptEnergy && energy.getAmount() >= job.euPerTick();

		setStatus(diagnose(recipe));

		// The shared cycle (MOD-557). "The input is no longer a recipe" restarts progress; on power loss, a
		// full tank or a blocked container slot the recipe is still there, so progress freezes (R-NRG-10).
		return job.canWork(canWork)
				.readyExceptEnergy(readyExceptEnergy)
				.jobIntact(recipe != null)
				.keepAwake(bucketWork)
				.run(level, () -> {
					// The cycle may complete an operation while canWork is false (the coasting skill), so the
					// completion checks its own preconditions again: never void honey, never overfill a slot.
					if (!canComplete(recipe)) {
						return;
					}
					ItemStack remainder = recipe.remainderStack();
					items.get(INPUT_SLOT).shrink(1);
					fill(recipe);
					if (!remainder.isEmpty()) {
						addOutput(REMAINDER_SLOT, remainder);
					}
				});
	}

	/** The recipe the input slot satisfies, or {@code null}. */
	private ItemToFluidRecipe resolveRecipe(ServerLevel level) {
		ItemStack input = items.get(INPUT_SLOT);
		if (input.isEmpty()) {
			return null;
		}
		return recipeCheck.getRecipeFor(new SingleRecipeInput(input), level).map(RecipeHolder::value).orElse(null);
	}

	/** Whether one more operation of {@code recipe} can land: the input, the tank room and the slot room. */
	private boolean canComplete(ItemToFluidRecipe recipe) {
		ItemStack remainder = recipe.remainderStack();
		return !items.get(INPUT_SLOT).isEmpty()
				&& fits(recipe)
				&& (remainder.isEmpty() || canOutput(REMAINDER_SLOT, remainder));
	}

	/** Whether the recipe's yield fits the tank — same fluid (or empty) and room for the whole volume. */
	private boolean fits(ItemToFluidRecipe recipe) {
		FluidHolder made = FluidHolder.of(recipe.result().fluid().value());
		boolean sameFluid = honeyTank.fluid.isEmpty() || honeyTank.fluid.equals(made);
		return sameFluid && honeyTank.amount + recipe.amount() <= honeyTank.capacity;
	}

	/**
	 * Write a completed operation's yield into the tank (guarded by {@link #fits}). The machine filling its
	 * own output bypasses the tank's {@code canInsert} guard (false, so neighbours cannot push fluid in) by
	 * mutating the field directly — the Fermenter's path for its biofuel.
	 */
	private void fill(ItemToFluidRecipe recipe) {
		honeyTank.fluid = FluidHolder.of(recipe.result().fluid().value());
		honeyTank.amount = Math.min(honeyTank.capacity, honeyTank.amount + recipe.amount());
	}

	private HoneyCentrifugeStatus diagnose(ItemToFluidRecipe recipe) {
		if (items.get(INPUT_SLOT).isEmpty()) {
			return HoneyCentrifugeStatus.NO_INPUT;
		}
		if (recipe == null) {
			return HoneyCentrifugeStatus.NO_RECIPE;
		}
		if (!fits(recipe)) {
			return HoneyCentrifugeStatus.TANK_FULL;
		}
		ItemStack remainder = recipe.remainderStack();
		if (!remainder.isEmpty() && !canOutput(REMAINDER_SLOT, remainder)) {
			return HoneyCentrifugeStatus.OUTPUT_BLOCKED;
		}
		return HoneyCentrifugeStatus.READY;
	}

	private void setStatus(HoneyCentrifugeStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	/** The honey tank, for the menu and gametests. */
	public FluidPort honeyPort() {
		return honeyTank;
	}

	/**
	 * GUI sync channels (MOD-712): the base four, the tank fill in permille, the held fluid's registry id
	 * and the idle reason ({@link HoneyCentrifugeStatus} ordinal); all read-only.
	 */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, LEVEL_PERMILLE, FLUID_ID, STATUS }

	/** Width of {@link #getDataAccess()}, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.LEVEL_PERMILLE, () -> SyncChannels.permille(honeyTank.amount, TANK_CAPACITY))
				.read(Channel.FLUID_ID, () -> honeyTank.fluidSyncId())
				.read(Channel.STATUS, () -> status.ordinal())
				.build();
	}

	/**
	 * What each slot may physically hold. The input takes anything a recipe accepts; the container pair takes
	 * fluid containers, but never a glass or honey bottle — a bottle is the recipe's business, and the
	 * bridge would otherwise treat one as a third of a bucket on Fabric. The container output slot must
	 * answer true too: the loader's item-transfer API asks before putting the filled container there.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case INPUT_SLOT -> level instanceof ServerLevel server && !stack.isEmpty()
					&& recipeCheck.getRecipeFor(new SingleRecipeInput(stack), server).isPresent();
			case BUCKET_INPUT_SLOT, BUCKET_OUTPUT_SLOT -> !stack.is(Items.GLASS_BOTTLE)
					&& !stack.is(Items.HONEY_BOTTLE) && ItemFluidBridge.get().isFluidContainer(stack);
			default -> false;
		};
	}

	/** Hoppers and pipes may feed the input and the empty-container slot; the machine fills the rest. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return (slot == INPUT_SLOT || slot == BUCKET_INPUT_SLOT) && canPlaceItem(slot, stack);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == REMAINDER_SLOT || slot == BUCKET_OUTPUT_SLOT;
	}

	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new HoneyCentrifugeMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		honeyTank.save(output, "Honey");
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		// The tank reads its fluid back from the stored registry id (MOD-261): a datapack recipe may fill it
		// with another fluid, so the contents are never assumed.
		honeyTank.load(input, "Honey");
	}
}

package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.menu.FermenterMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModTags;
import java.util.List;
import dev.alaindustrial.skill.SkillMachine;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV Fermenter (MOD-146): brews organic waste and water into biomass and biofuel — the mod's first
 * fuel that is farmed rather than pumped, and the head of the chain that ends at the sprinkler
 * (MOD-525).
 *
 * <p><b>Two outputs of one batch, of two different kinds — and only one of them is certain.</b>
 * Biofuel is always brewed; biomass is rolled against the recipe's {@code chance}. That split is the
 * feature: fermentation always yields the liquid, and the solid leftover is what sometimes survives
 * it.
 *
 * <p><b>Three price tiers, one cost.</b> Every batch costs the same energy and the same time, so the
 * only lever a player has is what they feed it. Four pieces of garden waste return 20 mB and a one-
 * in-ten chance of biomass; one golden carrot returns 150 mB and a seven-in-ten chance. The tier is
 * read from the input's tag ({@link ModTags.Items#FERMENTER_INPUT_POOR} and its two siblings), and
 * the biomass odds live in the recipe JSON, so a datapack can retune either side.
 *
 * <p>The item comes from an ordinary {@link AlaProcessingRecipe}; the fluid does not — it is a
 * per-tier {@code Config} value, and the water is a fixed {@link Config#fermenterWaterPerOp} cost the
 * same way.
 *
 * <p><b>Why the fluids are not in the recipe.</b> No recipe family in this mod takes items
 * <em>and</em> a fluid at once, and none produces both: {@link AlaProcessingRecipe} moves item
 * stacks, {@link dev.alaindustrial.recipe.FluidOutputRecipe} moves fluid volumes, and the schema
 * validator rejects a JSON that mixes them. Widening either would mean a new recipe input type,
 * codec, {@code RecipeType}/{@code RecipeSerializer} pair on both loaders and a new category in both
 * recipe viewers. The Galvanic Bath already answered this question the cheap way — its water is a
 * config cost — and this machine follows that precedent on both sides of the operation.
 *
 * <p><b>Faces.</b> Water goes in anywhere; biofuel comes out of the bottom only. That asymmetry is
 * the same readable convention the distillation column uses (heavy fraction at the base), and it
 * means a single pipe run cannot accidentally push water into the output tank.
 */
public class FermenterBlockEntity extends MachineBlockEntity
		implements Overclockable, FluidPortHost, MenuProvider {

	/** Organic matter to ferment: matched by tag through the recipe. */
	public static final int ORGANIC_SLOT = 0;
	/** A filled water container placed here is emptied into the water tank. */
	public static final int WATER_FILL_INPUT_SLOT = 1;
	/** The emptied container drops here. */
	public static final int WATER_FILL_OUTPUT_SLOT = 2;
	/** An empty container placed here is filled from the biofuel tank. */
	public static final int BIOFUEL_DRAIN_INPUT_SLOT = 3;
	/** The filled container drops here. */
	public static final int BIOFUEL_DRAIN_OUTPUT_SLOT = 4;
	/** Result slot: biomass (machine-fill only). */
	public static final int OUTPUT_SLOT = 5;
	/** Machine-specific slot count; the four upgrade slots are appended by the base class. */
	public static final int SLOT_COUNT = 6;

	/** Both tanks hold 10 buckets, matching every other tank-bearing machine in the mod. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/** Sentinel for an empty tank on a fluid-id sync channel — see {@link FluidTank#FLUID_ID_NONE}. */
	public static final int FLUID_ID_NONE = FluidTank.FLUID_ID_NONE;

	/**
	 * Water in. Source water only: {@code Fluids.FLOWING_WATER} is a different object and a
	 * single-variant tank holding it could never be topped up to a batch volume. Never extractable
	 * (R-CON-08) — water that entered is feedstock, not storage.
	 */
	public final FluidTank waterTank = new FluidTank(TANK_CAPACITY,
			FermenterBlockEntity::isWater,
			fluid -> false,
			() -> {
				setChanged();
				wake();
			});

	/** Biofuel out. Filled only by a completed batch; neighbours may drain it, never fill it. */
	public final FluidTank biofuelTank = new FluidTank(TANK_CAPACITY,
			fluid -> false,
			fluid -> true,
			() -> {
				setChanged();
				wake();
			});

	private final RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> recipeCheck =
			ModRecipes.FERMENTING.newCheck();

	private FermenterStatus status = FermenterStatus.NO_ORGANIC;

	/** The shared eight-step tick loop (MOD-557). */
	private final ProcessingCycle cycle = new ProcessingCycle(this);

	public FermenterBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0.
		super(ModContent.FERMENTER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.fermenterDuration);
	}

	private static boolean isWater(FluidHolder fluid) {
		return !fluid.isEmpty() && fluid.fluid() == Fluids.WATER;
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	/**
	 * The bottom face drains biofuel; every other face — and a {@code null} probe from Jade, WTHIT or
	 * a foreign pipe — answers the water tank, this machine's "identity" fluid.
	 */
	@Override
	public FluidPort fluidPort(Direction side) {
		return side == Direction.DOWN ? biofuelTank : waterTank;
	}

	/** The biofuel tank, for the renderer, the menu and gametests. */
	public FluidPort biofuelPort() {
		return biofuelTank;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// 1) Manual container handling, no EU cost: water in, biofuel out.
		boolean bucketWork = ItemFluidBridge.get().drainSlotIntoTank(this,
				WATER_FILL_INPUT_SLOT, WATER_FILL_OUTPUT_SLOT, waterTank, FluidAmounts.BUCKET) > 0;
		bucketWork |= ItemFluidBridge.get().fillSlotFromTank(this,
				BIOFUEL_DRAIN_INPUT_SLOT, BIOFUEL_DRAIN_OUTPUT_SLOT, biofuelTank, FluidAmounts.BUCKET) > 0;

		ProcessingRecipeInput input = new ProcessingRecipeInput(items.get(ORGANIC_SLOT));
		AlaProcessingRecipe recipe = level instanceof ServerLevel server
				? ModRecipes.lookup(recipeCheck, server, input)
				: null;

		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Config.machineEuPerTick)
				: Config.fermenterDuration;
		ProcessingCycle.Job job = cycle.job(Config.machineEuPerTick, baseDuration);

		ItemStack result = recipe != null ? recipe.resultStack() : ItemStack.EMPTY;
		long waterPerOp = waterPerOperation();
		long biofuelPerOp = biofuelFor(items.get(ORGANIC_SLOT));
		// The whole price must be on hand every tick, not only at the start: pulling the input or the
		// water out mid-batch stops the run rather than completing it underpaid.
		boolean canWork = recipe != null && recipe.hasEnough(input)
				&& waterTank.amount >= waterPerOp
				&& biofuelFits(biofuelPerOp)
				&& energy.getAmount() >= job.euPerTick()
				&& canOutput(OUTPUT_SLOT, result);

		setStatus(diagnose(recipe, input, result, waterPerOp, biofuelPerOp));

		// The shared cycle (MOD-557) owns the lit state, the rate report, the drain, the progress step,
		// the operation counter, the XP credit and the sleep answer. "The input is no longer a recipe"
		// is what restarts progress; on mere power loss, a dry tank or a full output the recipe is still
		// there, so progress FREEZES and resumes (R-NRG-10).
		return job.canWork(canWork)
				.jobIntact(recipe != null)
				.keepAwake(bucketWork)
				.run(level, () -> {
					// Read the tier BEFORE the input is consumed — afterwards the slot may be empty, and
					// an empty stack matches no tag, which would silently pay every batch at the poor rate.
					long brewed = biofuelFor(items.get(ORGANIC_SLOT));
					recipe.consume(List.of(items.get(ORGANIC_SLOT)));
					consumeWater(waterPerOp);
					brewBiofuel(brewed);
					// Biofuel is certain, biomass is a roll: the fluid is what fermentation always yields,
					// the solid leftover is what sometimes survives it. A recipe with no stated chance
					// (chance() < 0) always delivers, so a datapack can opt out of the gamble.
					if (recipe.chance() < 0 || level.getRandom().nextDouble() < recipe.chance()) {
						addOutput(OUTPUT_SLOT, result);
					}
				});
	}

	/** Water consumed per batch, clamped to at least 1 mB so a config of 0 cannot delete the gate. */
	private long waterPerOperation() {
		// MOD-483 Frugal Vat.
		return Math.max(1L, SkillMachine.fermenterWater(Config.fermenterWaterPerOp, level, getOwner()));
	}

	/**
	 * Biofuel brewed per batch, by the input's tier. Read from the tag rather than from the recipe:
	 * no recipe family in this mod carries a fluid output alongside an item one, so the fluid side of
	 * the operation belongs to the machine — exactly as the water cost does.
	 *
	 * <p>Clamped to at least 1 mB for the same reason the water cost is: a config of 0 would make the
	 * machine brew nothing while still eating its input, and the "tank full" gate would never trip.
	 */
	private static long biofuelFor(ItemStack input) {
		if (input.is(ModTags.Items.FERMENTER_INPUT_RICH)) {
			return Math.max(1L, Config.fermenterBiofuelRich);
		}
		if (input.is(ModTags.Items.FERMENTER_INPUT_COMMON)) {
			return Math.max(1L, Config.fermenterBiofuelCommon);
		}
		return Math.max(1L, Config.fermenterBiofuelPoor);
	}

	/** Whether another batch's brew fits. A full output tank stalls the machine; nothing is voided. */
	private boolean biofuelFits(long amount) {
		FluidHolder brewed = FluidHolder.of(ModContent.BIOFUEL.get());
		boolean sameFluid = biofuelTank.fluid.isEmpty() || biofuelTank.fluid.equals(brewed);
		return sameFluid && biofuelTank.amount + amount <= biofuelTank.capacity;
	}

	/**
	 * Take water out for a completed batch. The machine consuming its own feedstock bypasses the
	 * tank's {@code canExtract} guard (false, so neighbours cannot siphon it back) by mutating the
	 * field directly — the path the Polymerizer and the Galvanic Bath use. A direct mutator owns the
	 * tank invariant: clear the fluid when the amount hits 0.
	 */
	private void consumeWater(long amount) {
		waterTank.amount = Math.max(0L, waterTank.amount - amount);
		if (waterTank.amount == 0) {
			waterTank.fluid = FluidHolder.EMPTY;
		}
	}

	/** Write a completed batch's brew into the output tank (guarded by {@link #biofuelFits}). */
	private void brewBiofuel(long amount) {
		biofuelTank.fluid = FluidHolder.of(ModContent.BIOFUEL.get());
		biofuelTank.amount = Math.min(biofuelTank.capacity, biofuelTank.amount + amount);
	}

	/**
	 * Why the machine is idle. With a recipe in hand the shortfall is measured against its own batch
	 * price, so half a batch reads as "need more" rather than "no matching recipe" — and an empty
	 * water tank or a full biofuel tank reads as itself, even though neither is part of the recipe.
	 */
	private FermenterStatus diagnose(AlaProcessingRecipe recipe, ProcessingRecipeInput input,
			ItemStack result, long waterPerOp, long biofuelPerOp) {
		int organicNeeded = recipe != null ? recipe.inputCount(ORGANIC_SLOT) : 1;
		if (items.get(ORGANIC_SLOT).getCount() < organicNeeded) {
			return FermenterStatus.NO_ORGANIC;
		}
		if (recipe == null) {
			return FermenterStatus.NO_RECIPE;
		}
		if (waterTank.amount < waterPerOp) {
			return FermenterStatus.NO_WATER;
		}
		if (!biofuelFits(biofuelPerOp)) {
			return FermenterStatus.BIOFUEL_FULL;
		}
		if (!canOutput(OUTPUT_SLOT, result)) {
			return FermenterStatus.OUTPUT_BLOCKED;
		}
		return FermenterStatus.READY;
	}

	private void setStatus(FermenterStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	/** Nine-wide data: the base four, two gauges with their fluid ids, and the idle reason. */
	public static final int DATA_COUNT = 9;

	public static final int CH_WATER_PERMILLE = 4;
	public static final int CH_WATER_FLUID_ID = 5;
	public static final int CH_BIOFUEL_PERMILLE = 6;
	public static final int CH_BIOFUEL_FLUID_ID = 7;
	public static final int CH_STATUS = 8;

	/**
	 * Channels 4..8 are derived, server-authoritative projections; nothing writes them back.
	 *
	 * <p><b>Every channel must fit a signed 16-bit short</b> — the container-set-data packet writes
	 * each value with {@code writeShort}, so a larger one silently arrives truncated. Hence permille
	 * rather than raw mB, and hence a fluid id above {@link Short#MAX_VALUE} reporting as empty rather
	 * than as its truncated low 16 bits, which would resolve to an unrelated fluid.
	 */
	private final ContainerData fermenterData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case CH_WATER_PERMILLE -> permille(waterTank);
				case CH_WATER_FLUID_ID -> waterTank.fluidSyncId();
				case CH_BIOFUEL_PERMILLE -> permille(biofuelTank);
				case CH_BIOFUEL_FLUID_ID -> biofuelTank.fluidSyncId();
				case CH_STATUS -> status.ordinal();
				default -> FermenterBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index < MachineBlockEntity.DATA_COUNT) {
				FermenterBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	private static int permille(FluidTank tank) {
		return tank.amount <= 0 ? 0
				: Math.max(1, (int) Math.min(tank.amount * 1000L / tank.capacity, 1000));
	}

	@Override
	public ContainerData getDataAccess() {
		return fermenterData;
	}

	/**
	 * What each slot may physically hold. The organic slot takes anything a fermenting recipe accepts,
	 * both fill slots take any fluid container.
	 *
	 * <p>The container <em>output</em> slots must answer true as well: the loader's item-transfer API
	 * asks this before letting the exchange put the emptied or filled container there, and answering
	 * false made the bridge silently move nothing (the pump's MOD-107 bug). Automation policy is a
	 * separate question, answered by {@link #canPlaceItemThroughFace}.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case ORGANIC_SLOT -> stack.is(ModTags.Items.FERMENTER_INPUT);
			case WATER_FILL_INPUT_SLOT, WATER_FILL_OUTPUT_SLOT,
					BIOFUEL_DRAIN_INPUT_SLOT, BIOFUEL_DRAIN_OUTPUT_SLOT ->
					ItemFluidBridge.get().isFluidContainer(stack);
			default -> false;
		};
	}

	/** Hoppers and pipes may feed the input and the two container-in slots; the machine fills the rest. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot != WATER_FILL_OUTPUT_SLOT && slot != BIOFUEL_DRAIN_OUTPUT_SLOT
				&& canPlaceItem(slot, stack);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == WATER_FILL_OUTPUT_SLOT || slot == BIOFUEL_DRAIN_OUTPUT_SLOT || slot == OUTPUT_SLOT;
	}

	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.fermenter");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new FermenterMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		// The tanks write themselves (MOD-556) under the keys they have always used.
		waterTank.save(output, "Water");
		biofuelTank.save(output, "Biofuel");
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// Each tank reads its fluid back from the stored registry id, never from the machine's expected
		// contents — hardcoding it is the geothermal generator's save-corruption bug (MOD-261).
		waterTank.load(input, "Water");
		biofuelTank.load(input, "Biofuel");
	}
}

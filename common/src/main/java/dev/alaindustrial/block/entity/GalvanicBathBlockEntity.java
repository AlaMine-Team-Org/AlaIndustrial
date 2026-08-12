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
import dev.alaindustrial.menu.GalvanicBathMenu;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV Galvanic Bath (MOD-127): plates fibre with silver in a water bath to make flux thread — the
 * first step of the Fluxweave chain. Two item inputs (fibre, silver dust) drive a normal
 * {@link AlaProcessingRecipe}; the water is a fixed per-operation cost held in an internal tank.
 *
 * <p><b>Why the water is not part of the recipe.</b> No recipe family in the mod takes items
 * <em>and</em> a fluid at once: {@link AlaProcessingRecipe} consumes ordered item stacks and
 * {@link dev.alaindustrial.recipe.PolymerizingRecipe} consumes a fluid volume, and widening either
 * one would mean a new recipe input type, codec, {@code RecipeType}/{@code RecipeSerializer} pair on
 * both loaders and recipe-viewer integration. The bath instead pairs the Vulcanizer's two-input
 * recipe shape with the Polymerizer's tank, and charges {@link Config#galvanicBathWaterPerOp} mB per
 * completed operation from the tank directly. A datapack can therefore retune the fibre, the silver
 * and the energy, but the water volume lives in the config.
 *
 * <p><b>Two ways to fill it</b>, exactly like the Polymerizer: a bucket (or capsule, or a foreign
 * mod's cell) in the fill slot is emptied into the tank and drops out below, and adjacent transport
 * inserts straight into the tank through the neutral {@link FluidPort} exposed on every face. The
 * tank never gives water back ({@code canExtract = false}, R-CON-08): water that entered is
 * feedstock, not storage, so the bath cannot be used as a fluid buffer.
 *
 * <p><b>Fibre is a tag.</b> The shipped recipe accepts {@code #alaindustrial:fiber} — vanilla string
 * or our own cotton fibre (MOD-280) — so a spider farm and a trellis are equally valid ways in.
 */
public class GalvanicBathBlockEntity extends MachineBlockEntity implements Overclockable, FluidPortHost, MenuProvider {
	/** Fibre input: vanilla string or cotton fibre, matched by tag through the recipe. */
	public static final int FIBER_SLOT = 0;
	/** The silver that gets deposited onto the fibre. */
	public static final int SILVER_SLOT = 1;
	/** A filled water container placed here is emptied into the tank. */
	public static final int FILL_INPUT_SLOT = 2;
	/** The emptied container drops here. */
	public static final int FILL_OUTPUT_SLOT = 3;
	/** Result slot: flux thread (machine-fill only). */
	public static final int OUTPUT_SLOT = 4;
	/** Machine-specific slot count; the four upgrade slots are appended by the base class. */
	public static final int SLOT_COUNT = 5;

	/** Internal tank: 10 buckets, matching the pump, the geothermal generator and the polymerizer. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/** Output stack cap, mirroring the other processing machines. */
	private static final int OUTPUT_MAX = 64;

	/** Sentinel for an empty tank on the fluid-id sync channel — the vanilla {@code IdMap} default. */
	public static final int FLUID_ID_NONE = IdMap.DEFAULT;

	/**
	 * Water-only tank, insertable from any side, never extractable (R-CON-08 — the machine consumes
	 * its own feedstock). Only the <em>source</em> fluid is accepted: {@code Fluids.FLOWING_WATER} is a
	 * different object, and a tank never holds "flowing water" — letting a partial amount of it in
	 * would strand the machine, because the tank is single-variant and ordinary water could then never
	 * top it up to the operation volume.
	 */
	public final FluidTank fluidTank = new FluidTank(TANK_CAPACITY,
			GalvanicBathBlockEntity::isWater,
			fluid -> false,
			() -> {
				setChanged();
				// A neighbour that just filled the tank must restart an idle machine on the next tick
				// (R-29); without this the machine would wait out its idle-sleep window first.
				wake();
			});

	private final RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> recipeCheck =
			ModRecipes.GALVANIC_BATH.newCheck();

	private GalvanicBathStatus status = GalvanicBathStatus.READY;

	public GalvanicBathBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0.
		// Shares machineBuffer with the other LV processing machines.
		super(ModContent.GALVANIC_BATH_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.galvanicBathDuration);
	}

	private static boolean isWater(FluidHolder fluid) {
		return !fluid.isEmpty() && fluid.fluid() == Fluids.WATER;
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}


	/** Every face exposes the same tank — the bath has no per-face fluid restriction. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return fluidTank;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// 1) Empty a filled container from the fill slot into the tank (no EU cost — manual handling).
		//    Buckets, our capsule and foreign containers all ride the loader's item fluid capability.
		boolean filled = ItemFluidBridge.get()
				.drainSlotIntoTank(this, FILL_INPUT_SLOT, FILL_OUTPUT_SLOT, fluidTank, FluidAmounts.BUCKET) > 0;

		int euPerTick = effectiveEuPerTick(Config.machineEuPerTick);
		ProcessingRecipeInput input =
				new ProcessingRecipeInput(items.get(FIBER_SLOT), items.get(SILVER_SLOT));
		AlaProcessingRecipe recipe = level instanceof ServerLevel server
				? ModRecipes.lookup(recipeCheck, server, input)
				: null;

		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Config.machineEuPerTick)
				: Config.galvanicBathDuration;
		this.maxProgress = effectiveDuration(baseDuration);

		ItemStack result = recipe != null ? recipe.resultStack() : ItemStack.EMPTY;
		long waterPerOp = waterPerOperation();
		// The whole price must be on hand every tick, not just at the start: pulling an input or the
		// water out mid-cycle stops the run instead of completing it underpaid (MOD-271's lesson on
		// the Vulcanizer's batch price).
		boolean canWork = recipe != null && recipe.hasEnough(input) && fluidTank.amount >= waterPerOp
				&& energy.getAmount() >= euPerTick && canOutput(result);

		setStatus(diagnose(recipe, input, result, waterPerOp));
		updateLit(canWork);

		if (canWork) {
			energy.drainInternal(euPerTick);
			progress++;
			if (progress >= maxProgress) {
				progress = 0;
				recipe.consume(List.of(items.get(FIBER_SLOT), items.get(SILVER_SLOT)));
				consumeWater(waterPerOp);
				addOutput(result);
				creditUsefulWork(level, (long) euPerTick * maxProgress); // MOD-133: completed op → XP
			}
			setChanged();
		} else if (recipe == null && progress != 0) {
			// The inputs no longer form a recipe: restart from zero. On mere power loss, a dry tank or a
			// full output the recipe is still there, so progress FREEZES and resumes when work can
			// continue (R-NRG-10) — the same contract as the other item-fed machines.
			progress = 0;
			setChanged();
		}
		return canWork || filled ? 0 : IDLE_SLEEP_TICKS;
	}

	/**
	 * Water consumed per completed operation, clamped to at least 1 mB. The clamp is not cosmetic: a
	 * config of 0 would make the machine run for free and, worse, {@code amount >= 0} is always true,
	 * so the "no water" gate would silently disappear (the class of bug MOD-169 shipped with a zero
	 * divisor).
	 */
	private static long waterPerOperation() {
		return Math.max(1L, Config.galvanicBathWaterPerOp);
	}

	/**
	 * Take {@code amount} mB out of the tank for a completed operation. This is the machine consuming
	 * its own feedstock, so it bypasses the tank's {@code canExtract} guard (false, to stop neighbours
	 * siphoning water back out) by mutating the field directly — the same internal path the
	 * Polymerizer and the geothermal generator use. A direct mutator owns the tank's invariant: clear
	 * the fluid when the amount hits 0.
	 */
	private void consumeWater(long amount) {
		fluidTank.amount = Math.max(0L, fluidTank.amount - amount);
		if (fluidTank.amount == 0) {
			fluidTank.fluid = FluidHolder.EMPTY;
		}
	}

	/**
	 * Why the machine is idle. With a recipe in hand the shortfall is measured against its own
	 * per-input price, so half a batch reads as "need silver" rather than "no matching recipe" — and,
	 * most importantly, an empty tank reads as "no water" even though water is not part of the recipe.
	 */
	private GalvanicBathStatus diagnose(AlaProcessingRecipe recipe, ProcessingRecipeInput input,
			ItemStack result, long waterPerOp) {
		int fiberNeeded = recipe != null ? recipe.inputCount(FIBER_SLOT) : 1;
		int silverNeeded = recipe != null ? recipe.inputCount(SILVER_SLOT) : 1;
		if (items.get(FIBER_SLOT).getCount() < fiberNeeded) {
			return GalvanicBathStatus.NO_FIBER;
		}
		if (items.get(SILVER_SLOT).getCount() < silverNeeded) {
			return GalvanicBathStatus.NO_SILVER;
		}
		if (recipe == null) {
			return GalvanicBathStatus.NO_RECIPE;
		}
		if (fluidTank.amount < waterPerOp) {
			return GalvanicBathStatus.NO_WATER;
		}
		if (!canOutput(result)) {
			return GalvanicBathStatus.OUTPUT_BLOCKED;
		}
		return GalvanicBathStatus.READY;
	}

	private void setStatus(GalvanicBathStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	/** Whether the output slot can accept one more {@code result} stack. */
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

	/**
	 * Seven-wide data — hides {@link MachineBlockEntity#DATA_COUNT} so
	 * {@code GalvanicBathBlockEntity.DATA_COUNT} names this machine's width for both the bridge below
	 * and {@code GalvanicBathMenu}'s client stub (MOD-235).
	 */
	public static final int DATA_COUNT = 7;

	/**
	 * Base channels 0..3 (energy/capacity/progress/maxProgress) plus the tank fill as a permille (4),
	 * the held fluid's registry id (5) and the idle reason (6). Channels 4..6 are derived,
	 * server-authoritative projections; nothing writes them back.
	 *
	 * <p><b>Every channel must fit a signed 16-bit short</b> — {@code ClientboundContainerSetDataPacket}
	 * writes each value with {@code writeShort}, so a larger value silently arrives truncated. That is
	 * why the tank level travels as a permille (0..1000) rather than raw mB, and why the fluid's colour
	 * is not sent at all: a packed ARGB is 32 bits. The screen derives it from channel 5.
	 */
	private final ContainerData bathData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> fluidTank.amount <= 0 ? 0
						: Math.max(1, (int) Math.min(fluidTank.amount * 1000L / TANK_CAPACITY, 1000));
				case 5 -> fluidRegistryId(fluidTank.fluid);
				case 6 -> status.ordinal();
				default -> GalvanicBathBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index < 4) {
				GalvanicBathBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return bathData;
	}

	/**
	 * What each slot may physically hold. The fibre slot takes anything in the fibre tag, the silver
	 * slot the silver dust, and both fill slots any fluid container.
	 *
	 * <p>The fill-<em>output</em> slot must answer true as well: the loader's item-transfer API asks
	 * this before letting the exchange put the emptied container there, and answering false made the
	 * bridge silently move nothing (the pump's MOD-107 bug). Automation policy is a separate question,
	 * answered by {@link #canPlaceItemThroughFace}.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case FIBER_SLOT -> stack.is(ModTags.Items.FIBER);
			case SILVER_SLOT -> stack.is(ModContent.SILVER_DUST.get());
			case FILL_INPUT_SLOT, FILL_OUTPUT_SLOT -> ItemFluidBridge.get().isFluidContainer(stack);
			default -> false;
		};
	}

	/** Hoppers and pipes may feed the two inputs and the fill slot; the machine fills the outputs. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot != FILL_OUTPUT_SLOT && canPlaceItem(slot, stack);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == FILL_OUTPUT_SLOT || slot == OUTPUT_SLOT;
	}

	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.galvanic_bath");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new GalvanicBathMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("FluidTankMb", fluidTank.amount);
		output.putString("FluidTankFluid", fluidKey(fluidTank.fluid));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		fluidTank.amount = Math.max(0L, Math.min(TANK_CAPACITY, input.getLongOr("FluidTankMb", 0L)));
		FluidHolder restored = holderFromKey(input.getStringOr("FluidTankFluid", ""));
		// Uphold the tank invariant in both directions: no fluid means no amount, and vice versa.
		fluidTank.fluid = fluidTank.amount > 0 ? restored : FluidHolder.EMPTY;
		if (fluidTank.fluid.isEmpty()) {
			fluidTank.amount = 0L;
		}
	}

	/** Registry key of {@code fluid} for persistence (e.g. {@code "minecraft:water"}), or {@code ""}. */
	private static String fluidKey(FluidHolder fluid) {
		return fluid.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(fluid.fluid()).toString();
	}

	private static FluidHolder holderFromKey(String key) {
		if (key == null || key.isEmpty()) {
			return FluidHolder.EMPTY;
		}
		Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return FluidHolder.EMPTY;
		}
		Fluid resolved = BuiltInRegistries.FLUID.getValue(id);
		return resolved == null ? FluidHolder.EMPTY : FluidHolder.of(resolved);
	}

	/**
	 * The {@link BuiltInRegistries#FLUID} registry id of {@code fluid} ({@link IdMap#DEFAULT} when the
	 * tank is empty), for the client to resolve the fluid type over sync channel 5. Ids above
	 * {@link Short#MAX_VALUE} report as empty rather than as their truncated low 16 bits, which the
	 * channel's short encoding would otherwise resolve to an unrelated fluid.
	 */
	private static int fluidRegistryId(FluidHolder fluid) {
		if (fluid.isEmpty()) {
			return FLUID_ID_NONE;
		}
		int id = BuiltInRegistries.FLUID.getId(fluid.fluid());
		return id > Short.MAX_VALUE ? FLUID_ID_NONE : id;
	}
}

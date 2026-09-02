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
import dev.alaindustrial.menu.PolymerizerMenu;
import dev.alaindustrial.recipe.FluidRecipeInput;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModTags;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV Polymerizer (MOD-019): the first machine whose recipe input is a <em>fluid</em>. It holds oil in an
 * internal tank, spends EU over {@link Config#polymerizerDuration} ticks, and converts one recipe volume
 * (1000 mB by default) into raw rubber — the entry point of the oil → raw rubber → rubber chain.
 *
 * <p><b>Two ways to feed it.</b> Adjacent transport (a {@link PumpBlockEntity}, another mod's pipe) inserts
 * straight into the tank through the neutral {@link FluidPort} this block exposes on every face; a player
 * without that infrastructure drops an oil bucket — or any other filled container, ours or a foreign
 * mod's — into the fill slot, and the emptied container lands in the slot below it. Both paths land in the
 * same tank, so the machine has no "bucket mode".
 *
 * <p><b>Recipes are data.</b> The tank's contents are matched against {@link PolymerizingRecipe}s
 * ({@code data/<ns>/recipe/polymerizing/*.json}) rather than a hardcoded oil → rubber conversion, so a
 * datapack can add a second product or retune the volume. The shipped recipe accepts the conventional
 * {@code c:oil} tag, and so does the tank's insert filter — another mod's oil works everywhere ours does.
 *
 * <p><b>The tank never gives fluid back</b> ({@code canExtract = false}, R-CON-08, same rule as the
 * geothermal generator's lava): oil that entered the machine is feedstock, not storage, so a neighbour
 * cannot siphon it out and the machine cannot be used as a fluid buffer.
 */
public class PolymerizerBlockEntity extends MachineBlockEntity implements Overclockable, FluidPortHost, MenuProvider {
	/** Filled-container input: an oil bucket (or capsule/foreign cell) placed here is emptied into the tank. */
	public static final int FILL_INPUT_SLOT = 0;
	/** The emptied container drops here after its fluid moved into the tank. */
	public static final int FILL_OUTPUT_SLOT = 1;
	/** Result slot: raw rubber (insert-only by the machine — see {@link #canPlaceItem}). */
	public static final int OUTPUT_SLOT = 2;
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes its container from this (MOD-439). */
	public static final int SLOT_COUNT = 3;

	/** Internal tank: 10 buckets, matching the pump and the geothermal generator. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/** Sentinel for an empty tank on the fluid-id sync channel — see {@link FluidTank#FLUID_ID_NONE}. */
	public static final int FLUID_ID_NONE = FluidTank.FLUID_ID_NONE;

	/**
	 * Oil-only tank, insertable from any side, never extractable (R-CON-08 — the machine consumes its own
	 * feedstock). The filter is the {@code c:oil} tag rather than our own fluid id, so it matches what the
	 * shipped recipe accepts: any mod's oil that carries the conventional tag can be pumped in here.
	 */
	public final FluidTank fluidTank = new FluidTank(TANK_CAPACITY,
			PolymerizerBlockEntity::isOil,
			fluid -> false,
			() -> {
				setChanged();
				// A neighbour that just filled the tank must restart an idle machine on the next tick
				// (R-29); without this the machine would wait out its idle-sleep window first.
				wake();
			});

	private final RecipeManager.CachedCheck<FluidRecipeInput, PolymerizingRecipe> recipeCheck =
			ModRecipes.POLYMERIZING.newCheck();

	/** The shared eight-step tick loop (MOD-557). */
	private final ProcessingCycle cycle = new ProcessingCycle(this);

	public PolymerizerBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0. Three
		// slots: fill-in, fill-out, result. Shares machineBuffer with the other LV processing machines —
		// its operation (400 EU) fits in the 800 EU buffer.
		super(ModContent.POLYMERIZER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.polymerizerDuration);
	}

	/**
	 * Whether {@code fluid} is oil the tank may hold: tagged {@code c:oil} <em>and</em> a source variant.
	 * The tag names the flowing variant too — the pump's world search needs it — but a tank never holds
	 * "flowing oil", and letting it would strand the machine: the tank is single-variant, so a partial
	 * amount of the flowing form could never be topped up by ordinary oil to reach the recipe volume.
	 */
	private static boolean isOil(FluidHolder fluid) {
		return !fluid.isEmpty() && fluid.fluid().defaultFluidState().is(ModTags.Fluids.C_OIL)
				&& PolymerizingRecipe.isSourceFluid(fluid.fluid());
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}


	/** Every face exposes the same tank — the machine has no per-face fluid restriction. */
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

		// 2) Resolve what the tank's contents make. A recipe only matches once the tank holds at least the
		//    recipe's volume, so a half-filled tank simply idles.
		PolymerizingRecipe recipe = level instanceof ServerLevel serverLevel ? resolveRecipe(serverLevel) : null;
		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Config.machineEuPerTick)
				: Config.polymerizerDuration;
		ProcessingCycle.Job job = cycle.job(Config.machineEuPerTick, baseDuration);

		ItemStack result = recipe != null ? recipe.resultStack() : ItemStack.EMPTY;
		boolean canWork = recipe != null && energy.getAmount() >= job.euPerTick()
				&& canOutput(OUTPUT_SLOT, result);

		// 3) The shared cycle (MOD-557) draws the lit state, reports the rate, drains, steps the bar,
		//    counts the operation and answers the sleep gate. "The tank ran dry (or its fluid no longer
		//    has a recipe)" is what restarts progress; on mere power loss or a full output the recipe is
		//    still there, so progress FREEZES and resumes (R-NRG-10) — the item-fed machines' contract.
		//    A container exchange keeps the machine awake even when it cannot work.
		return job.canWork(canWork)
				.jobIntact(recipe != null)
				.keepAwake(filled)
				.run(level, () -> {
					consume(recipe.amount());
					addOutput(OUTPUT_SLOT, result);
				});
	}

	/** The recipe the tank's current contents satisfy, or {@code null}. */
	private PolymerizingRecipe resolveRecipe(ServerLevel level) {
		FluidRecipeInput input = new FluidRecipeInput(fluidTank.fluid, fluidTank.amount);
		if (input.isEmpty()) {
			return null;
		}
		return recipeCheck.getRecipeFor(input, level).map(RecipeHolder::value).orElse(null);
	}

	/**
	 * Take {@code amount} mB out of the tank for a completed operation. This is the machine consuming its
	 * own feedstock, so it bypasses the tank's {@code canExtract} guard (which is false to stop neighbours
	 * pulling oil back out) by mutating the field directly — the same internal path the geothermal
	 * generator uses. A direct mutator owns the tank's invariant: clear the fluid when the amount hits 0.
	 */
	private void consume(int amount) {
		fluidTank.amount = Math.max(0L, fluidTank.amount - amount);
		if (fluidTank.amount == 0) {
			fluidTank.fluid = FluidHolder.EMPTY;
		}
	}

	/**
	 * Six-wide data — hides {@link MachineBlockEntity#DATA_COUNT} so {@code PolymerizerBlockEntity.DATA_COUNT}
	 * names this machine's width for both the bridge below and {@code PolymerizerMenu}'s client stub (MOD-235).
	 */
	public static final int DATA_COUNT = 6;

	/**
	 * Base channels 0..3 (energy/capacity/progress/maxProgress) plus the tank fill as a permille (4) and the
	 * held fluid's registry id (5). Both extra channels are derived, server-authoritative projections of the
	 * tank; nothing writes them back.
	 *
	 * <p><b>Every channel must fit a signed 16-bit short</b> — {@code ClientboundContainerSetDataPacket}
	 * writes each value with {@code writeShort}, so a larger value silently arrives truncated. That is why
	 * the tank level travels as a permille (0..1000) rather than raw mB (10000 would still fit, but the
	 * permille keeps the screen independent of the capacity), and why the fluid's colour is not sent at all:
	 * a packed ARGB is 32 bits. {@code PolymerizerScreen} derives texture, tint and name from channel 5.
	 */
	private final ContainerData polymerizerData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> fluidTank.amount <= 0 ? 0
						: Math.max(1, (int) Math.min(fluidTank.amount * 1000L / TANK_CAPACITY, 1000));
				case 5 -> fluidTank.fluidSyncId();
				default -> PolymerizerBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != 4 && index != 5) {
				PolymerizerBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return polymerizerData;
	}

	/**
	 * Only the fill slot takes an item, and only a fluid container. The output slots answer false: the
	 * result slot is machine-fill only (spec), and the emptied-container slot is filled by the exchange
	 * itself, not by hand.
	 *
	 * <p>Unlike the pump, the emptied-container slot does <b>not</b> have to answer true here: the pump
	 * needed that because its bridge call moves a container between two slots the loader's item-transfer
	 * API validates. It asks {@code canPlaceItem} for the destination — so this machine, which runs the
	 * same exchange, keeps the fill-output slot placeable too. Automation policy stays separate
	 * ({@link #canPlaceItemThroughFace}).
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return (slot == FILL_INPUT_SLOT || slot == FILL_OUTPUT_SLOT)
				&& ItemFluidBridge.get().isFluidContainer(stack);
	}

	/** Hoppers and pipes may feed only the fill slot; the machine fills the two output slots itself. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == FILL_INPUT_SLOT && canPlaceItem(slot, stack);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == FILL_OUTPUT_SLOT || slot == OUTPUT_SLOT;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.polymerizer");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new PolymerizerMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		// The tank writes itself (MOD-556) under the keys it has always used. It persists which oil it
		// holds, not just how much: the tag accepts foreign oils, so the variant is not implicit.
		fluidTank.save(output, "FluidTank");
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// Clamped to the tank's own capacity (TANK_CAPACITY, the value it was built with), invariant
		// upheld both ways, and a save whose fluid id no longer resolves (its mod was removed) drops the
		// contents rather than keeping a phantom amount the machine could never consume.
		fluidTank.load(input, "FluidTank");
	}
}

package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.DistillationColumnBlock;
import dev.alaindustrial.block.DistillationColumnSegmentBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.item.fluid.DistillationColumnContents;
import dev.alaindustrial.item.fluid.FluidTankContents;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.menu.DistillationColumnMenu;
import dev.alaindustrial.recipe.FluidOutputRecipe;
import dev.alaindustrial.recipe.FluidRecipeInput;
import dev.alaindustrial.recipe.FluidStackTemplate;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModTags;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.IdMap;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Distillation Column's master block entity (MOD-251) — the "brain" living in the tower's
 * <b>bottom</b> segment. It owns all three tanks, the energy buffer, the warm-up state and the
 * recipe progress; the middle and top segments are thin proxies
 * ({@link DistillationColumnSegmentBlockEntity}) that forward their fluid ports here.
 *
 * <p><b>Port layout is fixed by the physics of distillation</b> (design session 2026-08-09): the
 * light fraction (diesel) condenses at the <b>top</b> segment, crude oil is fed into the
 * <b>middle</b>, and the heavy residue (fuel oil) settles at the <b>bottom</b>, which also accepts
 * EU on every face. Nothing is configurable — every column reads the same, which is exactly what
 * makes a three-pump automation hookup repeatable.
 *
 * <p><b>Warm-up (MOD-251).</b> A cold column heats for {@link Config#distillationColumnWarmupTicks}
 * ticks (drawing the ordinary machine rate) before the first run; losing power or running out of
 * oil cools it at half that rate, while a merely blocked output holds the heat (the column is still
 * powered and fed — the mod-wide "freeze, don't waste" convention). Heat persists across reloads
 * but deliberately not across break/re-place: a moved column starts cold.
 *
 * <p><b>Outputs are positional.</b> The recipe's first fluid result goes to the top (diesel) tank,
 * the second to the bottom (fuel-oil) tank — deterministic for datapacks, and the reason the two
 * output tanks carry no fluid filter of their own.
 */
public class DistillationColumnBlockEntity extends MachineBlockEntity implements FluidPortHost, MenuProvider {
	/** Filled oil container in — emptied into the middle (oil) tank. */
	public static final int OIL_FILL_INPUT_SLOT = 0;
	/** The emptied oil container drops here. */
	public static final int OIL_FILL_OUTPUT_SLOT = 1;
	/** Empty container in — filled from the diesel (top) tank. */
	public static final int DIESEL_DRAIN_INPUT_SLOT = 2;
	/** The filled diesel container drops here. */
	public static final int DIESEL_DRAIN_OUTPUT_SLOT = 3;
	/** Empty container in — filled from the fuel-oil (bottom) tank. */
	public static final int FUEL_OIL_DRAIN_INPUT_SLOT = 4;
	/** The filled fuel-oil container drops here. */
	public static final int FUEL_OIL_DRAIN_OUTPUT_SLOT = 5;

	/** Each of the three tanks: 10 buckets — the corpus convention (pump/polymerizer/geothermal). */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/** Sentinel for an empty tank on a fluid-id sync channel — the vanilla {@code IdMap} default. */
	public static final int FLUID_ID_NONE = IdMap.DEFAULT;

	/**
	 * Extra diesel a Rectification Section condenses per run (round 2): losses 10 % → 5 %, and the
	 * whole gain lands in the light fraction — better trays recover exactly what used to boil away.
	 */
	public static final int SECTION_DIESEL_BONUS_MB = 50;

	/** Fouling scale: 100 = coked up, the column stops until the player cleans it with a wrench. */
	public static final int FOULING_MAX = 100;
	/** Fouling added per completed run: 2 ⇒ fifty runs (a full oil tank ×5) between cleanings. */
	public static final int FOULING_PER_RUN = 2;

	/**
	 * Middle-segment tank: feedstock in, insertable from outside, never extractable — feedstock,
	 * not storage (R-CON-08). Round 2: the filter takes {@code c:oil} <b>or</b> {@code c:fuel_oil}
	 * — the column also re-runs its own heavy residue (cracking, fuel oil → diesel).
	 */
	public final FluidTank oilTank = new FluidTank(TANK_CAPACITY,
			DistillationColumnBlockEntity::isFeedstock,
			fluid -> false,
			() -> {
				setChanged();
				wake();
			});

	/**
	 * Top-segment tank: diesel out. External insertion is refused (the machine is the only producer);
	 * neighbours may pull freely, which is how a pump on the top segment drains it.
	 */
	public final FluidTank dieselTank = new FluidTank(TANK_CAPACITY,
			fluid -> false,
			fluid -> true,
			() -> {
				setChanged();
				wake();
			});

	/** Bottom-segment tank: fuel oil out. Same contract as {@link #dieselTank}. */
	public final FluidTank fuelOilTank = new FluidTank(TANK_CAPACITY,
			fluid -> false,
			fluid -> true,
			() -> {
				setChanged();
				wake();
			});

	/** Warm-up progress in ticks, 0..{@link Config#distillationColumnWarmupTicks}. */
	private int heat;
	/** Parity counter for the half-rate cooling (heat drops one tick every two idle ticks). */
	private int coolParity;
	/** Coke buildup 0..{@link #FOULING_MAX}; at max the column stops until wrench-cleaned. */
	private int fouling;
	/** Server-tick cache of "a Rectification Section stands three blocks up" for the GUI channel. */
	private boolean sectionPresent;
	/** Current idle diagnosis for the GUI status line, synced as an ordinal. */
	private DistillationColumnStatus status = DistillationColumnStatus.NO_OIL;

	private final RecipeManager.CachedCheck<FluidRecipeInput, FluidOutputRecipe> recipeCheck =
			ModRecipes.DISTILLING.newCheck();

	public DistillationColumnBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage, maxExtract = 0. Six slots: three container pairs
		// (oil in, diesel out, fuel oil out). One 400 EU run fits the shared 800 EU machineBuffer.
		super(ModContent.DISTILLATION_COLUMN_BE.get(), pos, state, EnergyTier.LV, 6,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.distillationColumnDuration);
	}

	/**
	 * The intake filter: a source fluid tagged {@code c:oil} (fractionation) or {@code c:fuel_oil}
	 * (cracking, round 2). The source-only rule is the polymerizer's: a tank holding a flowing
	 * variant could never be topped up to a recipe volume.
	 */
	private static boolean isFeedstock(FluidHolder fluid) {
		return !fluid.isEmpty()
				&& (fluid.fluid().is(ModTags.Fluids.C_OIL) || fluid.fluid().is(ModTags.Fluids.C_FUEL_OIL))
				&& PolymerizingRecipe.isSourceFluid(fluid.fluid());
	}

	/** Consumer on every face of the bottom segment — the tower has no FACING, so no inert front. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		if (!getBlockState().getValue(dev.alaindustrial.block.DistillationColumnBlock.FORMED)) {
			return EnergyRole.NONE; // a blank draws nothing (round 3)
		}
		return EnergyRole.IN;
	}

	/**
	 * The bottom segment's own fluid face: the fuel-oil (residue) tank. A {@code null} side — Jade,
	 * WTHIT, a foreign pipe probing without direction — answers the <b>oil</b> tank, the column's
	 * "identity" fluid (the task's explicit null contract; the first face-aware host must survive it).
	 */
	@Override
	public FluidPort fluidPort(Direction side) {
		if (!getBlockState().getValue(dev.alaindustrial.block.DistillationColumnBlock.FORMED)) {
			return null; // a blank exposes nothing — which also guarantees it forms empty (round 3)
		}
		return side == null ? oilTank : fuelOilTank;
	}

	/** The middle segment's port: crude oil in. */
	public FluidPort oilPort() {
		return oilTank;
	}

	/** The top segment's port: diesel out. */
	public FluidPort dieselPort() {
		return dieselTank;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Round 3: a lone segment blank is inert — no ports, no menu, no ticking work.
		if (!state.getValue(dev.alaindustrial.block.DistillationColumnBlock.FORMED)) {
			updateLit(false);
			return IDLE_SLEEP_TICKS;
		}
		// 1) Manual container handling, no EU cost: oil in, diesel out, fuel oil out.
		boolean bucketWork = ItemFluidBridge.get().drainSlotIntoTank(this,
				OIL_FILL_INPUT_SLOT, OIL_FILL_OUTPUT_SLOT, oilTank, FluidAmounts.BUCKET) > 0;
		bucketWork |= ItemFluidBridge.get().fillSlotFromTank(this,
				DIESEL_DRAIN_INPUT_SLOT, DIESEL_DRAIN_OUTPUT_SLOT, dieselTank, FluidAmounts.BUCKET) > 0;
		bucketWork |= ItemFluidBridge.get().fillSlotFromTank(this,
				FUEL_OIL_DRAIN_INPUT_SLOT, FUEL_OIL_DRAIN_OUTPUT_SLOT, fuelOilTank, FluidAmounts.BUCKET) > 0;

		// 2) Resolve the recipe and this run's duration (recipe energy is authoritative, Config is
		//    the fallback — the polymerizer's exact convention).
		FluidOutputRecipe recipe = level instanceof ServerLevel serverLevel ? resolveRecipe(serverLevel) : null;
		int euPerTick = Config.machineEuPerTickEffective();
		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Config.machineEuPerTick)
				: Config.distillationColumnDuration;
		this.maxProgress = Config.scaledDuration(baseDuration);

		int warmup = Math.max(1, Config.distillationColumnWarmupTicks);
		heat = Math.min(heat, warmup); // a lowered config value must not leave phantom heat

		// Round 2: the Rectification Section three blocks up (cached for the GUI channel).
		sectionPresent = level.getBlockState(pos.above(3)).getBlock()
				instanceof dev.alaindustrial.block.RectificationSectionBlock;
		boolean fouled = fouling >= FOULING_MAX;

		boolean hasEnergy = energy.amount >= euPerTick;
		boolean dieselFits = recipe != null && resultFits(recipe, 0, dieselTank, sectionBonus());
		boolean fuelOilFits = recipe != null && resultFits(recipe, 1, fuelOilTank, 0);
		boolean canRun = recipe != null && hasEnergy && dieselFits && fuelOilFits && !fouled;

		if (canRun) {
			energy.amount -= euPerTick;
			if (heat < warmup) {
				// Warming: EU burns, no oil is consumed, no progress accrues. One cold start costs
				// roughly one distillation's worth of EU — the deliberate price of intermittent power.
				heat++;
				status = DistillationColumnStatus.WARMING;
			} else {
				progress++;
				status = DistillationColumnStatus.WORKING;
				if (progress >= maxProgress) {
					progress = 0;
					consumeOil(recipe.amount());
					deliver(recipe);
					fouling = Math.min(FOULING_MAX, fouling + FOULING_PER_RUN);
					creditUsefulWork(level, (long) euPerTick * maxProgress);
				}
			}
			setChanged();
		} else {
			// A coked-up column outranks every other diagnosis: nothing runs until it is cleaned,
			// however full the buffers and tanks are.
			status = fouled ? DistillationColumnStatus.FOULED
					: !hasEnergy ? DistillationColumnStatus.NO_ENERGY
					: recipe == null ? DistillationColumnStatus.NO_OIL
					: !dieselFits ? DistillationColumnStatus.DIESEL_FULL
					: DistillationColumnStatus.FUEL_OIL_FULL;
			if (recipe == null && progress != 0) {
				// Tank ran dry: restart from zero. A full output or missing power keeps the recipe
				// matched, so progress freezes and resumes — the mod-wide R-NRG-10 contract.
				progress = 0;
				setChanged();
			}
			// Cooling only without power or without oil. A blocked output holds the heat: the column
			// is powered and fed, merely waiting for the player to drain a tank (freeze, don't waste).
			if ((!hasEnergy || recipe == null) && heat > 0 && ++coolParity >= 2) {
				coolParity = 0;
				heat--;
				setChanged();
			}
		}

		boolean hot = heat >= warmup;
		updateLit(canRun);
		syncSegmentLit(level, pos, canRun, canRun && hot);

		// Stay awake while running, exchanging containers, or holding any heat (cooling must tick);
		// only a cold idle column takes the standard nap.
		return canRun || bucketWork || heat > 0 ? 0 : IDLE_SLEEP_TICKS;
	}

	/** The distilling recipe the oil tank's contents satisfy, or {@code null}. */
	private FluidOutputRecipe resolveRecipe(ServerLevel level) {
		FluidRecipeInput input = new FluidRecipeInput(oilTank.fluid, oilTank.amount);
		if (input.isEmpty()) {
			return null;
		}
		return recipeCheck.getRecipeFor(input, level).map(RecipeHolder::value).orElse(null);
	}

	/** The extra diesel per run the Rectification Section grants (0 without one). */
	private int sectionBonus() {
		return sectionPresent ? SECTION_DIESEL_BONUS_MB : 0;
	}

	/**
	 * Whether result #{@code index} (positional: 0 → top/diesel tank, 1 → bottom/fuel-oil tank),
	 * plus any section bonus on the light fraction, has room in {@code tank}. A recipe with a
	 * single result trivially "fits" the absent slot.
	 */
	private static boolean resultFits(FluidOutputRecipe recipe, int index, FluidTank tank, int bonus) {
		if (index >= recipe.fluidResults().size()) {
			return true;
		}
		FluidStackTemplate result = recipe.fluidResults().get(index);
		FluidHolder produced = FluidHolder.of(result.fluid().value());
		boolean sameFluid = tank.fluid.isEmpty() || tank.fluid.equals(produced);
		return sameFluid && tank.amount + result.amount() + bonus <= tank.capacity;
	}

	/** Machine-internal oil draw for a completed run — bypasses {@code canExtract}, like the polymerizer. */
	private void consumeOil(int amount) {
		oilTank.amount = Math.max(0L, oilTank.amount - amount);
		if (oilTank.amount == 0) {
			oilTank.fluid = FluidHolder.EMPTY;
		}
	}

	/** Write a completed run's fractions into the output tanks (guarded by {@link #resultFits}). */
	private void deliver(FluidOutputRecipe recipe) {
		for (int i = 0; i < recipe.fluidResults().size(); i++) {
			FluidStackTemplate result = recipe.fluidResults().get(i);
			FluidTank tank = i == 0 ? dieselTank : fuelOilTank;
			tank.fluid = FluidHolder.of(result.fluid().value());
			tank.amount += result.amount() + (i == 0 ? sectionBonus() : 0);
		}
	}

	/**
	 * Wrench-clean (round 2): scrape the coke out. Returns how many coal items the cleaning yields
	 * (1..3, scaling with how clogged the column was), or 0 when there is nothing to clean.
	 */
	public int cleanFouling() {
		if (fouling <= 0) {
			return 0;
		}
		int coal = 1 + fouling * 2 / FOULING_MAX;
		fouling = 0;
		setChanged();
		wake();
		return coal;
	}

	/** Fouling 0..{@link #FOULING_MAX} for the GUI gauge and the wrench interaction. */
	public int getFouling() {
		return fouling;
	}

	/**
	 * Mirror the working state onto the proxy segments' {@code lit}: the middle glows whenever the
	 * column draws power (warming included), the top only once it is hot and running — which is the
	 * same gate its steam plume uses, so "steam == actually distilling" holds from a distance.
	 */
	private void syncSegmentLit(Level level, BlockPos pos, boolean middleLit, boolean topLit) {
		syncLitAt(level, pos.above(), middleLit);
		syncLitAt(level, pos.above(2), topLit);
		// Round 2: the Rectification Section glows (and steams) with the top segment's gate.
		syncLitAt(level, pos.above(3), topLit);
	}

	private static void syncLitAt(Level level, BlockPos pos, boolean lit) {
		BlockState state = level.getBlockState(pos);
		boolean towerPart = state.getBlock() instanceof DistillationColumnSegmentBlock
				|| state.getBlock() instanceof dev.alaindustrial.block.RectificationSectionBlock;
		if (towerPart && state.hasProperty(BlockStateProperties.LIT)
				&& state.getValue(BlockStateProperties.LIT) != lit) {
			level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
		}
	}

	// --- GUI sync ---

	/**
	 * 14 channels: 4 base + 3 tank permilles + 3 fluid ids + status ordinal + heat permille +
	 * fouling (0..100) + section flag (round 2).
	 */
	public static final int DATA_COUNT = 14;

	/** Channel indices, shared with {@link DistillationColumnMenu}. */
	public static final int CH_OIL_PERMILLE = 4;
	public static final int CH_DIESEL_PERMILLE = 5;
	public static final int CH_FUEL_OIL_PERMILLE = 6;
	public static final int CH_OIL_FLUID_ID = 7;
	public static final int CH_DIESEL_FLUID_ID = 8;
	public static final int CH_FUEL_OIL_FLUID_ID = 9;
	public static final int CH_STATUS = 10;
	public static final int CH_HEAT_PERMILLE = 11;
	public static final int CH_FOULING = 12;
	public static final int CH_SECTION = 13;

	/**
	 * Every channel fits a signed 16-bit short ({@code ClientboundContainerSetDataPacket} writes
	 * {@code writeShort}): tank levels travel as permille, fluids as registry ids with the
	 * {@code > Short.MAX_VALUE → NONE} guard, heat as permille of the warm-up.
	 */
	private final ContainerData columnData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case CH_OIL_PERMILLE -> permille(oilTank);
				case CH_DIESEL_PERMILLE -> permille(dieselTank);
				case CH_FUEL_OIL_PERMILLE -> permille(fuelOilTank);
				case CH_OIL_FLUID_ID -> fluidRegistryId(oilTank.fluid);
				case CH_DIESEL_FLUID_ID -> fluidRegistryId(dieselTank.fluid);
				case CH_FUEL_OIL_FLUID_ID -> fluidRegistryId(fuelOilTank.fluid);
				case CH_STATUS -> status.ordinal();
				case CH_HEAT_PERMILLE -> heatPermille();
				case CH_FOULING -> fouling;
				case CH_SECTION -> sectionPresent ? 1 : 0;
				default -> DistillationColumnBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index < MachineBlockEntity.DATA_COUNT) {
				DistillationColumnBlockEntity.this.dataAccess.set(index, value);
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

	private int heatPermille() {
		int warmup = Math.max(1, Config.distillationColumnWarmupTicks);
		return heat <= 0 ? 0 : Math.max(1, Math.min(heat * 1000 / warmup, 1000));
	}

	@Override
	public ContainerData getDataAccess() {
		return columnData;
	}

	// --- slots ---

	/** Every pair slot takes only fluid containers; the bridge validates both slots of an exchange. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot < baseSlots && ItemFluidBridge.get().isFluidContainer(stack);
	}

	/** Automation feeds only the three IN slots; the machine fills the OUT slots itself. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return (slot == OIL_FILL_INPUT_SLOT || slot == DIESEL_DRAIN_INPUT_SLOT
				|| slot == FUEL_OIL_DRAIN_INPUT_SLOT) && canPlaceItem(slot, stack);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OIL_FILL_OUTPUT_SLOT || slot == DIESEL_DRAIN_OUTPUT_SLOT
				|| slot == FUEL_OIL_DRAIN_OUTPUT_SLOT;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.distillation_column");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new DistillationColumnMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		saveTank(output, "Oil", oilTank);
		saveTank(output, "Diesel", dieselTank);
		saveTank(output, "FuelOil", fuelOilTank);
		output.putInt("Heat", heat);
		output.putInt("Fouling", fouling);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		loadTank(input, "Oil", oilTank);
		loadTank(input, "Diesel", dieselTank);
		loadTank(input, "FuelOil", fuelOilTank);
		heat = Math.max(0, input.getIntOr("Heat", 0));
		fouling = Math.max(0, Math.min(FOULING_MAX, input.getIntOr("Fouling", 0)));
	}

	/**
	 * Persist a tank as amount + fluid registry key. The key matters for all three: the oil tank
	 * accepts foreign {@code c:oil} fluids, and the output tanks hold whatever a datapack recipe
	 * produces — never hardcode the fluid on load (the geothermal generator's exact save-corruption
	 * bug, flagged 🔴 in MOD-261).
	 */
	private static void saveTank(ValueOutput output, String prefix, FluidTank tank) {
		output.putLong(prefix + "Mb", tank.amount);
		output.putString(prefix + "Fluid", fluidKey(tank.fluid));
	}

	private static void loadTank(ValueInput input, String prefix, FluidTank tank) {
		tank.amount = Math.max(0L, Math.min(tank.capacity, input.getLongOr(prefix + "Mb", 0L)));
		FluidHolder restored = holderFromKey(input.getStringOr(prefix + "Fluid", ""));
		tank.fluid = tank.amount > 0 ? restored : FluidHolder.EMPTY;
		if (tank.fluid.isEmpty()) {
			tank.amount = 0L;
		}
	}

	// --- item-form contents (MOD-251: the tower drops with its fluids, the portable-tank pattern) ---

	@SuppressWarnings("deprecation")
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		Optional<FluidTankContents> oil = contentsOf(oilTank);
		Optional<FluidTankContents> diesel = contentsOf(dieselTank);
		Optional<FluidTankContents> fuelOil = contentsOf(fuelOilTank);
		if (oil.isPresent() || diesel.isPresent() || fuelOil.isPresent() || fouling > 0) {
			builder.set(ModDataComponents.DISTILLATION_COLUMN_CONTENTS.get(),
					new DistillationColumnContents(oil, diesel, fuelOil, fouling));
			// A filled tower must not stack (same rule as the portable tank); a pristine one keeps
			// the BlockItem default and stacks normally.
			builder.set(DataComponents.MAX_STACK_SIZE, 1);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		DistillationColumnContents contents =
				getter.get(ModDataComponents.DISTILLATION_COLUMN_CONTENTS.get());
		applyStored(oilTank, contents == null ? Optional.empty() : contents.oil());
		applyStored(dieselTank, contents == null ? Optional.empty() : contents.diesel());
		applyStored(fuelOilTank, contents == null ? Optional.empty() : contents.fuelOil());
		// Heat deliberately does NOT ride the item: a re-placed column starts cold ("move the
		// furnace — heat it again", design session 2026-08-09). Fouling DOES ride it — otherwise
		// break-and-place would be a free wrench cleaning (round 2).
		heat = 0;
		fouling = contents == null ? 0 : contents.fouling();
		setChanged();
	}

	private static Optional<FluidTankContents> contentsOf(FluidTank tank) {
		if (tank.fluid.isEmpty() || tank.amount <= 0) {
			return Optional.empty();
		}
		return Optional.of(new FluidTankContents(tank.fluid.fluid().builtInRegistryHolder(), tank.amount));
	}

	private static void applyStored(FluidTank tank, Optional<FluidTankContents> stored) {
		if (stored.isEmpty()) {
			tank.fluid = FluidHolder.EMPTY;
			tank.amount = 0L;
			return;
		}
		tank.fluid = FluidHolder.of(stored.get().fluid().value());
		tank.amount = Math.min(tank.capacity, stored.get().amount());
	}

	// --- shared key helpers (the polymerizer's exact pair) ---

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

	/** See {@link PolymerizerBlockEntity#fluidRegistryId} — the same short-channel guard. */
	private static int fluidRegistryId(FluidHolder fluid) {
		if (fluid.isEmpty()) {
			return FLUID_ID_NONE;
		}
		int id = BuiltInRegistries.FLUID.getId(fluid.fluid());
		return id > Short.MAX_VALUE ? FLUID_ID_NONE : id;
	}
}

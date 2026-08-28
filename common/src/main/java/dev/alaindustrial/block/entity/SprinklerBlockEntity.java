package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.MenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.core.IdMap;
import dev.alaindustrial.menu.SprinklerMenu;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.block.SprinklerBlock;
import dev.alaindustrial.core.crop.CropMaturity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Sprinkler (MOD-525): a spinning head that sprays nutrient solution over everything growing
 * around it. The tail of the organic chain — waste fermented into biofuel, biofuel cracked into
 * solution, solution sprayed here.
 *
 * <p><b>It draws no EU.</b> The greenhouse already has two speed-ups, water and power, and a block
 * that wanted a cable <em>and</em> a pipe would charge twice for one effect and blur the line
 * against the Garden Drone Station, which is the EU-paid farm block. So the energy buffer is zero
 * and every face reports {@link EnergyRole#NONE}: no cable connects, and the block is registered in
 * {@code BlockCapabilityRoster.NO_ENERGY_CAPABILITY} so it does not even advertise an empty store.
 * The base class is still {@link EnergyBlockEntity} because that is what carries the server ticker,
 * the idle-sleep gate and the client sync in this codebase.
 *
 * <p><b>Growth is applied through vanilla's {@link BonemealableBlock}</b> — the same three methods
 * the Garden Drone and the Trellis use — rather than by forcing random ticks. A random tick on a
 * foreign block is an arbitrary side effect (it melts ice and spreads fire), and "which block did I
 * just tick" is exactly the class of silent damage MOD-524 shipped.
 *
 * <p><b>Inside a sealed greenhouse it does nothing on its own.</b> Seedbeds have no block entity and
 * are not bonemealable, so the aura cannot reach them; instead the farm controller looks for this
 * block in its interior and treats it as a third growth axis, drawing solution through
 * {@link #drawForGrowth} per delivered crystal.
 */
public class SprinklerBlockEntity extends MachineBlockEntity implements FluidPortHost, MenuProvider {

	/** How far above and below its own level the spray reaches — a crop bed is rarely flat. */
	private static final int SCAN_Y_BELOW = 1;
	private static final int SCAN_Y_ABOVE = 1;

	/** A filled container placed here is emptied into the tank. */
	public static final int FILL_INPUT_SLOT = 0;
	/** The emptied container drops here. */
	public static final int FILL_OUTPUT_SLOT = 1;
	/** Machine-specific slot count; no upgrade panel is appended (see {@link #hasUpgradePanel()}). */
	public static final int SLOT_COUNT = 2;

	/** Sentinel for an empty tank on the fluid-id sync channel — the vanilla {@code IdMap} default. */
	public static final int FLUID_ID_NONE = IdMap.DEFAULT;

	/**
	 * Solution in, never out. A neighbour may fill the tank; nothing may siphon it back (R-CON-08) —
	 * what entered is feedstock. Only the source variant is accepted, for the single-variant-tank
	 * reason every other machine states: a partial amount of the flowing form could never be topped
	 * up to a spray.
	 */
	public final FluidTank tank = new FluidTank(Math.max(1, Config.sprinklerTankMb),
			SprinklerBlockEntity::isNutrientSolution,
			fluid -> false,
			() -> {
				setChanged();
				wake();
			});

	/** Positions in range that could ever take a spray; rebuilt on a timer, like the drone's. */
	private final List<BlockPos> zoneCache = new ArrayList<>();

	private int rescanCountdown;
	private int sprayCountdown;

	public SprinklerBlockEntity(BlockPos pos, BlockState state) {
		// Zero buffer, zero throughput: this block is not on the energy network at all. The base class
		// is MachineBlockEntity purely for its container, menu and sync scaffolding.
		super(ModContent.SPRINKLER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, 0L, 0L, 0L);
	}

	/**
	 * No upgrade panel. Overclockers and the like all trade energy for speed, and this machine has no
	 * energy to trade — four slots that could never do anything would be four slots a player has to
	 * work out are dead.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	private static boolean isNutrientSolution(FluidHolder fluid) {
		return !fluid.isEmpty() && fluid.fluid() == ModContent.NUTRIENT_SOLUTION.get();
	}

	/** Not an energy block: no face takes a cable. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.NONE;
	}

	/** One tank, reachable from every face — a sprinkler has no front. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return tank;
	}

	/** Solution held, in mB — read by the greenhouse controller and the chat readout. */
	public long solution() {
		return tank.amount;
	}

	/** Whether the tank could pay for one greenhouse growth event right now. */
	public boolean canServeGrowth() {
		return tank.amount >= solutionPerGrowth();
	}

	/**
	 * Charge one greenhouse growth event to this sprinkler, returning whether it was paid. Called by
	 * the crystal farm controller on delivery — solution buys crystals, not dice rolls, exactly as EU
	 * does. Bypasses the tank's {@code canExtract} guard by mutating the field: this is the machine
	 * spending its own feedstock, the same internal path the Polymerizer uses.
	 */
	public boolean drawForGrowth() {
		long price = solutionPerGrowth();
		if (tank.amount < price) {
			return false;
		}
		spend(price);
		return true;
	}

	private static long solutionPerGrowth() {
		return Math.max(1L, Config.crystalFarmSolutionPerGrowthMb);
	}

	private static long solutionPerSpray() {
		return Math.max(1L, Config.sprinklerSolutionPerActionMb);
	}

	private void spend(long amount) {
		tank.amount = Math.max(0L, tank.amount - amount);
		if (tank.amount == 0) {
			tank.fluid = FluidHolder.EMPTY;
		}
		setChanged();
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel server)) {
			return IDLE_SLEEP_TICKS;
		}

		// Manual container handling first, no cost: a filled bucket or capsule in the fill slot is
		// emptied into the tank and drops out below.
		boolean bucketWork = ItemFluidBridge.get().drainSlotIntoTank(this,
				FILL_INPUT_SLOT, FILL_OUTPUT_SLOT, tank, FluidAmounts.BUCKET) > 0;

		if (rescanCountdown > 0) {
			rescanCountdown--;
		} else {
			rebuildZoneCache(server, pos);
			rescanCountdown = Math.max(1, Config.sprinklerIntervalTicks);
		}

		boolean sprayed = false;
		if (sprayCountdown > 0) {
			sprayCountdown--;
		} else {
			sprayCountdown = Math.max(1, Config.sprinklerIntervalTicks);
			sprayed = trySpray(server);
		}

		// The head spins whenever there is solution to spray, not only on the tick it lands one:
		// a sprinkler that twitched once every five seconds would read as broken.
		updateSpraying(server, pos, state, tank.amount >= solutionPerSpray());
		return sprayed || bucketWork ? 0 : IDLE_SLEEP_TICKS;
	}

	/** Reflect "has something to spray" in the blockstate, which is what the renderer spins on. */
	private void updateSpraying(ServerLevel level, BlockPos pos, BlockState state, boolean spraying) {
		if (!state.hasProperty(SprinklerBlock.SPRAYING) || state.getValue(SprinklerBlock.SPRAYING) == spraying) {
			return;
		}
		level.setBlock(pos, state.setValue(SprinklerBlock.SPRAYING, spraying), Block.UPDATE_ALL);
	}

	/**
	 * One spray: pick a target from the cache and grow it through vanilla's bonemeal path. Returns
	 * whether solution was actually spent — an empty tank or a zone with nothing left to grow costs
	 * nothing, the same demand-driven contract the Garden Drone and the Electric Heater keep.
	 */
	private boolean trySpray(ServerLevel level) {
		long price = solutionPerSpray();
		if (tank.amount < price || zoneCache.isEmpty()) {
			return false;
		}
		RandomSource random = level.getRandom();
		// Walk the cache from a random offset rather than scanning it in order: a fixed order would
		// pour everything into the first crop in the corner and leave the rest of the plot behind.
		int size = zoneCache.size();
		int start = random.nextInt(size);
		for (int i = 0; i < size; i++) {
			BlockPos target = zoneCache.get((start + i) % size);
			if (!level.isLoaded(target)) {
				continue; // never force a synchronous chunk load from a periodic sweep (R-277)
			}
			BlockState state = level.getBlockState(target);
			if (!(state.getBlock() instanceof BonemealableBlock bonemealable)
					|| !bonemealable.isValidBonemealTarget(level, target, state)) {
				continue;
			}
			if (bonemealable.isBonemealSuccess(level, random, target, state)) {
				bonemealable.performBonemeal(level, random, target, state);
			}
			spend(price);
			return true;
		}
		return false;
	}

	/**
	 * Rebuild the list of positions worth spraying. Filtering here rather than walking the raw cube
	 * every attempt is the point of the cache — the zone is a few hundred positions and the crops in
	 * it are a few dozen.
	 */
	private void rebuildZoneCache(ServerLevel level, BlockPos origin) {
		zoneCache.clear();
		int radius = Math.max(1, Config.sprinklerRange);
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -SCAN_Y_BELOW; dy <= SCAN_Y_ABOVE; dy++) {
					probe.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (probe.equals(origin) || !level.isLoaded(probe)) {
						continue;
					}
					BlockState state = level.getBlockState(probe);
					// Anything vanilla would accept bonemeal on and that is not finished growing.
					if (state.getBlock() instanceof BonemealableBlock
							&& CropMaturity.isFertilizable(level, probe, state)) {
						zoneCache.add(probe.immutable());
					}
				}
			}
		}
	}

	/** Six-wide data: the base four (energy/capacity/progress, all inert here) plus the tank gauge. */
	public static final int DATA_COUNT = 6;

	public static final int CH_SOLUTION_PERMILLE = 4;
	public static final int CH_SOLUTION_FLUID_ID = 5;

	/**
	 * Channels 4 and 5 are derived, server-authoritative projections; nothing writes them back. The
	 * level travels as a permille because each channel is a signed short — raw mB would truncate.
	 */
	private final ContainerData sprinklerData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case CH_SOLUTION_PERMILLE -> tank.amount <= 0 ? 0
						: Math.max(1, (int) Math.min(tank.amount * 1000L / tank.capacity, 1000));
				case CH_SOLUTION_FLUID_ID -> fluidRegistryId(tank.fluid);
				default -> SprinklerBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index < MachineBlockEntity.DATA_COUNT) {
				SprinklerBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return sprinklerData;
	}

	/**
	 * Both fill slots must accept a container: the loader's item-transfer API asks this before letting
	 * the exchange put the emptied one in the output, and answering false made the bridge silently
	 * move nothing (the pump's MOD-107 bug).
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return (slot == FILL_INPUT_SLOT || slot == FILL_OUTPUT_SLOT)
				&& ItemFluidBridge.get().isFluidContainer(stack);
	}

	/** A hopper may feed the fill slot; the machine fills the output. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == FILL_INPUT_SLOT && canPlaceItem(slot, stack);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == FILL_OUTPUT_SLOT;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.sprinkler");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new SprinklerMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	private static int fluidRegistryId(FluidHolder fluid) {
		if (fluid.isEmpty()) {
			return FLUID_ID_NONE;
		}
		int id = BuiltInRegistries.FLUID.getId(fluid.fluid());
		return id > Short.MAX_VALUE ? FLUID_ID_NONE : id;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("SolutionMb", tank.amount);
		output.putString("SolutionFluid", fluidKey(tank.fluid));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		tank.amount = Math.max(0L, Math.min(tank.capacity, input.getLongOr("SolutionMb", 0L)));
		FluidHolder restored = holderFromKey(input.getStringOr("SolutionFluid", ""));
		// Read the fluid back from the key, never assume it — hardcoding the expected fluid on load is
		// the geothermal generator's save-corruption bug (flagged in MOD-261).
		tank.fluid = tank.amount > 0 ? restored : FluidHolder.EMPTY;
		if (tank.fluid.isEmpty()) {
			tank.amount = 0L;
		}
	}

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
}

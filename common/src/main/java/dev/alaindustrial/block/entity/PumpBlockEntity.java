package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.core.FluidLookup;
import dev.alaindustrial.core.FluidMover;
import dev.alaindustrial.core.FluidPort;
import dev.alaindustrial.core.FluidPortHost;
import dev.alaindustrial.core.FluidTank;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV electric pump: an EU consumer that draws fluid from the world and pushes it on. It is one of the
 * most energy-hungry machines in the mod ({@link Config#pumpEuPerBucket} EU per bucket moved).
 *
 * <p><b>Fluid intake is single-variant.</b> The internal {@link FluidTank} accepts any whitelisted
 * fluid (currently lava + water) but the tank is single-variant — once it holds lava it refuses water
 * (and vice-versa) until it empties. This is enforced by the tank core ({@link FluidTank#insert}), so
 * mixing is impossible by construction.
 *
 * <p><b>Acquisition is FACING-only.</b> The pump draws fluid only from the block directly in front of
 * it (its {@link HorizontalMachineBlock#FACING} face): either a world fluid source block (which it
 * <b>irrevocably drains</b> — the source block is replaced with air) or an adjacent extractable
 * {@link FluidPort} (e.g. another tank). Every other face accepts EU and may receive the pumped fluid.
 *
 * <p><b>Bucket feed and drain.</b> The pump GUI has four bucket slots in a 2×2 grid:
 * the top row fills the tank (a lava/water bucket is emptied in, the empty bucket drops out) and the
 * bottom row drains it (an empty bucket is filled from the tank, the full bucket drops out). Both are
 * free of EU cost — manual handling, not pumping.
 *
 * <p><b>GUI.</b> Four slots and a {@link PumpMenu} screen showing the tank level and energy buffer.
 * The fluid level/type are projected through a 7-wide {@link ContainerData} (permille + denominator +
 * fluid-type id) so the client can render the right colour without a custom packet.
 *
 * <p>Draws EU from the energy network like other machines: it exposes a neutral
 * {@link dev.alaindustrial.core.EnergyPort} with {@code maxInsert > 0}, so the
 * {@link dev.alaindustrial.core.EnergyNetwork} discovers it as a consumer automatically.
 *
 * <p><b>MOD-028 multiloader migration.</b> Lives in {@code common}: the loader-specific fluid APIs are
 * replaced by the neutral {@link FluidTank}/{@link FluidPort}/{@link FluidLookup}/
 * {@link EnergyTransactions}. Each loader supplies its own adapter
 * ({@code TankAsFluidStorage}/{@code TankAsResourceHandler}) — see {@link FluidPort} class doc.
 */
public class PumpBlockEntity extends MachineBlockEntity implements FluidPortHost, MenuProvider {
	/** Top row — fill the tank: a full lava/water bucket placed here is emptied into the tank. */
	public static final int FILL_INPUT_SLOT = 0;
	/** Top row — fill the tank: the empty bucket drops here after filling. */
	public static final int FILL_OUTPUT_SLOT = 1;
	/** Bottom row — drain the tank: an empty bucket placed here is filled from the tank. */
	public static final int DRAIN_INPUT_SLOT = 2;
	/** Bottom row — drain the tank: the filled bucket drops here after draining. */
	public static final int DRAIN_OUTPUT_SLOT = 3;

	/** Internal fluid tank: 10 buckets, extractable from any side (so neighbours may pull from it). */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/** Fluid-type ids synced on ContainerData channel 6 (0 empty, 1 lava, 2 water). */
	public static final int FLUID_NONE = 0, FLUID_LAVA = 1, FLUID_WATER = 2;

	public final FluidTank fluidTank = new FluidTank(TANK_CAPACITY,
			PumpBlockEntity::isPumpableFluid,
			fluid -> true,
			this::setChanged);

	/** Ticks remaining before the pump is allowed to run a BFS source scan again. */
	private int scanCooldown = 0;

	public PumpBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0.
		// Buffer (Config.pumpBuffer) must be >= pumpEuPerBucket so the pump can actually accumulate a
		// full bucket's cost — machineBuffer (800) is below the 1000 EU/bucket threshold and would leave
		// the pump permanently starved. Four bucket slots: fill-in/out + drain-in/out.
		super(ModContent.PUMP_BE.get(), pos, state, EnergyTier.LV, 4,
				Config.pumpBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	/** Whitelist of fluids the pump will accept (into the tank or from a bucket). */
	private static boolean isPumpableFluid(FluidHolder fluid) {
		return !fluid.isEmpty() && (fluid.is(Fluids.LAVA) || fluid.is(Fluids.WATER));
	}

	/** Stable id for a fluid for sync/tooltip; mirrors {@link #fluidIdToHolder}. */
	private static int fluidId(FluidHolder fluid) {
		if (fluid.is(Fluids.LAVA)) {
			return FLUID_LAVA;
		}
		if (fluid.is(Fluids.WATER)) {
			return FLUID_WATER;
		}
		return FLUID_NONE;
	}

	/** Inverse of {@link #fluidId}. */
	private static FluidHolder fluidIdToHolder(int id) {
		return switch (id) {
			case FLUID_LAVA -> FluidHolder.of(Fluids.LAVA);
			case FLUID_WATER -> FluidHolder.of(Fluids.WATER);
			default -> FluidHolder.EMPTY;
		};
	}

	/** EU consumer: every face accepts energy, none emits (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	/** Every face exposes the same single tank — the pump has no per-face fluid restriction. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return fluidTank;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		boolean worked = false;
		int euPerBucket = Math.max(1, Config.pumpEuPerBucket);

		// 1) Bucket handling (no EU cost — manual refill/drain, not pumping):
		//    top row empties a full bucket INTO the tank; bottom row fills an empty bucket FROM the tank.
		worked |= emptyBucketIntoTank();
		worked |= fillBucketFromTank();

		// Update BFS scan cooldown
		if (scanCooldown > 0) {
			scanCooldown--;
		}

		// 2) Acquire fluid from the FACING block if we have power and tank room.
		BlockPos acquiredFrom = null;
		if (energy.amount >= euPerBucket && fluidTank.amount + FluidAmounts.BUCKET <= TANK_CAPACITY) {
			if (scanCooldown <= 0) {
				acquiredFrom = acquireFluid(level, pos, state);
				if (acquiredFrom != null) {
					energy.amount -= euPerBucket;
					worked = true;
					scanCooldown = 20; // 1 second cooldown on success to balance speed and reduce BFS frequency
				} else {
					scanCooldown = 20; // 1 second cooldown on failure to avoid ticking BFS
				}
			}
		}

		// 3) Push tank fluid into adjacent insertable fluid ports (every face except the FACING intake, so
		//    we never push straight back into the source we just drained). Skip the neighbour we just
		//    pulled from (same-tick push-back guard, MOD-032 D8 fix).
		if (fluidTank.amount > 0) {
			worked |= pushFluid(level, pos, state, acquiredFrom);
		}

		// Project tank level/type onto progress so the screen can render it (solar-panel-style channel
		// projection is in pumpData below; progress/maxProgress keep the base contract meaningful too).
		this.progress = (int) Math.min(Integer.MAX_VALUE, fluidTank.amount);
		this.maxProgress = (int) Math.min(Integer.MAX_VALUE, TANK_CAPACITY);

		updateLit(worked);
		if (worked) {
			setChanged();
		}
		// The pump reacts to world fluid state (sources / adjacent tanks) that change without a
		// block-entity wake event, so it stays awake every tick rather than sleep (R-29).
		return 0;
	}

	/**
	 * Empty a full fluid bucket from {@link #FILL_INPUT_SLOT} into the tank; the empty bucket drops into
	 * {@link #FILL_OUTPUT_SLOT}. Mirrors the geothermal generator's fill. No EU cost (manual handling).
	 */
	private boolean emptyBucketIntoTank() {
		ItemStack in = items.get(FILL_INPUT_SLOT);
		if (in.isEmpty() || !in.is(Items.LAVA_BUCKET) && !in.is(Items.WATER_BUCKET)) {
			return false;
		}
		FluidHolder fluid = in.is(Items.LAVA_BUCKET) ? FluidHolder.of(Fluids.LAVA) : FluidHolder.of(Fluids.WATER);
		if (!canPlaceEmptyBucket(FILL_OUTPUT_SLOT)) {
			return false;
		}
		// Dry-run: would the tank accept a full bucket of this fluid? The tank's single-variant guard and
		// capacity both gate this, so we never consume a bucket we can't store.
		boolean[] accepted = {false};
		EnergyTransactions.get().runCommitting(txn -> {
			long inserted = fluidTank.insert(fluid, FluidAmounts.BUCKET, txn);
			accepted[0] = inserted >= FluidAmounts.BUCKET;
		});
		if (!accepted[0]) {
			return false;
		}
		in.shrink(1);
		placeItem(FILL_OUTPUT_SLOT, new ItemStack(Items.BUCKET));
		setChanged();
		return true;
	}

	/**
	 * Fill an empty bucket from the tank: take a bucket from {@link #DRAIN_INPUT_SLOT}, extract 1 bucket
	 * of the tank's current fluid, and drop the filled bucket into {@link #DRAIN_OUTPUT_SLOT}. No EU cost
	 * (manual handling). The tank is single-variant, so the filled bucket always matches its content.
	 */
	private boolean fillBucketFromTank() {
		ItemStack in = items.get(DRAIN_INPUT_SLOT);
		if (!in.is(Items.BUCKET) || in.getCount() < 1) {
			return false;
		}
		if (fluidTank.amount < FluidAmounts.BUCKET || fluidTank.fluid.isEmpty()) {
			return false;
		}
		ItemStack fullBucket = fullBucketItem(fluidTank.fluid);
		if (fullBucket == null) {
			return false;
		}
		if (!canPlaceItemInSlot(DRAIN_OUTPUT_SLOT, fullBucket)) {
			return false;
		}
		// Dry-run extract: would the tank give up a full bucket? extract requires an exact fluid match.
		boolean[] extracted = {false};
		EnergyTransactions.get().runCommitting(txn -> {
			long got = fluidTank.extract(fluidTank.fluid, FluidAmounts.BUCKET, txn);
			extracted[0] = got >= FluidAmounts.BUCKET;
		});
		if (!extracted[0]) {
			return false;
		}
		in.shrink(1);
		placeItem(DRAIN_OUTPUT_SLOT, fullBucket);
		setChanged();
		return true;
	}

	/** The vanilla filled bucket item matching the tank's current fluid, or {@code null} if unsupported. */
	private static ItemStack fullBucketItem(FluidHolder fluid) {
		if (fluid.is(Fluids.LAVA)) {
			return new ItemStack(Items.LAVA_BUCKET);
		}
		if (fluid.is(Fluids.WATER)) {
			return new ItemStack(Items.WATER_BUCKET);
		}
		return null;
	}

	/** True if an empty bucket (or nothing) is in {@code slot}, so an empty bucket can be added there. */
	private boolean canPlaceEmptyBucket(int slot) {
		ItemStack out = items.get(slot);
		return out.isEmpty() || (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize());
	}

	/** True if {@code stack} can be placed/merged into {@code slot} (empty slot or same item with room). */
	private boolean canPlaceItemInSlot(int slot, ItemStack stack) {
		ItemStack out = items.get(slot);
		if (out.isEmpty()) {
			return true;
		}
		return ItemStack.isSameItemSameComponents(out, stack) && out.getCount() + stack.getCount() <= out.getMaxStackSize();
	}

	/** Place {@code stack} into {@code slot}: start a new stack if empty, otherwise grow the existing one. */
	private void placeItem(int slot, ItemStack stack) {
		ItemStack out = items.get(slot);
		if (out.isEmpty()) {
			items.set(slot, stack);
		} else {
			out.grow(stack.getCount());
		}
	}

	/**
	 * Try to put one bucket of fluid into the tank, drawn from the world (searching connected flowing blocks
	 * via BFS for the closest source block) or from an adjacent extractable fluid port.
	 * Returns the {@link BlockPos} that supplied the fluid (for same-tick push exclusion), or {@code null}.
	 *
	 * @param level the level the pump is in
	 * @param pos   the pump block position
	 * @param state the pump block state
	 * @return the block position that supplied the fluid, or null
	 */
	private BlockPos acquireFluid(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(HorizontalMachineBlock.FACING);
		BlockPos np = pos.relative(facing);

		// 1) World fluid search. Start BFS from the block directly in front of the pump.
		FluidState startState = level.getFluidState(np);
		if (!startState.isEmpty()) {
			Fluid startFluid = startState.getType();
			Fluid sourceFluid = (startFluid instanceof FlowingFluid flowing) ? flowing.getSource() : startFluid;
			FluidHolder holder = FluidHolder.of(sourceFluid);
			if (isPumpableFluid(holder)) {
				// Dry-run type match check (prevent BFS if tank already holds a different fluid)
				if (fluidTank.fluid.isEmpty() || fluidTank.fluid.equals(holder)) {
					// Perform BFS to find the closest source block
					BlockPos sourcePos = findClosestSource(level, pos, np, sourceFluid);
					if (sourcePos != null) {
						boolean[] acquired = {false};
						EnergyTransactions.get().runCommitting(txn -> {
							long inserted = fluidTank.insert(holder, FluidAmounts.BUCKET, txn);
							acquired[0] = inserted >= FluidAmounts.BUCKET;
						});
						if (acquired[0]) {
							// Irrevocably drain the source block
							level.setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
							return sourcePos;
						}
					}
				}
			}
		}

		// 2) Adjacent extractable fluid port in front of the pump. Try the tank's current variant first
		//    (keep pumping the same fluid), then each whitelisted fluid so a donor of a different kind can
		//    still prime an empty tank.
		FluidPort src = FluidLookup.get().find(level, np, facing.getOpposite());
		if (src != null && src.supportsExtraction()) {
			FluidHolder current = fluidTank.fluid;
			FluidHolder[] candidates = current.isEmpty()
					? new FluidHolder[] {FluidHolder.of(Fluids.LAVA), FluidHolder.of(Fluids.WATER)}
					: new FluidHolder[] {current};
			for (FluidHolder candidate : candidates) {
				long[] moved = {0};
				EnergyTransactions.get().runCommitting(
						txn -> moved[0] = FluidMover.move(src, fluidTank, candidate, FluidAmounts.BUCKET, txn));
				if (moved[0] > 0) {
					return np;
				}
			}
		}
		return null;
	}

	/**
	 * Finds the closest source block of targetSourceFluid connected to startPos using Breadth-First Search (BFS).
	 * Restricts search to loaded chunks, Manhattan distance <= 32, and visits at most 512 blocks.
	 *
	 * @param level              the level to search in
	 * @param pumpPos            the position of the pump block (used for distance constraint)
	 * @param startPos           the initial block position in front of the pump to start searching from
	 * @param targetSourceFluid the source representation of the fluid we are looking for
	 * @return the BlockPos of the closest source block, or null if none found
	 */
	private static BlockPos findClosestSource(Level level, BlockPos pumpPos, BlockPos startPos, Fluid targetSourceFluid) {
		java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
		java.util.Set<BlockPos> visited = new java.util.HashSet<>();

		queue.add(startPos);
		visited.add(startPos);

		try {
			while (!queue.isEmpty()) {
				BlockPos current = queue.poll();
				FluidState currentState = level.getFluidState(current);
				if (currentState.isEmpty()) {
					continue;
				}

				Fluid currentFluid = currentState.getType();
				if (isSameFluid(currentFluid, targetSourceFluid)) {
					if (currentState.isSource()) {
						return current;
					}

					for (Direction dir : Direction.values()) {
						BlockPos next = current.relative(dir);
						if (!visited.contains(next)) {
							// Distance check: Manhattan distance to pumpPos must be <= 32 blocks
							if (next.distManhattan(pumpPos) <= 32) {
								// Limit max visited blocks to 512 to avoid lag spikes
								if (visited.size() < 512) {
									visited.add(next);
									queue.add(next);
								}
							}
						}
					}
				}
			}
		} catch (Exception exception) {
			// Catch any exceptions to prevent server crashes, returning null
			return null;
		}
		return null;
	}

	/**
	 * Helper to check if two fluids share the same source type.
	 *
	 * @param a first fluid to compare
	 * @param b second fluid to compare
	 * @return true if both fluids share the same source type
	 */
	private static boolean isSameFluid(Fluid a, Fluid b) {
		Fluid aSource = (a instanceof FlowingFluid flowing) ? flowing.getSource() : a;
		Fluid bSource = (b instanceof FlowingFluid flowing) ? flowing.getSource() : b;
		return aSource == bSource;
	}

	/** Push tank fluid into any adjacent insertable fluid port (except the FACING face and {@code excludePos}). */
	private boolean pushFluid(Level level, BlockPos pos, BlockState state, BlockPos excludePos) {
		boolean moved = false;
		Direction facing = state.getValue(HorizontalMachineBlock.FACING);
		FluidHolder current = fluidTank.fluid;
		if (current.isEmpty()) {
			return false;
		}
		for (Direction dir : Direction.values()) {
			if (fluidTank.amount <= 0) {
				break;
			}
			if (dir == facing) {
				continue; // never push back through the intake face
			}
			BlockPos np = pos.relative(dir);
			if (np.equals(excludePos)) {
				continue; // MOD-032: don't push straight back into the neighbour we just pulled from
			}
			FluidPort dst = FluidLookup.get().find(level, np, dir.getOpposite());
			if (dst == null || !dst.supportsInsertion()) {
				continue;
			}
			long amountToPush = fluidTank.amount;
			long[] pushed = {0};
			EnergyTransactions.get().runCommitting(
					txn -> pushed[0] = FluidMover.move(fluidTank, dst, current, amountToPush, txn));
			if (pushed[0] > 0) {
				moved = true;
			}
		}
		return moved;
	}

	/**
	 * Seven-wide sync bridge: base channels 0..3 (energy/capacity/progress/maxProgress) plus tank permille
	 * (4), permille denominator 1000 (5), and fluid-type id {@link #fluidId} (6). Channels 4..6 are
	 * derived, server-authoritative projections of the tank — nothing writes them back.
	 */
	private final ContainerData pumpData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> fluidTank.amount <= 0 ? 0
						: Math.max(1, (int) Math.min(fluidTank.amount * 1000L / TANK_CAPACITY, 1000));
				case 5 -> 1000;
				case 6 -> fluidId(fluidTank.fluid);
				default -> PumpBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != 4 && index != 5 && index != 6) {
				PumpBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return 7;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return pumpData;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// Top-row input accepts full fluid buckets; bottom-row input accepts empty buckets.
		if (slot == FILL_INPUT_SLOT) {
			return stack.is(Items.LAVA_BUCKET) || stack.is(Items.WATER_BUCKET);
		}
		return slot == DRAIN_INPUT_SLOT && stack.is(Items.BUCKET);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		// Hoppers/automation may pull the buckets that drop out, never the inputs.
		return slot == FILL_OUTPUT_SLOT || slot == DRAIN_OUTPUT_SLOT;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.pump");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new PumpMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("FluidTankMb", fluidTank.amount);
		// Persist which fluid the tank holds so a reload restores the right variant (lava/water), not just
		// the amount — essential now that the tank is no longer lava-only.
		output.putString("FluidTankFluid", fluidKey(fluidTank.fluid));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// MOD-028: prefer the new mB-valued key; fall back to the legacy Fabric v0.1.0 droplet-valued
		// "FluidTank" key, converting ÷81 (81000 droplets/bucket ÷ 81 = 1000 mB/bucket, exact).
		long amount = input.getLongOr("FluidTankMb", 0L);
		if (amount == 0L) {
			amount = input.getLong("FluidTank")
					.map(dr -> dr / FluidAmounts.FABRIC_DROPLETS_PER_MB).orElse(0L);
		}
		fluidTank.amount = Math.max(0L, Math.min(TANK_CAPACITY, amount));
		// Restore the fluid variant from the persisted key. Legacy lava-only saves have no key and a
		// non-zero amount → treat as lava (the only fluid a legacy pump could ever hold).
		String key = input.getStringOr("FluidTankFluid", "");
		FluidHolder restored = holderFromKey(key);
		fluidTank.fluid = fluidTank.amount > 0 && restored.isEmpty()
				? FluidHolder.of(Fluids.LAVA) : restored;
		if (fluidTank.amount == 0) {
			fluidTank.fluid = FluidHolder.EMPTY;
		}
	}

	private static String fluidKey(FluidHolder fluid) {
		if (fluid.is(Fluids.LAVA)) {
			return "lava";
		}
		if (fluid.is(Fluids.WATER)) {
			return "water";
		}
		return "";
	}

	private static FluidHolder holderFromKey(String key) {
		return switch (key) {
			case "lava" -> FluidHolder.of(Fluids.LAVA);
			case "water" -> FluidHolder.of(Fluids.WATER);
			default -> FluidHolder.EMPTY;
		};
	}
}

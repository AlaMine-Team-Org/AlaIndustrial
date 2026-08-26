package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.structure.FuelRodMath;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * State of one reactor column (MOD-468): which rods are racked and how much charge is left in each.
 *
 * <p><b>It does not tick.</b> The reactor is one machine, not a floor full of them: the controller
 * drives every assembly in its room from a single tick, so a room with thirty racks still costs one
 * block entity's worth of work per tick rather than thirty. This class is the state and the rules for
 * changing it; the schedule lives with the controller.
 *
 * <p><b>Every fuelled rod in the rack burns at once, evenly.</b> A round of wear costs the rack one
 * point off each fuelled rod, so the four bars fall together the way four rods in one neutron flux
 * should. Burning them one at a time meant a room of any size spent exactly one rod per nominal
 * lifetime, which handed the whole neighbour bonus to the player for free; the fill level still drops
 * visibly, but as spent rods turning into casings rather than as the rack emptying.
 */
public class FuelRodAssemblyBlockEntity extends BlockEntity implements FluidPortHost {

	/**
	 * What is actually racked, slot by slot: a fuelled rod carrying its own wear, a spent casing, or
	 * nothing.
	 *
	 * <p><b>Stacks rather than a count.</b> The rack used to hold a number and a single "EU left in the
	 * one currently burning", which had two consequences a player met immediately: a rod pulled out
	 * mid-burn was destroyed, because there was nowhere to write down how much of it was left; and a
	 * spent rod vanished, so refuelling meant crafting casings from raw materials over and over. Both
	 * problems are the same missing thing — per-rod state — and both go away once the rack holds the
	 * items themselves.
	 */
	private final NonNullList<ItemStack> rack =
			NonNullList.withSize(FuelRodAssemblyBlock.MAX_RODS, ItemStack.EMPTY);

	/**
	 * Draw that has not yet added up to a whole point of wear.
	 *
	 * <p>A tick of a small reactor is a few dozen EU against a rod worth hundreds of thousands, so
	 * converting each tick's draw straight into damage would round it to zero and the rod would never
	 * wear out at all. See {@link FuelRodMath}.
	 */
	private long carriedEu;

	public FuelRodAssemblyBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.FUEL_ROD_ASSEMBLY_BE.get(), pos, state);
	}

	/** Fuelled rods — what the reactor counts and what the model draws. Spent casings are not fuel. */
	public int getRods() {
		int fuelled = 0;
		for (ItemStack stack : rack) {
			if (isFuelled(stack)) {
				fuelled++;
			}
		}
		return fuelled;
	}

	public boolean isEmpty() {
		for (ItemStack stack : rack) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Whether this column still has fuel the reactor can burn right now. */
	public boolean hasFuel() {
		return getRods() > 0;
	}

	/**
	 * Racks one rod, fuelled or spent, keeping whatever wear it already carries.
	 *
	 * @return {@code false} if the rack is full, so the caller does not consume the item
	 */
	public boolean insertRod(ItemStack rod) {
		if (rod.isEmpty() || !(isFuelled(rod) || rod.is(ModContent.EMPTY_FUEL_ROD.get()))) {
			return false;
		}
		for (int i = 0; i < rack.size(); i++) {
			if (rack.get(i).isEmpty()) {
				rack.set(i, rod.copyWithCount(1));
				syncRods();
				return true;
			}
		}
		return false;
	}

	/**
	 * Hands one rod back, <b>spent casings first</b>.
	 *
	 * <p>That order is the whole interaction: a player standing at a worked-out column wants the empty
	 * casings out so they can refill them, and does not want to first fish out the live rods they just
	 * put in. Wear travels with the rod, so a half-burnt one can be taken out and put back — the old
	 * rack destroyed it, which made "just check what's in there" an expensive mistake.
	 */
	public ItemStack removeRod() {
		int chosen = -1;
		for (int i = rack.size() - 1; i >= 0; i--) {
			ItemStack stack = rack.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (!isFuelled(stack)) {
				chosen = i;
				break;
			}
			if (chosen < 0) {
				chosen = i;
			}
		}
		if (chosen < 0) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = rack.get(chosen);
		rack.set(chosen, ItemStack.EMPTY);
		syncRods();
		return removed;
	}

	/** Everything racked, for the drop when the column is broken. Nothing is lost any more. */
	public java.util.List<ItemStack> contents() {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		for (ItemStack stack : rack) {
			if (!stack.isEmpty()) {
				out.add(stack.copy());
			}
		}
		return out;
	}

	/**
	 * Charges {@code eu} of this tick's output against the racked rods.
	 *
	 * <p>Called only when the reactor actually had somewhere to put the energy — a full buffer with no
	 * consumer costs no uranium, the same demand-driven discipline the charging station uses.
	 *
	 * <p><b>A rod is an amount of energy, not a duration.</b> Every rod is worth
	 * {@link ReactorCore#rodEnergy} EU wherever it stands, so the neighbour bonus buys power density
	 * rather than free fuel. Wear is applied to one rod at a time and cascades: a single tick of a large
	 * reactor can be worth more than a whole rod, and dropping that overshoot would make uranium cheaper
	 * the bigger the core.
	 *
	 * @return {@code true} if this column contributed to this tick
	 */
	public boolean burn(long eu) {
		long perRod = ReactorCore.rodEnergy(Config.reactorEuPerRod, Config.reactorRodBurnTicks);
		if (eu <= 0 || perRod <= 0 || !hasFuel()) {
			return false;
		}
		carriedEu += eu;
		boolean changed = false;
		// EVERY fuelled rod wears, together, one point at a time. They all sit in the same neutron flux
		// and they all count towards the room's output, so burning them one after another was simply
		// wrong: the player saw four rods working and one of them ageing. Waiting until there is a
		// point's worth FOR EACH rod before applying any keeps the arithmetic exact — the column still
		// spends precisely one rod's energy per rod, it just spends it out of all of them at once.
		while (true) {
			int fuelled = getRods();
			if (fuelled <= 0) {
				// Nothing left to wear down: draw beyond the last rod is dropped rather than banked, or a
				// column refuelled later would pay for energy it never produced.
				carriedEu = 0;
				break;
			}
			long threshold = FuelRodMath.euPerPoint(perRod) * fuelled;
			if (threshold <= 0 || carriedEu < threshold) {
				break;
			}
			carriedEu -= threshold;
			for (int i = 0; i < rack.size(); i++) {
				ItemStack rod = rack.get(i);
				if (!isFuelled(rod)) {
					continue;
				}
				rod.setDamageValue(rod.getDamageValue() + 1);
				if (rod.getDamageValue() >= rod.getMaxDamage()) {
					rack.set(i, new ItemStack(ModContent.EMPTY_FUEL_ROD.get()));
				}
			}
			changed = true;
		}
		if (changed) {
			syncRods();
		} else {
			setChanged();
		}
		return true;
	}


	private static boolean isFuelled(ItemStack stack) {
		return stack.is(ModContent.URANIUM_FUEL_ROD.get()) && stack.getDamageValue() < stack.getMaxDamage();
	}

	/**
	 * Cooling water. <b>Insert-only from the outside</b> — a pipe can fill this column and can never
	 * drain it. Reactor water is not a reservoir to borrow from: making it extractable would let a
	 * player park a pump on a hot core and pull the coolant back out, which is both free steam and a
	 * meltdown on demand.
	 *
	 * <p>Strictly this column's own water. An earlier version had the controller even the tanks out
	 * across the whole room, so one pipe filled every rack in it — convenient, and wrong: coolant
	 * appearing inside a column nothing was plumbed to reads as a bug however useful it is. Plumb the
	 * columns you want cooled.
	 */
	public final FluidTank waterTank = new FluidTank(Config.reactorColumnWaterCapacity,
			fluid -> fluid.is(Fluids.WATER), fluid -> false, this::onTankChanged);

	/**
	 * Steam, the other half of the loop. The mirror image of the water tank: the reactor fills it from
	 * the inside, the outside may only take it away. When it is full the column stops boiling and
	 * cooling stops with it — which is what makes the exhaust line load-bearing rather than decorative.
	 */
	public final FluidTank steamTank = new FluidTank(Config.reactorColumnSteamCapacity,
			fluid -> false, fluid -> true, this::onTankChanged);

	/**
	 * The coolant port a pipe actually talks to: it stands for the whole tower, not for this block.
	 *
	 * <p>Reporting one block's tank was the last thing keeping a two-high column from filling. Water
	 * settles to the bottom, so the bottom block reaches capacity first — and a pipe asking "is there
	 * room?" got "no" from a vessel that was half empty, and stopped. Publishing the tower's totals and
	 * inserting from the bottom up makes the answer match what the player is looking at.
	 *
	 * <p>Insertion is delegated to each block's own {@link FluidTank}, never done by hand, so every
	 * tank enrols itself in the caller's transaction exactly as a single-block port would. Rolling back
	 * a half-finished transfer across a tower therefore needs no code of its own.
	 */
	private final FluidPort stackWater = new StackPort(true);

	/** The exhaust side of the same idea: steam collects at the top, and the tower vents as one. */
	private final FluidPort stackSteam = new StackPort(false);

	/**
	 * How far a tower is followed. Far beyond the tallest room the scan allows — it exists so a
	 * corrupted world or a future taller room cannot turn a walk into a hang.
	 */
	private static final int MAX_RUN = 64;

	/**
	 * This block's unbroken vertical run of columns, lowest first.
	 *
	 * <p>Walked from the world rather than cached: columns are placed and mined constantly, and a
	 * cached run would have to be invalidated from six directions. The walk is a handful of block-entity
	 * lookups inside one chunk, and it happens once per fluid transfer rather than once per tick.
	 */
	private java.util.List<FuelRodAssemblyBlockEntity> run() {
		java.util.List<FuelRodAssemblyBlockEntity> run = new java.util.ArrayList<>();
		if (level == null) {
			run.add(this);
			return run;
		}
		BlockPos bottom = worldPosition;
		for (int i = 0; i < MAX_RUN; i++) {
			if (!(level.getBlockEntity(bottom.below()) instanceof FuelRodAssemblyBlockEntity)) {
				break;
			}
			bottom = bottom.below();
		}
		BlockPos cursor = bottom;
		for (int i = 0; i < MAX_RUN; i++) {
			if (!(level.getBlockEntity(cursor) instanceof FuelRodAssemblyBlockEntity column)) {
				break;
			}
			run.add(column);
			cursor = cursor.above();
		}
		if (run.isEmpty()) {
			run.add(this);
		}
		return run;
	}

	/** One end of the tower's plumbing: coolant in at the bottom, steam out at the top. */
	private final class StackPort implements FluidPort {

		private final boolean water;

		private StackPort(boolean water) {
			this.water = water;
		}

		private FluidTank tankOf(FuelRodAssemblyBlockEntity column) {
			return water ? column.waterTank : column.steamTank;
		}

		@Override
		public long insert(FluidHolder fluid, long maxAmount, dev.alaindustrial.core.energy.EnergyPort.Txn txn) {
			if (!water) {
				return 0;
			}
			long inserted = 0;
			// Bottom first: that is where the water ends up anyway, so filling in that order means the
			// tower never shows a floating body of liquid between two empty blocks mid-transfer.
			for (FuelRodAssemblyBlockEntity column : run()) {
				if (inserted >= maxAmount) {
					break;
				}
				inserted += tankOf(column).insert(fluid, maxAmount - inserted, txn);
			}
			return inserted;
		}

		@Override
		public long extract(FluidHolder fluid, long maxAmount, dev.alaindustrial.core.energy.EnergyPort.Txn txn) {
			if (water) {
				return 0;
			}
			long extracted = 0;
			java.util.List<FuelRodAssemblyBlockEntity> run = run();
			// Top first, for the same reason in reverse: steam is drawn off the top of the vessel.
			for (int i = run.size() - 1; i >= 0 && extracted < maxAmount; i--) {
				extracted += tankOf(run.get(i)).extract(fluid, maxAmount - extracted, txn);
			}
			return extracted;
		}

		@Override
		public FluidHolder fluid() {
			for (FuelRodAssemblyBlockEntity column : run()) {
				FluidTank tank = tankOf(column);
				if (tank.amount > 0) {
					return tank.fluid;
				}
			}
			return FluidHolder.EMPTY;
		}

		@Override
		public long getAmount() {
			long total = 0;
			for (FuelRodAssemblyBlockEntity column : run()) {
				total += tankOf(column).amount;
			}
			return total;
		}

		@Override
		public long getCapacity() {
			long total = 0;
			for (FuelRodAssemblyBlockEntity column : run()) {
				total += tankOf(column).capacity;
			}
			return total;
		}

		@Override
		public boolean supportsInsertion() {
			return water;
		}

		@Override
		public boolean supportsExtraction() {
			return !water;
		}
	}

	/**
	 * Water goes in anywhere but the top; steam comes out of the top alone.
	 *
	 * <p>That split is what keeps a single pipe unambiguous — one face, one fluid, one direction. It
	 * survives stacking because steam rises: the controller walks it up the stack one block a tick, so
	 * the top column of a run exhausts for all of them while each column still takes its own water
	 * through its own sides.
	 */
	@Override
	public FluidPort fluidPort(Direction side) {
		return side == Direction.UP ? stackSteam : stackWater;
	}

	/** Boils {@code water} mB into the same amount of steam. Returns what was actually boiled. */
	public long boil(long water) {
		long room = steamTank.capacity - steamTank.amount;
		long moved = Math.min(Math.min(water, waterTank.amount), room);
		if (moved <= 0) {
			return 0;
		}
		waterTank.amount -= moved;
		steamTank.fluid = FluidHolder.of(ModContent.STEAM.get());
		steamTank.amount += moved;
		if (waterTank.amount == 0) {
			waterTank.fluid = FluidHolder.EMPTY;
		}
		onTankChanged();
		return moved;
	}

	/**
	 * Sets one tank outright, used by the controller when it walks steam up a stack. Clamped to
	 * capacity so a transfer into a column of a different size cannot overfill it.
	 */
	public void setTank(boolean water, long amount) {
		FluidTank tank = water ? waterTank : steamTank;
		long clamped = Math.max(0, Math.min(tank.capacity, amount));
		if (tank.amount == clamped) {
			return;
		}
		tank.amount = clamped;
		tank.fluid = clamped == 0 ? FluidHolder.EMPTY
				: FluidHolder.of(water ? Fluids.WATER : ModContent.STEAM.get());
		onTankChanged();
	}

	private void onTankChanged() {
		setChanged();
		syncWaterLevel();
	}

	/**
	 * Mirrors the water tank into the blockstate as quarters, so the level is visible from outside.
	 * Anything above empty shows at least one quarter: a column with a swallow of water left must not
	 * look identical to a dry one, because those two states call for very different reactions.
	 */
	private void syncWaterLevel() {
		if (level == null || level.isClientSide()) {
			return;
		}
		long amount = waterTank.amount;
		int quarters = amount <= 0 ? 0
				: (int) Math.max(1, Math.min(FuelRodAssemblyBlock.WATER_LEVELS,
						amount * FuelRodAssemblyBlock.WATER_LEVELS / waterTank.capacity));
		BlockState state = getBlockState();
		if (state.hasProperty(FuelRodAssemblyBlock.WATER)
				&& state.getValue(FuelRodAssemblyBlock.WATER) != quarters) {
			level.setBlock(worldPosition, state.setValue(FuelRodAssemblyBlock.WATER, quarters), 3);
		}
	}

	/**
	 * Keeps the blockstate — and therefore the model — equal to the number of FUELLED rods.
	 *
	 * <p>Spent casings are deliberately invisible: a column that has worked itself out should look
	 * worked out from across the floor, and the casings still inside are something to collect, not
	 * something to mistake for fuel.
	 */
	private void syncRods() {
		setChanged();
		if (level == null || level.isClientSide()) {
			return;
		}
		int total = 0;
		for (ItemStack stack : rack) {
			if (!stack.isEmpty()) {
				total++;
			}
		}
		int fuelled = getRods();
		BlockState state = getBlockState();
		if (!state.hasProperty(FuelRodAssemblyBlock.RODS)
				|| !state.hasProperty(FuelRodAssemblyBlock.FUELLED)) {
			return;
		}
		if (state.getValue(FuelRodAssemblyBlock.RODS) != total
				|| state.getValue(FuelRodAssemblyBlock.FUELLED) != fuelled) {
			level.setBlock(worldPosition, state
					.setValue(FuelRodAssemblyBlock.RODS, total)
					.setValue(FuelRodAssemblyBlock.FUELLED, fuelled), 3);
		}
	}


	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		// The rack itself, wear and all. The old form was a bare count plus one shared "EU left", which
		// could not describe four rods at four different stages — which is exactly what a column is.
		ContainerHelper.saveAllItems(output, rack, true);
		output.putLong("CarriedEu", carriedEu);
		// Only the amounts: each tank holds exactly one fluid by construction, so storing which one
		// would be a field that can only ever disagree with the code.
		output.putLong("Water", waterTank.amount);
		output.putLong("Steam", steamTank.amount);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		rack.clear();
		ContainerHelper.loadAllItems(input, rack);
		carriedEu = Math.max(0, input.getLongOr("CarriedEu", 0));
		waterTank.amount = Math.max(0, Math.min(waterTank.capacity, input.getLongOr("Water", 0)));
		waterTank.fluid = waterTank.amount > 0 ? FluidHolder.of(Fluids.WATER) : FluidHolder.EMPTY;
		steamTank.amount = Math.max(0, Math.min(steamTank.capacity, input.getLongOr("Steam", 0)));
		steamTank.fluid = steamTank.amount > 0
				? FluidHolder.of(ModContent.STEAM.get()) : FluidHolder.EMPTY;
	}
}

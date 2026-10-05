package dev.alaindustrial.command.demo;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ItemCapabilityRoster;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * The demo stand's one way to write a cell, and its write ledger: one cell, one owner (MOD-597,
 * ADR-028, MOD-709).
 *
 * <p>Every block a zone places and every inventory, buffer or pipe face it fills goes through an
 * instance of this class, which records the cell first — so a second write on it is reported rather
 * than swallowed — and records a fill that found nothing to fill next to the overwrite that caused it.
 *
 * <p><b>Only {@link DemoStand#buildAll} can make one.</b> The constructor takes a
 * {@link DemoStand.BuildPass}, whose constructor is private to {@code DemoStand}; that is what keeps
 * ADR-028's rule "a zone is never called past {@code buildAll}" a compiler rule now that zones take a
 * writer instead of being private methods of one file. The ledger is the instance's own state, not a
 * static field, so two builds can never share one.
 */
final class StandWriter {
	/** A tank filled to its own capacity: the {@code amount} of {@link #fillTank} that means "full". */
	static final LongUnaryOperator FULL = capacity -> capacity;

	private final ServerLevel level;
	private final BlockPos origin;

	/**
	 * Every cell the zone pass has written, and what wrote it. Keyed by the cell's coordinates
	 * RELATIVE to the origin, so the ledger reads the same in the command's world and in the
	 * gametest rig.
	 */
	private final Map<BlockPos, String> written = new LinkedHashMap<>();

	/** What went wrong in this build, in the order it happened — empty when the stand is healthy. */
	private final List<String> problems = new ArrayList<>();

	/** Whether {@link #place} is currently recording: the zone pass yes, the base passes no. */
	private boolean recording;

	StandWriter(DemoStand.BuildPass pass, ServerLevel level, BlockPos origin) {
		if (pass == null) {
			throw new IllegalArgumentException("a stand writer is made by DemoStand.buildAll only");
		}
		this.level = level;
		this.origin = origin;
	}

	/** The level the stand is built into, for the helpers a zone calls that place their own cells. */
	ServerLevel level() {
		return level;
	}

	/** The stand origin: the north-west corner of the floor layer. */
	BlockPos origin() {
		return origin;
	}

	/** Start recording: the base passes are done, the zones are about to write. */
	void open() {
		written.clear();
		problems.clear();
		recording = true;
	}

	/** Stop recording, shout about anything the zones overwrote, and hand back what went wrong. */
	List<String> close() {
		recording = false;
		written.clear();
		for (String problem : problems) {
			Industrialization.LOGGER.warn("demo stand: {}", problem);
		}
		return List.copyOf(problems);
	}

	/**
	 * The one way the stand writes a block. Records the cell first, so a second write on it is
	 * reported rather than swallowed, then places it exactly as before.
	 */
	void place(BlockPos pos, BlockState state) {
		write(pos, state, false);
	}

	/**
	 * Replace a cell this same zone has just filled — a fitting punched into a shell it built itself:
	 * the reactor room lays five walls of casing and then sets the glass, the controller and the door
	 * into them, and the greenhouse does the same with its glass.
	 *
	 * <p>It is a separate call rather than an entry in an allow-list somewhere because the intent
	 * belongs at the site that has it, and because the claim is <b>checked</b>: a refit of a cell
	 * nobody built is reported exactly like a collision. An author who reaches for this to silence the
	 * ledger on a cell another zone owns gets the same red they were trying to avoid.
	 */
	void refit(BlockPos pos, BlockState state) {
		write(pos, state, true);
	}

	/** {@code refit}'s int-coordinate twin, for the zones that write in local coordinates. */
	void refit(int x, int y, int z, Block block) {
		refit(origin.offset(x, y, z), block.defaultBlockState());
	}

	private void write(BlockPos pos, BlockState state, boolean expectedToReplace) {
		if (recording) {
			BlockPos local = pos.subtract(origin);
			String owner = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			String previous = written.put(local, owner);
			String cell = "cell (" + local.getX() + ", " + local.getY() + ", " + local.getZ() + ")";
			if (previous != null && !expectedToReplace) {
				problems.add(cell + " written twice — " + previous + " overwritten by " + owner);
			} else if (previous == null && expectedToReplace) {
				problems.add(cell + " is a refit of a cell no zone built — " + owner
						+ " expected to replace something and replaced nothing");
			}
		}
		level.setBlockAndUpdate(pos, state);
	}

	/** Place a block's default state at a cell in local coordinates. */
	void set(int x, int y, int z, Block block) {
		place(origin.offset(x, y, z), block.defaultBlockState());
	}

	/** Place a processing machine with a full EU buffer and an input stack — it starts working immediately. */
	void placeWorkingMachine(int x, int z, Block machine, ItemStack input) {
		set(x, 1, z, machine);
		chargeBuffer(x, 1, z);
		fillSlot(x, 1, z, 0, input);
	}

	/**
	 * Stock one slot of the block at a cell — and say so when there is nothing there to stock.
	 *
	 * <p>The silent {@code if (instanceof)} this used to be is the other half of every overwrite
	 * incident on this stand: the block that owned the cell is gone, its inventory is filled into
	 * nothing, and the build finishes green. Now the miss is recorded next to the overwrite that
	 * caused it (MOD-597).
	 */
	void fillSlot(int x, int y, int z, int slot, ItemStack stack) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof Container container) {
			container.setItem(slot, stack);
			return;
		}
		missed(x, y, z, "has no container to stock");
	}

	/** Put {@code stacks} into the first slots of the container at a cell. */
	void stock(int x, int y, int z, List<ItemStack> stacks) {
		for (int slot = 0; slot < stacks.size(); slot++) {
			fillSlot(x, y, z, slot, stacks.get(slot));
		}
	}

	/** Fill a machine's EU buffer — and say so when the cell holds no machine (see {@link #fillSlot}). */
	void chargeBuffer(int x, int y, int z) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof MachineBlockEntity machine) {
			machine.getEnergyStorage().setAmountUntracked(machine.getEnergyStorage().getCapacity());
			machine.setChangedQuietly();
			machine.wake();
			return;
		}
		missed(x, y, z, "has no machine to charge");
	}

	/** Wrench one face of a fluid pipe — and say so when the cell holds no pipe (see {@link #fillSlot}). */
	void pipeFace(int x, int y, int z, Direction face, PipeFaceMode mode) {
		if (level.getBlockEntity(origin.offset(x, y, z)) instanceof FluidPipeBlockEntity pipe) {
			pipe.setFaceMode(face, mode);
			return;
		}
		missed(x, y, z, "has no fluid pipe to wrench");
	}

	/**
	 * Place a horizontal machine turned to {@code facing}. The rotation is load-bearing for every
	 * storage block of the stand: single-axis IO (MOD-006) emits ONLY from the face opposite FACING, so
	 * a box placed with the default state (FACING=NORTH) emits into thin air and its run sits dead.
	 */
	void placeFacing(int x, int y, int z, Block block, Direction facing) {
		place(origin.offset(x, y, z), block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, facing));
	}

	/**
	 * Set up the block entity at a cell — and say so when the cell holds none of that type (see
	 * {@link #fillSlot}). The one way a zone touches a block entity the other helpers do not cover:
	 * the silent {@code if (getBlockEntity(...) instanceof X)} it replaces is how a lost exhibit used to
	 * go unreported.
	 */
	<T extends BlockEntity> void configure(int x, int y, int z, Class<T> type, String what, Consumer<T> action) {
		BlockEntity be = level.getBlockEntity(origin.offset(x, y, z));
		if (type.isInstance(be)) {
			action.accept(type.cast(be));
			return;
		}
		missed(x, y, z, "has no " + what);
	}

	/**
	 * Fill one tank of the block entity at a cell with {@code fluid} — {@code amount} maps the tank's
	 * capacity to the amount put in. A machine is then marked changed quietly and woken, so it starts on
	 * the first tick; a plain tank is marked changed.
	 */
	<T extends BlockEntity> void fillTank(int x, int y, int z, Class<T> type, Function<T, FluidTank> tankOf,
			Fluid fluid, LongUnaryOperator amount) {
		configure(x, y, z, type, "tank to fill", be -> {
			FluidTank tank = tankOf.apply(be);
			tank.fluid = FluidHolder.of(fluid);
			tank.amount = amount.applyAsLong(tank.capacity);
			if (be instanceof MachineBlockEntity machine) {
				machine.setChangedQuietly();
				machine.wake();
			} else {
				be.setChanged();
			}
		});
	}

	/**
	 * A ripe cotton trellis on moist farmland: the farmland at {@code y = 0}, the plant placed by the
	 * vanilla two-block helper so both halves appear, and then the age written to BOTH halves through
	 * the ledger — the upper one carries it only to keep its model in step with the lower.
	 */
	void ripenTrellis(int x, int z) {
		place(origin.offset(x, 0, z),
				Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
		DoublePlantBlock.placeAt(level, ModContent.TRELLIS.get().defaultBlockState(), origin.offset(x, 1, z), 3);
		for (int dy = 1; dy <= 2; dy++) {
			BlockState state = level.getBlockState(origin.offset(x, dy, z));
			if (state.is(ModContent.TRELLIS.get())) {
				place(origin.offset(x, dy, z), state.setValue(TrellisBlock.AGE, TrellisBlock.MAX_AGE));
			} else {
				missed(x, dy, z, "has no trellis half to ripen");
			}
		}
	}

	/**
	 * A teleporter station's capsule (MOD-112): two glass blocks on top of the station, then the real
	 * assembler swaps them in place — a station is a jump destination only with its capsule.
	 */
	void assembleCapsule(int x, int y, int z) {
		set(x, y + 1, z, Blocks.GLASS);
		set(x, y + 2, z, Blocks.GLASS);
		TeleporterBlock.tryAssemble(level, origin.offset(x, y, z));
	}

	/**
	 * Load the fuel assembly at a cell to the brim and half its coolant column: a full column and an
	 * empty one look the same from the front, and neither is the state the property exists to show.
	 */
	void loadFuelRods(int x, int y, int z) {
		configure(x, y, z, FuelRodAssemblyBlockEntity.class, "fuel assembly to load", assembly -> {
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				assembly.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			assembly.setTank(true, assembly.waterTank.capacity / 2);
		});
	}

	/** Record a fill that found nothing, naming the block that actually stands there. */
	void missed(int x, int y, int z, String what) {
		if (!recording) {
			return;
		}
		String actual = BuiltInRegistries.BLOCK.getKey(
				level.getBlockState(origin.offset(x, y, z)).getBlock()).toString();
		problems.add("cell (" + x + ", " + y + ", " + z + ") " + what + " — it holds " + actual);
	}

	/** A stack of {@code item}, filled to the brim if it holds energy at all (a plain item is returned as it is). */
	static ItemStack charged(Item item) {
		ItemStack stack = new ItemStack(item);
		long capacity = ItemEnergy.capacity(stack);
		if (capacity > 0) {
			ItemEnergy.set(stack, capacity);
		}
		return stack;
	}

	/**
	 * One charged stack of every powered item of the mod ({@code PoweredItem}), in registry id order — the
	 * roster {@link ItemCapabilityRoster#energyItems()} both loaders publish the item energy capability
	 * from, so a stand chest filled from it cannot fall behind the items (MOD-709).
	 */
	static List<ItemStack> chargedPoweredItems() {
		List<ItemStack> stacks = new ArrayList<>();
		for (Item item : ItemCapabilityRoster.energyItems()) {
			stacks.add(charged(item));
		}
		return stacks;
	}
}

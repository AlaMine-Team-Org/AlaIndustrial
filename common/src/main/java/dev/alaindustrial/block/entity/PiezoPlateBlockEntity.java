package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyMover;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

/**
 * The piezo plate's store and outlet (MOD-764). A press puts a small pulse into this plate's own buffer; a
 * plate that has a receiver beside it or under its floor block collects the whole group's energy and hands it
 * on. The plate never accepts energy ({@code maxInsert} 0), so a cable beside it reads it as a generator and
 * the plates can never carry somebody else's energy — they are not conductors, by construction.
 *
 * <p><b>Who delivers what.</b> A cable — beside the plate, or under the floor, where
 * {@link dev.alaindustrial.core.energy.FloorPortLender} lends the plate's port to the floor's bottom face —
 * pulls from the outlet like from any generator. Every other receiver (a machine, a Battery Box) is pushed to
 * here, probe-then-commit, exactly as {@code DirectAdjacencyDistributor} does for generators. The plate's top
 * face exposes nothing: a plate is something you walk on.
 *
 * <p><b>Sleeping.</b> A plate with no outlet sleeps between group checks; the press wakes the group's outlets,
 * so a pulse leaves the same tick it was made.
 */
public class PiezoPlateBlockEntity extends EnergyBlockEntity {
	/** Idle sleep of an outlet with nothing to move; a press or a draw wakes it sooner. */
	private static final int OUTLET_IDLE_TICKS = 10;

	private @Nullable PiezoPlateGroup group;
	private int outletMask;
	// What the wrench overlay shows; filled by the group walk on the server, read from the update tag on the client.
	private PiezoPlateGroup.Status viewStatus = PiezoPlateGroup.Status.NO_OUTLET;
	private int viewSize = 1;
	private int viewOutlets;
	private long viewStored;
	private long viewCapacity;
	private int sentOutletMask = -1;

	public PiezoPlateBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.PIEZO_PLATE_BE.get(), pos, state, EnergyTier.LV,
				Math.max(1, GeneratorConfig.piezoPlateBuffer), 0L,
				Math.max(1, GeneratorConfig.piezoGroupOutputPerTick));
	}

	// --- presses ---------------------------------------------------------------------------------

	/**
	 * A new press: store the pulse and wake the group's outlets. What does not fit is lost — a full group turns
	 * red under the wrench — and is not an error.
	 *
	 * @return the EU actually stored
	 */
	public long onPressed(boolean living) {
		long pulse = living ? GeneratorConfig.piezoPlateLivingPressEu : GeneratorConfig.piezoPlateObjectPressEu;
		long stored = energy.produceInternal(Math.max(0, pulse));
		setChanged();
		wake();
		if (group != null) {
			group.wakeOutlets();
		}
		return stored;
	}

	/** A neighbour changed (a cable, a machine, another plate): the group and its outlets need a new look. */
	public void onNeighbourChanged() {
		if (group != null) {
			group.invalidate();
		}
		wake();
	}

	// --- tick ------------------------------------------------------------------------------------

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		PiezoPlateGroup current = PiezoPlateGroup.of(level, this);
		if (!current.isOutlet(this)) {
			return IDLE_SLEEP_TICKS;
		}
		long moved = 0;
		long room = energy.getCapacity() - energy.getAmount();
		if (room > 0) {
			moved += current.gatherInto(this, room, level.getGameTime());
		}
		moved += pushToReceivers(level, pos);
		// A draw by the cable network or a new press wakes an idle outlet sooner than this.
		return moved > 0 ? 0 : OUTLET_IDLE_TICKS;
	}

	/**
	 * Push to every receiver that is not a cable — a cable's network pulls from this plate on its own, and
	 * pushing into it too would hand the same energy over twice.
	 */
	private long pushToReceivers(Level level, BlockPos pos) {
		long moved = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if ((outletMask & 1 << dir.get2DDataValue()) != 0) {
				moved += pushInto(level, pos.relative(dir), dir.getOpposite());
			}
		}
		if ((outletMask & PiezoPlateGroup.OUTLET_FLOOR) != 0) {
			BlockPos receiver = PiezoPlateGroup.floorReceiverPos(level, pos);
			if (receiver != null) {
				moved += pushInto(level, receiver, Direction.UP);
			}
		}
		return moved;
	}

	private long pushInto(Level level, BlockPos target, Direction face) {
		if (energy.getAmount() <= 0 || PiezoPlateGroup.isCable(level, target)) {
			return 0;
		}
		EnergyPort port = EnergyLookup.get().find(level, target, face);
		if (port == null || !port.supportsInsertion()) {
			return 0;
		}
		long movable = EnergyMover.probe(energy, port, tier.maxVoltage());
		if (movable <= 0) {
			return 0;
		}
		EnergyTransactions.get().runCommitting(tx -> EnergyMover.commit(energy, port, movable, tx));
		return movable;
	}

	// --- energy faces ----------------------------------------------------------------------------

	/** Out on the four sides and down (the floor lends the down face to a cable below it); nothing on top. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	// --- group view --------------------------------------------------------------------------------

	@Nullable PiezoPlateGroup group() {
		return group;
	}

	void setOutletMask(int mask) {
		outletMask = mask;
	}

	/** Called by the group walk on every member; re-sends the overlay data only when it changed. */
	void joinGroup(PiezoPlateGroup joined, PiezoPlateGroup.Status status, int size, int outlets, long stored,
			long capacity) {
		group = joined;
		long storedStep = capacity > 0 ? stored * 20 / capacity : 0;
		long oldStep = viewCapacity > 0 ? viewStored * 20 / viewCapacity : 0;
		boolean changed = status != viewStatus || size != viewSize || outlets != viewOutlets
				|| capacity != viewCapacity || storedStep != oldStep || outletMask != sentOutletMask;
		viewStatus = status;
		viewSize = size;
		viewOutlets = outlets;
		viewStored = stored;
		viewCapacity = capacity;
		if (changed) {
			sentOutletMask = outletMask;
			syncBlockEntityToClient();
		}
	}

	public PiezoPlateGroup.Status viewStatus() {
		return viewStatus;
	}

	public int viewSize() {
		return viewSize;
	}

	public int viewOutlets() {
		return viewOutlets;
	}

	public long viewStored() {
		return viewStored;
	}

	public long viewCapacity() {
		return viewCapacity;
	}

	/** Bits 0-3: a receiver beside the plate (2D data value of the face); {@link PiezoPlateGroup#OUTLET_FLOOR}. */
	public int outletMask() {
		return outletMask;
	}

	// --- client sync -------------------------------------------------------------------------------

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		CompoundTag tag = super.getUpdateTag(provider);
		tag.putInt("GroupStatus", viewStatus.ordinal());
		tag.putInt("GroupSize", viewSize);
		tag.putInt("GroupOutlets", viewOutlets);
		tag.putLong("GroupStored", viewStored);
		tag.putLong("GroupCapacity", viewCapacity);
		tag.putInt("OutletMask", outletMask);
		return tag;
	}

	/** Reads the overlay fields from an update tag; a save carries none of them and keeps the defaults. */
	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		viewStatus = PiezoPlateGroup.Status.byOrdinal(input.getIntOr("GroupStatus", viewStatus.ordinal()));
		viewSize = input.getIntOr("GroupSize", viewSize);
		viewOutlets = input.getIntOr("GroupOutlets", viewOutlets);
		viewStored = input.getLongOr("GroupStored", viewStored);
		viewCapacity = input.getLongOr("GroupCapacity", viewCapacity);
		outletMask = input.getIntOr("OutletMask", outletMask);
	}
}

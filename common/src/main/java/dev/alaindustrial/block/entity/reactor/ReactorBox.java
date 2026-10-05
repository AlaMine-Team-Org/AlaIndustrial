package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.core.structure.RoomValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The box a reactor controller last sealed, or an empty one if it never has (MOD-713, BE-5) — the interior,
 * not the shell.
 *
 * <p><b>This is what lets a room come apart.</b> A failed scan measures nothing, so a sweep driven
 * by the scan result can only ever switch the shell ON — punch a hole in a finished room and every
 * block stayed seamless and lit, which is precisely what the first playtest reported. Remembering
 * the sealed box gives the controller something to clear.
 *
 * <p>Kept in NBT: a chunk can unload while the room is whole and reload after a creeper has opened
 * it, and a controller that forgot its box on load would leave the shell stuck looking sealed.
 */
public final class ReactorBox {

	/** The controller's {@code setChanged}: the box is saved. */
	private final Runnable changed;

	private int boxMinX;
	private int boxMinY;
	private int boxMinZ;
	private int boxMaxX = Integer.MIN_VALUE;
	private int boxMaxY = Integer.MIN_VALUE;
	private int boxMaxZ = Integer.MIN_VALUE;

	public ReactorBox(Runnable changed) {
		this.changed = changed;
	}

	/** Whether a box is remembered at all; the six bounds mean nothing while it is not. */
	public boolean isSet() {
		return boxMaxX != Integer.MIN_VALUE;
	}

	/** Whether this scan measured a different interior than the one currently remembered. */
	public boolean changesTo(RoomScan.Result result) {
		return boxMaxX != Integer.MIN_VALUE
				&& (boxMinX != result.minX() || boxMinY != result.minY() || boxMinZ != result.minZ()
						|| boxMaxX != result.maxX() || boxMaxY != result.maxY() || boxMaxZ != result.maxZ());
	}

	public void remember(RoomScan.Result result) {
		boxMinX = result.minX();
		boxMinY = result.minY();
		boxMinZ = result.minZ();
		boxMaxX = result.maxX();
		boxMaxY = result.maxY();
		boxMaxZ = result.maxZ();
		changed.run();
	}

	/** Hands the last sealed shell back its unbuilt look and forgets the box; how many blocks changed. */
	public int clear(Level level) {
		if (boxMaxX == Integer.MIN_VALUE) {
			return 0;
		}
		int changed = RoomValidator.applyFormed(level, boxMinX, boxMinY, boxMinZ,
				boxMaxX, boxMaxY, boxMaxZ, false);
		boxMaxX = Integer.MIN_VALUE;
		boxMaxY = Integer.MIN_VALUE;
		boxMaxZ = Integer.MIN_VALUE;
		this.changed.run();
		return changed;
	}

	/**
	 * Clears the drone flag across the last sealed interior, block by block.
	 *
	 * <p>The recovery path for every case where the list of racks is gone but the flag is not: a room
	 * reloaded from disk and found broken, an interior partitioned so that some racks fell outside the
	 * new box, a controller taken out by something that skips the mining hook. Without it those racks
	 * hum for as long as they stand, and nothing in the world can switch them off.
	 */
	public int clearActive(Level level) {
		if (boxMaxX == Integer.MIN_VALUE) {
			return 0;
		}
		int cleared = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = boxMinX; x <= boxMaxX; x++) {
			for (int y = boxMinY; y <= boxMaxY; y++) {
				for (int z = boxMinZ; z <= boxMaxZ; z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (state.getBlock() instanceof FuelRodAssemblyBlock
							&& state.getValue(FuelRodAssemblyBlock.ACTIVE)) {
						level.setBlock(cursor.immutable(),
								state.setValue(FuelRodAssemblyBlock.ACTIVE, false), 2);
						cleared++;
					}
				}
			}
		}
		return cleared;
	}

	/**
	 * Whether a position lies inside the interior this controller last sealed.
	 *
	 * <p>The INTERIOR, not the shell: the question being asked is "is this rack part of a working
	 * reactor", and a rack is only ever inside the room. An empty box answers no to everything, which is
	 * the right answer for a controller that has never sealed anything.
	 */
	public boolean contains(BlockPos at) {
		if (boxMaxX == Integer.MIN_VALUE) {
			return false;
		}
		return at.getX() >= boxMinX && at.getX() <= boxMaxX
				&& at.getY() >= boxMinY && at.getY() <= boxMaxY
				&& at.getZ() >= boxMinZ && at.getZ() <= boxMaxZ;
	}

	/** Writes the box — the address of the shell this controller must be able to switch back off. */
	public void save(ValueOutput output) {
		output.putInt("BoxMinX", boxMinX);
		output.putInt("BoxMinY", boxMinY);
		output.putInt("BoxMinZ", boxMinZ);
		output.putInt("BoxMaxX", boxMaxX);
		output.putInt("BoxMaxY", boxMaxY);
		output.putInt("BoxMaxZ", boxMaxZ);
	}

	public void load(ValueInput input) {
		boxMinX = input.getIntOr("BoxMinX", 0);
		boxMinY = input.getIntOr("BoxMinY", 0);
		boxMinZ = input.getIntOr("BoxMinZ", 0);
		boxMaxX = input.getIntOr("BoxMaxX", Integer.MIN_VALUE);
		boxMaxY = input.getIntOr("BoxMaxY", Integer.MIN_VALUE);
		boxMaxZ = input.getIntOr("BoxMaxZ", Integer.MIN_VALUE);
	}

	public int minX() {
		return boxMinX;
	}

	public int minY() {
		return boxMinY;
	}

	public int minZ() {
		return boxMinZ;
	}

	public int maxX() {
		return boxMaxX;
	}

	public int maxY() {
		return boxMaxY;
	}

	public int maxZ() {
		return boxMaxZ;
	}
}

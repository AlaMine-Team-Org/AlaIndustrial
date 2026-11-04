package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.ReactorAlarmLampBlock;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.RoomScan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The alarm lamps that belong to a reactor (MOD-945, owner decision 2026-11-04): a lamp whose base is pressed to a
 * cell of the sealed room's shell — on the outside or on the inside of the wall — or, for a bare reactor, to its
 * controller or one of its racks. The controller lights them by its alarm with no wire at all.
 *
 * <p>Found on the scan timer, like the antennas: a lamp put up by hand counts from the next sweep. Only the cells
 * that touch the shell are read, so a room costs about two reads per shell cell per sweep, never its whole volume.
 */
public final class ReactorLamps {

	private ReactorLamps() {}

	/** Adds every lamp pressed to the shell ring around {@code box} (the room's interior) to {@code out}. */
	public static void collectRoom(Level level, RoomScan.Result box, List<BlockPos> out) {
		int minX = box.minX() - 1;
		int minY = box.minY() - 1;
		int minZ = box.minZ() - 1;
		int maxX = box.maxX() + 1;
		int maxY = box.maxY() + 1;
		int maxZ = box.maxZ() + 1;
		BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					boolean ring = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
					if (!ring) {
						continue;
					}
					cell.set(x, y, z);
					for (Direction face : Direction.values()) {
						int nx = x + face.getStepX();
						int ny = y + face.getStepY();
						int nz = z + face.getStepZ();
						boolean inBox = nx >= minX && nx <= maxX && ny >= minY && ny <= maxY
								&& nz >= minZ && nz <= maxZ;
						boolean neighbourIsRing = inBox && (nx == minX || nx == maxX || ny == minY || ny == maxY
								|| nz == minZ || nz == maxZ);
						if (!neighbourIsRing) {
							addIfPressed(level, cell, face, out);
						}
					}
				}
			}
		}
	}

	/** Adds every lamp pressed to one of {@code supports} (a bare reactor's controller and racks) to {@code out}. */
	public static void collectAround(Level level, List<BlockPos> supports, List<BlockPos> out) {
		for (BlockPos support : supports) {
			for (Direction face : Direction.values()) {
				addIfPressed(level, support, face, out);
			}
		}
	}

	/** The lamp in the cell on {@code face} of {@code support}, if it stands there with its base on that support. */
	private static void addIfPressed(Level level, BlockPos support, Direction face, List<BlockPos> out) {
		BlockPos at = support.relative(face);
		if (!level.isLoaded(at)) {
			return;
		}
		BlockState state = level.getBlockState(at);
		if (state.getBlock() instanceof ReactorAlarmLampBlock
				&& state.getValue(ReactorAlarmLampBlock.FACING) == face && !out.contains(at)) {
			out.add(at.immutable());
		}
	}

	/** Shows the reactor's alarm on every lamp in {@code lamps}: lit at the warning level and above. */
	public static void show(Level level, List<BlockPos> lamps, int signal) {
		boolean on = signal >= ReactorCore.ALARM_SIGNAL_WARN;
		for (BlockPos at : lamps) {
			ReactorAlarmLampBlock.showReactorAlarm(level, at, on);
		}
	}
}

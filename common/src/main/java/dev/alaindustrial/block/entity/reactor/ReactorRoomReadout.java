package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.core.structure.RoomScan;
import net.minecraft.core.BlockPos;

/**
 * What the last room scan measured, as the console's channels carry it (MOD-619; MOD-713, BE-5): the breach, the
 * measured interior and the listed holes, every position an offset from the controller.
 *
 * <p><b>Offsets, not coordinates.</b> {@code ContainerData} ships each channel as a short, and an absolute block
 * position arrives on the client as garbage; an offset from the controller is at most a room across.
 *
 * @param holeOffsets the first {@link RoomScan#MAX_LISTED_HOLES} holes, three entries each — east, up and south
 */
public record ReactorRoomReadout(int breachDx, int breachDy, int breachDz, int sizeX, int sizeY, int sizeZ,
		int boxWest, int boxNorth, int holeCount, int[] holeOffsets) {

	/** Before the first scan: every channel zero. */
	public static final ReactorRoomReadout EMPTY =
			new ReactorRoomReadout(0, 0, 0, 0, 0, 0, 0, 0, 0, new int[3 * RoomScan.MAX_LISTED_HOLES]);

	/** The readout of {@code result}, measured from a controller at {@code pos}. */
	public static ReactorRoomReadout of(RoomScan.Result result, BlockPos pos) {
		// Every verdict the rays got far enough to measure carries its box (MOD-619): the «Room» tab draws the
		// walls around a breach as well as around a sealed room.
		boolean measured = result.maxX() >= result.minX();
		int[] holes = new int[3 * RoomScan.MAX_LISTED_HOLES];
		for (int i = 0; i < result.listedHoles(); i++) {
			holes[3 * i] = result.holeX(i) - pos.getX();
			holes[3 * i + 1] = result.holeY(i) - pos.getY();
			holes[3 * i + 2] = result.holeZ(i) - pos.getZ();
		}
		return new ReactorRoomReadout(result.x() - pos.getX(), result.y() - pos.getY(), result.z() - pos.getZ(),
				measured ? result.sizeX() : 0, measured ? result.sizeY() : 0, measured ? result.sizeZ() : 0,
				measured ? result.minX() - pos.getX() : 0, measured ? result.minZ() - pos.getZ() : 0,
				result.holeCount(), holes);
	}

	/** Hole offset channel {@code index}: hole {@code index / 3}, axis {@code index % 3}. */
	public int holeOffset(int index) {
		return holeOffsets[index];
	}

	/** Number of hole offset channels: three per listed hole. */
	public int holeOffsetCount() {
		return holeOffsets.length;
	}
}

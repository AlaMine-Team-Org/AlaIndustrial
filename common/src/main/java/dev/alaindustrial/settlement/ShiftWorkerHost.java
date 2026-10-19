package dev.alaindustrial.settlement;

import dev.alaindustrial.settlement.record.RecordNumber;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Where the reception capsule (MOD-833) hands a worker over to the shift workers' side (MOD-832): how many workers
 * a town has, which of its dead wait to come back, and the two ways a worker enters the world from the capsule —
 * a new one, or a dead one brought back (R39).
 *
 * <p>The capsule asks only after every condition holds and takes its price only when the host answers that the
 * worker stands in the world: the host issues a new worker's number itself ({@code ShiftWorkers.spawn}, through
 * {@code RecordNumberLedger.issue()}), and closes it if the body could not join the world.
 *
 * <p>The capsule's host is {@code ShiftWorkers.CAPSULE_HOST}; a game test may hand one capsule a host of its own
 * ({@code ReceptionCapsuleBlockEntity#useHost}) to make a release fail on purpose.
 */
public interface ShiftWorkerHost {

	/** How many living workers town {@code settlementId} has — what its tier's limit is measured against. */
	int livingWorkers(MinecraftServer server, int settlementId);

	/** The dead of town {@code settlementId} who wait to be brought back (R39), in number order. */
	List<RecordNumber> awaitingReturn(MinecraftServer server, int settlementId);

	/**
	 * Puts a new worker of {@code town} into {@code level}, standing at {@code standAt} and looking along {@code yaw}.
	 *
	 * @return the new worker's number once it stands in the world; empty if no worker could be made
	 */
	Optional<RecordNumber> releaseNew(ServerLevel level, Vec3 standAt, float yaw, Settlement town);

	/**
	 * Brings the dead worker with {@code number} of {@code town} back, standing at {@code standAt} (R39).
	 *
	 * @return true once the worker stands in the world; false if it could not come back
	 */
	boolean bringBack(ServerLevel level, Vec3 standAt, float yaw, RecordNumber number, Settlement town);
}

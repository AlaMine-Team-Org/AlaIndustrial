package dev.alaindustrial.gametest;

import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.shift.RoutineActivity;
import dev.alaindustrial.settlement.shift.RoutineState;
import dev.alaindustrial.settlement.shift.ShiftRoutine;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * L2 of a shift worker just out of its reception capsule (MOD-893; epic MOD-824), on both gametest lanes. The capsule
 * is three cells tall and the game counts every cell of it as solid, so the game's walk and its random stands aim
 * above the capsule, onto its roof: a break that kept the new worker round the capsule's floor, or a short walk to a
 * stand lifted out of the capsule, sent it up whatever stood next to the capsule (the owner's playtest: a gallery of
 * the desert town hall) and left it there, «did not reach its goal».
 */
public final class ShiftWorkerReleaseScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerReleaseScenarios::aBreakFindingANewWorkerInItsCapsuleKeepsItToTheCore,
						"shift_worker_release_break_keeps_to_the_core").ticks(200),
				RosterEntry.of(ShiftWorkerReleaseScenarios::theCapsuleRoofIsNoPlaceToKeepOrToWalkTo,
						"shift_worker_release_capsule_roof_is_no_place").ticks(20));

		private Roster() {
		}
	}

	private ShiftWorkerReleaseScenarios() {
	}

	/** Mid-break: every worker of a town is on its break, whatever its offsets (break 6000 ± 1000, gathering 9000). */
	static final long BREAK = 7_500;

	/** Where the capsule's restoration puts a worker: on its floor, as {@code ReceptionCapsuleBlockEntity} does. */
	private static Vec3 onTheFloor(BlockPos lower) {
		return new Vec3(lower.getX() + 0.5, lower.getY() + ReceptionCapsuleBlock.FLOOR_PX / 16.0, lower.getZ() + 0.5);
	}

	/** A cell near {@code near} with three cells of air from it up and a sturdy block under it: room for a capsule. */
	private static BlockPos capsuleRoom(GameTestHelper helper, ServerLevel level, BlockPos near) {
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos cell = near.offset(dx, 0, dz);
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r && level.getBlockState(cell).isAir()
							&& level.getBlockState(cell.above()).isAir() && level.getBlockState(cell.above(2)).isAir()
							&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
						return cell;
					}
				}
			}
		}
		throw helper.assertionException("no room for a capsule near " + near);
	}

	private static void removeCapsule(ServerLevel level, BlockPos lower) {
		for (int up = 2; up >= 0; up--) {
			level.setBlock(lower.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * A worker released by its town's capsule at the break hour: its first think starts the break while it still stands
	 * on the capsule's floor, and the break keeps it to the core, never to that floor: the place it keeps to is one it
	 * can walk back to.
	 *
	 * @implements MOD-893-RELEASE — a break that finds a new worker in its capsule keeps it to the core, not to the
	 *     capsule's floor
	 */
	public static void aBreakFindingANewWorkerInItsCapsuleKeepsItToTheCore(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		int town = rig.town();
		ShiftWorkerRoutineScenarios.pin(helper, town, BREAK);
		BlockPos lower = capsuleRoom(helper, level, rig.core().getBlockPos().offset(3, 0, 0));
		ReceptionCapsuleBlock.placeWhole(level, lower, Direction.NORTH, ModContent.RECEPTION_CAPSULE.get());
		ShiftWorker worker = ShiftWorkers.spawn(level, onTheFloor(lower), 180.0F, town)
				.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker in the capsule"));
		helper.assertTrue(worker.blockPosition().equals(lower),
				"control: a released worker stands in the capsule's lower cell: " + worker.blockPosition());
		helper.startSequence().thenWaitUntil(() -> {
			ShiftWorkerRoutineScenarios.live(worker);
			if (worker.routine().activity() == null) {
				throw helper.assertionException("the new worker has not thought yet");
			}
		}).thenExecute(() -> {
			helper.assertTrue(worker.routine().activity() == RoutineActivity.BREAK,
					"control: the clock is the break's: " + worker.routine().activity());
			helper.assertTrue(worker.routine().spot() == RoutineState.NONE,
					"the break must not keep the worker to the capsule's floor " + lower + ", its spot is "
							+ BlockPos.of(worker.routine().spot()));
			helper.assertTrue(worker.getHomePosition().equals(worker.townAnchor()),
					"a break found in the capsule keeps the worker to the core " + worker.townAnchor() + ", not to "
							+ worker.getHomePosition());
			ShiftWorkers.book(level.getServer()).remove(worker.getUUID());
			worker.discard();
			removeCapsule(level, lower);
			rig.release();
		}).thenSucceed();
	}

	/**
	 * The trap itself, and both ways out of it: the game lifts a stand in the capsule's cell onto the roof (the control
	 * that the premise holds in this version of the game); the break keeps no spot inside the capsule, but keeps a cell
	 * of plain floor; a short walk to the roof is refused, one to the floor is not.
	 *
	 * @implements MOD-893-ROOF — a stand on a capsule's roof is never a place to keep to or to walk to
	 */
	public static void theCapsuleRoofIsNoPlaceToKeepOrToWalkTo(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos floor = helper.absolutePos(new BlockPos(0, 2, 0));
		ReceptionCapsuleBlock.placeWhole(level, lower, Direction.NORTH, ModContent.RECEPTION_CAPSULE.get());
		ShiftWorker body = ModMobs.SHIFT_WORKER.type().create(level, EntitySpawnReason.TRIGGERED);
		helper.assertTrue(body != null, "control: the worker type made no body");
		Vec3 at = onTheFloor(lower);
		body.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		BlockPos lifted = LandRandomPos.movePosUpOutOfSolid(body, lower);
		helper.assertTrue(lower.above(3).equals(lifted),
				"control: the game lifts a stand in the capsule's cell onto its roof " + lower.above(3) + ": "
						+ lifted);
		helper.assertTrue(ShiftRoutine.breakSpot(level, lower) == RoutineState.NONE,
				"a break must keep no spot inside the capsule " + lower);
		helper.assertTrue(ShiftRoutine.breakSpot(level, floor) == floor.asLong(),
				"a break keeps the cell of plain floor it finds the worker on " + floor);
		helper.assertTrue(ShiftRoutine.onCapsuleRoof(level, Vec3.atBottomCenterOf(lifted)),
				"a short walk to the capsule's roof " + lifted + " must be refused");
		helper.assertFalse(ShiftRoutine.onCapsuleRoof(level, Vec3.atBottomCenterOf(floor)),
				"a short walk to the floor " + floor + " must not be refused");
		body.discard();
		helper.succeed();
	}
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.DroneNestBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.core.drone.DroneFlightPath;
import dev.alaindustrial.core.drone.DroneRoute;
import dev.alaindustrial.settlement.drone.DroneAirspace;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import dev.alaindustrial.settlement.drone.DroneNestRules.Status;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * L2 suite of the builder drone's flight (MOD-858, owner's round 2), replayed by both loaders through the roster
 * (ADR-038), on the rig of {@link TownDroneScenarios}: the drone never flies through a block — from its nest on the
 * roof to a cell inside the closed hall it goes round, through the door; and a dispatcher without the plan, or no
 * dispatcher at all, stops the drones — the one in the air does nothing on its cell, comes home, and its job stands
 * «no plan» till the plan is back.
 */
public final class TownDroneFlightScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		private Roster() {
		}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownDroneFlightScenarios::droneNeverEntersASolidCell,
						"town_drone_never_enters_a_solid_cell").ticks(800),
				RosterEntry.of(TownDroneFlightScenarios::noPlanStopsTheDronesTillItIsBack,
						"town_drone_no_plan_stops_the_drones").ticks(1600),
				RosterEntry.of(TownDroneFlightScenarios::noDispatcherStopsTheDrones,
						"town_drone_no_dispatcher_stops_the_drones").ticks(800));
	}

	private TownDroneFlightScenarios() {
	}

	/** Where along a route a check looks, in blocks. */
	private static final float SAMPLE_STEP = 0.1F;

	/**
	 * @implements MOD-858-ROUTE — a cell on the hall's ground floor, the nest on its roof: the drone's route goes round
	 *     through open cells and the door, never into a block (the roof and the floor between the storeys stand between
	 *     the nest and the cell); the drone gets there and clears the cell.
	 */
	public static void droneNeverEntersASolidCell(GameTestHelper helper) {
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 1);
		BlockPos cell = groundFloorDebris(helper, rig);
		rig.level().setBlock(cell, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockPos nest = rig.nests().getFirst();
		helper.assertTrue(crossesASolidCell(rig, straightDown(nest, cell)), "control: the old way over the roofs must "
				+ "go through a block here, or the case proves nothing");
		measureSearch(rig, nest, cell);
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		int[] checked = {0};
		long start = helper.getTick();
		helper.onEachTick(() -> {
			rig.tick();
			DroneNestBlockEntity drone = rig.nest(0);
			if (drone.flight().flying() && checked[0] == 0) {
				int[] corners = drone.routeCorners();
				helper.assertTrue(drone.flight().path().length >= 6, "the leg must carry its own route");
				helper.assertTrue(!crossesASolidCell(rig, corners), "the route goes through a block: "
						+ Arrays.toString(corners));
				checked[0] = corners.length / 3;
				Industrialization.LOGGER.info("[ala-towns] measure: route roof -> ground floor, {} corners, {} blocks",
						corners.length / 3, String.format("%.1f", new DroneFlightPath(floats(corners)).length()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0] > 0, "control: the drone never took off");
			helper.assertTrue(rig.level().getBlockState(cell).isAir(), "the drone must reach the cell and clear it");
			helper.assertTrue(rig.works().queue().job(job.id()).orElseThrow().status() == WorkJobStatus.DONE,
					"the clearing must be done");
			Industrialization.LOGGER.info("[ala-towns] measure: clearing through the door took {} ticks",
					helper.getTick() - start);
			rig.release();
		});
	}

	/**
	 * @implements MOD-858-NO-PLAN — the plan taken out of the dispatcher while the drone flies to fill a cell: on the
	 *     cell it does nothing, it comes home, nothing leaves the buffer, the job stands «no plan» and the nest says
	 *     so; the plan put back, the drone builds the block and exactly one brick leaves the buffer.
	 */
	public static void noPlanStopsTheDronesTillItIsBack(GameTestHelper helper) {
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 1);
		BlockPos cell = TownDroneScenarios.takeOutAWallBrick(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 5));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		ItemStack[] plan = {ItemStack.EMPTY};
		boolean[] stopped = {false};
		helper.onEachTick(() -> {
			rig.tick();
			DroneNestBlockEntity drone = rig.nest(0);
			if (plan[0].isEmpty() && !stopped[0] && drone.flight().flying() && !drone.flight().returning()) {
				plan[0] = rig.dispatcher().getItem(TownDispatcherBlockEntity.PLAN_SLOT).copy();
				rig.dispatcher().setItem(TownDispatcherBlockEntity.PLAN_SLOT, ItemStack.EMPTY);
				return;
			}
			if (!plan[0].isEmpty() && !stopped[0] && !drone.flight().flying()
					&& drone.status() == Status.NO_PLAN) {
				WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
				helper.assertTrue(now.status() == WorkJobStatus.BLOCKED && now.reason().map(BlockReason::key)
						.equals(Optional.of(BlockReason.NO_PLAN)), "the job must stand «no plan», it is " + now);
				helper.assertTrue(rig.level().getBlockState(cell).isAir(), "with no plan the drone must not build");
				helper.assertTrue(rig.dispatcher().buffer().count("minecraft:bricks") == 5,
						"nothing may leave the buffer when the drone did nothing");
				stopped[0] = true;
				rig.dispatcher().setItem(TownDispatcherBlockEntity.PLAN_SLOT, plan[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stopped[0], "the drone must come home and stand «no plan»: nest " + rig.nest(0).status()
					+ ", flying " + rig.nest(0).flight().flying() + ", job " + rig.job(WorkJobType.BUILD));
			helper.assertTrue(rig.works().queue().job(job.id()).orElseThrow().status() == WorkJobStatus.DONE,
					"with the plan back the job must be done, it is " + rig.job(WorkJobType.BUILD));
			helper.assertTrue(rig.level().getBlockState(cell).is(Blocks.BRICKS), "the brick must be back");
			helper.assertTrue(rig.dispatcher().buffer().count("minecraft:bricks") == 4,
					"exactly one brick must have left the buffer, it holds "
							+ rig.dispatcher().buffer().count("minecraft:bricks"));
			rig.release();
		});
	}

	/**
	 * @implements MOD-858-NO-DISPATCHER — the dispatcher broken while the drone flies to its cell: the drone does
	 *     nothing there, comes home, and its job stands «no plan».
	 */
	public static void noDispatcherStopsTheDrones(GameTestHelper helper) {
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 1);
		BlockPos cell = groundFloorDebris(helper, rig);
		rig.level().setBlock(cell, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			DroneNestBlockEntity drone = rig.nest(0);
			if (!broken[0]) {
				rig.tick();
				if (drone.flight().flying() && !drone.flight().returning()) {
					rig.level().setBlock(rig.dispatcherAt(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					broken[0] = true;
				}
				return;
			}
			rig.core().getEnergyStorage().setAmountUntracked(rig.core().getEnergyStorage().getCapacity());
			rig.core().serverTick(rig.level(), rig.core().getBlockPos(),
					rig.level().getBlockState(rig.core().getBlockPos()));
			drone.serverTick(rig.level(), drone.getBlockPos(), rig.level().getBlockState(drone.getBlockPos()));
		});
		helper.succeedWhen(() -> {
			DroneNestBlockEntity drone = rig.nest(0);
			helper.assertTrue(broken[0], "control: the drone never took off");
			helper.assertTrue(!drone.flight().flying(), "the drone must come home");
			helper.assertTrue(rig.level().getBlockState(cell).is(Blocks.COBWEB), "with no dispatcher the drone must "
					+ "leave the cell alone");
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.BLOCKED && now.reason().map(BlockReason::key)
					.equals(Optional.of(BlockReason.NO_PLAN)) && drone.status() == Status.NO_DISPATCHER,
					"the job must stand «no plan»: " + now + ", nest " + drone.status());
			rig.release();
		});
	}

	/**
	 * The cost of a route search, for MOD-858's research: the roof to the ground floor (found), and a cell beside the
	 * hall walled in on every side (no route: the search spends its whole budget). Logged, not asserted — a timing.
	 */
	private static void measureSearch(TownDroneScenarios.Rig rig, BlockPos nest, BlockPos cell) {
		BlockPos shut = rig.origin().offset(-3, 2, 5);
		for (Direction side : Direction.values()) {
			rig.level().setBlock(shut.relative(side), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (BlockPos target : List.of(cell, shut)) {
			DroneRoute route = DroneAirspace.route(rig.level(), nest, target, null);
			long whole = Long.MAX_VALUE;
			long tick = 0L;
			int ticks = 0;
			for (int run = 0; run < 20; run++) {
				DroneRoute.Planner planner = DroneAirspace.plan(rig.level(), nest, target, null);
				long started = System.nanoTime();
				long longest = 0L;
				int steps = 0;
				boolean done = false;
				while (!done) {
					long t0 = System.nanoTime();
					done = planner.advance(DroneAirspace.NODES_PER_TICK);
					longest = Math.max(longest, System.nanoTime() - t0);
					steps++;
				}
				if (System.nanoTime() - started < whole) {
					whole = System.nanoTime() - started;
					tick = longest;
					ticks = steps;
				}
			}
			Industrialization.LOGGER.info("[ala-towns] measure: route search to {} -> {}, {} cells expanded, {} ticks, "
					+ "longest tick {} us, whole search {} us (best of 20)", target, route.kind(), route.expanded(),
					ticks, tick / 1000, whole / 1000);
		}
		for (Direction side : Direction.values()) {
			rig.level().setBlock(shut.relative(side), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** A debris cell of the hall's ground floor (the lowest storey), its first in plan order. */
	private static BlockPos groundFloorDebris(GameTestHelper helper, TownDroneScenarios.Rig rig) {
		int floor = rig.origin().getY() + 1;
		for (int i : rig.debris()) {
			if (rig.plan().pos(i).getY() == floor) {
				return rig.plan().pos(i);
			}
		}
		throw helper.assertionException("control: the intact hall has no debris cell on its ground floor");
	}

	/** The legacy way to {@code cell}: up two blocks over the higher end, across, down onto it. */
	private static int[] straightDown(BlockPos nest, BlockPos cell) {
		int cruise = DroneNestRules.cruiseY(nest.getY(), cell.getY());
		return new int[] {nest.getX(), nest.getY(), nest.getZ(), nest.getX(), cruise, nest.getZ(), cell.getX(), cruise,
				cell.getZ(), cell.getX(), cell.getY() + 1, cell.getZ()};
	}

	/**
	 * Whether a drone's centre, flying the polyline through the centres of {@code corners}' cells, is ever inside a
	 * block's collision box — a doorway (a door, a trapdoor) excepted, and the nest it starts from. Independent of the
	 * route's own rule of open cells: it asks the world's collision shapes.
	 */
	private static boolean crossesASolidCell(TownDroneScenarios.Rig rig, int[] corners) {
		float[] xyz = floats(corners);
		for (int i = 0; i < xyz.length; i++) {
			xyz[i] += 0.5F;
		}
		DroneFlightPath path = new DroneFlightPath(xyz);
		BlockPos nest = new BlockPos(corners[0], corners[1], corners[2]);
		float[] at = new float[3];
		int samples = Math.max(1, (int) Math.ceil(path.length() / SAMPLE_STEP));
		for (int s = 0; s <= samples; s++) {
			path.point((float) s / samples, at);
			Vec3 point = new Vec3(at[0], at[1], at[2]);
			BlockPos pos = BlockPos.containing(point);
			if (pos.equals(nest)) {
				continue;
			}
			BlockState state = rig.level().getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(rig.level(), pos);
			for (AABB box : shape.toAabbs()) {
				if (box.move(pos).contains(point)) {
					return true;
				}
			}
		}
		return false;
	}

	private static float[] floats(int[] values) {
		float[] out = new float[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = values[i];
		}
		return out;
	}
}

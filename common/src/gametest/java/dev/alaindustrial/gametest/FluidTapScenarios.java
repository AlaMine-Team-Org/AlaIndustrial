package dev.alaindustrial.gametest;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.FluidTapBlock;
import dev.alaindustrial.block.entity.ElectricMobTrapBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.FluidTapBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.fluid.ExperienceFluid;
import dev.alaindustrial.mixin.ExperienceOrbAccessor;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametests of the Tap (MOD-837, suite TC-TAP-001), run by both loaders from the roster.
 *
 * <p>The tap, the pipe network and the clicks are driven synchronously inside one test tick: nothing waits on
 * the schedule, so no world tick runs the tap a second time, a placed fluid has not flowed yet, and the orbs
 * counted are still where they were spawned (a fresh orb is not merged with another until it has ticked). An
 * orb is counted with its merge count.
 */
public final class FluidTapScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidTapScenarios::drawsAnyFluidThroughItsBackOnly,
						"fluid_tap_draws_any_fluid_through_its_back_only"),
				RosterEntry.of(FluidTapScenarios::pipeFillsItWithoutAWrench,
						"fluid_tap_pipe_fills_it_without_a_wrench"),
				RosterEntry.of(FluidTapScenarios::closedKeepsItsBuffer, "fluid_tap_closed_keeps_its_buffer"),
				RosterEntry.of(FluidTapScenarios::rightClickToggles, "fluid_tap_right_click_toggles"),
				RosterEntry.of(FluidTapScenarios::redstoneHoldsItOpen, "fluid_tap_redstone_holds_it_open"),
				RosterEntry.of(FluidTapScenarios::poursWaterAsASourceBlock, "fluid_tap_pours_water_as_a_source_block"),
				RosterEntry.of(FluidTapScenarios::poursIntoATankBelow, "fluid_tap_pours_into_a_tank_below"),
				RosterEntry.of(FluidTapScenarios::experienceBecomesExactPoints,
						"fluid_tap_experience_becomes_exact_points"),
				RosterEntry.of(FluidTapScenarios::foreignFluidWaits, "fluid_tap_foreign_fluid_waits"));

		private Roster() {}
	}

	private FluidTapScenarios() {}

	private static final long MB = ExperienceFluid.MB_PER_POINT;

	/** A tap at {@code pos} in the given form, nozzle toward {@code facing}. */
	private static FluidTapBlockEntity tap(GameTestHelper helper, BlockPos pos, Direction facing, boolean ceiling) {
		BlockState state = ModContent.FLUID_TAP.get().defaultBlockState()
				.setValue(FluidTapBlock.FACING, facing).setValue(FluidTapBlock.CEILING, ceiling);
		helper.setBlock(pos, state);
		return helper.getBlockEntity(pos, FluidTapBlockEntity.class);
	}

	private static FluidTankBlockEntity tank(GameTestHelper helper, BlockPos pos, Fluid fluid, long mb) {
		helper.setBlock(pos, ModContent.FLUID_TANK.get());
		FluidTankBlockEntity tank = helper.getBlockEntity(pos, FluidTankBlockEntity.class);
		fill(tank.fluidTank, fluid, mb);
		return tank;
	}

	private static void drive(GameTestHelper helper, FluidTapBlockEntity tap, int ticks) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < ticks; i++) {
			BlockPos pos = tap.getBlockPos();
			tap.serverTick(level, pos, level.getBlockState(pos));
		}
	}

	private static void fill(FluidTank tank, Fluid fluid, long mb) {
		tank.fluid = FluidHolder.of(fluid);
		tank.amount = mb;
	}

	/** Open the tap by its toggle, as a right click does, with no player to tell. */
	private static void openByHand(GameTestHelper helper, BlockPos pos) {
		helper.useBlock(pos, player(helper));
		helper.assertTrue(helper.getBlockState(pos).getValue(FluidTapBlock.OPEN), "precondition: the click opens it");
	}

	private static AABB around(GameTestHelper helper, BlockPos pos) {
		return new AABB(helper.absolutePos(pos)).inflate(3.0);
	}

	private static int orbPoints(GameTestHelper helper, BlockPos pos) {
		int points = 0;
		for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around(helper, pos))) {
			points += orb.getValue() * ((ExperienceOrbAccessor) orb).alaindustrial$getCount();
		}
		return points;
	}

	/** A player in the level, far enough from every test rig not to count as "near" by accident. */
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		Vec3 far = helper.absoluteVec(new Vec3(0.5, 40.0, 0.5));
		player.snapTo(far.x, far.y, far.z);
		return player;
	}

	/**
	 * @implements TC-TAP-001-FUN01 — a closed wall tap on a water tank draws 200 mB a tick through its back
	 *     until its 1000 mB buffer is full, and a tank beside it on another face gives nothing; only the back
	 *     face carries a port. Lava behind a ceiling tap is drawn the same way: any fluid, not only experience.
	 */
	public static void drawsAnyFluidThroughItsBackOnly(GameTestHelper helper) {
		BlockPos backPos = new BlockPos(1, 3, 2);
		BlockPos tapPos = new BlockPos(2, 3, 2);
		BlockPos sidePos = new BlockPos(2, 3, 3);
		FluidTankBlockEntity back = tank(helper, backPos, Fluids.WATER, 4000);
		FluidTankBlockEntity side = tank(helper, sidePos, Fluids.WATER, 4000);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.EAST, false);

		drive(helper, tap, 1);
		helper.assertTrue(tap.tank.amount == FluidTapBlockEntity.PULL_PER_TICK_MB && back.fluidTank.amount == 3800,
				"one tick draws 200 mB from the back: tap " + tap.tank.amount + ", back tank " + back.fluidTank.amount);
		drive(helper, tap, 10);
		helper.assertTrue(tap.tank.amount == FluidTapBlockEntity.CAPACITY_MB && tap.tank.fluid.is(Fluids.WATER)
						&& back.fluidTank.amount == 3000,
				"the buffer fills to 1000 mB of water and stops: tap " + tap.tank.amount + ", back "
						+ back.fluidTank.amount);
		helper.assertTrue(side.fluidTank.amount == 4000,
				"the tank on a side face gives nothing: " + side.fluidTank.amount);
		helper.assertTrue(tap.fluidPort(Direction.WEST) != null && tap.fluidPort(Direction.SOUTH) == null
						&& tap.fluidPort(Direction.UP) == null && tap.fluidPort(Direction.DOWN) == null,
				"only the back face carries a port");

		BlockPos lavaPos = new BlockPos(5, 4, 5);
		BlockPos ceilingPos = new BlockPos(5, 3, 5);
		FluidTankBlockEntity lava = tank(helper, lavaPos, Fluids.LAVA, 500);
		FluidTapBlockEntity hanging = tap(helper, ceilingPos, Direction.SOUTH, true);
		drive(helper, hanging, 5);
		helper.assertTrue(hanging.tank.amount == 500 && hanging.tank.fluid.is(Fluids.LAVA)
						&& lava.fluidTank.amount == 0,
				"a ceiling tap draws all 500 mB of lava from above: tap " + hanging.tank.amount + ", tank "
						+ lava.fluidTank.amount);
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-FUN02 — a fluid pipe on the tap's back connects to it and fills it with no wrench
	 *     on the tap's face: the tap's port gives nothing back, so the pipe takes the face as a sink. A pipe
	 *     beside the tap draws no arm toward it.
	 */
	public static void pipeFillsItWithoutAWrench(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos tankPos = new BlockPos(1, 3, 2);
		BlockPos pipePos = new BlockPos(2, 3, 2);
		BlockPos tapPos = new BlockPos(3, 3, 2);
		BlockPos sidePipePos = new BlockPos(3, 3, 3);
		FluidTankBlockEntity water = tank(helper, tankPos, Fluids.WATER, 4000);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.EAST, false);
		helper.setBlock(pipePos, ModContent.FLUID_PIPE.get());
		helper.setBlock(sidePipePos, ModContent.FLUID_PIPE.get());
		FluidPipeBlockEntity pipe = helper.getBlockEntity(pipePos, FluidPipeBlockEntity.class);
		FluidPipeBlockEntity sidePipe = helper.getBlockEntity(sidePipePos, FluidPipeBlockEntity.class);
		pipe.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		helper.assertTrue(pipe.faceMode(Direction.EAST) == PipeFaceMode.NEUTRAL,
				"precondition: the tap face is untouched");
		pipe.serverTick(level, pipe.getBlockPos(), pipe.getBlockState());
		sidePipe.serverTick(level, sidePipe.getBlockPos(), sidePipe.getBlockState());

		helper.assertTrue(FluidPipeBlock.shouldConnectTo(level, helper.absolutePos(pipePos), Direction.EAST),
				"the pipe on the tap's back must connect to it");
		helper.assertFalse(FluidPipeBlock.shouldConnectTo(level, helper.absolutePos(sidePipePos), Direction.NORTH),
				"a pipe beside the tap must not connect to it");
		for (int t = 0; t < 200 && tap.tank.amount < FluidTapBlockEntity.CAPACITY_MB; t++) {
			FluidNetworkManager.tickAll(level);
		}
		helper.assertTrue(tap.tank.amount == FluidTapBlockEntity.CAPACITY_MB && tap.tank.fluid.is(Fluids.WATER),
				"the pipe must fill the tap's 1000 mB with water, it holds " + tap.tank.amount + "; tank left "
						+ water.fluidTank.amount);
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-NEG01 — a closed tap full of water, with air under its nozzle, keeps all 1000 mB
	 *     and places nothing, however long it ticks.
	 */
	public static void closedKeepsItsBuffer(GameTestHelper helper) {
		BlockPos tapPos = new BlockPos(2, 3, 2);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.SOUTH, false);
		fill(tap.tank, Fluids.WATER, 1000);
		drive(helper, tap, 60);
		helper.assertFalse(helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN), "precondition: it is closed");
		helper.assertTrue(tap.tank.amount == 1000 && helper.getBlockState(tapPos.below()).isAir(),
				"a closed tap pours nothing: it holds " + tap.tank.amount + ", under it "
						+ helper.getBlockState(tapPos.below()));
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-FUN03 — a right click opens the tap and turns the lever, a second click closes it.
	 */
	public static void rightClickToggles(GameTestHelper helper) {
		BlockPos tapPos = new BlockPos(2, 3, 2);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.SOUTH, false);
		ServerPlayer player = player(helper);
		helper.useBlock(tapPos, player);
		helper.assertTrue(helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN) && tap.isToggledOpen(),
				"the first click must open the tap");
		helper.useBlock(tapPos, player);
		helper.assertTrue(!helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN) && !tap.isToggledOpen(),
				"the second click must close it");
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-FUN04 — a redstone signal opens a closed tap and its loss closes it again; a tap
	 *     opened by hand stays open when the signal goes; a click on a powered tap leaves it open.
	 */
	public static void redstoneHoldsItOpen(GameTestHelper helper) {
		BlockPos tapPos = new BlockPos(2, 3, 2);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.SOUTH, false);
		helper.setBlock(tapPos.above(), Blocks.REDSTONE_BLOCK);
		helper.assertTrue(helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN), "a signal must open the tap");
		ServerPlayer player = player(helper);
		helper.useBlock(tapPos, player);
		helper.assertTrue(tap.isToggledOpen() && helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN),
				"a click on a powered tap toggles it but it stays open");
		helper.useBlock(tapPos, player);
		helper.assertTrue(!tap.isToggledOpen() && helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN),
				"toggled closed, the signal still holds it open");
		helper.setBlock(tapPos.above(), Blocks.AIR);
		helper.assertFalse(helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN), "no signal, not toggled: closed");
		helper.useBlock(tapPos, player);
		helper.setBlock(tapPos.above(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(tapPos.above(), Blocks.AIR);
		helper.assertTrue(helper.getBlockState(tapPos).getValue(FluidTapBlock.OPEN),
				"a tap opened by hand stays open when a signal comes and goes");
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-FUN05 — an open wall tap with 1000 mB of water places one water source in the cell
	 *     under its nozzle and spends the bucket; with only 600 mB and nothing behind it, it places nothing.
	 */
	public static void poursWaterAsASourceBlock(GameTestHelper helper) {
		BlockPos tapPos = new BlockPos(2, 3, 2);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.SOUTH, false);
		fill(tap.tank, Fluids.WATER, 1000);
		openByHand(helper, tapPos);
		drive(helper, tap, 1);
		helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(tapPos.below()))
						.isSourceOfType(Fluids.WATER),
				"a water source must stand under the nozzle, there is " + helper.getBlockState(tapPos.below()));
		helper.assertTrue(tap.tank.amount == 0 && tap.tank.fluid.isEmpty(),
				"the bucket is spent: the tap holds " + tap.tank.amount);
		helper.setBlock(tapPos.below(), Blocks.AIR);

		BlockPos lowPos = new BlockPos(5, 3, 5);
		FluidTapBlockEntity low = tap(helper, lowPos, Direction.SOUTH, false);
		fill(low.tank, Fluids.WATER, 600);
		openByHand(helper, lowPos);
		drive(helper, low, 40);
		helper.assertTrue(helper.getBlockState(lowPos.below()).isAir() && low.tank.amount == 600,
				"less than a bucket places nothing: under it " + helper.getBlockState(lowPos.below()) + ", tap "
						+ low.tank.amount);
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-FUN06 — an open tap with a tank under its nozzle pours into the tank, 200 mB a tick,
	 *     and places no block.
	 */
	public static void poursIntoATankBelow(GameTestHelper helper) {
		BlockPos tapPos = new BlockPos(2, 3, 2);
		BlockPos belowPos = new BlockPos(2, 2, 2);
		FluidTankBlockEntity below = tank(helper, belowPos, Fluids.EMPTY, 0);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.SOUTH, false);
		fill(tap.tank, Fluids.WATER, 1000);
		openByHand(helper, tapPos);
		drive(helper, tap, 3);
		helper.assertTrue(below.fluidTank.amount == 3 * FluidTapBlockEntity.POUR_INTO_HOLDER_MB
						&& below.fluidTank.fluid.is(Fluids.WATER) && tap.tank.amount == 400,
				"three ticks pour 600 mB into the tank below: tank " + below.fluidTank.amount + ", tap "
						+ tap.tank.amount);
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-FUN07 — trap, pipe, tap: the pipe carries all 410 mB of liquid experience into the
	 *     tap; open and with a player near, it pours exactly 410 / MB_PER_POINT = 20 points as orbs of at most
	 *     5, and the 10 mB below a point stay in the tap. With nobody near it pours nothing.
	 */
	public static void experienceBecomesExactPoints(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos trapPos = new BlockPos(2, 4, 2);
		BlockPos down = new BlockPos(2, 3, 2);
		BlockPos across = new BlockPos(3, 3, 2);
		BlockPos tapPos = new BlockPos(4, 3, 2);
		helper.setBlock(trapPos, ModContent.ELECTRIC_MOB_TRAP.get());
		ElectricMobTrapBlockEntity trap = helper.getBlockEntity(trapPos, ElectricMobTrapBlockEntity.class);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.EAST, false);
		helper.setBlock(down, ModContent.FLUID_PIPE.get());
		helper.setBlock(across, ModContent.FLUID_PIPE.get());
		FluidPipeBlockEntity first = helper.getBlockEntity(down, FluidPipeBlockEntity.class);
		FluidPipeBlockEntity last = helper.getBlockEntity(across, FluidPipeBlockEntity.class);
		first.setFaceMode(Direction.UP, PipeFaceMode.EXTRACT);
		long total = 20 * MB + 10;
		fill(trap.xpTank, ModContent.EXPERIENCE.get(), total);
		for (FluidPipeBlockEntity pipe : List.of(first, last)) {
			pipe.serverTick(level, pipe.getBlockPos(), pipe.getBlockState());
		}
		for (int t = 0; t < 200 && tap.tank.amount < total; t++) {
			FluidNetworkManager.tickAll(level);
		}
		helper.assertTrue(trap.xpTank.amount == 0 && tap.tank.amount == total,
				"the pipe must carry all " + total + " mB into the tap; trap holds " + trap.xpTank.amount
						+ ", tap " + tap.tank.amount);

		openByHand(helper, tapPos);
		drive(helper, tap, 50);
		helper.assertTrue(orbPoints(helper, tapPos) == 0 && tap.tank.amount == total,
				"with nobody near the open tap must pour nothing: orbs " + orbPoints(helper, tapPos) + ", tap "
						+ tap.tank.amount);

		ServerPlayer player = player(helper);
		Vec3 near = helper.absoluteVec(new Vec3(6.5, 3.0, 2.5));
		player.snapTo(near.x, near.y, near.z);
		drive(helper, tap, 6 * FluidTapBlockEntity.POUR_INTERVAL);
		helper.assertTrue(orbPoints(helper, tapPos) == 20,
				"the open tap must pour exactly 20 points, orbs hold " + orbPoints(helper, tapPos));
		helper.assertTrue(tap.tank.amount == 10,
				"the 10 mB below a point stay in the tap, it holds " + tap.tank.amount);
		helper.succeed();
	}

	/**
	 * @implements TC-TAP-001-NEG02 — a tap holding water does not take the lava behind it; once its buffer is
	 *     empty, it draws the lava.
	 */
	public static void foreignFluidWaits(GameTestHelper helper) {
		BlockPos lavaPos = new BlockPos(1, 3, 2);
		BlockPos tapPos = new BlockPos(2, 3, 2);
		FluidTankBlockEntity lava = tank(helper, lavaPos, Fluids.LAVA, 2000);
		FluidTapBlockEntity tap = tap(helper, tapPos, Direction.EAST, false);
		fill(tap.tank, Fluids.WATER, 300);
		drive(helper, tap, 10);
		helper.assertTrue(tap.tank.amount == 300 && tap.tank.fluid.is(Fluids.WATER) && lava.fluidTank.amount == 2000,
				"lava waits while the tap holds water: tap " + tap.tank.amount + " of " + tap.tank.fluid + ", lava "
						+ lava.fluidTank.amount);
		tap.tank.amount = 0;
		tap.tank.fluid = FluidHolder.EMPTY;
		drive(helper, tap, 1);
		helper.assertTrue(tap.tank.fluid.is(Fluids.LAVA) && tap.tank.amount == FluidTapBlockEntity.PULL_PER_TICK_MB,
				"with the buffer empty the lava comes in: tap " + tap.tank.amount + " of " + tap.tank.fluid);
		helper.succeed();
	}
}

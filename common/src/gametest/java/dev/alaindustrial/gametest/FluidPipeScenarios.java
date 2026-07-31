package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/**
 * Cross-loader MOD-151 fluid-pipe scenarios: a source tank, two pipe segments, a destination tank.
 * Both tanks are the mod's own, so every transfer crosses the real fluid-port seam on each loader.
 */
public final class FluidPipeScenarios {
	private FluidPipeScenarios() {
	}

	private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
	private static final BlockPos PIPE_A = new BlockPos(2, 2, 1);
	private static final BlockPos PIPE_B = new BlockPos(3, 2, 1);
	private static final BlockPos TARGET = new BlockPos(4, 2, 1);

	private static BlockEntity be(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(relative));
	}

	private static FluidPipeBlockEntity pipe(GameTestHelper helper, BlockPos relative) {
		return be(helper, relative) instanceof FluidPipeBlockEntity pipe ? pipe : null;
	}

	private static FluidTankBlockEntity tank(GameTestHelper helper, BlockPos relative) {
		return be(helper, relative) instanceof FluidTankBlockEntity tank ? tank : null;
	}

	/** Source tank full of water, pipes wired to pull from it and push into the destination. */
	private static boolean build(GameTestHelper helper) {
		helper.setBlock(SOURCE, ModContent.FLUID_TANK.get());
		helper.setBlock(PIPE_A, ModContent.FLUID_PIPE.get());
		helper.setBlock(PIPE_B, ModContent.FLUID_PIPE.get());
		helper.setBlock(TARGET, ModContent.FLUID_TANK.get());
		FluidTankBlockEntity source = tank(helper, SOURCE);
		FluidPipeBlockEntity a = pipe(helper, PIPE_A);
		FluidPipeBlockEntity b = pipe(helper, PIPE_B);
		FluidTankBlockEntity target = tank(helper, TARGET);
		if (source == null || a == null || b == null || target == null) {
			helper.fail("MOD-151 rig block entity missing");
			return false;
		}
		source.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
		source.fluidTank.amount = Config.fluidTankCapacity;
		a.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		b.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		a.serverTick(helper.getLevel(), a.getBlockPos(), a.getBlockState());
		b.serverTick(helper.getLevel(), b.getBlockPos(), b.getBlockState());
		return true;
	}

	/** Run the network for {@code ticks} server ticks. */
	private static void run(GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			FluidNetworkManager.tickAll(helper.getLevel());
		}
	}

	private static boolean pipesShareNetwork(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FluidNetwork a = FluidNetworkManager.networkAt(level, helper.absolutePos(PIPE_A));
		FluidNetwork b = FluidNetworkManager.networkAt(level, helper.absolutePos(PIPE_B));
		return a != null && a == b;
	}

	/**
	 * MOD-151: water crosses tank → pipe → pipe → tank. Fluid occupies the line one hop per tick, so
	 * this deliberately runs several ticks rather than expecting an instant teleport.
	 */
	public static void transfersBetweenTanks(GameTestHelper helper) {
		if (!build(helper)) {
			return;
		}
		run(helper, 20);
		FluidTankBlockEntity target = tank(helper, TARGET);
		if (target == null || target.fluidTank.amount <= 0) {
			helper.fail("MOD-151 no water reached the destination tank; got "
					+ (target == null ? "no tank" : target.fluidTank.amount + " mB"));
			return;
		}
		if (!target.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("MOD-151 destination holds the wrong fluid: " + target.fluidTank.fluid.fluid());
			return;
		}
		helper.succeed();
	}

	/** MOD-151: fluid is really IN the line — an intermediate segment holds some while running. */
	public static void segmentsHoldFluidInTransit(GameTestHelper helper) {
		if (!build(helper)) {
			return;
		}
		run(helper, 5);
		FluidPipeBlockEntity a = pipe(helper, PIPE_A);
		FluidPipeBlockEntity b = pipe(helper, PIPE_B);
		if (a == null || b == null) {
			helper.fail("MOD-151 pipes vanished mid-run");
			return;
		}
		if (a.fluidBuffer.amount <= 0 && b.fluidBuffer.amount <= 0) {
			helper.fail("MOD-151 both segments are empty while the line is running — fluid is "
					+ "teleporting past the pipes instead of flowing through them");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-151, carrying MOD-282's lesson forward: disabling a pipe-to-pipe joint splits the network,
	 * and re-enabling it must join the halves back. Getting this wrong leaves a line that looks whole
	 * and silently moves nothing.
	 */
	public static void reEnabledPipeLinkRejoinsNetwork(GameTestHelper helper) {
		if (!build(helper)) {
			return;
		}
		run(helper, 2);
		FluidPipeBlockEntity a = pipe(helper, PIPE_A);
		if (a == null) {
			helper.fail("MOD-151 source pipe vanished before the link check");
			return;
		}
		a.setFaceMode(Direction.EAST, PipeFaceMode.DISABLED);
		if (pipesShareNetwork(helper)) {
			helper.fail("MOD-151 disabling the joint did not split the network");
			return;
		}
		a.setFaceMode(Direction.EAST, PipeFaceMode.NEUTRAL);
		if (!pipesShareNetwork(helper)) {
			helper.fail("MOD-151 re-enabled joint left two separate networks — the line looks whole "
					+ "but cannot carry anything across the former break");
			return;
		}
		helper.succeed();
	}

	/** MOD-151: a segment holding one fluid refuses another — no mixing water and lava in a line. */
	public static void segmentRefusesASecondFluid(GameTestHelper helper) {
		helper.setBlock(PIPE_A, ModContent.FLUID_PIPE.get());
		FluidPipeBlockEntity a = pipe(helper, PIPE_A);
		if (a == null) {
			helper.fail("MOD-151 pipe missing for the mixing check");
			return;
		}
		a.fluidBuffer.fluid = FluidHolder.of(Fluids.WATER);
		a.fluidBuffer.amount = 10;
		long[] insertedLava = {0};
		dev.alaindustrial.core.energy.EnergyTransactions.get().runCommitting(txn ->
				insertedLava[0] = a.fluidBuffer.insert(FluidHolder.of(Fluids.LAVA), 10, txn));
		if (insertedLava[0] != 0) {
			helper.fail("MOD-151 a water-filled segment accepted " + insertedLava[0] + " mB of lava");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-151: breaking a segment loses its contents and creates nothing. The buffer is tiny by design,
	 * so vanishing is the intended behaviour — what must not happen is the fluid being duplicated into
	 * the world or the neighbouring segment.
	 */
	public static void brokenSegmentLosesItsContentsWithoutDuplicating(GameTestHelper helper) {
		if (!build(helper)) {
			return;
		}
		run(helper, 6);
		FluidPipeBlockEntity b = pipe(helper, PIPE_B);
		FluidTankBlockEntity target = tank(helper, TARGET);
		if (b == null || target == null) {
			helper.fail("MOD-151 rig missing before the break check");
			return;
		}
		long inTargetBefore = target.fluidTank.amount;
		long inSegment = b.fluidBuffer.amount;
		helper.setBlock(PIPE_B, Blocks.AIR);
		run(helper, 2);
		FluidTankBlockEntity targetAfter = tank(helper, TARGET);
		if (targetAfter == null) {
			helper.fail("MOD-151 destination tank vanished");
			return;
		}
		if (targetAfter.fluidTank.amount > inTargetBefore + inSegment) {
			helper.fail("MOD-151 breaking a segment duplicated fluid: destination went from "
					+ inTargetBefore + " to " + targetAfter.fluidTank.amount
					+ " mB while the broken segment held only " + inSegment);
			return;
		}
		helper.succeed();
	}
}

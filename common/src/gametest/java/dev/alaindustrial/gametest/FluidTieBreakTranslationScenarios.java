package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/**
 * The fluid network behaves the same wherever a build stands (MOD-733, ADR-006): the same rig, built once
 * across the plane {@code y = 0} and once ten blocks higher, moves the same volumes tick by tick.
 *
 * <p><b>Why the rig must cross zero.</b> The defect was a tie-break on {@code BlockPos.asLong}, whose y and
 * z fields sort a negative coordinate as a large positive one. Inside one sign that order is
 * translation-invariant, so a rig moved within one sign proves nothing; only a move across {@code y = 0}
 * flips it. The rig is therefore built at ABSOLUTE heights, in the structure's own x/z column so its
 * chunks are the ones the test keeps loaded. A crossing of {@code z = 0} cannot be placed reliably from a
 * world test (the framework picks x/z); the L1.5 {@code FluidPositionOrderTest} covers it.
 *
 * <p>Two cases: two donors at equal distance sharing the one segment next to a hungry tank (the
 * hungry-sink sweep), and a column with no consumer and an odd total (the levelling pass).
 */
public final class FluidTieBreakTranslationScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidTieBreakTranslationScenarios::sameFlowAcrossYZero,
						"mod733_fluid_tie_break_translation"));

		private Roster() {}
	}

	private static final int TICKS = 10;
	/** Height of the shared segment in the crossing build, and in the shifted one. */
	private static final int CROSSING_Y = 0;
	private static final int SHIFTED_Y = 10;

	private FluidTieBreakTranslationScenarios() {
	}

	/**
	 * Both cases, each built across {@code y = 0} and ten blocks up; the per-tick lines must match.
	 *
	 * @implements MOD-733-AC1 — equal-distance donors sharing a neighbour flow the same after a translation
	 *     across the sign of a coordinate
	 */
	public static void sameFlowAcrossYZero(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		if (level.getMinY() > CROSSING_Y - 3) {
			helper.fail("world min y " + level.getMinY() + " leaves no room for a rig across y = 0");
			return;
		}
		BlockPos column = helper.absolutePos(new BlockPos(1, 0, 1));
		List<String> differences = new ArrayList<>();
		compare(differences, "donors", donors(helper, column, CROSSING_Y), donors(helper, column, SHIFTED_Y));
		compare(differences, "level", levelling(helper, column, CROSSING_Y), levelling(helper, column, SHIFTED_Y));
		if (!differences.isEmpty()) {
			helper.fail(String.join("\n", differences));
			return;
		}
		helper.succeed();
	}

	/** Both cases are compared before failing, so one run tells which of the two orders a build depends on. */
	private static void compare(List<String> differences, String name, List<String> crossing, List<String> shifted) {
		if (!crossing.equals(shifted)) {
			differences.add(name +": the same rig flows differently across y = 0 than ten blocks up\n  at y=0:  "
					+ String.join("\n          ", crossing) + "\n  at y=10: " + String.join("\n          ", shifted));
		}
	}

	/**
	 * Segment N at height {@code y} empties into a tank north of it; donors A (below) and B (above), full,
	 * touch only N, so both are one hop from the hungry sink and compete for N's room.
	 */
	private static List<String> donors(GameTestHelper helper, BlockPos column, int y) {
		BlockPos n = new BlockPos(column.getX(), y, column.getZ());
		BlockPos a = n.below();
		BlockPos b = n.above();
		BlockPos tankPos = n.north();
		List<BlockPos> placed = List.of(a, n, b, tankPos);
		clear(helper, placed);
		place(helper, tankPos, ModContent.FLUID_TANK.get());
		for (BlockPos p : List.of(a, n, b)) {
			place(helper, p, ModContent.FLUID_PIPE.get());
		}
		FluidTankBlockEntity tank = (FluidTankBlockEntity) helper.getLevel().getBlockEntity(tankPos);
		pipe(helper, n).setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);
		for (BlockPos p : List.of(a, b)) {
			FluidPipeBlockEntity donor = pipe(helper, p);
			donor.fluidBuffer.fluid = FluidHolder.of(Fluids.WATER);
			donor.fluidBuffer.amount = donor.fluidBuffer.getCapacity();
		}
		List<String> lines = run(helper, "donors", n, List.of(a, n, b), tank);
		clear(helper, placed);
		return lines;
	}

	/** A column of four segments from {@code y - 2} to {@code y + 1}, no consumer, an odd total. */
	private static List<String> levelling(GameTestHelper helper, BlockPos column, int y) {
		List<BlockPos> pipes = new ArrayList<>();
		for (int dy = -2; dy <= 1; dy++) {
			pipes.add(new BlockPos(column.getX(), y + dy, column.getZ()));
		}
		clear(helper, pipes);
		for (BlockPos p : pipes) {
			place(helper, p, ModContent.FLUID_PIPE.get());
		}
		FluidPipeBlockEntity bottom = pipe(helper, pipes.get(0));
		bottom.fluidBuffer.fluid = FluidHolder.of(Fluids.WATER);
		bottom.fluidBuffer.amount = bottom.fluidBuffer.getCapacity() - 1;
		FluidPipeBlockEntity second = pipe(helper, pipes.get(1));
		second.fluidBuffer.fluid = FluidHolder.of(Fluids.WATER);
		second.fluidBuffer.amount = 6;
		List<String> lines = run(helper, "level", pipes.get(0), pipes, null);
		clear(helper, pipes);
		return lines;
	}

	private static List<String> run(GameTestHelper helper, String name, BlockPos member, List<BlockPos> pipes,
			FluidTankBlockEntity tank) {
		for (BlockPos p : pipes) {
			FluidPipeBlockEntity pipe = pipe(helper, p);
			pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState()); // registers the segment
		}
		List<String> lines = new ArrayList<>();
		for (int t = 1; t <= TICKS; t++) {
			FluidNetwork net = FluidNetworkManager.networkAt(helper.getLevel(), member);
			boolean awake = net != null && net.isAwake();
			if (awake) {
				net.tick();
			}
			StringBuilder line = new StringBuilder(name).append(" t").append(t < 10 ? "0" : "").append(t).append(" p=");
			for (int i = 0; i < pipes.size(); i++) {
				line.append(i == 0 ? "" : ",").append(pipe(helper, pipes.get(i)).fluidBuffer.amount);
			}
			if (tank != null) {
				line.append(" k=").append(tank.fluidTank.amount);
			}
			lines.add(line.append(" a=").append(awake ? 1 : 0).toString());
		}
		return lines;
	}

	private static void place(GameTestHelper helper, BlockPos absolute, Block block) {
		helper.getLevel().setBlock(absolute, block.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static void clear(GameTestHelper helper, List<BlockPos> absolute) {
		for (BlockPos p : absolute) {
			helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private static FluidPipeBlockEntity pipe(GameTestHelper helper, BlockPos absolute) {
		if (helper.getLevel().getBlockEntity(absolute) instanceof FluidPipeBlockEntity p) {
			return p;
		}
		throw new IllegalStateException("no fluid pipe at " + absolute);
	}
}

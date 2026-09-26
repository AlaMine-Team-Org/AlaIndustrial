package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;

/**
 * MOD-677: a fluid line carries its segment's worth per tick whatever its length.
 *
 * <p>Before MOD-677 each hop moved HALF the difference between two segments, so a line of n
 * segments carried about capacity / n: 16 mB/t over three basic pipes, 12 over four. These
 * scenarios pin the promise the tooltip makes — a tank-fed line delivers at least 90 % of its
 * thinnest segment per tick in steady state — on 3, 10 and 30 segments, for both grades, and with
 * a thin segment in a thick line.
 *
 * <p>The long line is a serpentine in two layers (y = 2 and y = 4, one riser between them) with a
 * free row between runs, so no two segments that are not consecutive touch: a shortcut would
 * measure a shorter line than the one named.
 */
public final class FluidLineThroughputScenarios {
	private FluidLineThroughputScenarios() {
	}

	private static final int WARM_UP = 90;
	private static final int MEASURE = 100;

	/** The first {@code n} cells of the two-layer serpentine, consecutive cells adjacent. */
	static List<BlockPos> serpentine(int n) {
		List<BlockPos> cells = new ArrayList<>();
		for (int layer = 0; layer < 2 && cells.size() < n; layer++) {
			int y = 2 + layer * 2;
			int[] rows = layer == 0 ? new int[] {1, 3, 5} : new int[] {5, 3, 1};
			boolean east = layer == 0;
			for (int r = 0; r < rows.length; r++) {
				for (int i = 1; i <= 6; i++) {
					cells.add(new BlockPos(east ? i : 7 - i, y, rows[r]));
				}
				if (r + 1 < rows.length) {
					int turnX = east ? 6 : 1;
					cells.add(new BlockPos(turnX, y, (rows[r] + rows[r + 1]) / 2));
				}
				east = !east;
			}
			if (layer == 0) {
				cells.add(new BlockPos(6, 3, 5));   // riser to the upper layer
			}
		}
		if (cells.size() < n) {
			throw new IllegalArgumentException("serpentine holds " + cells.size() + " cells, asked " + n);
		}
		return cells.subList(0, n);
	}

	/** The side of {@code a} that faces {@code b}; the two must be adjacent. */
	private static Direction toward(BlockPos a, BlockPos b) {
		for (Direction d : Direction.values()) {
			if (a.relative(d).equals(b)) {
				return d;
			}
		}
		throw new IllegalArgumentException(a + " and " + b + " are not adjacent");
	}

	/**
	 * Build tank → n segments → tank, run to steady state, and return mB delivered per tick. The
	 * source is topped up and the target emptied every tick, so neither end is the bottleneck.
	 */
	static double deliveredPerTick(GameTestHelper helper, int n, IntFunction<Block> gradeAt) {
		List<BlockPos> path = serpentine(n);
		BlockPos first = path.get(0);
		BlockPos last = path.get(n - 1);
		BlockPos source = first.west();
		Direction out = n > 1 ? toward(path.get(n - 2), last) : Direction.EAST;
		BlockPos target = last.relative(out);
		helper.setBlock(source, ModContent.FLUID_TANK.get());
		for (int i = 0; i < n; i++) {
			helper.setBlock(path.get(i), gradeAt.apply(i));
		}
		helper.setBlock(target, ModContent.FLUID_TANK.get());
		List<FluidPipeBlockEntity> pipes = new ArrayList<>();
		for (BlockPos p : path) {
			if (!(helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof FluidPipeBlockEntity pipe)) {
				helper.fail("MOD-677 no pipe at " + p);
				return -1;
			}
			pipes.add(pipe);
		}
		FluidTankBlockEntity src = tank(helper, source);
		FluidTankBlockEntity dst = tank(helper, target);
		if (src == null || dst == null) {
			helper.fail("MOD-677 rig tanks missing");
			return -1;
		}
		pipes.get(0).setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		pipes.get(n - 1).setFaceMode(out, PipeFaceMode.INSERT);
		for (FluidPipeBlockEntity pipe : pipes) {
			pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
		}
		long delivered = 0;
		for (int t = 0; t < WARM_UP + MEASURE; t++) {
			src.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			src.fluidTank.amount = Config.fluidTankCapacity;
			FluidNetworkManager.tickAll(helper.getLevel());
			if (t >= WARM_UP) {
				delivered += dst.fluidTank.amount;
			}
			dst.fluidTank.amount = 0;
			dst.fluidTank.fluid = FluidHolder.EMPTY;
		}
		return delivered / (double) MEASURE;
	}

	private static FluidTankBlockEntity tank(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof FluidTankBlockEntity t
				? t : null;
	}

	private static void expectAtLeast(GameTestHelper helper, String what, double rate, int segment) {
		double floor = segment * 0.9;
		if (rate < floor) {
			helper.fail(String.format(java.util.Locale.ROOT,
					"MOD-677 %s delivered %.1f mB/t, expected at least %.1f (90%% of a %d mB segment)",
					what, rate, floor, segment));
			return;
		}
		helper.succeed();
	}

	public static void basicLineOf3(GameTestHelper helper) {
		expectAtLeast(helper, "3 basic segments",
				deliveredPerTick(helper, 3, i -> ModContent.FLUID_PIPE.get()), Config.fluidPipeSegmentBuffer);
	}

	public static void basicLineOf10(GameTestHelper helper) {
		expectAtLeast(helper, "10 basic segments",
				deliveredPerTick(helper, 10, i -> ModContent.FLUID_PIPE.get()), Config.fluidPipeSegmentBuffer);
	}

	public static void basicLineOf30(GameTestHelper helper) {
		expectAtLeast(helper, "30 basic segments",
				deliveredPerTick(helper, 30, i -> ModContent.FLUID_PIPE.get()), Config.fluidPipeSegmentBuffer);
	}

	public static void advancedLineOf30(GameTestHelper helper) {
		expectAtLeast(helper, "30 advanced segments",
				deliveredPerTick(helper, 30, i -> ModContent.FLUID_PIPE_ADVANCED.get()),
				Config.fluidPipeAdvancedSegmentBuffer);
	}

	/**
	 * A fork: one branch ends in a FULL tank one hop from the junction, the other in an empty tank two
	 * hops away. The full one takes nothing, so the whole flow must reach the far one — the energy
	 * network's lesson (MOD-252): a direction seeded from every consumer would wall the line off at
	 * the full tank.
	 */
	public static void fullNearTankDoesNotStarveAFarOne(GameTestHelper helper) {
		BlockPos source = new BlockPos(0, 2, 3);
		BlockPos[] trunk = {new BlockPos(1, 2, 3), new BlockPos(2, 2, 3), new BlockPos(3, 2, 3)};
		BlockPos nearPipe = new BlockPos(3, 2, 2);
		BlockPos nearTank = new BlockPos(3, 2, 1);
		BlockPos[] farPipes = {new BlockPos(4, 2, 3), new BlockPos(5, 2, 3)};
		BlockPos farTank = new BlockPos(6, 2, 3);
		helper.setBlock(source, ModContent.FLUID_TANK.get());
		helper.setBlock(nearTank, ModContent.FLUID_TANK.get());
		helper.setBlock(farTank, ModContent.FLUID_TANK.get());
		List<BlockPos> all = new ArrayList<>(List.of(trunk));
		all.add(nearPipe);
		all.addAll(List.of(farPipes));
		List<FluidPipeBlockEntity> pipes = new ArrayList<>();
		for (BlockPos p : all) {
			helper.setBlock(p, ModContent.FLUID_PIPE.get());
		}
		for (BlockPos p : all) {
			if (!(helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof FluidPipeBlockEntity pipe)) {
				helper.fail("MOD-677 no pipe at " + p);
				return;
			}
			pipes.add(pipe);
		}
		FluidTankBlockEntity src = tank(helper, source);
		FluidTankBlockEntity near = tank(helper, nearTank);
		FluidTankBlockEntity far = tank(helper, farTank);
		if (src == null || near == null || far == null) {
			helper.fail("MOD-677 fork tanks missing");
			return;
		}
		near.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
		near.fluidTank.amount = near.fluidTank.capacity;
		pipes.get(0).setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		pipes.get(3).setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);
		pipes.get(5).setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		for (FluidPipeBlockEntity pipe : pipes) {
			pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
		}
		long delivered = 0;
		for (int t = 0; t < WARM_UP + MEASURE; t++) {
			src.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			src.fluidTank.amount = Config.fluidTankCapacity;
			FluidNetworkManager.tickAll(helper.getLevel());
			if (t >= WARM_UP) {
				delivered += far.fluidTank.amount;
			}
			far.fluidTank.amount = 0;
			far.fluidTank.fluid = FluidHolder.EMPTY;
		}
		expectAtLeast(helper, "the far tank behind a full near one", delivered / (double) MEASURE,
				Config.fluidPipeSegmentBuffer);
	}

	/** One basic segment in the middle of ten advanced: the line runs at the basic rate, not below. */
	public static void thinSegmentCapsAThickLine(GameTestHelper helper) {
		double rate = deliveredPerTick(helper, 10,
				i -> i == 5 ? ModContent.FLUID_PIPE.get() : ModContent.FLUID_PIPE_ADVANCED.get());
		double ceiling = Config.fluidPipeSegmentBuffer * 1.05;
		if (rate > ceiling) {
			helper.fail(String.format(java.util.Locale.ROOT,
					"MOD-677 a basic segment in an advanced line let %.1f mB/t through, above its own %d",
					rate, Config.fluidPipeSegmentBuffer));
			return;
		}
		expectAtLeast(helper, "an advanced line with one basic segment", rate, Config.fluidPipeSegmentBuffer);
	}
}

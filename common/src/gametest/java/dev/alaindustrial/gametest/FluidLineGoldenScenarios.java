package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/**
 * World golden master of the fluid network (MOD-715, batch 0): the volume in every pipe segment and both
 * consumer tanks, tick by tick, on a symmetric fork from one source to two hungry tanks — and on the same
 * fork with one tank already full, which the hungry-sink field must route around (MOD-677, MOD-252).
 *
 * <p>The witness for moving {@code FluidNetwork.distanceFromHungrySinks} onto a shared distance field: the
 * field decides which segment hands its contents to which, so a changed field moves these numbers. The fork
 * is symmetric so that segments at equal distance never compete for one neighbour — the sweep breaks such
 * ties on {@code BlockPos.asLong}, which is not translation-invariant, and a golden that depended on it
 * would differ between the two lanes' structure positions (recorded as a finding of MOD-715).
 *
 * <p>The golden {@link FluidLineGolden} is rewritten only by the command in its javadoc (ADR-032).
 */
public final class FluidLineGoldenScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidLineGoldenScenarios::forkMatchesGolden, "mod715_fluid_line_golden"));

		private Roster() {}
	}

	/** System property naming the file the explicit update command writes the golden into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.fluidLineGolden.writeTo";

	private static final int TICKS = 40;
	private static final BlockPos SOURCE = new BlockPos(0, 2, 3);
	private static final BlockPos NORTH_TANK = new BlockPos(3, 2, 0);
	private static final BlockPos SOUTH_TANK = new BlockPos(3, 2, 6);
	/** Trunk, then the north branch, then the south branch. */
	private static final List<BlockPos> PIPES = List.of(
			new BlockPos(1, 2, 3), new BlockPos(2, 2, 3), new BlockPos(3, 2, 3),
			new BlockPos(3, 2, 2), new BlockPos(3, 2, 1),
			new BlockPos(3, 2, 4), new BlockPos(3, 2, 5));

	private FluidLineGoldenScenarios() {
	}

	/**
	 * Both forks, the rig cleared between them, compared with the golden.
	 *
	 * @implements MOD-715-CH03 — the fluid network's per-tick segment volumes on a fork are unchanged
	 */
	public static void forkMatchesGolden(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		lines.addAll(fork(helper, "fork", false));
		lines.addAll(fork(helper, "full", true));
		clear(helper);
		GoldenLines.check(helper, "FluidLineGolden", WRITE_TO_PROPERTY, "*:mod715_fluid_line_golden",
				FluidLineGolden.LINES, lines);
	}

	private static FluidTankBlockEntity tank(GameTestHelper helper, BlockPos pos) {
		if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof FluidTankBlockEntity t) {
			return t;
		}
		throw new IllegalStateException("no fluid tank at " + pos);
	}

	private static FluidPipeBlockEntity pipe(GameTestHelper helper, BlockPos pos) {
		if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof FluidPipeBlockEntity p) {
			return p;
		}
		throw new IllegalStateException("no fluid pipe at " + pos);
	}

	private static List<String> fork(GameTestHelper helper, String name, boolean northFull) {
		clear(helper);
		helper.setBlock(SOURCE, ModContent.FLUID_TANK.get());
		helper.setBlock(NORTH_TANK, ModContent.FLUID_TANK.get());
		helper.setBlock(SOUTH_TANK, ModContent.FLUID_TANK.get());
		for (BlockPos pos : PIPES) {
			helper.setBlock(pos, ModContent.FLUID_PIPE.get());
		}
		FluidTankBlockEntity source = tank(helper, SOURCE);
		FluidTankBlockEntity north = tank(helper, NORTH_TANK);
		FluidTankBlockEntity south = tank(helper, SOUTH_TANK);
		if (northFull) {
			north.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			north.fluidTank.amount = north.fluidTank.getCapacity();
		}
		pipe(helper, PIPES.get(0)).setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		pipe(helper, PIPES.get(4)).setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);
		pipe(helper, PIPES.get(6)).setFaceMode(Direction.SOUTH, PipeFaceMode.INSERT);
		for (BlockPos pos : PIPES) {
			FluidPipeBlockEntity p = pipe(helper, pos);
			p.serverTick(helper.getLevel(), p.getBlockPos(), p.getBlockState()); // registers the segment
		}
		List<String> lines = new ArrayList<>();
		for (int t = 1; t <= TICKS; t++) {
			source.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
			source.fluidTank.amount = Config.fluidTankCapacity;
			FluidNetwork net = FluidNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(PIPES.get(0)));
			boolean awake = net != null && net.isAwake();
			if (awake) {
				net.tick();
			}
			StringBuilder line = new StringBuilder(name).append(" t").append(t < 10 ? "0" : "").append(t).append(" p=");
			for (int i = 0; i < PIPES.size(); i++) {
				line.append(i == 0 ? "" : ",").append(pipe(helper, PIPES.get(i)).fluidBuffer.amount);
			}
			line.append(" k=").append(north.fluidTank.amount).append(',').append(south.fluidTank.amount)
					.append(" a=").append(awake ? 1 : 0);
			lines.add(line.toString());
		}
		return lines;
	}

	private static void clear(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
	}
}

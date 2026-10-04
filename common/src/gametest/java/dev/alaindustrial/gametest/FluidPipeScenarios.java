package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Cross-loader MOD-151 fluid-pipe scenarios: a source tank, two pipe segments, a destination tank.
 * Both tanks are the mod's own, so every transfer crosses the real fluid-port seam on each loader.
 */
public final class FluidPipeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidPipeScenarios::transfersBetweenTanks, "fluid_pipe_transfers_between_tanks")
						.fabricId("FluidPipeGameTest", "mod151TransfersBetweenTanks").ticks(20, 60),
				RosterEntry.of(FluidPipeScenarios::segmentsHoldFluidInTransit,
								"fluid_pipe_segments_hold_fluid_in_transit")
						.fabricId("FluidPipeGameTest", "mod151SegmentsHoldFluidInTransit").ticks(20, 60),
				RosterEntry.of(FluidPipeScenarios::reEnabledPipeLinkRejoinsNetwork,
								"fluid_pipe_reenabled_link_rejoins_network")
						.fabricId("FluidPipeGameTest", "mod151ReEnabledPipeLinkRejoinsNetwork").ticks(20, 40),
				RosterEntry.of(FluidPipeScenarios::segmentRefusesASecondFluid,
								"fluid_pipe_segment_refuses_second_fluid")
						.fabricId("FluidPipeGameTest", "mod151SegmentRefusesASecondFluid").ticks(20, 40),
				RosterEntry.of(FluidPipeScenarios::brokenSegmentLosesItsContentsWithoutDuplicating,
								"fluid_pipe_broken_segment_loses_contents")
						.fabricId("FluidPipeGameTest", "mod151BrokenSegmentLosesItsContents").ticks(20, 60),
				RosterEntry.of(FluidPipeScenarios::advancedLineCarriesTwiceAndABasicSegmentSlowsIt,
								"fluid_pipe_advanced_carries_twice")
						.fabricId("FluidPipeGameTest", "mod675AdvancedLineCarriesTwice").ticks(20, 100),
				RosterEntry.of(FluidPipeScenarios::advancedPipeObeysSurvivesExplosionLikeTheBasicOne,
								"fluid_pipe_advanced_obeys_survives_explosion")
						.fabricId("FluidPipeGameTest", "mod689AdvancedPipeObeysExplosions").ticks(20, 40));

		private Roster() {}
	}

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

	// --- MOD-675: the advanced grade ---

	/**
	 * One tank → three segments → tank line along X at row {@code z}; returns the destination tank, or
	 * {@code null} after failing the test. Each line is its own network, so three rows run side by side.
	 */
	private static FluidTankBlockEntity buildLine(GameTestHelper helper, int z, net.minecraft.world.level.block.Block first,
			net.minecraft.world.level.block.Block middle, net.minecraft.world.level.block.Block last) {
		BlockPos source = new BlockPos(1, 2, z);
		BlockPos[] segments = {new BlockPos(2, 2, z), new BlockPos(3, 2, z), new BlockPos(4, 2, z)};
		BlockPos target = new BlockPos(5, 2, z);
		helper.setBlock(source, ModContent.FLUID_TANK.get());
		helper.setBlock(segments[0], first);
		helper.setBlock(segments[1], middle);
		helper.setBlock(segments[2], last);
		helper.setBlock(target, ModContent.FLUID_TANK.get());
		FluidTankBlockEntity sourceTank = tank(helper, source);
		FluidTankBlockEntity targetTank = tank(helper, target);
		FluidPipeBlockEntity in = pipe(helper, segments[0]);
		FluidPipeBlockEntity out = pipe(helper, segments[2]);
		FluidPipeBlockEntity mid = pipe(helper, segments[1]);
		if (sourceTank == null || targetTank == null || in == null || mid == null || out == null) {
			helper.fail("MOD-675 rig block entity missing on row " + z);
			return null;
		}
		sourceTank.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
		sourceTank.fluidTank.amount = Config.fluidTankCapacity;
		in.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		out.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		for (FluidPipeBlockEntity pipe : new FluidPipeBlockEntity[] {in, mid, out}) {
			pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
		}
		return targetTank;
	}

	/**
	 * MOD-675: an advanced segment holds the advanced knob, an advanced line delivers about twice what
	 * a basic one does, and a single basic segment left in an advanced line holds it at the basic rate
	 * (MOD-677: the line flows, so its thinnest segment decides).
	 */
	public static void advancedLineCarriesTwiceAndABasicSegmentSlowsIt(GameTestHelper helper) {
		net.minecraft.world.level.block.Block basic = ModContent.FLUID_PIPE.get();
		net.minecraft.world.level.block.Block advanced = ModContent.FLUID_PIPE_ADVANCED.get();
		FluidTankBlockEntity basicOut = buildLine(helper, 1, basic, basic, basic);
		FluidTankBlockEntity advancedOut = buildLine(helper, 3, advanced, advanced, advanced);
		FluidTankBlockEntity mixedOut = buildLine(helper, 5, advanced, basic, advanced);
		if (basicOut == null || advancedOut == null || mixedOut == null) {
			return;
		}
		FluidPipeBlockEntity probe = pipe(helper, new BlockPos(3, 2, 3));
		if (probe == null || probe.fluidBuffer.getCapacity() != Config.fluidPipeAdvancedSegmentBuffer) {
			helper.fail("MOD-675 an advanced segment holds "
					+ (probe == null ? "nothing" : probe.fluidBuffer.getCapacity() + " mB")
					+ ", expected Config.fluidPipeAdvancedSegmentBuffer = " + Config.fluidPipeAdvancedSegmentBuffer);
			return;
		}
		FluidPipeBlockEntity basicProbe = pipe(helper, new BlockPos(3, 2, 1));
		if (basicProbe == null || basicProbe.fluidBuffer.getCapacity() != Config.fluidPipeSegmentBuffer) {
			helper.fail("MOD-675 a basic segment no longer holds Config.fluidPipeSegmentBuffer");
			return;
		}
		run(helper, 60);
		long viaBasic = basicOut.fluidTank.amount;
		long viaAdvanced = advancedOut.fluidTank.amount;
		long viaMixed = mixedOut.fluidTank.amount;
		String measured = " (basic " + viaBasic + " mB, advanced " + viaAdvanced + " mB, advanced with one basic "
				+ viaMixed + " mB in 60 ticks)";
		if (viaBasic <= 0) {
			helper.fail("MOD-675 the basic reference line delivered nothing" + measured);
			return;
		}
		if (viaAdvanced * 10 < viaBasic * 18) {
			helper.fail("MOD-675 an advanced line does not carry about twice a basic one" + measured);
			return;
		}
		// MOD-677: the line now flows instead of levelling, so a basic segment caps an advanced line at
		// the basic rate — exactly, not partly (before MOD-677 it was 1450 mB between 923 and 1903).
		if (viaMixed * 10 >= viaAdvanced * 9) {
			helper.fail("MOD-675 a basic segment in an advanced line costs it nothing" + measured);
			return;
		}
		if (viaMixed * 10 < viaBasic * 9 || viaMixed * 10 > viaBasic * 11) {
			helper.fail("MOD-677 a basic segment should hold an advanced line at the basic rate" + measured);
			return;
		}
		helper.succeed();
	}

	/** Seeds rolled per pipe at {@link #MID_RADIUS}; enough that a 1-in-4 survival must lose some drops. */
	private static final int EXPLOSION_SEEDS = 32;
	/** {@code survives_explosion} keeps a drop with chance {@code 1 / radius}: one in four here. */
	private static final float MID_RADIUS = 4.0F;
	/** A radius no drop survives, short of a random draw of exactly zero. */
	private static final float HUGE_RADIUS = 1.0E9F;

	/**
	 * MOD-689: the advanced pipe's loot table obeys {@code survives_explosion} exactly as the basic
	 * pipe's does. Each pipe's own table is rolled with {@link LootContextParams#EXPLOSION_RADIUS} — the
	 * parameter an explosion adds — from the same fixed seed, so the two drops must match seed for seed;
	 * a huge radius must leave both empty. A table whose condition the codec silently skipped (the 26.2
	 * line) drops the advanced pipe every time and fails here.
	 */
	public static void advancedPipeObeysSurvivesExplosionLikeTheBasicOne(GameTestHelper helper) {
		helper.setBlock(PIPE_A, ModContent.FLUID_PIPE.get());
		helper.setBlock(PIPE_B, ModContent.FLUID_PIPE_ADVANCED.get());
		int basicLost = 0;
		for (long seed = 0; seed < EXPLOSION_SEEDS; seed++) {
			int basic = explosionDrops(helper, PIPE_A, MID_RADIUS, seed);
			int advanced = explosionDrops(helper, PIPE_B, MID_RADIUS, seed);
			if (basic < 0 || advanced < 0) {
				return;
			}
			if (basic != advanced) {
				helper.fail("MOD-689 at explosion radius " + MID_RADIUS + ", seed " + seed + " the basic pipe dropped "
						+ basic + " and the advanced one " + advanced + " — the advanced table ignores survives_explosion");
				return;
			}
			if (basic == 0) {
				basicLost++;
			}
		}
		if (basicLost == 0) {
			helper.fail("MOD-689 rig: the basic pipe survived all " + EXPLOSION_SEEDS + " rolls at radius " + MID_RADIUS
					+ " — the explosion radius never reached survives_explosion");
			return;
		}
		int basicHuge = explosionDrops(helper, PIPE_A, HUGE_RADIUS, 1L);
		int advancedHuge = explosionDrops(helper, PIPE_B, HUGE_RADIUS, 1L);
		if (basicHuge != 0 || advancedHuge != 0) {
			helper.fail("MOD-689 at explosion radius " + HUGE_RADIUS + " nothing should survive; the basic pipe dropped "
					+ basicHuge + ", the advanced one " + advancedHuge);
			return;
		}
		helper.succeed();
	}

	/**
	 * How many of its own item the pipe at {@code relative} drops when its loot table is rolled for an
	 * explosion of {@code radius} with a fixed {@code seed}; -1 after failing the test.
	 */
	private static int explosionDrops(GameTestHelper helper, BlockPos relative, float radius, long seed) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(relative);
		BlockState state = level.getBlockState(abs);
		Optional<ResourceKey<LootTable>> key = state.getBlock().getLootTable();
		if (key.isEmpty()) {
			helper.fail("MOD-689 " + state.getBlock() + " has no loot table");
			return -1;
		}
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key.get());
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(abs))
				.withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
				.withParameter(LootContextParams.BLOCK_STATE, state)
				.withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(abs))
				.withParameter(LootContextParams.EXPLOSION_RADIUS, radius)
				.create(LootContextParamSets.BLOCK);
		List<ItemStack> drops = table.getRandomItems(params, RandomSource.create(seed));
		int count = 0;
		for (ItemStack stack : drops) {
			if (stack.is(state.getBlock().asItem())) {
				count += stack.getCount();
			}
		}
		return count;
	}
}

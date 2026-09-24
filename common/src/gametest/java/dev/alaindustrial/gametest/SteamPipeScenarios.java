package dev.alaindustrial.gametest;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.core.item.PipeFaceRender;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Cross-loader MOD-662 scenarios: the two pipe families, and the one-off migration that turns an old
 * world's steam lines — laid in fluid pipe before the steam pipes existed — into steam pipes.
 *
 * <p><b>How an old segment is made here.</b> A pipe is placed, saved, stripped of the one key the steam
 * split added ({@code SteamSplit}) and loaded back through the real {@code loadWithComponents} path —
 * exactly what the chunk loader does with a segment from an older save. Nothing in the pipe is poked
 * past its own persistence.
 *
 * <p><b>Ticks are driven by hand</b>, in an order each scenario picks, so a case that depends on the
 * wave can prove that its middle segments had NO evidence of their own on the first round.
 */
public final class SteamPipeScenarios {
	private SteamPipeScenarios() {
	}

	private static BlockPos abs(GameTestHelper helper, BlockPos relative) {
		return helper.absolutePos(relative);
	}

	private static FluidPipeBlockEntity pipe(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getBlockEntity(abs(helper, relative)) instanceof FluidPipeBlockEntity p ? p : null;
	}

	private static FluidPipeBlockEntity place(GameTestHelper helper, BlockPos relative, Block block) {
		helper.setBlock(relative, block);
		FluidPipeBlockEntity pipe = pipe(helper, relative);
		if (pipe == null) {
			helper.fail("no pipe block entity at " + relative);
			throw new IllegalStateException("unreachable");
		}
		return pipe;
	}

	/**
	 * Place {@code block} and reload it as a segment from a save older than the steam split, holding
	 * {@code amount} mB of {@code fluid} (none when {@code fluid} is null).
	 */
	private static FluidPipeBlockEntity placeLegacy(GameTestHelper helper, BlockPos relative, Block block,
			net.minecraft.world.level.material.Fluid fluid, long amount) {
		FluidPipeBlockEntity pipe = place(helper, relative, block);
		ServerLevel level = helper.getLevel();
		CompoundTag tag = pipe.saveWithoutMetadata(level.registryAccess());
		if (!tag.getBooleanOr("SteamSplit", false)) {
			helper.fail("a pipe placed today did not save the SteamSplit marker: " + tag);
		}
		tag.remove("SteamSplit");
		if (fluid != null) {
			tag.putString("FluidId", BuiltInRegistries.FLUID.getKey(fluid).toString());
			tag.putLong("FluidMb", amount);
		}
		pipe.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		if (!pipe.isLegacy()) {
			helper.fail("a segment loaded without the SteamSplit marker is not treated as legacy at " + relative);
		}
		return pipe;
	}

	private static FuelRodAssemblyBlockEntity column(GameTestHelper helper, BlockPos relative) {
		helper.setBlock(relative, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
		FuelRodAssemblyBlockEntity column = helper.getBlockEntity(relative, FuelRodAssemblyBlockEntity.class);
		if (column == null) {
			helper.fail("reactor column has no block entity at " + relative);
			throw new IllegalStateException("unreachable");
		}
		return column;
	}

	/** One server tick of each listed segment, in the listed order. */
	private static void tickPipes(GameTestHelper helper, List<BlockPos> order) {
		ServerLevel level = helper.getLevel();
		for (BlockPos relative : order) {
			FluidPipeBlockEntity pipe = pipe(helper, relative);
			if (pipe != null) {
				pipe.serverTick(level, pipe.getBlockPos(), pipe.getBlockState());
			}
		}
	}

	private static void runNetworks(GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			FluidNetworkManager.tickAll(helper.getLevel());
		}
	}

	private static boolean is(GameTestHelper helper, BlockPos relative, Block block) {
		return helper.getBlockState(relative).is(block);
	}

	private static void expectBlock(GameTestHelper helper, BlockPos relative, Block block, String why) {
		if (!is(helper, relative, block)) {
			helper.fail(why + ": found " + helper.getBlockState(relative) + " at " + relative);
		}
	}

	// ── the families ────────────────────────────────────────────────────────────────────────────

	/**
	 * A steam pipe laid against a fluid pipe does not join it: neither draws an arm toward the other,
	 * and they sit in two networks. Without the family check both would read "pipe next door" and merge.
	 */
	public static void familiesNeverJoin(GameTestHelper helper) {
		BlockPos steamAt = new BlockPos(1, 2, 1);
		BlockPos fluidAt = new BlockPos(2, 2, 1);
		FluidPipeBlockEntity steam = place(helper, steamAt, ModContent.STEAM_PIPE.get());
		FluidPipeBlockEntity fluid = place(helper, fluidAt, ModContent.FLUID_PIPE.get());
		tickPipes(helper, List.of(steamAt, fluidAt));
		ServerLevel level = helper.getLevel();
		if (FluidPipeBlock.shouldConnectTo(level, steam.getBlockPos(), Direction.EAST)
				|| FluidPipeBlock.shouldConnectTo(level, fluid.getBlockPos(), Direction.WEST)) {
			helper.fail("a steam pipe and a fluid pipe connect to each other");
			return;
		}
		if (FluidPipeBlock.renderAt(helper.getBlockState(steamAt), Direction.EAST) != PipeFaceRender.DISABLED
				|| FluidPipeBlock.renderAt(helper.getBlockState(fluidAt), Direction.WEST) != PipeFaceRender.DISABLED) {
			helper.fail("a steam pipe and a fluid pipe draw arms toward each other");
			return;
		}
		FluidNetwork a = FluidNetworkManager.networkAt(level, steam.getBlockPos());
		FluidNetwork b = FluidNetworkManager.networkAt(level, fluid.getBlockPos());
		if (a == null || b == null || a == b) {
			helper.fail("a steam pipe and a fluid pipe share a network (or one has none): " + a + " / " + b);
			return;
		}
		// The control: two steam pipes side by side DO join, so the refusal above is the family's.
		BlockPos secondSteam = new BlockPos(1, 2, 2);
		place(helper, secondSteam, ModContent.STEAM_PIPE.get());
		tickPipes(helper, List.of(secondSteam));
		if (FluidNetworkManager.networkAt(level, abs(helper, secondSteam)) != FluidNetworkManager.networkAt(level,
				steam.getBlockPos())) {
			helper.fail("two touching steam pipes did not join one network");
			return;
		}
		helper.succeed();
	}

	/** A fluid pipe refuses steam and a steam pipe refuses everything else; each takes its own. */
	public static void eachFamilyRefusesTheOthersFluid(GameTestHelper helper) {
		FluidPipeBlockEntity fluid = place(helper, new BlockPos(1, 2, 1), ModContent.FLUID_PIPE.get());
		FluidPipeBlockEntity steam = place(helper, new BlockPos(3, 2, 1), ModContent.STEAM_PIPE.get());
		FluidPipeBlockEntity reinforcedSteam = place(helper, new BlockPos(5, 2, 1),
				ModContent.REINFORCED_STEAM_PIPE.get());
		FluidHolder steamFluid = FluidHolder.of(ModContent.STEAM.get());
		FluidHolder water = FluidHolder.of(Fluids.WATER);
		if (insert(fluid, steamFluid) != 0) {
			helper.fail("a fluid pipe accepted steam");
			return;
		}
		if (insert(steam, water) != 0 || insert(reinforcedSteam, water) != 0) {
			helper.fail("a steam pipe accepted water");
			return;
		}
		if (insert(fluid, water) <= 0) {
			helper.fail("a fluid pipe refused water");
			return;
		}
		if (insert(steam, steamFluid) <= 0 || insert(reinforcedSteam, steamFluid) <= 0) {
			helper.fail("a steam pipe refused steam");
			return;
		}
		helper.succeed();
	}

	private static long insert(FluidPipeBlockEntity pipe, FluidHolder fluid) {
		long[] inserted = {0};
		EnergyTransactions.get().runCommitting(txn -> inserted[0] = pipe.fluidBuffer.insert(fluid, 10, txn));
		return inserted[0];
	}

	/**
	 * A fluid pipe laid today on a column's top neither connects to it nor migrates, while a steam pipe
	 * on another column's top draws its steam — the control that makes the refusal mean something.
	 */
	public static void aNewFluidPipeOnAColumnTopStaysDisconnected(GameTestHelper helper) {
		FuelRodAssemblyBlockEntity wrong = column(helper, new BlockPos(1, 1, 1));
		FuelRodAssemblyBlockEntity right = column(helper, new BlockPos(3, 1, 1));
		wrong.setTank(false, 1000);
		right.setTank(false, 1000);
		BlockPos fluidAt = new BlockPos(1, 2, 1);
		BlockPos steamAt = new BlockPos(3, 2, 1);
		FluidPipeBlockEntity fluid = place(helper, fluidAt, ModContent.FLUID_PIPE.get());
		FluidPipeBlockEntity steam = place(helper, steamAt, ModContent.STEAM_PIPE.get());
		if (fluid.isLegacy() || steam.isLegacy()) {
			helper.fail("a pipe placed today counts as legacy");
			return;
		}
		for (int i = 0; i < 5; i++) {
			tickPipes(helper, List.of(fluidAt, steamAt));
			runNetworks(helper, 1);
		}
		expectBlock(helper, fluidAt, ModContent.FLUID_PIPE.get(), "a fluid pipe placed today migrated");
		if (FluidPipeBlock.renderAt(helper.getBlockState(fluidAt), Direction.DOWN) != PipeFaceRender.DISABLED) {
			helper.fail("a fluid pipe draws an arm onto a column's steam top");
			return;
		}
		if (fluid.fluidBuffer.amount != 0 || wrong.steamTank.amount != 1000) {
			helper.fail("a fluid pipe drew steam off a column: pipe " + fluid.fluidBuffer.amount
					+ " mB, column " + wrong.steamTank.amount + " mB");
			return;
		}
		if (FluidPipeBlock.renderAt(helper.getBlockState(steamAt), Direction.DOWN) == PipeFaceRender.DISABLED) {
			helper.fail("a steam pipe draws no arm onto a column's steam top");
			return;
		}
		if (right.steamTank.amount >= 1000 || !steam.fluidBuffer.fluid.is(ModContent.STEAM.get())) {
			helper.fail("a steam pipe on a column's top drew no steam: column " + right.steamTank.amount
					+ " mB, pipe " + steam.fluidBuffer.amount + " mB of " + steam.fluidBuffer.fluid);
			return;
		}
		helper.succeed();
	}

	// ── the migration ───────────────────────────────────────────────────────────────────────────

	/**
	 * Case 1: an old fluid pipe holding steam becomes a steam pipe — the SAME block entity, its wrenched
	 * face and its steam kept, and from now on saved with the marker.
	 */
	public static void migrationTurnsASteamFilledPipeIntoASteamPipe(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 2, 2);
		helper.setBlock(at, ModContent.FLUID_PIPE.get());
		FluidPipeBlockEntity before = pipe(helper, at);
		if (before == null) {
			helper.fail("no pipe block entity");
			return;
		}
		before.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		FluidPipeBlockEntity pipe = placeLegacyKeepingModes(helper, at, ModContent.STEAM.get(), 30);
		tickPipes(helper, List.of(at));
		expectBlock(helper, at, ModContent.STEAM_PIPE.get(), "an old fluid pipe holding steam did not migrate");
		if (pipe(helper, at) != pipe) {
			helper.fail("the migration replaced the block entity instead of keeping it");
			return;
		}
		if (pipe.faceMode(Direction.EAST) != PipeFaceMode.INSERT) {
			helper.fail("the migration lost the wrenched face: east is " + pipe.faceMode(Direction.EAST));
			return;
		}
		if (pipe.fluidBuffer.amount != 30 || !pipe.fluidBuffer.fluid.is(ModContent.STEAM.get())) {
			helper.fail("the migration lost the steam: " + pipe.fluidBuffer.amount + " mB of " + pipe.fluidBuffer.fluid);
			return;
		}
		if (!pipe.saveWithoutMetadata(helper.getLevel().registryAccess()).getBooleanOr("SteamSplit", false)) {
			helper.fail("a migrated segment does not save the SteamSplit marker");
			return;
		}
		helper.succeed();
	}

	/** {@link #placeLegacy} for a pipe already standing, so a face set on it survives into the save. */
	private static FluidPipeBlockEntity placeLegacyKeepingModes(GameTestHelper helper, BlockPos relative,
			net.minecraft.world.level.material.Fluid fluid, long amount) {
		FluidPipeBlockEntity pipe = pipe(helper, relative);
		ServerLevel level = helper.getLevel();
		CompoundTag tag = pipe.saveWithoutMetadata(level.registryAccess());
		tag.remove("SteamSplit");
		tag.putString("FluidId", BuiltInRegistries.FLUID.getKey(fluid).toString());
		tag.putLong("FluidMb", amount);
		pipe.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		if (!pipe.isLegacy()) {
			helper.fail("a segment loaded without the SteamSplit marker is not treated as legacy");
		}
		return pipe;
	}

	/** Case 2: an old, empty fluid pipe standing on a column's top becomes a steam pipe. */
	public static void migrationTurnsAnEmptyPipeOnAColumnTop(GameTestHelper helper) {
		column(helper, new BlockPos(1, 1, 1));
		BlockPos at = new BlockPos(1, 2, 1);
		placeLegacy(helper, at, ModContent.FLUID_PIPE.get(), null, 0);
		tickPipes(helper, List.of(at));
		expectBlock(helper, at, ModContent.STEAM_PIPE.get(), "an empty old pipe on a column's top did not migrate");
		helper.succeed();
	}

	/**
	 * Case 3: an empty old line column → pipe → pipe → inlet → pipe → pipe → nozzle converts by the wave.
	 * The two middle pipes touch only a pipe and the two-way inlet, so they have no evidence of their own;
	 * ticked BEFORE their neighbours on the first round they must stay, and convert on the second.
	 */
	public static void migrationWaveConvertsAWholeEmptyLine(GameTestHelper helper) {
		column(helper, new BlockPos(1, 1, 1));
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 2, 1);
		helper.setBlock(new BlockPos(3, 2, 1), ModContent.REACTOR_PORT.get().defaultBlockState());
		BlockPos c = new BlockPos(4, 2, 1);
		BlockPos d = new BlockPos(5, 2, 1);
		helper.setBlock(new BlockPos(5, 3, 1), ModContent.STEAM_NOZZLE.get().defaultBlockState()
				.setValue(SteamNozzleBlock.FACING, Direction.UP));
		for (BlockPos at : List.of(a, b, c, d)) {
			placeLegacy(helper, at, ModContent.FLUID_PIPE.get(), null, 0);
		}
		List<BlockPos> order = List.of(b, c, a, d);
		tickPipes(helper, order);
		expectBlock(helper, a, ModContent.STEAM_PIPE.get(), "the pipe on the column's top did not migrate");
		expectBlock(helper, d, ModContent.STEAM_PIPE.get(), "the pipe under the nozzle did not migrate");
		expectBlock(helper, b, ModContent.FLUID_PIPE.get(),
				"a middle pipe migrated with no evidence of its own — the rule is too loose");
		expectBlock(helper, c, ModContent.FLUID_PIPE.get(),
				"a middle pipe migrated with no evidence of its own — the rule is too loose");
		tickPipes(helper, order);
		expectBlock(helper, b, ModContent.STEAM_PIPE.get(), "the wave did not reach the pipe after the column's");
		expectBlock(helper, c, ModContent.STEAM_PIPE.get(), "the wave did not reach the pipe before the nozzle's");
		helper.succeed();
	}

	/**
	 * Case 4: water lines stay. An empty old pipe on a column's SIDE is a water feed, and an old pipe
	 * holding water stays even on a column's top — what a segment holds outranks where it stands.
	 */
	public static void migrationLeavesWaterLinesAlone(GameTestHelper helper) {
		column(helper, new BlockPos(1, 1, 1));
		column(helper, new BlockPos(4, 1, 1));
		BlockPos side = new BlockPos(2, 1, 1);
		BlockPos wetTop = new BlockPos(4, 2, 1);
		placeLegacy(helper, side, ModContent.FLUID_PIPE.get(), null, 0);
		placeLegacy(helper, wetTop, ModContent.FLUID_PIPE.get(), Fluids.WATER, 40);
		for (int i = 0; i < 3; i++) {
			tickPipes(helper, List.of(side, wetTop));
		}
		expectBlock(helper, side, ModContent.FLUID_PIPE.get(), "an empty feed pipe on a column's side migrated");
		expectBlock(helper, wetTop, ModContent.FLUID_PIPE.get(), "an old pipe holding water migrated");
		helper.succeed();
	}

	/** Case 5: the grade is kept — an old reinforced fluid pipe holding steam becomes a reinforced steam pipe. */
	public static void migrationKeepsTheReinforcedGrade(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 2, 2);
		placeLegacy(helper, at, ModContent.REINFORCED_FLUID_PIPE.get(), ModContent.STEAM.get(), 20);
		tickPipes(helper, List.of(at));
		expectBlock(helper, at, ModContent.REINFORCED_STEAM_PIPE.get(),
				"an old reinforced fluid pipe holding steam did not become a reinforced steam pipe");
		helper.succeed();
	}

	/**
	 * Case 6: ambiguous stays. An empty old pipe on one column's top AND against another column's side
	 * is both a steam outlet and a water inlet; guessing would break one of the two lines, so it stays.
	 */
	public static void migrationLeavesAnAmbiguousPipe(GameTestHelper helper) {
		column(helper, new BlockPos(1, 1, 1));
		column(helper, new BlockPos(2, 2, 1));
		BlockPos at = new BlockPos(1, 2, 1);
		placeLegacy(helper, at, ModContent.FLUID_PIPE.get(), null, 0);
		for (int i = 0; i < 3; i++) {
			tickPipes(helper, List.of(at));
		}
		expectBlock(helper, at, ModContent.FLUID_PIPE.get(),
				"a pipe touching both a column's top and a column's side migrated");
		helper.succeed();
	}
}

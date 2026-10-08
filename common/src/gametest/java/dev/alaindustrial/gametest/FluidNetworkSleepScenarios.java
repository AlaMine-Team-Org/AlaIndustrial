package dev.alaindustrial.gametest;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * A fluid network that has nothing to move sleeps, and wakes for every change that would let it move
 * something (MOD-734, ADR-047). There is no periodic real tick behind the sleep — by the owner's decision
 * an error in the probe has to turn one of these red rather than be hidden every N ticks.
 *
 * <p>Each scenario drives its own network directly ({@link FluidNetwork#isAwake}, {@link FluidNetwork#tick})
 * inside one game tick, as the tick-cost benchmark does, so no other rig in the shared level is involved and
 * no world tick runs between two checks. Every write the scenarios make is one a player or a mod can make:
 * through the fluid API, or straight into a tank's fields (which no event announces).
 */
public final class FluidNetworkSleepScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidNetworkSleepScenarios::fullLineSleeps, "fluid_sleep_full_line_sleeps"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenTheSinkIsDrainedThroughTheApi,
						"fluid_sleep_wakes_on_sink_drained"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenTheSinkIsEmptiedByAFieldWrite,
						"fluid_sleep_wakes_on_sink_field_write"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenAMiddleSegmentIsWritten,
						"fluid_sleep_wakes_on_middle_segment_write"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenAFaceModeChanges,
						"fluid_sleep_wakes_on_face_mode_change"),
				RosterEntry.of(FluidNetworkSleepScenarios::emptyLineWithoutSourceSleepsAndWakesOnInsert,
						"fluid_sleep_empty_line_without_source"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenAnEmptySourceIsRefilled,
						"fluid_sleep_wakes_on_empty_source_refilled"),
				RosterEntry.of(FluidNetworkSleepScenarios::fillFollowsTheBufferEveryTick,
						"fluid_sleep_settles_fill_every_tick"),
				RosterEntry.of(FluidNetworkSleepScenarios::settleMarksTheChunkUnsaved,
						"fluid_sleep_settle_marks_chunk_unsaved"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenALiveSegmentIsReloaded,
						"fluid_sleep_wakes_on_live_reload"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenAOneWayNeighbourIsPlaced,
						"fluid_sleep_wakes_on_new_one_way_neighbour"),
				RosterEntry.of(FluidNetworkSleepScenarios::wakesWhenTheConsumerBehindAnEmptySinkSegmentIsDrained,
						"fluid_sleep_wakes_on_hungry_sink_behind_empty_segment"));

		private Roster() {}
	}

	static final int SEGMENTS = 5;
	private static final BlockPos SOURCE = new BlockPos(1, 2, 2);
	private static final BlockPos TARGET = new BlockPos(2 + SEGMENTS, 2, 2);
	/** Ticks a five-segment line may take to settle — far above what it needs, far below "never". */
	private static final int SETTLE_LIMIT = 2_000;
	/** How many asleep ticks a sleeping network is watched for before it is believed. */
	private static final int WATCH = 40;

	private FluidNetworkSleepScenarios() {
	}

	/** The rig: optional source tank, five basic segments wired EXTRACT → INSERT, target tank. */
	record Rig(FluidNetwork network, FluidTankBlockEntity source, FluidTankBlockEntity target,
			List<FluidPipeBlockEntity> pipes) {
	}

	/** Fail the test with {@code message}; {@link GameTestHelper#fail(String)} throws, the return satisfies javac. */
	private static RuntimeException failure(GameTestHelper helper, String message) {
		helper.fail(message);
		return new IllegalStateException(message);
	}

	static BlockPos pipePos(int i) {
		return new BlockPos(2 + i, 2, 2);
	}

	static Rig build(GameTestHelper helper, boolean withSource, long sourceMb, long targetMb) {
		return build(helper, withSource, sourceMb, targetMb, new int[] {0, 1, 2, 3, 4});
	}

	/** {@link #build} registering the segments in {@code registerOrder}, which fixes the network's node order. */
	static Rig build(GameTestHelper helper, boolean withSource, long sourceMb, long targetMb, int[] registerOrder) {
		if (withSource) {
			helper.setBlock(SOURCE, ModContent.FLUID_TANK.get());
		}
		for (int i = 0; i < SEGMENTS; i++) {
			helper.setBlock(pipePos(i), ModContent.FLUID_PIPE.get());
		}
		helper.setBlock(TARGET, ModContent.FLUID_TANK.get());
		FluidTankBlockEntity source = withSource ? helper.getBlockEntity(SOURCE, FluidTankBlockEntity.class) : null;
		FluidTankBlockEntity target = helper.getBlockEntity(TARGET, FluidTankBlockEntity.class);
		fill(source, sourceMb);
		fill(target, targetMb);
		List<FluidPipeBlockEntity> pipes = new ArrayList<>();
		for (int i = 0; i < SEGMENTS; i++) {
			pipes.add(helper.getBlockEntity(pipePos(i), FluidPipeBlockEntity.class));
		}
		if (withSource) {
			pipes.get(0).setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		}
		pipes.get(SEGMENTS - 1).setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		for (int i : registerOrder) {
			FluidPipeBlockEntity pipe = pipes.get(i);
			pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
		}
		FluidNetwork network = FluidNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(pipePos(0)));
		if (network == null || network.size() < SEGMENTS) {
			throw failure(helper, "the sleep rig is not one fluid network of at least "
					+ SEGMENTS + " segments: " + network);
		}
		return new Rig(network, source, target, pipes);
	}

	static void fill(FluidTankBlockEntity tank, long mb) {
		if (tank == null) {
			return;
		}
		tank.fluidTank.fluid = mb > 0 ? FluidHolder.of(Fluids.WATER) : FluidHolder.EMPTY;
		tank.fluidTank.amount = mb;
	}

	/** Tick while awake; the number of ticks it took to fall asleep, or fail when it never does. */
	private static int runUntilAsleep(GameTestHelper helper, Rig rig, String what) {
		for (int t = 0; t < SETTLE_LIMIT; t++) {
			if (!rig.network().isAwake()) {
				return t;
			}
			rig.network().tick();
		}
		throw failure(helper, what + ": the network never fell asleep in " + SETTLE_LIMIT + " ticks");
	}

	/** Everything the network could move, as one comparable row: the two tanks and every segment. */
	private static long[] snapshot(Rig rig) {
		long[] row = new long[SEGMENTS + 2];
		row[0] = rig.source() == null ? -1 : rig.source().fluidTank.amount;
		for (int i = 0; i < SEGMENTS; i++) {
			row[1 + i] = rig.pipes().get(i).fluidBuffer.amount;
		}
		row[SEGMENTS + 1] = rig.target().fluidTank.amount;
		return row;
	}

	/** The network stays asleep for {@link #WATCH} probes and nothing it owns changes meanwhile. */
	private static void assertStaysAsleep(GameTestHelper helper, Rig rig, String what) {
		long[] before = snapshot(rig);
		for (int t = 0; t < WATCH; t++) {
			if (rig.network().isAwake()) {
				throw failure(helper, what + ": an asleep network woke up on its own at probe " + t
						+ " with nothing changed — " + Arrays.toString(snapshot(rig)));
			}
		}
		if (!Arrays.equals(before, snapshot(rig))) {
			throw failure(helper, what + ": the probe moved fluid — " + Arrays.toString(before)
					+ " became " + Arrays.toString(snapshot(rig)));
		}
	}

	private static void assertAwake(GameTestHelper helper, Rig rig, String what) {
		if (!rig.network().isAwake()) {
			throw failure(helper, what + ": the network stayed asleep — " + Arrays.toString(snapshot(rig)));
		}
	}

	/** A sleeping line with both tanks full: source, five full segments, full target. */
	private static Rig asleepFullLine(GameTestHelper helper) {
		Rig rig = build(helper, true, 0, 0);
		fill(rig.source(), rig.source().fluidTank.capacity);
		fill(rig.target(), rig.target().fluidTank.capacity);
		runUntilAsleep(helper, rig, "full line");
		return rig;
	}

	/**
	 * Full tanks at both ends: once the segments have filled from the source, nothing can move, and the
	 * network stops ticking — and the probe that keeps checking does not move anything itself.
	 *
	 * @implements MOD-734-SL01 — a line with full tanks at both ends sleeps
	 */
	public static void fullLineSleeps(GameTestHelper helper) {
		Rig rig = asleepFullLine(helper);
		if (rig.pipes().get(0).fluidBuffer.amount <= 0) {
			helper.fail("full line: the segments never filled from the source — the rig slept before it ran");
			return;
		}
		assertStaysAsleep(helper, rig, "full line");
		helper.succeed();
	}

	/**
	 * The consumer is drained through the fluid API — a committed transaction on the tank, no block event:
	 * the probe asks the sink what the tick would and wakes the line, which refills the tank.
	 *
	 * @implements MOD-734-SL02 — wakes when the sink is drained through the API
	 */
	public static void wakesWhenTheSinkIsDrainedThroughTheApi(GameTestHelper helper) {
		Rig rig = asleepFullLine(helper);
		long drained = 1_000;
		EnergyTransactions.get().runCommitting(txn ->
				rig.target().fluidTank.extract(FluidHolder.of(Fluids.WATER), drained, txn));
		long after = rig.target().fluidTank.amount;
		assertAwake(helper, rig, "sink drained through the API");
		runUntilAsleep(helper, rig, "sink drained through the API");
		if (rig.target().fluidTank.amount <= after) {
			helper.fail("sink drained through the API: the line woke but delivered nothing ("
					+ rig.target().fluidTank.amount + " mB)");
			return;
		}
		helper.succeed();
	}

	/**
	 * The consumer is emptied by writing its fields directly — what a machine does to its own tank, and
	 * what no event announces. Only asking the sink can see it.
	 *
	 * @implements MOD-734-SL03 — wakes when the sink is emptied by a field write
	 */
	public static void wakesWhenTheSinkIsEmptiedByAFieldWrite(GameTestHelper helper) {
		Rig rig = asleepFullLine(helper);
		fill(rig.target(), 0);
		assertAwake(helper, rig, "sink emptied by a field write");
		rig.network().tick();
		if (rig.target().fluidTank.amount <= 0) {
			helper.fail("sink emptied by a field write: the woken line delivered nothing in its first tick");
			return;
		}
		helper.succeed();
	}

	/**
	 * A middle segment is drawn from through its fluid port — a pump or another mod reaching into the line.
	 * Neither end changed, so only the segment's own commit can wake the network.
	 *
	 * @implements MOD-734-SL04 — wakes when a middle segment is written from outside
	 */
	public static void wakesWhenAMiddleSegmentIsWritten(GameTestHelper helper) {
		Rig rig = asleepFullLine(helper);
		FluidPort middle = FluidLookup.get().find(helper.getLevel(), helper.absolutePos(pipePos(SEGMENTS / 2)),
				Direction.UP);
		if (middle == null) {
			helper.fail("middle segment write: the segment publishes no fluid port upward");
			return;
		}
		long[] taken = {0};
		EnergyTransactions.get().runCommitting(txn -> taken[0] = middle.extract(FluidHolder.of(Fluids.WATER), 20, txn));
		if (taken[0] <= 0) {
			helper.fail("middle segment write: nothing could be drawn from the full middle segment");
			return;
		}
		assertAwake(helper, rig, "middle segment write");
		helper.succeed();
	}

	/**
	 * The player switches the line's insert face off: the endpoints change, so the network refreshes on its
	 * next tick — as it always did, through the dirty flag.
	 *
	 * @implements MOD-734-SL05 — wakes when a face mode changes
	 */
	public static void wakesWhenAFaceModeChanges(GameTestHelper helper) {
		Rig rig = asleepFullLine(helper);
		rig.pipes().get(SEGMENTS - 1).setFaceMode(Direction.EAST, PipeFaceMode.DISABLED);
		FluidNetwork now = FluidNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(pipePos(0)));
		if (now == null || !now.isAwake()) {
			helper.fail("face mode change: the network stayed asleep after the insert face was switched off");
			return;
		}
		helper.succeed();
	}

	/**
	 * Five empty segments and a consumer, no source: nothing can ever move, so the line sleeps at once — and
	 * fluid poured into its middle wakes it and reaches the consumer.
	 *
	 * @implements MOD-734-SL06 — an empty line without a source sleeps, and wakes on an insert
	 */
	public static void emptyLineWithoutSourceSleepsAndWakesOnInsert(GameTestHelper helper) {
		Rig rig = build(helper, false, 0, 0);
		int ticks = runUntilAsleep(helper, rig, "empty line");
		if (ticks > 2) {
			helper.fail("empty line: a line with nothing to move took " + ticks + " ticks to fall asleep");
			return;
		}
		assertStaysAsleep(helper, rig, "empty line");
		FluidPort middle = FluidLookup.get().find(helper.getLevel(), helper.absolutePos(pipePos(SEGMENTS / 2)),
				Direction.UP);
		long[] poured = {0};
		EnergyTransactions.get().runCommitting(txn -> poured[0] = middle.insert(FluidHolder.of(Fluids.WATER), 30, txn));
		if (poured[0] != 30) {
			helper.fail("empty line: the middle segment took " + poured[0] + " of 30 mB");
			return;
		}
		assertAwake(helper, rig, "insert into an empty line");
		runUntilAsleep(helper, rig, "insert into an empty line");
		if (rig.target().fluidTank.amount != 30) {
			helper.fail("empty line: the consumer received " + rig.target().fluidTank.amount
					+ " of the 30 mB poured in");
			return;
		}
		helper.succeed();
	}

	/**
	 * Empty source, empty line, empty consumer: asleep. Filling the source's fields — a pump does exactly
	 * that to its own tank — wakes the line through the source probe.
	 *
	 * @implements MOD-734-SL07 — wakes when an empty source is refilled
	 */
	public static void wakesWhenAnEmptySourceIsRefilled(GameTestHelper helper) {
		Rig rig = build(helper, true, 0, 0);
		runUntilAsleep(helper, rig, "empty source");
		assertStaysAsleep(helper, rig, "empty source");
		fill(rig.source(), 1_000);
		assertAwake(helper, rig, "refilled source");
		runUntilAsleep(helper, rig, "refilled source");
		if (rig.target().fluidTank.amount <= 0) {
			helper.fail("refilled source: the woken line delivered nothing to the consumer");
			return;
		}
		helper.succeed();
	}

	/** Whether the drawn core of every segment matches its buffer; the first mismatch, or {@code null}. */
	private static String fillMismatch(GameTestHelper helper, Rig rig) {
		for (int i = 0; i < SEGMENTS; i++) {
			BlockState state = helper.getLevel().getBlockState(helper.absolutePos(pipePos(i)));
			long amount = rig.pipes().get(i).fluidBuffer.amount;
			if (state.getValue(FluidPipeBlock.FILLED) != amount > 0) {
				return "segment " + i + " holds " + amount + " mB but draws FILLED="
						+ state.getValue(FluidPipeBlock.FILLED);
			}
		}
		return null;
	}

	/**
	 * A running line — every segment empties into the next and refills within one tick — and then the same
	 * line running dry: after every network tick each segment's drawn core matches its buffer (MOD-734). The
	 * commits inside a tick only mark the segments; the settle at the end of the tick is what flips the core,
	 * so a tick that forgot to settle leaves a full line drawn empty.
	 *
	 * @implements MOD-734-SL08 — the drawn fill follows the buffer after every network tick
	 */
	public static void fillFollowsTheBufferEveryTick(GameTestHelper helper) {
		Rig rig = build(helper, true, 600, 0);
		boolean sawFluid = false;
		for (int t = 0; t < SETTLE_LIMIT && rig.network().isAwake(); t++) {
			rig.network().tick();
			String mismatch = fillMismatch(helper, rig);
			if (mismatch != null) {
				helper.fail("fill after network tick " + t + ": " + mismatch);
				return;
			}
			sawFluid |= Arrays.stream(snapshot(rig), 1, SEGMENTS + 1).anyMatch(a -> a > 0);
		}
		if (!sawFluid || rig.target().fluidTank.amount != 600) {
			helper.fail("fill: the line never ran dry into the consumer (" + rig.target().fluidTank.amount
					+ " of 600 mB delivered, fluid seen in the line: " + sawFluid + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * A line with no endpoint that moves anything — a full consumer, no source — levelling a slug of fluid:
	 * every move is a commit on two segments and nothing else. After each network tick the chunk of every
	 * segment whose buffer changed is marked unsaved, or the save after it would roll the line back
	 * (MOD-734: inside a tick only the settle marks the chunk).
	 *
	 * @implements MOD-734-SL09 — the settle marks the chunk of every changed segment unsaved
	 */
	public static void settleMarksTheChunkUnsaved(GameTestHelper helper) {
		Rig rig = build(helper, false, 0, 0);
		fill(rig.target(), rig.target().fluidTank.capacity);
		rig.pipes().get(0).fluidBuffer.fluid = FluidHolder.of(Fluids.WATER);
		rig.pipes().get(0).fluidBuffer.amount = 40;
		int changedTicks = 0;
		for (int t = 0; t < SETTLE_LIMIT && rig.network().isAwake(); t++) {
			long[] before = snapshot(rig);
			for (int i = 0; i < SEGMENTS; i++) {
				chunkOf(helper, i).tryMarkSaved();
			}
			rig.network().tick();
			long[] after = snapshot(rig);
			for (int i = 0; i < SEGMENTS; i++) {
				if (before[1 + i] != after[1 + i]) {
					changedTicks++;
					if (!chunkOf(helper, i).isUnsaved()) {
						helper.fail("chunk after network tick " + t + ": segment " + i + " went " + before[1 + i]
								+ " -> " + after[1 + i] + " mB, but its chunk is not marked unsaved");
						return;
					}
				}
			}
		}
		if (changedTicks == 0) {
			helper.fail("chunk: the slug never moved — the rig checked nothing");
			return;
		}
		helper.succeed();
	}

	private static ChunkAccess chunkOf(GameTestHelper helper, int segment) {
		return helper.getLevel().getChunk(helper.absolutePos(pipePos(segment)));
	}

	/**
	 * A live segment's saved data is loaded over it — {@code /data merge}, a structure, a schematic tool —
	 * putting fluid into an asleep line without any commit. The segment's next tick has to settle that and
	 * wake its network (MOD-734 review): the load wrote the buffer and the drawn core, nothing else saw it.
	 *
	 * @implements MOD-734-SL10 — a live reload of a segment wakes the network and redraws the fill
	 */
	public static void wakesWhenALiveSegmentIsReloaded(GameTestHelper helper) {
		Rig rig = build(helper, false, 0, 0);
		runUntilAsleep(helper, rig, "live reload");
		FluidPipeBlockEntity middle = rig.pipes().get(SEGMENTS / 2);
		ServerLevel level = helper.getLevel();
		CompoundTag tag = middle.saveWithoutMetadata(level.registryAccess());
		tag.putLong("FluidMb", 30);
		tag.putString("FluidId", "minecraft:water");
		middle.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		if (middle.fluidBuffer.amount != 30) {
			helper.fail("live reload: the reloaded segment holds " + middle.fluidBuffer.amount + " mB, not 30");
			return;
		}
		middle.serverTick(level, middle.getBlockPos(), middle.getBlockState());
		assertAwake(helper, rig, "live reload");
		if (!level.getBlockState(middle.getBlockPos()).getValue(FluidPipeBlock.FILLED)) {
			helper.fail("live reload: the segment holds 30 mB and still draws empty after its tick");
			return;
		}
		runUntilAsleep(helper, rig, "live reload");
		if (rig.target().fluidTank.amount != 30) {
			helper.fail("live reload: the consumer received " + rig.target().fluidTank.amount + " of 30 mB");
			return;
		}
		helper.succeed();
	}

	/**
	 * An asleep steam line, and a steam nozzle placed against a segment's untouched (NEUTRAL) face: the nozzle
	 * only takes, so the face becomes a sink without the wrench. Nothing in the line was written — only the
	 * neighbour changed, which reaches the network as the dirty flag ({@code onNeighbourChanged}).
	 *
	 * @implements MOD-734-SL11 — a new one-way neighbour wakes the network through the dirty flag
	 */
	public static void wakesWhenAOneWayNeighbourIsPlaced(GameTestHelper helper) {
		List<FluidPipeBlockEntity> pipes = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			helper.setBlock(pipePos(i), ModContent.STEAM_PIPE.get());
			pipes.add(helper.getBlockEntity(pipePos(i), FluidPipeBlockEntity.class));
		}
		for (FluidPipeBlockEntity pipe : pipes) {
			pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
		}
		FluidHolder steam = FluidHolder.of(ModContent.STEAM.get());
		long[] poured = {0};
		EnergyTransactions.get().runCommitting(txn -> poured[0] = pipes.get(0).fluidBuffer.insert(steam, 30, txn));
		FluidNetwork network = FluidNetworkManager.networkAt(helper.getLevel(), helper.absolutePos(pipePos(0)));
		if (poured[0] != 30 || network == null || network.size() != 3) {
			helper.fail("one-way neighbour: rig not built (poured " + poured[0] + ", network " + network + ")");
			return;
		}
		for (int t = 0; t < SETTLE_LIMIT && network.isAwake(); t++) {
			network.tick();
		}
		if (network.isAwake()) {
			helper.fail("one-way neighbour: the steam line never fell asleep");
			return;
		}
		BlockPos nozzle = pipePos(3);
		helper.setBlock(nozzle, ModContent.STEAM_NOZZLE.get().defaultBlockState()
				.setValue(SteamNozzleBlock.FACING, Direction.EAST));
		if (!network.isAwake()) {
			helper.fail("one-way neighbour: a nozzle placed against the line left the asleep network asleep");
			return;
		}
		for (int t = 0; t < SETTLE_LIMIT && network.isAwake(); t++) {
			network.tick();
		}
		long nozzleHolds = helper.getBlockEntity(nozzle, SteamNozzleBlockEntity.class).tank.amount;
		if (nozzleHolds <= 0) {
			helper.fail("one-way neighbour: the woken line sent no steam into the nozzle");
			return;
		}
		helper.succeed();
	}

	/**
	 * The probe's third question, alone: fluid stands in the line but not in the sink segment (one mB that
	 * levelling will not split), and the consumer is full. Draining the consumer changes nothing the sink or
	 * source questions see — the sink segment is empty — only who is hungry. The network must wake, or the
	 * slug waits for ever beside a consumer that wants it.
	 *
	 * @implements MOD-734-SL12 — wakes when the consumer behind an empty sink segment turns hungry
	 */
	public static void wakesWhenTheConsumerBehindAnEmptySinkSegmentIsDrained(GameTestHelper helper) {
		Rig rig = build(helper, false, 0, 0);
		fill(rig.target(), rig.target().fluidTank.capacity);
		FluidPipeBlockEntity beside = rig.pipes().get(SEGMENTS - 2);
		beside.fluidBuffer.fluid = FluidHolder.of(Fluids.WATER);
		beside.fluidBuffer.amount = 1;
		runUntilAsleep(helper, rig, "hungry sink");
		if (rig.pipes().get(SEGMENTS - 1).fluidBuffer.amount != 0 || beside.fluidBuffer.amount != 1) {
			helper.fail("hungry sink: the rig did not settle as planned — " + Arrays.toString(snapshot(rig)));
			return;
		}
		fill(rig.target(), 0);
		assertAwake(helper, rig, "hungry sink");
		runUntilAsleep(helper, rig, "hungry sink");
		if (rig.target().fluidTank.amount != 1) {
			helper.fail("hungry sink: the consumer received " + rig.target().fluidTank.amount + " of 1 mB");
			return;
		}
		helper.succeed();
	}
}

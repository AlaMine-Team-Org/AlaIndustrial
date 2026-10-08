package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.gametest.FluidNetworkSleepScenarios.Rig;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * What the fluid network caches between refreshes — the topology, the endpoints, the flow field — stays true
 * to the world (MOD-734, second review): a refresh that throws half-way, a dead-end branch removed without a
 * split, a segment removed in the middle of a tick, and face modes loaded over a live segment.
 *
 * <p>Like {@link FluidNetworkSleepScenarios}, each scenario drives its own network directly inside one game
 * tick. Two of them swap the installed {@link FluidLookup} for a wrapper to stand in for a foreign port;
 * the wrapper is restored in a {@code finally}, and nothing else in the level runs while it is installed.
 */
public final class FluidNetworkTopologyScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidNetworkTopologyScenarios::aRefreshThatThrowsLeavesTheLineWhole,
						"fluid_topology_throwing_refresh_leaves_line_whole"),
				RosterEntry.of(FluidNetworkTopologyScenarios::removingADeadEndBranchKeepsTheFlow,
						"fluid_topology_dead_end_branch_removed"),
				RosterEntry.of(FluidNetworkTopologyScenarios::aSegmentRemovedMidTickIsNotUsed,
						"fluid_topology_segment_removed_mid_tick"),
				RosterEntry.of(FluidNetworkTopologyScenarios::faceModesLoadedOverALiveSegmentRejoinIt,
						"fluid_topology_live_reload_rejoins_faces"),
				RosterEntry.of(FluidNetworkTopologyScenarios::theFieldFollowsWhichConsumersAreHungry,
						"fluid_topology_field_follows_hungry_sinks"));

		private Roster() {}
	}

	/** A basic line carries 50 mB a tick; over this many steady ticks the consumer must see nearly all of it. */
	private static final int STEADY_TICKS = 40;
	private static final long STEADY_MIN = STEADY_TICKS * 50L - 50L;

	private FluidNetworkTopologyScenarios() {
	}

	/** Tick {@code ticks} times; the consumer's gain. Any exception fails the test with what it was. */
	private static long run(GameTestHelper helper, Rig rig, int ticks, String what) {
		long before = rig.target().fluidTank.amount;
		for (int t = 0; t < ticks; t++) {
			try {
				if (rig.network().isAwake()) {
					rig.network().tick();
				}
			} catch (RuntimeException failure) {
				helper.fail(what + ": tick " + t + " threw " + failure);
			}
		}
		return rig.target().fluidTank.amount - before;
	}

	private static Rig runningLine(GameTestHelper helper) {
		return runningLine(helper, new int[] {0, 1, 2, 3, 4});
	}

	private static Rig runningLine(GameTestHelper helper, int[] registerOrder) {
		Rig rig = FluidNetworkSleepScenarios.build(helper, true, 0, 0, registerOrder);
		FluidNetworkSleepScenarios.fill(rig.source(), rig.source().fluidTank.capacity);
		run(helper, rig, 20, "warm-up");
		return rig;
	}

	/** A {@link FluidLookup} that answers like {@code real} except where {@code override} says otherwise. */
	private interface LookupOverride {
		FluidPort find(FluidLookup real, net.minecraft.world.level.Level level, BlockPos pos, Direction side);
	}

	private static void withLookup(LookupOverride override, Runnable body) {
		FluidLookup real = FluidLookup.get();
		FluidLookup.install((level, pos, side) -> override.find(real, level, pos, side));
		try {
			body.run();
		} finally {
			FluidLookup.install(real);
		}
	}

	/**
	 * A foreign port that throws while the network refreshes (MOD-186 says it may): the refresh must leave
	 * the previous layout whole and keep the network dirty, so the next tick reads again and the line runs on
	 * at full rate — not with fresh positions beside stale neighbour slots, and not with the flag cleared.
	 *
	 * @implements MOD-734-TP01 — a refresh that throws leaves the line consistent and retries
	 */
	public static void aRefreshThatThrowsLeavesTheLineWhole(GameTestHelper helper) {
		// The middle segment registered last joins the two halves, which leaves a segment that still has to
		// push (index 3 of 0..4) last in node order. The trap sits above the LAST segment in that order: every
		// block entity is read before the throw, so nothing but the refresh itself can notice it did not finish.
		Rig rig = runningLine(helper, new int[] {0, 1, 3, 4, 2});
		List<BlockPos> order = new ArrayList<>(rig.network().pipes());
		BlockPos trap = order.get(order.size() - 1).above();
		boolean[] thrown = {false};
		rig.network().markDirty();
		withLookup((real, level, pos, side) -> {
			if (pos.equals(trap)) {
				throw new IllegalStateException("MOD-734 test: a foreign capability failed");
			}
			return real.find(level, pos, side);
		}, () -> {
			try {
				rig.network().tick();
			} catch (IllegalStateException expected) {
				thrown[0] = true;
			}
		});
		if (!thrown[0]) {
			helper.fail("throwing refresh: the refresh never asked the trapped position — the rig tested nothing");
			return;
		}
		long delivered = run(helper, rig, STEADY_TICKS, "after a throwing refresh");
		if (delivered < STEADY_MIN) {
			helper.fail("throwing refresh: the line delivered " + delivered + " mB in " + STEADY_TICKS
					+ " ticks afterwards, a running line carries " + STEADY_MIN + "+");
			return;
		}
		helper.succeed();
	}

	/**
	 * A dead-end branch on a running line is broken: the network does not split, the hungry consumer is the
	 * same, but every segment after the branch moves to a new index. The flow field built for the old indices
	 * must not outlive the refresh, or the sweep walks positions that are no longer there.
	 *
	 * @implements MOD-734-TP02 — the flow field is rebuilt when the topology changes under the same seeds
	 */
	public static void removingADeadEndBranchKeepsTheFlow(GameTestHelper helper) {
		BlockPos spur = FluidNetworkSleepScenarios.pipePos(2).above();
		helper.setBlock(spur, ModContent.FLUID_PIPE.get());
		FluidPipeBlockEntity branch = helper.getBlockEntity(spur, FluidPipeBlockEntity.class);
		// Registered before the line, so the branch takes an early index and its removal shifts the rest.
		branch.serverTick(helper.getLevel(), branch.getBlockPos(), branch.getBlockState());
		Rig rig = runningLine(helper);
		if (rig.network().size() != FluidNetworkSleepScenarios.SEGMENTS + 1) {
			helper.fail("dead-end branch: the branch is not part of the line (" + rig.network().size() + " segments)");
			return;
		}
		helper.setBlock(spur, Blocks.AIR);
		FluidNetwork after = FluidNetworkManager.networkAt(helper.getLevel(),
				helper.absolutePos(FluidNetworkSleepScenarios.pipePos(0)));
		if (after != rig.network() || after.size() != FluidNetworkSleepScenarios.SEGMENTS) {
			helper.fail("dead-end branch: removing the branch split or replaced the network");
			return;
		}
		long delivered = run(helper, rig, STEADY_TICKS, "after the branch was removed");
		if (delivered < STEADY_MIN) {
			helper.fail("dead-end branch: the line delivered " + delivered + " mB in " + STEADY_TICKS
					+ " ticks after the branch went, a running line carries " + STEADY_MIN + "+");
			return;
		}
		helper.succeed();
	}

	/**
	 * The consumer's port breaks a middle segment while the network is serving it — a foreign mod reacting to
	 * an insert. The rest of the tick still holds the segment's cached block entity; it must read as missing
	 * (removed), never take or give fluid after it left the world.
	 *
	 * @implements MOD-734-TP03 — a segment removed during a tick is not used by the rest of that tick
	 */
	public static void aSegmentRemovedMidTickIsNotUsed(GameTestHelper helper) {
		Rig rig = runningLine(helper);
		FluidPipeBlockEntity middle = rig.pipes().get(2);
		ServerLevel level = helper.getLevel();
		long[] atRemoval = {-1};
		withLookup((real, lvl, pos, side) -> {
			FluidPort port = real.find(lvl, pos, side);
			if (port == null || !pos.equals(rig.target().getBlockPos())) {
				return port;
			}
			return new RemovingPort(port, () -> {
				if (atRemoval[0] < 0) {
					atRemoval[0] = middle.fluidBuffer.amount;
					level.removeBlock(middle.getBlockPos(), false);
				}
			});
		}, () -> {
			try {
				rig.network().tick();
			} catch (RuntimeException failure) {
				helper.fail("segment removed mid-tick: the tick threw " + failure);
			}
		});
		if (atRemoval[0] < 0 || !middle.isRemoved()) {
			helper.fail("segment removed mid-tick: the consumer was never asked — the rig tested nothing");
			return;
		}
		// The segment past the gap has only the removed one upstream of it: once that is gone, nothing can
		// reach it this tick. A tick that still used the removed block entity emptied it into this segment
		// and refilled it from the other side — the removed buffer ends where it began, this one does not.
		long pastTheGap = rig.pipes().get(3).fluidBuffer.amount;
		if (pastTheGap != 0 || middle.fluidBuffer.amount != atRemoval[0]) {
			helper.fail("segment removed mid-tick: the rest of the tick still moved fluid through the removed "
					+ "segment (" + atRemoval[0] + " -> " + middle.fluidBuffer.amount + " mB, " + pastTheGap
					+ " mB arrived past the gap)");
			return;
		}
		run(helper, rig, 5, "after a mid-tick removal");
		helper.succeed();
	}

	/** A port that runs {@code onInsert} before every insert and otherwise is {@code real}. */
	private record RemovingPort(FluidPort real, Runnable onInsert) implements FluidPort {
		@Override
		public long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
			onInsert.run();
			return real.insert(fluid, maxAmount, txn);
		}

		@Override
		public long extract(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
			return real.extract(fluid, maxAmount, txn);
		}

		@Override
		public FluidHolder fluid() {
			return real.fluid();
		}

		@Override
		public long getAmount() {
			return real.getAmount();
		}

		@Override
		public long getCapacity() {
			return real.getCapacity();
		}

		@Override
		public boolean supportsInsertion() {
			return real.supportsInsertion();
		}

		@Override
		public boolean supportsExtraction() {
			return real.supportsExtraction();
		}
	}

	/**
	 * Saved data with the insert face switched off is loaded over the line's last, live segment — what
	 * {@code /data merge} does. No wrench was used, so nothing announced the new face; the segment's next tick
	 * must re-join it as a wrench would, or the network keeps serving the consumer through a face that is off.
	 *
	 * @implements MOD-734-TP04 — face modes loaded over a live segment re-join it
	 */
	public static void faceModesLoadedOverALiveSegmentRejoinIt(GameTestHelper helper) {
		Rig rig = runningLine(helper);
		FluidPipeBlockEntity last = rig.pipes().get(FluidNetworkSleepScenarios.SEGMENTS - 1);
		ServerLevel level = helper.getLevel();
		CompoundTag tag = last.saveWithoutMetadata(level.registryAccess());
		int shift = Direction.EAST.ordinal() * 2;
		int modes = tag.getIntOr("FaceModes", 0);
		tag.putInt("FaceModes", (modes & ~(3 << shift)) | (PipeFaceMode.DISABLED.ordinal() << shift));
		last.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		if (last.faceMode(Direction.EAST) != PipeFaceMode.DISABLED) {
			helper.fail("live face reload: the load did not switch the insert face off");
			return;
		}
		last.serverTick(level, last.getBlockPos(), last.getBlockState());
		FluidNetworkSleepScenarios.fill(rig.target(), 0);
		long delivered = run(helper, rig, STEADY_TICKS, "after a live face reload");
		if (delivered != 0) {
			helper.fail("live face reload: the consumer received " + delivered
					+ " mB through a face the loaded data switched off");
			return;
		}
		helper.succeed();
	}

	/**
	 * Two consumers on one line, the topology never changes: first only the one at the line's middle is
	 * hungry, then only the one at its end. The flow field is cached between ticks (MOD-734), so it is right
	 * only if it is flooded again when the hungry set changes — a field still pointing at the middle consumer
	 * pulls the fluid away from the end one, which then starves.
	 *
	 * @implements MOD-734-TP05 — the cached flow field follows the set of hungry consumers
	 */
	public static void theFieldFollowsWhichConsumersAreHungry(GameTestHelper helper) {
		Rig rig = FluidNetworkSleepScenarios.build(helper, true, 0, 0);
		BlockPos middleTank = FluidNetworkSleepScenarios.pipePos(2).north();
		helper.setBlock(middleTank, ModContent.FLUID_TANK.get());
		FluidTankBlockEntity middle = helper.getBlockEntity(middleTank, FluidTankBlockEntity.class);
		rig.pipes().get(2).setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);
		FluidNetworkSleepScenarios.fill(rig.source(), rig.source().fluidTank.capacity);
		FluidNetworkSleepScenarios.fill(rig.target(), rig.target().fluidTank.capacity);
		FluidNetworkSleepScenarios.fill(middle, 0);
		long toMiddle = middle.fluidTank.amount;
		run(helper, rig, 30, "middle consumer hungry");
		if (middle.fluidTank.amount <= toMiddle) {
			helper.fail("hungry sinks: the middle consumer received nothing while it was the only hungry one");
			return;
		}
		FluidNetworkSleepScenarios.fill(middle, middle.fluidTank.capacity);
		FluidNetworkSleepScenarios.fill(rig.target(), 0);
		// The segments past the middle drained toward it while it was hungry; they refill before the end
		// consumer sees a full rate, so the bar is a running line less one fill of the line.
		long bar = STEADY_MIN - FluidNetworkSleepScenarios.SEGMENTS * 50L;
		long delivered = run(helper, rig, STEADY_TICKS, "end consumer hungry");
		if (delivered < bar) {
			helper.fail("hungry sinks: once only the end consumer was hungry it received " + delivered + " mB in "
					+ STEADY_TICKS + " ticks; a running line carries " + bar + "+ even after refilling");
			return;
		}
		helper.succeed();
	}
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.core.structure.ReactorLog;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * A wall broken on a running room, tick by tick (MOD-713, batch 0): what the controller does on the very tick its
 * scan finds the hole.
 *
 * <p><b>Why this needs its own scenarios.</b> On that tick the room scan runs three steps in an order that matters:
 * the racks are silenced (drone flag off, voice latch emptied), then the room's lists are forgotten, then the bare
 * sweep runs. Silenced after the lists are gone, the racks would keep the flag for good — nothing else ever clears a
 * rack the controller no longer lists, and a bare controller paints nothing. And the latch emptied there is what makes
 * a room with no bare reach log its stop on the breach tick instead of forty ticks later. Both are invisible at the
 * end of a long run, where {@code breachingAWallDropsTheReactorIntoBareMode} looks; these scenarios look at the tick.
 *
 * <p><b>What they cannot see.</b> The spin-down edge ({@code wasVoiced} is deliberately not cleared by the silencing)
 * is heard only as a sound played to clients; nothing on the server side records it, so no scenario here pins it. The
 * comment on the silencing step in the controller is the guard for that half.
 */
public final class ReactorBreachOrderScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReactorBreachOrderScenarios::aBreachWithNothingInReachStopsOnTheBreachTick,
								"reactor_breach_out_of_reach_stops_on_the_breach_tick")
						.ticks(100),
				RosterEntry.of(ReactorBreachOrderScenarios::aBreachWithinReachFallsBareWithSilentRacks,
								"reactor_breach_within_reach_falls_bare_with_silent_racks")
						.ticks(100));

		private Roster() {}
	}

	private ReactorBreachOrderScenarios() {}

	/** The rack: interior floor, in the middle. */
	private static final BlockPos RACK = new BlockPos(2, 1, 2);

	/** A ceiling block away from the controller and from the rack's floor: the hole. */
	private static final BlockPos HOLE = new BlockPos(2, 4, 2);

	/** Ticks a scan is waited for at most: two sweeps. */
	private static final int SCAN_WAIT = 100;

	/**
	 * With no bare reach (search radius zero) a breach silences the rack, logs the broken wall and then the stop —
	 * all on the breach tick — and nothing more afterwards.
	 *
	 * @implements MOD-713 — a breach silences the racks before the room lists are dropped, and the emptied voice
	 *     latch puts the stop line on the breach tick.
	 */
	public static void aBreachWithNothingInReachStopsOnTheBreachTick(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("reactorBareSearchRadius", 0)) {
			ReactorControllerBlockEntity brain = runningRoom(helper);
			int before = brain.logEntries().size();
			if (!breach(helper, brain)) {
				return;
			}
			expectSilent(helper, "on the breach tick");
			if (brain.isBare()) {
				helper.fail("a controller with a bare reach of zero fell into bare mode");
			}
			expectTail(helper, brain, before, "on the breach tick",
					ReactorLog.Kind.ROOM_UNSEALED, ReactorLog.Kind.REACTION_STOPPED);
			driveCooled(helper, brain, 60);
			expectSilent(helper, "sixty ticks after the breach");
			expectTail(helper, brain, before, "sixty ticks after the breach",
					ReactorLog.Kind.ROOM_UNSEALED, ReactorLog.Kind.REACTION_STOPPED);
			helper.succeed();
		}
	}

	/**
	 * With the rack in bare reach a breach still silences it on the breach tick, the controller falls bare and keeps
	 * reacting, and the rack stays silent: a bare controller paints the drone onto nothing.
	 *
	 * @implements MOD-713 — a breach into bare mode leaves the racks of the old room silent, and logs no stop.
	 */
	public static void aBreachWithinReachFallsBareWithSilentRacks(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("reactorBareSearchRadius", 4)
				.set("reactorBareMeltRadius", 0)) {
			ReactorControllerBlockEntity brain = runningRoom(helper);
			int before = brain.logEntries().size();
			if (!breach(helper, brain)) {
				return;
			}
			expectSilent(helper, "on the breach tick");
			if (!brain.isBare()) {
				helper.fail("a breached room with its rack in reach did not fall into bare mode");
			}
			expectTail(helper, brain, before, "on the breach tick",
					ReactorLog.Kind.ROOM_UNSEALED, ReactorLog.Kind.BARE_ENTERED);
			driveCooled(helper, brain, 60);
			expectSilent(helper, "sixty ticks into bare mode");
			if (brain.getLastOutput() <= 0) {
				helper.fail("the bare controller stopped producing after the breach");
			}
			expectTail(helper, brain, before, "sixty ticks into bare mode",
					ReactorLog.Kind.ROOM_UNSEALED, ReactorLog.Kind.BARE_ENTERED);
			helper.succeed();
		}
	}

	/** A sealed room with one rack of four rods, powered and run until the rack wears the drone flag. */
	private static ReactorControllerBlockEntity runningRoom(GameTestHelper helper) {
		ReactorRig.buildRoom(helper);
		ReactorControllerBlockEntity brain = ReactorRig.controller(helper);
		ReactorRig.fuel(ReactorRig.placeColumnAt(helper, RACK));
		helper.setBlock(ReactorRig.CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		driveCooled(helper, brain, 120);
		if (brain.getStatus() != ReactorRoomStatus.FORMED || brain.getLastOutput() <= 0) {
			helper.fail("the room did not seal and run, so a breach is not under test: " + brain.getStatus()
					+ ", output " + brain.getLastOutput());
		}
		if (!helper.getBlockState(RACK).getValue(FuelRodAssemblyBlock.ACTIVE)) {
			helper.fail("the running room's rack does not wear the drone flag, so its silencing proves nothing");
		}
		return brain;
	}

	/** Opens the ceiling and ticks one at a time until the scan reports it; false (and failed) if it never does. */
	private static boolean breach(GameTestHelper helper, ReactorControllerBlockEntity brain) {
		helper.setBlock(HOLE, Blocks.AIR.defaultBlockState());
		for (int i = 0; i < SCAN_WAIT; i++) {
			driveCooled(helper, brain, 1);
			if (brain.getStatus() != ReactorRoomStatus.FORMED) {
				return true;
			}
		}
		helper.fail("the scan never noticed the hole in the ceiling within " + SCAN_WAIT + " ticks");
		return false;
	}

	/**
	 * Ticks under load with the rack's water topped up and its steam carried off, as a plumbed loop keeps it: the
	 * room stays cool, so no overheat line can land among the lines a breach writes.
	 */
	private static void driveCooled(GameTestHelper helper, ReactorControllerBlockEntity brain, int ticks) {
		FuelRodAssemblyBlockEntity rack = helper.getBlockEntity(RACK, FuelRodAssemblyBlockEntity.class);
		for (int i = 0; i < ticks; i++) {
			if (rack != null) {
				rack.setTank(true, rack.waterTank.capacity);
				rack.setTank(false, 0);
			}
			ReactorRig.driveUnderLoad(helper, brain, 1);
		}
	}

	private static void expectSilent(GameTestHelper helper, String when) {
		if (helper.getBlockState(RACK).getValue(FuelRodAssemblyBlock.ACTIVE)) {
			helper.fail("the rack of the breached room still wears the drone flag " + when
					+ " — nothing will ever take it off");
		}
	}

	/** The lines the log gained since it held {@code before} entries are exactly {@code kinds}, in order. */
	private static void expectTail(GameTestHelper helper, ReactorControllerBlockEntity brain, int before, String when,
			ReactorLog.Kind... kinds) {
		List<ReactorLog.Kind> tail = brain.logEntries().subList(before, brain.logEntries().size()).stream()
				.map(ReactorLog.Entry::kind).toList();
		if (!tail.equals(List.of(kinds))) {
			helper.fail("the breach logged " + tail + " " + when + ", expected " + List.of(kinds));
		}
	}
}

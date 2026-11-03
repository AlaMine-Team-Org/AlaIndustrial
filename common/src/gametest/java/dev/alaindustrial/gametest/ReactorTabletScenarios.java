package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.ReactorRig.BARE_CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.buildBareRig;
import static dev.alaindustrial.gametest.ReactorRig.buildRoom;
import static dev.alaindustrial.gametest.ReactorRig.controller;
import static dev.alaindustrial.gametest.ReactorRig.drive;
import static dev.alaindustrial.gametest.ReactorRig.driveAt;

import dev.alaindustrial.block.ReactorAntennaBlock;
import dev.alaindustrial.block.ReactorAntennaMastBlock;
import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.block.entity.reactor.ReactorTabletLink;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.menu.ReactorTabletMenu;
import dev.alaindustrial.item.misc.ReactorTabletItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The reactor antenna and the tier-1 tablet (MOD-941): the antenna is a shell block that keeps the room sealed, the
 * controller links and powers it, and the tablet hears the reactor only through a powered antenna in range — every
 * other case is a named state, never a silent blank. Each scenario asserts both sides of its rule, so it can fail.
 */
final class ReactorTabletScenarios {

	private ReactorTabletScenarios() {}

	public static final class Roster {
		private Roster() {}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReactorTabletScenarios::anAntennaInTheWallKeepsTheRoomAndLinksTheTablet,
						"reactor_tablet_hears_room_through_antenna").ticks(100),
				RosterEntry.of(ReactorTabletScenarios::noAntennaMeansNoLink, "reactor_tablet_no_antenna").ticks(100),
				RosterEntry.of(ReactorTabletScenarios::anUnpoweredAntennaIsToppedUpByTheReactor,
						"reactor_tablet_antenna_fed_by_reactor").ticks(100),
				RosterEntry.of(ReactorTabletScenarios::aBareReactorLinksATouchingAntenna,
						"reactor_tablet_bare_reactor_antenna").ticks(100),
				RosterEntry.of(ReactorTabletScenarios::theTabletViewRefusesTheThrottle,
						"reactor_tablet_view_refuses_throttle").ticks(100),
				RosterEntry.of(ReactorTabletScenarios::anAntennaWithItsBoomBuiltOverIsSilent,
						"reactor_tablet_boom_built_over").ticks(100),
				RosterEntry.of(ReactorTabletScenarios::aCalmReactorClearsTheTabletsAlarmMark,
						"reactor_tablet_alarm_mark_cleared").ticks(100),
				RosterEntry.of(ReactorTabletScenarios::aCableJoinsTheBoomIntoTheAntenna,
						"reactor_tablet_cable_sleeved_into_antenna").ticks(100));
	}

	/** East wall, middle: a shell cell of the smallest room that is neither an edge nor the controller. */
	private static final BlockPos WALL_ANTENNA = new BlockPos(ReactorRig.SHELL_MAX, 2, 2);

	/**
	 * An antenna set into the wall is shell: the room still forms. The controller links it, and a tablet in range
	 * hears the reactor; out of range, discharged or unbound it says so instead.
	 */
	public static void anAntennaInTheWallKeepsTheRoomAndLinksTheTablet(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		ReactorControllerBlockEntity brain = controller(helper);
		ReactorAntennaBlockEntity antenna = antenna(helper, WALL_ANTENNA);
		fill(antenna);
		drive(helper, brain, 2);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("a room with an antenna in its wall reads " + brain.getStatus() + ", expected FORMED");
		}
		if (!brain.servedAntennas().contains(helper.absolutePos(WALL_ANTENNA))) {
			helper.fail("the controller did not serve the antenna in its wall");
		}
		if (!(helper.getBlockState(WALL_ANTENNA.east()).getBlock() instanceof ReactorAntennaMastBlock)) {
			helper.fail("the antenna grew no boom in the free cell in front of it");
		}
		Player player = playerAt(helper, WALL_ANTENNA.east(4));
		ItemStack tablet = boundTablet(helper, CONTROLLER);
		expect(helper, player, tablet, ReactorTabletLink.State.LINKED, "in range");

		player.setPos(player.getX() + ReactorConfig.reactorTabletRange + 10, player.getY(), player.getZ());
		expect(helper, player, tablet, ReactorTabletLink.State.OUT_OF_RANGE, "past the range");

		ItemEnergy.set(tablet, 0);
		expect(helper, player, tablet, ReactorTabletLink.State.DISCHARGED, "with a flat tablet");
		expect(helper, player, new ItemStack(ModContent.REACTOR_TABLET.get()), ReactorTabletLink.State.UNBOUND,
				"unbound");
		helper.succeed();
	}

	/** Without an antenna the reactor is out of reach, even standing next to it. */
	public static void noAntennaMeansNoLink(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		drive(helper, brain, 2);
		if (!brain.servedAntennas().isEmpty()) {
			helper.fail("a room without an antenna served " + brain.servedAntennas());
		}
		expect(helper, playerAt(helper, CONTROLLER.west(2)), boundTablet(helper, CONTROLLER),
				ReactorTabletLink.State.NO_ANTENNA, "with no antenna");
		helper.succeed();
	}

	/**
	 * No cable and an empty reactor: the antenna is silent. Charge in the reactor's buffer tops it up and the tablet
	 * hears the reactor again — the reactor powers its own antenna when nothing else does.
	 */
	public static void anUnpoweredAntennaIsToppedUpByTheReactor(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		ReactorControllerBlockEntity brain = controller(helper);
		ReactorAntennaBlockEntity antenna = antenna(helper, WALL_ANTENNA);
		brain.getEnergyStorage().setAmountUntracked(0);
		drive(helper, brain, 2);
		Player player = playerAt(helper, WALL_ANTENNA.east(4));
		ItemStack tablet = boundTablet(helper, CONTROLLER);
		expect(helper, player, tablet, ReactorTabletLink.State.ANTENNA_UNPOWERED, "with no power anywhere (antenna "
				+ antenna.getEnergyStorage().getAmount() + " EU, reactor " + brain.getEnergyStorage().getAmount()
				+ " EU)");

		long before = 10_000L;
		brain.getEnergyStorage().setAmountUntracked(before);
		// A tick later, as in the game: the upkeep is billed per elapsed tick, and a second call within the same
		// tick keeps that tick's verdict.
		helper.runAfterDelay(1, () -> {
			drive(helper, brain, 1);
			if (brain.getEnergyStorage().getAmount() >= before) {
				helper.fail("the reactor's buffer paid nothing towards its antenna");
			}
			if (antenna.getEnergyStorage().getAmount() <= 0) {
				helper.fail("the antenna holds nothing after the reactor topped it up");
			}
			expect(helper, player, tablet, ReactorTabletLink.State.LINKED, "on the reactor's own charge");
			helper.succeed();
		});
	}

	/** A bare reactor has no wall: an antenna touching the controller is the one it serves. */
	public static void aBareReactorLinksATouchingAntenna(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = buildBareRig(helper);
		BlockPos touching = BARE_CONTROLLER.north();
		helper.setBlock(touching.north(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		placeAntenna(helper, touching, Direction.NORTH);
		fill(antenna(helper, touching));
		driveAt(helper, brain, BARE_CONTROLLER, 2);
		if (!brain.servedAntennas().contains(helper.absolutePos(touching))) {
			helper.fail("a bare controller did not serve the antenna touching it");
		}
		expect(helper, playerAt(helper, touching.north(3).above(2)), boundTablet(helper, BARE_CONTROLLER),
				ReactorTabletLink.State.LINKED, "at a bare reactor");
		helper.succeed();
	}

	/**
	 * The tablet's panel only shows: the server refuses a depth request from it and the throttle stays where it
	 * was, while the same request on the controller's own panel is accepted.
	 */
	public static void theTabletViewRefusesTheThrottle(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		ReactorControllerBlockEntity brain = controller(helper);
		fill(antenna(helper, WALL_ANTENNA));
		drive(helper, brain, 2);
		Player player = playerAt(helper, WALL_ANTENNA.east(4));
		player.setItemInHand(InteractionHand.MAIN_HAND, boundTablet(helper, CONTROLLER));

		ReactorTabletMenu view = ReactorTabletMenu.open(0, player.getInventory());
		if (view.linkState() != ReactorTabletLink.State.LINKED) {
			helper.fail("the tablet's panel opened as " + view.linkState() + ", expected LINKED");
		}
		if (!view.isViewOnly()) {
			helper.fail("the tablet's panel is not view-only");
		}
		int clientWidth = new ReactorTabletMenu(0, player.getInventory()).getDataChannelCount();
		if (view.getDataChannelCount() != clientWidth || clientWidth != ReactorTabletMenu.DATA_COUNT) {
			helper.fail("the tablet's panel syncs " + view.getDataChannelCount() + " channels, its client stub "
					+ clientWidth + ", expected " + ReactorTabletMenu.DATA_COUNT);
		}
		int depthBefore = brain.getDataAccess().get(
				dev.alaindustrial.block.entity.reactor.ReactorChannels.DEPTH_PERCENT.ordinal());
		int asked = depthBefore == 50 ? 25 : 50;
		if (view.clickMenuButton(player, asked)) {
			helper.fail("the tablet's panel accepted a depth request");
		}
		int depthAfter = brain.getDataAccess().get(
				dev.alaindustrial.block.entity.reactor.ReactorChannels.DEPTH_PERCENT.ordinal());
		if (depthAfter != depthBefore) {
			helper.fail("the depth moved from " + depthBefore + " to " + depthAfter + " through a tablet");
		}
		ReactorControllerMenu own = new ReactorControllerMenu(0, player.getInventory(), brain,
				net.minecraft.world.inventory.ContainerLevelAccess.NULL);
		if (!own.clickMenuButton(player, asked)) {
			helper.fail("the controller's own panel refused the same depth request, the control this test"
					+ " compares with");
		}
		helper.succeed();
	}

	/**
	 * The boom is the antenna's aerial: with its cell built over the antenna stays silent, and once the cell is
	 * cleared the boom grows back and the tablet hears the reactor again.
	 */
	public static void anAntennaWithItsBoomBuiltOverIsSilent(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		helper.setBlock(WALL_ANTENNA.east(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		ReactorControllerBlockEntity brain = controller(helper);
		fill(antenna(helper, WALL_ANTENNA));
		drive(helper, brain, 2);
		Player player = playerAt(helper, WALL_ANTENNA.east(4));
		ItemStack tablet = boundTablet(helper, CONTROLLER);
		expect(helper, player, tablet, ReactorTabletLink.State.ANTENNA_UNPOWERED, "with its boom built over");
		helper.setBlock(WALL_ANTENNA.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		helper.runAfterDelay(1, () -> {
			drive(helper, brain, 1);
			if (!(helper.getBlockState(WALL_ANTENNA.east()).getBlock() instanceof ReactorAntennaMastBlock)) {
				helper.fail("the boom did not grow back into its cleared cell");
			}
			expect(helper, player, tablet, ReactorTabletLink.State.LINKED, "once its cell is clear");
			helper.succeed();
		});
	}

	/**
	 * The red lamps on the item follow the reactor the tablet hears: a tablet still marked from an earlier alarm loses
	 * the mark at a calm reactor, and an unbound one never gains it. Which states count as an alarm is the panel's own
	 * rule, held in L1 ({@code ReactorConsoleTest}).
	 */
	public static void aCalmReactorClearsTheTabletsAlarmMark(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		ReactorControllerBlockEntity brain = controller(helper);
		fill(antenna(helper, WALL_ANTENNA));
		drive(helper, brain, 2);
		Player player = playerAt(helper, WALL_ANTENNA.east(4));
		ItemStack tablet = boundTablet(helper, CONTROLLER);
		expect(helper, player, tablet, ReactorTabletLink.State.LINKED, "before the alarm check");
		tablet.set(ModDataComponents.REACTOR_TABLET_ALARM.get(), net.minecraft.util.Unit.INSTANCE);
		ReactorTabletItem.refreshAlarm(tablet, player);
		if (tablet.has(ModDataComponents.REACTOR_TABLET_ALARM.get())) {
			helper.fail("a tablet at a calm reactor kept its red alarm mark");
		}
		ItemStack unbound = new ItemStack(ModContent.REACTOR_TABLET.get());
		ReactorTabletItem.refreshAlarm(unbound, player);
		if (unbound.has(ModDataComponents.REACTOR_TABLET_ALARM.get())) {
			helper.fail("an unbound tablet was marked as in alarm");
		}
		helper.succeed();
	}

	/**
	 * A cable beside the boom goes straight into the antenna (owner's playtest 2026-11-03): the boom lends the
	 * antenna's port through every face of its cell but the antenna's and the one over the Yagi elements, and a real
	 * cable laid under a wall antenna's boom gets its sleeve — the boom's flag toward it set, the cable's arm toward
	 * the boom drawn.
	 */
	public static void aCableJoinsTheBoomIntoTheAntenna(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		ReactorControllerBlockEntity brain = controller(helper);
		drive(helper, brain, 2);
		BlockPos mast = WALL_ANTENNA.east();
		BlockState boom = helper.getBlockState(mast);
		if (!(boom.getBlock() instanceof ReactorAntennaMastBlock mastBlock)) {
			helper.fail("no boom in front of the antenna at " + mast);
			return;
		}
		BlockPos abs = helper.absolutePos(mast);
		for (Direction side : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST}) {
			if (mastBlock.energyHost(boom, abs, side) == null) {
				helper.fail("the boom refused a cable on its " + side + " face");
			}
		}
		if (mastBlock.energyHost(boom, abs, Direction.UP) != null) {
			helper.fail("the boom lent its port over the Yagi elements, where a sleeve would run into them");
		}
		if (mastBlock.energyHost(boom, abs, Direction.WEST) != null) {
			helper.fail("the boom lent its port through the antenna's own face");
		}
		BlockPos under = mast.below();
		helper.setBlock(under, ModContent.COPPER_CABLE.get());
		helper.runAfterDelay(5, () -> {
			BlockState sleeved = helper.getBlockState(mast);
			BlockState cable = helper.getBlockState(under);
			if (!cable.getValue(net.minecraft.world.level.block.PipeBlock.UP)) {
				helper.fail("the cable under the boom drew no arm up into it");
			}
			if (!sleeved.getValue(net.minecraft.world.level.block.PipeBlock.DOWN)) {
				helper.fail("the boom drew no sleeve toward the cable under it");
			}
			if (sleeved.getValue(net.minecraft.world.level.block.PipeBlock.NORTH)) {
				helper.fail("the boom drew a sleeve toward a face with no cable");
			}
			helper.succeed();
		});
	}

	// ── helpers ──

	/** An antenna whose boom points {@code facing}, set directly as a structure template would. */
	private static void placeAntenna(GameTestHelper helper, BlockPos at, Direction facing) {
		helper.setBlock(at, ModContent.REACTOR_ANTENNA.get().defaultBlockState()
				.setValue(ReactorAntennaBlock.FACING, facing));
	}

	private static ReactorAntennaBlockEntity antenna(GameTestHelper helper, BlockPos at) {
		ReactorAntennaBlockEntity antenna = helper.getBlockEntity(at, ReactorAntennaBlockEntity.class);
		if (antenna == null) {
			helper.fail("reactor antenna has no block entity at " + at);
			throw new IllegalStateException("unreachable");
		}
		return antenna;
	}

	private static void fill(ReactorAntennaBlockEntity antenna) {
		antenna.getEnergyStorage().setAmountUntracked(antenna.getEnergyStorage().getCapacity());
	}

	/** A charged tablet bound to the controller at {@code controllerAt}. */
	private static ItemStack boundTablet(GameTestHelper helper, BlockPos controllerAt) {
		ItemStack tablet = new ItemStack(ModContent.REACTOR_TABLET.get());
		tablet.set(ModDataComponents.REACTOR_TABLET_LINK.get(),
				GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(controllerAt)));
		ItemEnergy.set(tablet, ItemEnergy.capacity(tablet));
		return tablet;
	}

	private static Player playerAt(GameTestHelper helper, BlockPos at) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static void expect(GameTestHelper helper, Player player, ItemStack tablet,
			ReactorTabletLink.State expected, String when) {
		ReactorTabletLink.State actual = ReactorTabletLink.resolve(player, tablet).state();
		if (actual != expected) {
			helper.fail("the tablet reads " + actual + " " + when + ", expected " + expected);
		}
	}
}

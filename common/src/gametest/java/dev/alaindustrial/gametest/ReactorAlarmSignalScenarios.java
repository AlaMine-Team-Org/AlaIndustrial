package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.ReactorRig.BARE_CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.BARE_SIGNAL;
import static dev.alaindustrial.gametest.ReactorRig.CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.buildBareRig;
import static dev.alaindustrial.gametest.ReactorRig.buildRoom;
import static dev.alaindustrial.gametest.ReactorRig.controller;
import static dev.alaindustrial.gametest.ReactorRig.drive;
import static dev.alaindustrial.gametest.ReactorRig.driveAt;

import dev.alaindustrial.block.ReactorAlarmLampBlock;
import dev.alaindustrial.block.ReactorAlarmLevel;
import dev.alaindustrial.block.ReactorAntennaBlock;
import dev.alaindustrial.block.ReactorAntennaMastBlock;
import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The reactor antenna's redstone alarm and the alarm lamp (MOD-945): the boom shows 0 / 7 / 15 on both scales, with
 * the latches' deadbands; a silent antenna and a broken controller show 0; the signal never reaches the controller;
 * and the lamp lights on any redstone and goes dark four ticks after it. Each scenario asserts both sides of its rule.
 *
 * <p>The gauge is set by writing the controller's own save key ({@code Heat}, {@code Instability}) and loading it
 * back, a little over the wanted percentage so one tick of cooling cannot take it under: nothing in the rig reacts,
 * so the one tick driven after each write reads exactly that point of the scale.
 */
final class ReactorAlarmSignalScenarios {

	private ReactorAlarmSignalScenarios() {}

	public static final class Roster {
		private Roster() {}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReactorAlarmSignalScenarios::aRoomShowsItsAlarmOnTheBoom,
						"reactor_alarm_room_levels").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::aBarePileShowsItsAlarmOnTheBoom,
						"reactor_alarm_bare_levels").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::theWarningDoesNotChatterAtItsLine,
						"reactor_alarm_warning_hysteresis").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::anUnpoweredAntennaShowsNoAlarm,
						"reactor_alarm_unpowered_antenna").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::aBoomGoesDarkWhenItsControllerIsGone,
						"reactor_alarm_controller_gone").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::theAlarmNeverPowersTheController,
						"reactor_alarm_never_powers_controller").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::theLampFollowsAnyRedstone,
						"reactor_alarm_lamp_follows_redstone").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::theLampLightsFromTheBoomOnly,
						"reactor_alarm_lamp_from_boom").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::lampsOnTheRoomLightByItsAlarm,
						"reactor_alarm_lamps_on_room").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::aLampGoesDarkWhenItsSupportGoes,
						"reactor_alarm_lamp_support_gone").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::lampsOnABarePileLightByItsAlarm,
						"reactor_alarm_lamps_on_bare_pile").ticks(100),
				RosterEntry.of(ReactorAlarmSignalScenarios::aLampStandsOnTheFaceItIsPlacedOn,
						"reactor_alarm_lamp_six_faces").ticks(20));
	}

	/** East wall, middle: the antenna of the tablet scenarios; its boom stands at x = 5. */
	private static final BlockPos WALL_ANTENNA = new BlockPos(ReactorRig.SHELL_MAX, 2, 2);

	private static final BlockPos WALL_BOOM = WALL_ANTENNA.east();

	/** Heat or instability over the wanted percentage: more than a tick of cooling, less than one point. */
	private static final long MARGIN = 90L;

	/** Both scales top out at 10 000; one point of the gauge is a hundred units. */
	private static final long UNITS_PER_PERCENT = 100L;

	/** A sealed room climbing to the top of its heat scale: 0, 7, 15 at the meltdown line, 15 on the countdown. */
	public static void aRoomShowsItsAlarmOnTheBoom(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = roomWithAntenna(helper);
		int[] gauge = {50, 75, 90, 100, 50};
		int[] expected = {0, 7, 15, 15, 15};
		for (int i = 0; i < gauge.length; i++) {
			gaugeAt(helper, brain, "Heat", gauge[i], CONTROLLER);
			expectBoom(helper, WALL_BOOM, expected[i], "a room at " + gauge[i] + " %"
					+ (i == gauge.length - 1 ? " with its countdown still armed" : ""));
		}
		if (brain.getBlastCountdown() <= 0) {
			helper.fail("the room at 100 % armed no countdown, so the last step proved nothing");
		}
		helper.succeed();
	}

	/**
	 * A bare pile on its instability scale: no meltdown line, so 90 % is only a warning — the lava farm of three
	 * racks gives 7 for good — and 15 comes with the countdown at the top.
	 */
	public static void aBarePileShowsItsAlarmOnTheBoom(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = buildBareRig(helper);
		// Scrammed: the pile only decays, so the gauge stays where it is set.
		helper.setBlock(BARE_SIGNAL, Blocks.AIR.defaultBlockState());
		BlockPos antenna = bareAntenna(helper);
		BlockPos boom = antenna.north();
		driveAt(helper, brain, BARE_CONTROLLER, 2);
		if (!brain.isBare()) {
			helper.fail("the bare rig's controller does not read as a bare pile");
		}
		int[] gauge = {50, 75, 90, 100};
		int[] expected = {0, 7, 7, 15};
		for (int i = 0; i < gauge.length; i++) {
			gaugeAt(helper, brain, "Instability", gauge[i], BARE_CONTROLLER);
			expectBoom(helper, boom, expected[i], "a bare pile at " + gauge[i] + " %");
		}
		helper.succeed();
	}

	/**
	 * A gauge wobbling across the warning line switches the boom on once and leaves it on. The negative control
	 * runs the same wobble with the rearm floor lifted to the line: there the deadband is gone and the boom flips.
	 */
	public static void theWarningDoesNotChatterAtItsLine(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = roomWithAntenna(helper);
		int[] gauge = {65, 71, 69, 71, 69, 71, 69};
		int latched = boomChanges(helper, brain, gauge);
		if (latched != 1) {
			helper.fail("the boom changed " + latched + " times on a gauge wobbling at the warning line, expected 1");
		}
		gaugeAt(helper, brain, "Heat", 50, CONTROLLER);
		int chattered;
		try (ConfigOverrides o = ConfigOverrides.sync().set("reactorCoolantTargetPercent", 70)) {
			chattered = boomChanges(helper, brain, gauge);
		}
		if (chattered < 5) {
			helper.fail("with no deadband the same wobble changed the boom only " + chattered
					+ " times: this scenario could not tell a latch from a bare threshold");
		}
		helper.succeed();
	}

	/** No cable and an empty reactor: the antenna does not transmit, so a hot room shows nothing on its boom. */
	public static void anUnpoweredAntennaShowsNoAlarm(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		ReactorControllerBlockEntity brain = controller(helper);
		brain.getEnergyStorage().setAmountUntracked(0);
		drive(helper, brain, 2);
		gaugeAt(helper, brain, "Heat", 90, CONTROLLER);
		expectBoom(helper, WALL_BOOM, 0, "on an unpowered antenna at 90 %");
		// The control: the same antenna, paid for, shows the emergency — a tick later, as the upkeep is billed.
		antenna(helper, WALL_ANTENNA).getEnergyStorage().setAmountUntracked(
				antenna(helper, WALL_ANTENNA).getEnergyStorage().getCapacity());
		helper.runAfterDelay(1, () -> {
			gaugeAt(helper, brain, "Heat", 90, CONTROLLER);
			expectBoom(helper, WALL_BOOM, 15, "once the antenna is powered");
			helper.succeed();
		});
	}

	/** A lit boom whose controller is broken goes dark by its own check, within about a second. */
	public static void aBoomGoesDarkWhenItsControllerIsGone(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = roomWithAntenna(helper);
		gaugeAt(helper, brain, "Heat", 75, CONTROLLER);
		expectBoom(helper, WALL_BOOM, 7, "before the controller goes");
		helper.setBlock(CONTROLLER, Blocks.AIR.defaultBlockState());
		int limit = ReactorAntennaMastBlock.ALARM_CHECK_TICKS + (int) ReactorAntennaBlockEntity.LINK_TIMEOUT_TICKS + 2;
		helper.runAfterDelay(limit, () -> {
			expectBoom(helper, WALL_BOOM, 0, limit + " ticks after its controller was broken");
			helper.succeed();
		});
	}

	/**
	 * The safety rule: a bare pile with the antenna touching its controller, scram lever pulled, alarm at 15 — and
	 * the controller still has no signal and does not start. The control reads the boom itself giving 15.
	 */
	public static void theAlarmNeverPowersTheController(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = buildBareRig(helper);
		helper.setBlock(BARE_SIGNAL, Blocks.AIR.defaultBlockState());
		BlockPos antenna = bareAntenna(helper);
		BlockPos boom = antenna.north();
		driveAt(helper, brain, BARE_CONTROLLER, 2);
		gaugeAt(helper, brain, "Instability", 100, BARE_CONTROLLER);
		expectBoom(helper, boom, 15, "on a bare pile pinned at the top");
		// Dust right beside the boom takes its signal: the control that the boom does give 15 into the world.
		BlockPos dust = boom.west();
		helper.setBlock(dust.below(), Blocks.STONE.defaultBlockState());
		helper.setBlock(dust, Blocks.REDSTONE_WIRE.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			int wire = helper.getBlockState(dust).getValue(BlockStateProperties.POWER);
			if (wire != 15) {
				helper.fail("dust beside a boom at 15 carries " + wire);
			}
			if (helper.getLevel().hasNeighborSignal(helper.absolutePos(BARE_CONTROLLER))) {
				helper.fail("the controller of a scrammed bare pile is powered by its own alarm");
			}
			// A reacting pile gains instability every tick; a scrammed one only loses it.
			long before = brain.getInstability();
			driveAt(helper, brain, BARE_CONTROLLER, 1);
			if (brain.getInstability() > before) {
				helper.fail("the alarm started a scrammed bare pile: instability rose from " + before + " to "
						+ brain.getInstability());
			}
			helper.succeed();
		});
	}

	/**
	 * The lamp, with no reactor anywhere: lit by a redstone block beside it at light 12, still lit three ticks after
	 * the block goes and dark after four; never lit with no signal.
	 */
	public static void theLampFollowsAnyRedstone(GameTestHelper helper) {
		BlockPos lamp = new BlockPos(2, 1, 2);
		BlockPos unpowered = new BlockPos(5, 1, 5);
		BlockPos source = lamp.east();
		helper.setBlock(lamp, ModContent.REACTOR_ALARM_LAMP.get().defaultBlockState());
		helper.setBlock(unpowered, ModContent.REACTOR_ALARM_LAMP.get().defaultBlockState());
		helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
		helper.runAfterDelay(1, () -> {
			BlockState lit = helper.getBlockState(lamp);
			if (!lit.getValue(ReactorAlarmLampBlock.LIT) || lit.getLightEmission() != ReactorAlarmLampBlock.LIT_LIGHT) {
				helper.fail("a powered lamp reads lit=" + lit.getValue(ReactorAlarmLampBlock.LIT) + " light="
						+ lit.getLightEmission() + ", expected lit at " + ReactorAlarmLampBlock.LIT_LIGHT);
			}
			if (helper.getLevel().getBlockEntity(helper.absolutePos(lamp)) != null) {
				helper.fail("the alarm lamp has a block entity");
			}
			BlockState dark = helper.getBlockState(unpowered);
			if (dark.getValue(ReactorAlarmLampBlock.LIT) || dark.getLightEmission() != 0) {
				helper.fail("a lamp with no signal is lit");
			}
			helper.setBlock(source, Blocks.AIR.defaultBlockState());
			helper.runAfterDelay(ReactorAlarmLampBlock.OFF_DELAY_TICKS - 1, () -> {
				if (!helper.getBlockState(lamp).getValue(ReactorAlarmLampBlock.LIT)) {
					helper.fail("the lamp went dark before its " + ReactorAlarmLampBlock.OFF_DELAY_TICKS + " ticks");
				}
				helper.runAfterDelay(2, () -> {
					if (helper.getBlockState(lamp).getValue(ReactorAlarmLampBlock.LIT)) {
						helper.fail("the lamp is still lit " + (ReactorAlarmLampBlock.OFF_DELAY_TICKS + 1)
								+ " ticks after its signal went");
					}
					helper.succeed();
				});
			});
		});
	}

	/**
	 * A lamp beside the boom lights on the warning; one behind a stone block beside the boom does not — the signal
	 * is weak and does not power a solid block, which is what keeps it out of the controller.
	 */
	public static void theLampLightsFromTheBoomOnly(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = roomWithAntenna(helper);
		BlockPos beside = WALL_BOOM.south();
		BlockPos stone = WALL_BOOM.north();
		BlockPos behindStone = stone.north();
		helper.setBlock(beside, ModContent.REACTOR_ALARM_LAMP.get().defaultBlockState());
		helper.setBlock(stone, Blocks.STONE.defaultBlockState());
		helper.setBlock(behindStone, ModContent.REACTOR_ALARM_LAMP.get().defaultBlockState());
		gaugeAt(helper, brain, "Heat", 75, CONTROLLER);
		expectBoom(helper, WALL_BOOM, 7, "with lamps around it");
		helper.runAfterDelay(1, () -> {
			if (!helper.getBlockState(beside).getValue(ReactorAlarmLampBlock.LIT)) {
				helper.fail("the lamp beside a boom at 7 is dark");
			}
			if (helper.getBlockState(behindStone).getValue(ReactorAlarmLampBlock.LIT)) {
				helper.fail("the boom's weak signal powered a stone block and lit the lamp behind it");
			}
			helper.succeed();
		});
	}

	/** The room's top, a side wall from outside and a wall from inside — each shell cell with a lamp on it. */
	private static final BlockPos TOP_LAMP = new BlockPos(2, ReactorRig.SHELL_MAX + 1, 2);
	private static final BlockPos SIDE_LAMP = new BlockPos(2, 2, ReactorRig.SHELL_MAX + 1);
	private static final BlockPos INNER_LAMP = new BlockPos(2, 2, 1);
	/** Beside the roof but standing on air: next to the reactor, not pressed to it. */
	private static final BlockPos LOOSE_LAMP = new BlockPos(1, ReactorRig.SHELL_MAX + 1, 1);

	/**
	 * Lamps pressed to the room — on its roof, on a side wall, on a wall from the inside — light by the room's
	 * alarm with no wire: dark in the norm, lit from 70 %, still lit at 65 %, dark again under 60 %. The negative
	 * control is a lamp beside the roof whose base stands on air: it never lights.
	 */
	public static void lampsOnTheRoomLightByItsAlarm(GameTestHelper helper) {
		buildRoom(helper);
		placeLamp(helper, TOP_LAMP, Direction.UP);
		placeLamp(helper, SIDE_LAMP, Direction.SOUTH);
		placeLamp(helper, INNER_LAMP, Direction.SOUTH);
		placeLamp(helper, LOOSE_LAMP, Direction.NORTH);
		ReactorControllerBlockEntity brain = controller(helper);
		drive(helper, brain, 2);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("the room with lamps on it reads " + brain.getStatus() + ", expected FORMED");
		}
		int[] gauge = {50, 75, 65, 59};
		boolean[] lit = {false, true, true, false};
		for (int i = 0; i < gauge.length; i++) {
			gaugeAt(helper, brain, "Heat", gauge[i], CONTROLLER);
			for (BlockPos lamp : List.of(TOP_LAMP, SIDE_LAMP, INNER_LAMP)) {
				expectAlarm(helper, lamp, lit[i], "a lamp pressed to the room at " + gauge[i] + " %");
			}
			expectAlarm(helper, LOOSE_LAMP, false, "a lamp beside the room on air at " + gauge[i] + " %");
		}
		gaugeAt(helper, brain, "Heat", 75, CONTROLLER);
		BlockState top = helper.getBlockState(TOP_LAMP);
		if (top.getLightEmission() != ReactorAlarmLampBlock.LIT_LIGHT || top.getValue(ReactorAlarmLampBlock.LIT)) {
			helper.fail("a lamp lit by its reactor reads light=" + top.getLightEmission() + " lit="
					+ top.getValue(ReactorAlarmLampBlock.LIT) + ", expected light " + ReactorAlarmLampBlock.LIT_LIGHT
					+ " with no redstone");
		}
		helper.succeed();
	}

	/** A lamp lit by its room goes dark by its own check once the shell cell it stood on is gone. */
	public static void aLampGoesDarkWhenItsSupportGoes(GameTestHelper helper) {
		buildRoom(helper);
		placeLamp(helper, TOP_LAMP, Direction.UP);
		ReactorControllerBlockEntity brain = controller(helper);
		drive(helper, brain, 2);
		gaugeAt(helper, brain, "Heat", 75, CONTROLLER);
		expectAlarm(helper, TOP_LAMP, true, "on the roof of a room at 75 %");
		helper.setBlock(TOP_LAMP.below(), Blocks.AIR.defaultBlockState());
		int limit = ReactorAlarmLampBlock.ALARM_CHECK_TICKS + 2;
		helper.runAfterDelay(limit, () -> {
			expectAlarm(helper, TOP_LAMP, false, limit + " ticks after the roof cell under it was taken");
			helper.succeed();
		});
	}

	/** A bare pile lights the lamps on its controller and on its rack, by the same latch, and puts them out. */
	public static void lampsOnABarePileLightByItsAlarm(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = buildBareRig(helper);
		helper.setBlock(BARE_SIGNAL, Blocks.AIR.defaultBlockState());
		BlockPos underController = BARE_SIGNAL;
		BlockPos besideRack = ReactorRig.BARE_RACK.east();
		placeLamp(helper, underController, Direction.DOWN);
		placeLamp(helper, besideRack, Direction.EAST);
		driveAt(helper, brain, BARE_CONTROLLER, 2);
		int[] gauge = {50, 75, 50};
		boolean[] lit = {false, true, false};
		for (int i = 0; i < gauge.length; i++) {
			gaugeAt(helper, brain, "Instability", gauge[i], BARE_CONTROLLER);
			expectAlarm(helper, underController, lit[i], "on a bare controller at " + gauge[i] + " %");
			expectAlarm(helper, besideRack, lit[i], "on a bare rack at " + gauge[i] + " %");
		}
		helper.succeed();
	}

	/** Placed on each of the six faces, the lamp points away from the clicked block with its base against it. */
	public static void aLampStandsOnTheFaceItIsPlacedOn(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos clicked = helper.absolutePos(new BlockPos(3, 3, 3));
		ItemStack stack = new ItemStack(ModContent.REACTOR_ALARM_LAMP_ITEM.get());
		for (Direction face : Direction.values()) {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false);
			BlockState placed = ModContent.REACTOR_ALARM_LAMP.get()
					.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
			if (placed == null || placed.getValue(ReactorAlarmLampBlock.FACING) != face) {
				helper.fail("a lamp placed on the " + face + " face reads "
						+ (placed == null ? "nothing" : placed.getValue(ReactorAlarmLampBlock.FACING)));
				return;
			}
			VoxelShape shape = placed.getShape(helper.getLevel(), clicked.relative(face));
			Direction base = face.getOpposite();
			double touching = base.getAxisDirection() == Direction.AxisDirection.POSITIVE
					? shape.max(base.getAxis()) : 1.0 - shape.min(base.getAxis());
			if (touching != 1.0) {
				helper.fail("a lamp on the " + face + " face does not reach the block it stands on");
			}
		}
		helper.succeed();
	}

	// ── helpers ──

	private static void placeLamp(GameTestHelper helper, BlockPos at, Direction facing) {
		helper.setBlock(at, ModContent.REACTOR_ALARM_LAMP.get().defaultBlockState()
				.setValue(ReactorAlarmLampBlock.FACING, facing));
	}

	private static void expectAlarm(GameTestHelper helper, BlockPos lamp, boolean expected, String when) {
		BlockState state = helper.getBlockState(lamp);
		if (!(state.getBlock() instanceof ReactorAlarmLampBlock)) {
			helper.fail("no lamp at " + lamp + " " + when);
			return;
		}
		if (state.getValue(ReactorAlarmLampBlock.ALARM) != expected) {
			helper.fail((expected ? "dark: " : "lit: ") + when);
		}
	}

	/** The smallest room with a powered antenna in its east wall, formed and serving it. */
	private static ReactorControllerBlockEntity roomWithAntenna(GameTestHelper helper) {
		buildRoom(helper);
		placeAntenna(helper, WALL_ANTENNA, Direction.EAST);
		ReactorControllerBlockEntity brain = controller(helper);
		ReactorAntennaBlockEntity antenna = antenna(helper, WALL_ANTENNA);
		antenna.getEnergyStorage().setAmountUntracked(antenna.getEnergyStorage().getCapacity());
		drive(helper, brain, 2);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("the room with an antenna reads " + brain.getStatus() + ", expected FORMED");
		}
		return brain;
	}

	/** A powered antenna touching the bare controller on its north face, its boom one cell further north. */
	private static BlockPos bareAntenna(GameTestHelper helper) {
		BlockPos at = BARE_CONTROLLER.north();
		helper.setBlock(at.north(), Blocks.AIR.defaultBlockState());
		placeAntenna(helper, at, Direction.NORTH);
		ReactorAntennaBlockEntity antenna = antenna(helper, at);
		antenna.getEnergyStorage().setAmountUntracked(antenna.getEnergyStorage().getCapacity());
		return at;
	}

	/** Sets the controller's gauge to {@code percent} through its save key, then drives one tick. */
	private static void gaugeAt(GameTestHelper helper, ReactorControllerBlockEntity brain, String key, int percent,
			BlockPos controllerAt) {
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = brain.saveCustomOnly(registries);
		tag.putLong(key, percent * UNITS_PER_PERCENT + MARGIN);
		brain.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		driveAt(helper, brain, controllerAt, 1);
	}

	/** How many times the wall boom changed while the room's gauge walked {@code gauge}. */
	private static int boomChanges(GameTestHelper helper, ReactorControllerBlockEntity brain, int[] gauge) {
		List<ReactorAlarmLevel> seen = new ArrayList<>();
		for (int percent : gauge) {
			gaugeAt(helper, brain, "Heat", percent, CONTROLLER);
			seen.add(helper.getBlockState(WALL_BOOM).getValue(ReactorAntennaMastBlock.ALARM));
		}
		int changes = 0;
		for (int i = 1; i < seen.size(); i++) {
			if (seen.get(i) != seen.get(i - 1)) {
				changes++;
			}
		}
		return changes;
	}

	private static void expectBoom(GameTestHelper helper, BlockPos boom, int expected, String when) {
		BlockState state = helper.getBlockState(boom);
		if (!(state.getBlock() instanceof ReactorAntennaMastBlock)) {
			helper.fail("no boom at " + boom + " " + when);
			return;
		}
		int shown = state.getValue(ReactorAntennaMastBlock.ALARM).signal();
		if (shown != expected) {
			helper.fail("the boom shows " + shown + " " + when + ", expected " + expected);
		}
	}

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
}

package dev.alaindustrial.gametest;

import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.MobWheelCellBlock;
import dev.alaindustrial.block.MobWheelControllerBlock;
import dev.alaindustrial.block.MobWheelGateBlock;
import dev.alaindustrial.block.MobWheelRotorBlock;
import dev.alaindustrial.block.MobWheelStructure;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.core.environment.MobWheelLayout;
import dev.alaindustrial.core.environment.MobWheelProfile;
import dev.alaindustrial.core.environment.MobWheelStatus;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the mob wheel (MOD-763): the 3×3×3 structure assembles from its fourteen parts in every
 * facing and refuses a mirrored or incomplete build; losing a part takes it apart; a pig shut in by the gate
 * runs, tires and charges an adjacent battery box; opening the gate or the mob's death stops it without
 * harming the structure; the feeder spends exactly one portion and only once the mob is exhausted.
 *
 * <p>Every rig fits the 8×8×8 template ({@code 0..7}, see the force-loaded-chunk rule): the drive stands at
 * {@link #DRIVE} and the structure reaches at most two blocks from it on each horizontal axis.
 */
public final class MobWheelScenarios {

	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MobWheelScenarios::assemblesFromPartsInEveryFacing,
						"mob_wheel_assembles_in_every_facing")
						.ticks(40),
				RosterEntry.of(MobWheelScenarios::refusesMirroredIncompleteAndBlockedBuilds,
						"mob_wheel_refuses_mirrored_incomplete_blocked").ticks(40),
				RosterEntry.of(MobWheelScenarios::breakingAFrameUnformsAndRemovesCells,
						"mob_wheel_breaking_frame_unforms").ticks(40),
				RosterEntry.of(MobWheelScenarios::pigInsideChargesTheBatteryAndTires,
						"mob_wheel_pig_charges_battery_and_tires").ticks(60),
				RosterEntry.of(MobWheelScenarios::openingTheGateStopsAndReleases,
						"mob_wheel_open_gate_stops_and_releases")
						.ticks(60),
				RosterEntry.of(MobWheelScenarios::deathOfTheOccupantStopsOnlyTheWheel,
						"mob_wheel_occupant_death_stops_generation").ticks(60),
				RosterEntry.of(MobWheelScenarios::feederSpendsOnePortionOnlyWhenExhausted,
						"mob_wheel_feeder_spends_one_portion_when_exhausted").ticks(60),
				RosterEntry.of(MobWheelScenarios::wrongFoodAndWrongMobAreRefused,
						"mob_wheel_wrong_food_and_mob_refused")
						.ticks(60),
				RosterEntry.of(MobWheelScenarios::closedGateCatchesAMobThatGetsIn,
						"mob_wheel_closed_gate_catches_mob_that_gets_in").ticks(40),
				RosterEntry.of(MobWheelScenarios::leadShortcutPutsTheMobIn,
						"mob_wheel_lead_shortcut_puts_mob_in").ticks(40),
				RosterEntry.of(MobWheelScenarios::occupantStandsOnTheRunningSurfaceFacingTheRun,
						"mob_wheel_occupant_on_running_surface_facing_run").ticks(60),
				RosterEntry.of(MobWheelScenarios::cableJoinsTheDrivePortFaces,
						"mob_wheel_cable_joins_drive_port_faces").ticks(40),
				RosterEntry.of(MobWheelScenarios::plantsInFreeCellsAreClearedFluidsRefuse,
						"mob_wheel_plants_cleared_fluids_refuse").ticks(40),
				RosterEntry.of(MobWheelScenarios::zombieVillagerRunsAsAZombie,
						"mob_wheel_zombie_villager_runs_as_zombie").ticks(60));

		private Roster() {}
	}

	private MobWheelScenarios() {
	}

	/** The drive, relative; every facing's box spans {@code DRIVE ± 2} horizontally, {@code y 1..3}. */
	private static final BlockPos DRIVE = new BlockPos(4, 1, 4);
	/** The rig used for the energy scenarios: facing south, battery on the drive's east (port) face. */
	static final BlockPos ENERGY_DRIVE = new BlockPos(4, 1, 2);

	// --- helpers ---

	private static BlockPos at(BlockPos drive, Direction facing, int x, int y, int z) {
		return MobWheelStructure.at(drive, facing, x, y, z);
	}

	static void clearBox(GameTestHelper helper) {
		for (BlockPos pos : BlockPos.betweenClosed(1, 1, 1, 7, 4, 7)) {
			helper.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState());
		}
	}

	/** Place the fourteen parts of the wheel driven from {@code drive}; {@code skipFrame} leaves one post out. */
	private static void placeParts(GameTestHelper helper, BlockPos drive, Direction facing, boolean gateOpen,
			boolean skipFrame) {
		boolean skipped = false;
		for (int x = 0; x < MobWheelStructure.SIZE; x++) {
			for (int y = 0; y < MobWheelStructure.SIZE; y++) {
				for (int z = 0; z < MobWheelStructure.SIZE; z++) {
					BlockPos pos = at(drive, facing, x, y, z);
					switch (MobWheelStructure.slotAt(x, y, z)) {
						case CONTROLLER -> helper.setBlock(pos,
								ModContent.MOB_WHEEL_CONTROLLER.get().defaultBlockState()
										.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
						case FRAME -> {
							if (skipFrame && !skipped) {
								skipped = true;
							} else {
								helper.setBlock(pos, ModContent.MOB_WHEEL_FRAME.get().defaultBlockState());
							}
						}
						// Owner review 2: the wheel is taken in any facing; turned off the structure's on purpose.
						case ROTOR -> helper.setBlock(pos, ModContent.MOB_WHEEL_ROTOR.get().defaultBlockState()
								.setValue(MobWheelRotorBlock.FACING, facing.getClockWise()));
						case GATE -> helper.setBlock(pos, ModContent.MOB_WHEEL_GATE.get().defaultBlockState()
								.setValue(MobWheelGateBlock.OPEN, gateOpen));
						case CELL -> { }
					}
				}
			}
		}
	}

	private static void assemble(GameTestHelper helper, BlockPos drive) {
		MobWheelStructure.tryAssemble(helper.getLevel(), helper.absolutePos(drive));
	}

	private static boolean formed(GameTestHelper helper, BlockPos drive) {
		BlockState state = helper.getBlockState(drive);
		return MobWheelStructure.isController(state) && state.getValue(MobWheelStructure.FORMED);
	}

	private static void assertAssembled(GameTestHelper helper, BlockPos drive, Direction facing) {
		BlockState driveState = helper.getBlockState(drive);
		if (!formed(helper, drive) || driveState.getValue(MobWheelControllerBlock.FACING) != facing) {
			helper.fail("drive did not assemble facing " + facing + ": " + driveState);
		}
		for (int x = 0; x < MobWheelStructure.SIZE; x++) {
			for (int y = 0; y < MobWheelStructure.SIZE; y++) {
				for (int z = 0; z < MobWheelStructure.SIZE; z++) {
					BlockState state = helper.getBlockState(at(drive, facing, x, y, z));
					if (!MobWheelStructure.isFormedMember(state)) {
						helper.fail("cell (" + x + "," + y + "," + z + ") of the " + facing + " wheel is not formed: "
								+ state);
					}
					if (MobWheelStructure.slotAt(x, y, z) == MobWheelStructure.Slot.CELL
							&& state.getValue(MobWheelCellBlock.SHAPE) != MobWheelStructure.cellShapeAt(x, y, z)) {
						helper.fail("cell (" + x + "," + y + "," + z + ") has shape "
								+ state.getValue(MobWheelCellBlock.SHAPE));
					}
				}
			}
		}
	}

	/** Facing-south wheel with an open gate and a battery box on the drive's east (port) face. */
	static MobWheelBlockEntity energyRig(GameTestHelper helper) {
		placeParts(helper, ENERGY_DRIVE, Direction.SOUTH, true, false);
		assemble(helper, ENERGY_DRIVE);
		assertAssembled(helper, ENERGY_DRIVE, Direction.SOUTH);
		helper.setBlock(ENERGY_DRIVE.east(), ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		MobWheelBlockEntity drive = helper.getBlockEntity(ENERGY_DRIVE, MobWheelBlockEntity.class);
		if (drive == null) {
			helper.fail("mob wheel drive has no block entity");
		}
		return drive;
	}

	static BlockPos deck() {
		return at(ENERGY_DRIVE, Direction.SOUTH, 1, 0, 1);
	}

	static BlockPos gate() {
		return at(ENERGY_DRIVE, Direction.SOUTH, 1, 0, 2);
	}

	private static boolean gateOpen(GameTestHelper helper) {
		return helper.getBlockState(gate()).getValue(MobWheelGateBlock.OPEN);
	}

	static void swingGate(GameTestHelper helper) {
		MobWheelGateBlock.toggle(helper.getLevel(), helper.absolutePos(gate()), helper.getBlockState(gate()), null);
	}

	private static Pig pigInside(GameTestHelper helper, MobWheelBlockEntity drive) {
		Pig pig = helper.spawn(EntityTypes.PIG, deck());
		swingGate(helper);
		if (drive.species() != MobWheelProfile.PIG) {
			helper.fail("closing the gate did not shut the pig in (species " + drive.species() + ")");
		}
		return pig;
	}

	static long batteryCharge(GameTestHelper helper) {
		BatteryBoxBlockEntity box = helper.getBlockEntity(ENERGY_DRIVE.east(), BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery box missing");
		}
		return box.getEnergyStorage().getAmount();
	}

	// --- scenarios ---

	/** D1: the same fourteen parts assemble in each of the four facings, with every cell of the right kind. */
	public static void assemblesFromPartsInEveryFacing(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			clearBox(helper);
			placeParts(helper, DRIVE, facing, false, false);
			assemble(helper, DRIVE);
			assertAssembled(helper, DRIVE, facing);
		}
		helper.succeed();
	}

	/** D1: one post missing, a stone where a cell must be, or a mirrored build — none of them assembles. */
	public static void refusesMirroredIncompleteAndBlockedBuilds(GameTestHelper helper) {
		clearBox(helper);
		placeParts(helper, DRIVE, Direction.SOUTH, false, true);
		assemble(helper, DRIVE);
		if (formed(helper, DRIVE)) {
			helper.fail("the wheel assembled with a frame post missing");
		}
		Component missing = MobWheelStructure.diagnose(helper.getLevel(), helper.absolutePos(DRIVE));
		expectKey(helper, missing, "message.alaindustrial.mob_wheel.missing");

		clearBox(helper);
		placeParts(helper, DRIVE, Direction.SOUTH, false, false);
		// Undo the automatic assembly placement may already have done, then block one cell.
		MobWheelStructure.disassemble(helper.getLevel(), helper.absolutePos(DRIVE), Direction.SOUTH);
		helper.setBlock(at(DRIVE, Direction.SOUTH, 1, 2, 1), Blocks.STONE.defaultBlockState());
		assemble(helper, DRIVE);
		if (formed(helper, DRIVE)) {
			helper.fail("the wheel assembled around a stone block");
		}
		Component blocked = MobWheelStructure.diagnose(helper.getLevel(), helper.absolutePos(DRIVE));
		expectKey(helper, blocked, "message.alaindustrial.mob_wheel.blocked");

		// Mirrored: the drive in the back LEFT corner (canonical x = 0) instead of the back right.
		clearBox(helper);
		for (int x = 0; x < MobWheelStructure.SIZE; x++) {
			for (int y = 0; y < MobWheelStructure.SIZE; y++) {
				for (int z = 0; z < MobWheelStructure.SIZE; z++) {
					BlockPos pos = at(DRIVE, Direction.SOUTH, 2 - x, y, z);
					switch (MobWheelStructure.slotAt(x, y, z)) {
						case CONTROLLER -> helper.setBlock(pos,
								ModContent.MOB_WHEEL_CONTROLLER.get().defaultBlockState());
						case FRAME -> helper.setBlock(pos, ModContent.MOB_WHEEL_FRAME.get().defaultBlockState());
						case ROTOR -> helper.setBlock(pos, ModContent.MOB_WHEEL_ROTOR.get().defaultBlockState());
						case GATE -> helper.setBlock(pos, ModContent.MOB_WHEEL_GATE.get().defaultBlockState());
						case CELL -> { }
					}
				}
			}
		}
		BlockPos mirroredDrive = at(DRIVE, Direction.SOUTH, 0, 0, 0);
		assemble(helper, mirroredDrive);
		if (formed(helper, mirroredDrive)) {
			helper.fail("a mirrored wheel assembled");
		}
		helper.succeed();
	}

	private static void expectKey(GameTestHelper helper, Component message, String key) {
		if (!(message.getContents() instanceof TranslatableContents contents) || !key.equals(contents.getKey())) {
			helper.fail("expected diagnosis " + key + ", got " + message);
		}
	}

	/** D1: losing one post turns every part back into its loose form and leaves no cell behind. */
	public static void breakingAFrameUnformsAndRemovesCells(GameTestHelper helper) {
		clearBox(helper);
		placeParts(helper, DRIVE, Direction.EAST, false, false);
		assemble(helper, DRIVE);
		assertAssembled(helper, DRIVE, Direction.EAST);
		helper.setBlock(at(DRIVE, Direction.EAST, 0, 2, 2), Blocks.AIR.defaultBlockState());
		if (formed(helper, DRIVE)) {
			helper.fail("the drive stayed formed after a frame post was removed");
		}
		for (int x = 0; x < MobWheelStructure.SIZE; x++) {
			for (int y = 0; y < MobWheelStructure.SIZE; y++) {
				for (int z = 0; z < MobWheelStructure.SIZE; z++) {
					BlockState state = helper.getBlockState(at(DRIVE, Direction.EAST, x, y, z));
					if (state.is(ModContent.MOB_WHEEL_CELL.get())) {
						helper.fail("a cell survived the disassembly at (" + x + "," + y + "," + z + ")");
					}
					if (MobWheelStructure.isPart(state) && state.getValue(MobWheelStructure.FORMED)) {
						helper.fail("a part stayed formed at (" + x + "," + y + "," + z + "): " + state);
					}
				}
			}
		}
		helper.succeed();
	}

	/** D3/D4: a pig shut in runs, charges the battery on the port face and spends stamina. */
	public static void pigInsideChargesTheBatteryAndTires(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		pigInside(helper, drive);
		AlaGameTestHelper.drive(drive, helper, 40);
		if (drive.status() != MobWheelStatus.RUNNING) {
			helper.fail("the wheel is " + drive.status() + ", not running, with a pig inside");
		}
		if (batteryCharge(helper) <= 0) {
			helper.fail("the battery box on the drive's port face received no EU");
		}
		if (drive.staminaPermille() >= 1000) {
			helper.fail("the pig ran 40 ticks and spent no stamina");
		}
		if (drive.getEnergyStorage().getCapacity() > 12) {
			helper.fail("the drive buffers " + drive.getEnergyStorage().getCapacity() + " EU — more than one tick");
		}
		helper.succeed();
	}

	/** D3: opening the gate is the only switch — it stops the wheel at once and lets the mob go. */
	public static void openingTheGateStopsAndReleases(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		pigInside(helper, drive);
		AlaGameTestHelper.drive(drive, helper, 10);
		swingGate(helper);
		if (drive.species() != null) {
			helper.fail("opening the gate did not release the pig");
		}
		AlaGameTestHelper.drive(drive, helper, 2);
		long before = batteryCharge(helper);
		AlaGameTestHelper.drive(drive, helper, 20);
		if (batteryCharge(helper) != before) {
			helper.fail("the battery kept charging with the gate open: " + before + " -> " + batteryCharge(helper));
		}
		if (drive.status() != MobWheelStatus.NO_MOB) {
			helper.fail("status with the gate open is " + drive.status());
		}
		helper.succeed();
	}

	/** D3: the occupant's death empties the wheel at once and does not take the structure apart. */
	public static void deathOfTheOccupantStopsOnlyTheWheel(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		Pig pig = pigInside(helper, drive);
		AlaGameTestHelper.drive(drive, helper, 10);
		pig.kill(helper.getLevel());
		AlaGameTestHelper.drive(drive, helper, 2);
		if (drive.species() != null || drive.status() != MobWheelStatus.NO_MOB) {
			helper.fail("a dead pig is still the occupant (status " + drive.status() + ")");
		}
		long before = batteryCharge(helper);
		AlaGameTestHelper.drive(drive, helper, 20);
		if (batteryCharge(helper) != before) {
			helper.fail("the battery kept charging after the pig died");
		}
		if (!formed(helper, ENERGY_DRIVE)) {
			helper.fail("the occupant's death took the wheel apart");
		}
		helper.succeed();
	}

	/** D5: a fresh pig eats nothing; once exhausted it eats exactly one portion (three carrots) and runs again. */
	public static void feederSpendsOnePortionOnlyWhenExhausted(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		pigInside(helper, drive);
		drive.setItem(MobWheelBlockEntity.FEED_SLOT, new ItemStack(Items.CARROT, 5));
		AlaGameTestHelper.drive(drive, helper, 20);
		if (drive.getItem(MobWheelBlockEntity.FEED_SLOT).getCount() != 5) {
			helper.fail("a fresh pig ate ahead of time");
		}
		// Run the pig dry: the feeder must not be touched until the very tick it is exhausted.
		drive.setItem(MobWheelBlockEntity.FEED_SLOT, ItemStack.EMPTY);
		AlaGameTestHelper.drive(drive, helper, MobWheelProfile.PIG.staminaTicks());
		if (drive.status() != MobWheelStatus.EXHAUSTED) {
			helper.fail("after a full stamina of running the pig is " + drive.status() + ", not exhausted");
		}
		drive.setItem(MobWheelBlockEntity.FEED_SLOT, new ItemStack(Items.CARROT, 5));
		AlaGameTestHelper.drive(drive, helper, 1);
		int left = drive.getItem(MobWheelBlockEntity.FEED_SLOT).getCount();
		if (left != 2) {
			helper.fail("one portion is three carrots; " + (5 - left) + " were eaten");
		}
		if (drive.status() != MobWheelStatus.RUNNING || drive.staminaPermille() < 999) {
			helper.fail("the fed pig is " + drive.status() + " at " + drive.staminaPermille() + "‰");
		}
		AlaGameTestHelper.drive(drive, helper, 5);
		if (drive.getItem(MobWheelBlockEntity.FEED_SLOT).getCount() != 2) {
			helper.fail("the fed pig kept eating");
		}
		helper.succeed();
	}

	/**
	 * D3 (owner review 1): a closed, empty wheel catches a mob that gets onto the deck or into the gate cell
	 * behind the panel within {@link MobWheelBlockEntity#CAPTURE_INTERVAL} ticks — not only on the instant
	 * the gate closes — while a mob standing outside the closed gate is left alone.
	 */
	public static void closedGateCatchesAMobThatGetsIn(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		swingGate(helper);
		if (gateOpen(helper) || drive.species() != null) {
			helper.fail("closing the gate on an empty wheel caught something or left it open");
		}
		// Outside the closed panel (facing south: the gate cell's south half and beyond).
		Pig outside = helper.spawn(EntityTypes.PIG, new Vec3(gate().getX() + 0.5, gate().getY(), gate().getZ() + 1.2));
		AlaGameTestHelper.drive(drive, helper, MobWheelBlockEntity.CAPTURE_INTERVAL * 2);
		if (drive.species() != null) {
			helper.fail("a pig outside the closed gate was pulled in");
		}
		outside.discard();
		// Inside the gate cell, on the wheel's side of the panel, not touching the deck cell.
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE,
				new Vec3(gate().getX() + 0.5, gate().getY(), gate().getZ() + 0.31));
		AlaGameTestHelper.drive(drive, helper, MobWheelBlockEntity.CAPTURE_INTERVAL);
		if (drive.species() != MobWheelProfile.ZOMBIE) {
			helper.fail("a zombie in the gate cell behind the closed gate was not caught (" + drive.species() + ")");
		}
		Vec3 anchor = MobWheelStructure.anchor(helper.absolutePos(ENERGY_DRIVE), Direction.SOUTH);
		if (zombie.position().distanceToSqr(anchor) > 1.0E-6) {
			helper.fail("the caught zombie was not snapped to the deck: " + zombie.position() + " vs " + anchor);
		}
		helper.succeed();
	}

	/**
	 * D3 (owner review 1): right-clicking any part of the formed wheel while leading a pig puts the pig on the
	 * deck, shuts the gate, makes it the occupant and gives the lead back.
	 */
	public static void leadShortcutPutsTheMobIn(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		if (!gateOpen(helper)) {
			helper.fail("the rig's gate should start open");
		}
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		Vec3 front = helper.absoluteVec(new Vec3(gate().getX() + 0.5, gate().getY(), gate().getZ() + 2.5));
		player.snapTo(front.x, front.y, front.z, 0.0F, 0.0F);
		Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(gate().getX() + 0.5, gate().getY(), gate().getZ() + 1.5));
		pig.setLeashedTo(player, true);
		if (!pig.isLeashed()) {
			helper.fail("the pig did not take the lead");
		}
		helper.useBlock(at(ENERGY_DRIVE, Direction.SOUTH, 0, 1, 0), player);
		if (drive.species() != MobWheelProfile.PIG) {
			helper.fail("the lead shortcut did not put the pig in (species " + drive.species() + ")");
		}
		if (gateOpen(helper)) {
			helper.fail("the lead shortcut left the gate open");
		}
		if (pig.isLeashed()) {
			helper.fail("the pig is still on the lead inside the wheel");
		}
		if (player.getInventory().countItem(Items.LEAD) != 1) {
			helper.fail("the lead did not come back to the player");
		}
		Vec3 anchor = MobWheelStructure.anchor(helper.absolutePos(ENERGY_DRIVE), Direction.SOUTH);
		if (pig.position().distanceToSqr(anchor) > 1.0E-6) {
			helper.fail("the led pig is not on the deck: " + pig.position() + " vs " + anchor);
		}
		helper.succeed();
	}

	/**
	 * Owner review 1: after real world ticks (gravity, goals, body turn) the held pig still has its feet exactly on
	 * the drawn running surface, not in the planks, and faces strictly along the run; the deck cell's collision
	 * top is that same surface, so a released mob stands on it too.
	 */
	public static void occupantStandsOnTheRunningSurfaceFacingTheRun(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		Pig pig = pigInside(helper, drive);
		double surface = helper.absolutePos(deck()).getY() + MobWheelLayout.ROTOR_FLOOR_PX / 16.0;
		double collisionTop = helper.getBlockState(deck())
				.getCollisionShape(helper.getLevel(), helper.absolutePos(deck())).max(Direction.Axis.Y);
		if (Math.abs(helper.absolutePos(deck()).getY() + collisionTop - surface) > 1.0E-6) {
			helper.fail("the deck's collision top " + collisionTop + " is not the running surface "
					+ MobWheelLayout.ROTOR_FLOOR_PX / 16.0);
		}
		float yaw = MobWheelStructure.runDirection(Direction.SOUTH).toYRot();
		helper.runAfterDelay(20, () -> {
			if (pig.getY() < surface - 1.0E-6 || pig.getY() > surface + 0.01) {
				helper.fail("the held pig's feet are at " + pig.getY() + ", the running surface at " + surface);
			}
			if (Math.abs(pig.getYRot() - yaw) > 1.0E-3 || Math.abs(pig.yBodyRot - yaw) > 1.0E-3
					|| Math.abs(pig.getYHeadRot() - yaw) > 1.0E-3) {
				helper.fail("the held pig faces " + pig.getYRot() + "/" + pig.yBodyRot + "/" + pig.getYHeadRot()
						+ ", the run is " + yaw);
			}
			if (drive.species() != MobWheelProfile.PIG) {
				helper.fail("the pig left the wheel during world ticks");
			}
			helper.succeed();
		});
	}

	/**
	 * Owner review 1 (cable gap): a cable draws an arm to the drive's two outward faces — canonical {@code +x}
	 * and the back — formed and loose, and to no inner face.
	 */
	public static void cableJoinsTheDrivePortFaces(GameTestHelper helper) {
		clearBox(helper);
		energyRig(helper);
		BlockPos abs = helper.absolutePos(ENERGY_DRIVE);
		for (Direction face : Direction.values()) {
			boolean want = face == Direction.EAST || face == Direction.NORTH;
			if (CableBlock.shouldConnectTo(helper.getLevel(), abs.relative(face), face.getOpposite()) != want) {
				helper.fail("formed drive: cable arm on its " + face + " face is " + !want + ", expected " + want);
			}
		}
		// A post taken away leaves the drive loose (disassembling alone would let it assemble again at once).
		helper.setBlock(at(ENERGY_DRIVE, Direction.SOUTH, 0, 2, 2), Blocks.AIR.defaultBlockState());
		if (formed(helper, ENERGY_DRIVE)) {
			helper.fail("the drive did not come apart");
		}
		for (Direction face : new Direction[] {Direction.EAST, Direction.NORTH}) {
			if (!CableBlock.shouldConnectTo(helper.getLevel(), abs.relative(face), face.getOpposite())) {
				helper.fail("loose drive: no cable arm on its " + face + " port face");
			}
		}
		helper.succeed();
	}

	/**
	 * Owner review 2: a poppy, short grass and a snow layer standing in cells that must be free do not block
	 * assembly — the wheel assembles in any rotor facing and breaks them with their drops — while water in a
	 * cell refuses the build and the diagnosis says to drain it.
	 */
	public static void plantsInFreeCellsAreClearedFluidsRefuse(GameTestHelper helper) {
		clearBox(helper);
		Direction facing = Direction.SOUTH;
		BlockPos poppy = at(DRIVE, facing, 1, 0, 0);
		BlockPos grass = at(DRIVE, facing, 0, 0, 1);
		BlockPos snow = at(DRIVE, facing, 2, 0, 1);
		for (BlockPos cell : new BlockPos[] {poppy, grass, snow}) {
			helper.setBlock(cell.below(), Blocks.GRASS_BLOCK.defaultBlockState());
		}
		// Water first: the build must stay loose, and the drive must say what is in the way.
		helper.setBlock(poppy, Blocks.WATER.defaultBlockState());
		placeParts(helper, DRIVE, facing, false, false);
		assemble(helper, DRIVE);
		if (formed(helper, DRIVE)) {
			helper.fail("the wheel assembled around a water source");
		}
		expectKey(helper, MobWheelStructure.diagnose(helper.getLevel(), helper.absolutePos(DRIVE)),
				"message.alaindustrial.mob_wheel.fluid");
		// Plants and snow while the water still holds the build back, then a poppy in place of the water (same
		// tick, before the water can flow): the neighbour update of that last change assembles the wheel.
		helper.setBlock(grass, Blocks.SHORT_GRASS.defaultBlockState());
		helper.setBlock(snow, Blocks.SNOW.defaultBlockState());
		if (formed(helper, DRIVE)) {
			helper.fail("the wheel assembled while water still stood in a cell");
		}
		helper.setBlock(poppy, Blocks.POPPY.defaultBlockState());
		assemble(helper, DRIVE);
		assertAssembled(helper, DRIVE, facing);
		AABB around = MobWheelStructure.bounds(helper.absolutePos(DRIVE), facing).inflate(2.0);
		boolean poppyDropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
				item -> item.getItem().is(Items.POPPY)).isEmpty();
		if (!poppyDropped) {
			helper.fail("the poppy in a free cell was not broken with its drop");
		}
		helper.succeed();
	}

	/**
	 * Owner review 2: an adult zombie villager is taken with the zombie's profile (numbers, night bonus, food)
	 * and the drive knows its own type for the screen; a baby zombie villager is not taken.
	 */
	public static void zombieVillagerRunsAsAZombie(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		ZombieVillager baby = helper.spawn(EntityTypes.ZOMBIE_VILLAGER, deck());
		baby.setBaby(true);
		swingGate(helper);
		if (drive.species() != null) {
			helper.fail("the gate shut a baby zombie villager in");
		}
		baby.discard();
		swingGate(helper);
		helper.spawn(EntityTypes.ZOMBIE_VILLAGER, deck());
		swingGate(helper);
		if (drive.species() != MobWheelProfile.ZOMBIE) {
			helper.fail("a zombie villager was not taken as a zombie (species " + drive.species() + ")");
		}
		if (!"minecraft:zombie_villager".equals(drive.runnerType())) {
			helper.fail("the drive names the occupant " + drive.runnerType() + ", not a zombie villager");
		}
		AlaGameTestHelper.drive(drive, helper, 20);
		if (drive.status() != MobWheelStatus.RUNNING || batteryCharge(helper) <= 0) {
			helper.fail("the zombie villager does not run the wheel: " + drive.status());
		}
		helper.succeed();
	}

	/** D3/D5: a wolf (not on the roster) is not caught, and food the pig does not eat stays in the feeder. */
	public static void wrongFoodAndWrongMobAreRefused(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		Wolf wolf = helper.spawn(EntityTypes.WOLF, deck());
		swingGate(helper);
		if (drive.species() != null) {
			helper.fail("the gate shut a wolf in");
		}
		wolf.discard();
		swingGate(helper);
		pigInside(helper, drive);
		AlaGameTestHelper.drive(drive, helper, MobWheelProfile.PIG.staminaTicks());
		drive.setItem(MobWheelBlockEntity.FEED_SLOT, new ItemStack(Items.ROTTEN_FLESH, 8));
		AlaGameTestHelper.drive(drive, helper, 5);
		if (drive.getItem(MobWheelBlockEntity.FEED_SLOT).getCount() != 8) {
			helper.fail("the pig ate rotten flesh");
		}
		if (drive.status() != MobWheelStatus.EXHAUSTED) {
			helper.fail("wrong food revived the pig: " + drive.status());
		}
		helper.succeed();
	}
}

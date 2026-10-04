package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.ReactorAdvancementScenarios.assertEarned;
import static dev.alaindustrial.gametest.ReactorAdvancementScenarios.assertNotEarned;
import static dev.alaindustrial.gametest.ReactorAdvancementScenarios.earned;
import static dev.alaindustrial.gametest.ReactorConsoleScenarios.logCount;
import static dev.alaindustrial.gametest.ReactorConsoleScenarios.logKinds;
import static dev.alaindustrial.gametest.ReactorRig.CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.LEVER;
import static dev.alaindustrial.gametest.ReactorRig.SHELL_MAX;
import static dev.alaindustrial.gametest.ReactorRig.buildRoom;
import static dev.alaindustrial.gametest.ReactorRig.controller;
import static dev.alaindustrial.gametest.ReactorRig.drive;
import static dev.alaindustrial.gametest.ReactorRig.driveCooled;
import static dev.alaindustrial.gametest.ReactorRig.driveUnderLoad;
import static dev.alaindustrial.gametest.ReactorRig.placeColumn;
import static dev.alaindustrial.gametest.ReactorRig.placeColumnAt;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorIdleReason;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ReactorOutletBlockEntity;
import dev.alaindustrial.block.entity.reactor.ReactorChannels;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.core.structure.ReactorMeltdown;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;

/**
 * World scenarios of the sealed reactor room (MOD-468, MOD-660, MOD-662, MOD-514): sealing, producing, the
 * scram, the coolant loop, the outlet, the rods, the nozzle, the meltdown that spares the shell, the working
 * room that melts plain pipes, and the shielded lever. Reached through the {@link ReactorScenarios} facade,
 * whose names the two lanes run (MOD-713, TST-2).
 */
final class ReactorRoomScenarios {

	private ReactorRoomScenarios() {}

	/**
	 * Builds the shell, racks four rods, applies a signal — and asserts the reactor actually produces.
	 *
	 * <p>Asserting the OUTPUT rather than merely "no exception" is the point: every earlier version of
	 * this feature compiled, ticked, and sat at zero.
	 */
	public static void sealedFuelledAndPoweredReactorProduces(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);

		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		// A redstone block against the controller's outer face: the plainest possible signal, and the
		// one a player reaches for first.
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		drive(helper, brain, 120);

		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("room did not seal: " + brain.getStatus());
		}
		if (brain.getRods() != FuelRodAssemblyBlock.MAX_RODS) {
			helper.fail("controller counted " + brain.getRods() + " rods, expected "
					+ FuelRodAssemblyBlock.MAX_RODS);
		}
		if (brain.getIdleReason() != ReactorIdleReason.RUNNING) {
			helper.fail("reactor idle: " + brain.getIdleReason());
		}
		// Four rods with no neighbours: 4 x ReactorConfig.reactorEuPerRod, and nothing may inflate that.
		int expected = 4 * ReactorConfig.reactorEuPerRod;
		if (brain.getLastOutput() != expected) {
			helper.fail("expected " + expected + " EU/t from four lone rods, got " + brain.getLastOutput());
		}

		// Dry, it works — and it heats (MOD-623). While the rods work the shell sheds nothing, so a core with
		// no water has no settle point below the top; one column used to park at 15 % for ever.
		ContainerData data = brain.getDataAccess();
		long dryHeat = brain.getHeat();
		drive(helper, brain, 20);
		if (brain.getHeat() <= dryHeat) {
			helper.fail("a dry working room stopped heating at " + brain.getHeat());
		}
		if (data.get(ReactorChannels.COOLANT_SHARE.ordinal()) != 0) {
			helper.fail("a room with no water reported carrying "
					+ data.get(ReactorChannels.COOLANT_SHARE.ordinal()) + "% of its heat");
		}

		// Water: the gauge comes all the way down while the output holds, and then the readouts hold still.
		// The loop used to switch on at the coolant target and boil in steps of two, so a core resting there
		// read 59 and 60 in turn (playtest, MOD-618).
		column.setTank(true, column.waterTank.capacity);
		long heat = brain.getHeat();
		for (int i = 0; i < 200; i++) {
			drive(helper, brain, 1);
			if (brain.getHeat() > heat || brain.getLastOutput() != expected) {
				helper.fail("tick " + i + " with water: heat " + brain.getHeat() + " (was " + heat + "), output "
						+ brain.getLastOutput());
			}
			heat = brain.getHeat();
		}
		if (heat != 0) {
			helper.fail("two hundred ticks of water left the gauge at " + heat);
		}
		int rate = data.get(ReactorChannels.WATER_RATE.ordinal());
		for (int i = 0; i < 40; i++) {
			drive(helper, brain, 1);
			if (brain.getHeat() != 0 || brain.getLastOutput() != expected
					|| data.get(ReactorChannels.WATER_RATE.ordinal()) != rate) {
				helper.fail("tick " + i + " of a cooled room moved: heat " + brain.getHeat() + ", output "
						+ brain.getLastOutput() + ", water " + data.get(ReactorChannels.WATER_RATE.ordinal())
						+ " mB/t (was " + rate + ")");
			}
		}
		if (data.get(ReactorChannels.COOLANT_SHARE.ordinal()) != 100) {
			helper.fail("a column with water to spare carried only "
					+ data.get(ReactorChannels.COOLANT_SHARE.ordinal()) + "% of its heat");
		}
		helper.succeed();
	}

	/** Cutting the signal stops the reaction — the scram, checked rather than assumed. */
	public static void removingTheSignalScramsTheReactor(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		drive(helper, brain, 120);
		if (brain.getLastOutput() <= 0) {
			helper.fail("reactor never started, so the scram cannot be under test");
		}

		helper.setBlock(CONTROLLER.west(), Blocks.AIR.defaultBlockState());
		drive(helper, brain, 5);
		if (brain.getLastOutput() != 0) {
			helper.fail("reactor kept producing " + brain.getLastOutput() + " EU/t with no signal");
		}
		if (brain.getIdleReason() != ReactorIdleReason.NO_SIGNAL) {
			helper.fail("expected NO_SIGNAL, got " + brain.getIdleReason());
		}
		helper.succeed();
	}

	/**
	 * A core the shell cannot hold runs away dry, and the coolant loop catches it.
	 *
	 * <p>Three columns in a row, not one or two: the shell sheds up to 84 heat a tick by itself, and
	 * anything under that settles at a safe temperature with no plumbing at all — which is the whole
	 * point of the ramp, and also means a test built on a small core would pass whatever the coolant
	 * code did. Three loaded columns make 124 a tick and have nowhere to put it.
	 */
	public static void coolantCatchesACoreTheShellCannotHold(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		List<FuelRodAssemblyBlockEntity> row = new ArrayList<>();
		for (int x = 1; x <= 3; x++) {
			FuelRodAssemblyBlockEntity column = placeColumnAt(helper, new BlockPos(x, 1, 2));
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			row.add(column);
		}
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		// Still a full 1000 dry ticks against a full gauge. That survived MOD-469 only because the fuel
		// racks are exempt from melting everywhere: a runaway room eats its floor and its plumbing, never
		// the columns making the heat, so a long dry run still ends pinned at the top instead of melting
		// its way back down. An interim version of the meltdown DID eat them, and this assertion caught it.
		driveUnderLoad(helper, brain, 1000);
		if (brain.getHeat() < ReactorConfig.reactorHeatCapacity) {
			helper.fail("three loaded columns should run away dry, stopped at " + brain.getHeat());
		}

		long warnAt = (long) ReactorConfig.reactorHeatCapacity * ReactorConfig.reactorHeatWarnPercent / 100;
		// Kept topped up every tick, because that is what a plumbed loop IS. Filling the tanks once and
		// walking away measures how long three columns hold water, not whether the coolant catches the
		// core — and the two answers diverged the moment the shell's own losses were tuned down.
		for (int i = 0; i < 400; i++) {
			for (FuelRodAssemblyBlockEntity column : row) {
				column.setTank(true, column.waterTank.capacity);
				// And the steam goes away, because a plumbed loop has an exhaust. Without this the steam
				// tanks fill in about two seconds, boiling stops, and the scenario measures tank size
				// again instead of the thing it is named after.
				column.setTank(false, 0);
			}
			driveUnderLoad(helper, brain, 1);
		}
		// Caught: the water carries the reaction's whole heat and pulls the overheat back down (MOD-623), so
		// the core comes off the top within ticks. Anything at or under the ceiling means it caught.
		if (brain.getHeat() >= ReactorConfig.reactorHeatCapacity) {
			helper.fail("coolant did not hold the core: still at " + brain.getHeat());
		}
		if (brain.getHeat() > warnAt + ReactorConfig.reactorHeatCapacity / 20) {
			helper.fail("coolant caught it too late: " + brain.getHeat() + " far above " + warnAt);
		}
		long steam = 0;
		for (FuelRodAssemblyBlockEntity column : row) {
			steam += column.steamTank.amount;
		}
		if (steam <= 0) {
			helper.fail("heat was removed but no steam appeared");
		}
		helper.succeed();
	}

	/**
	 * A cable on the outside of the shell actually receives the reactor's power.
	 *
	 * <p><b>The scenario that catches the worst bug of this stage.</b> The reactor produced, the panel
	 * showed a figure, the buffer filled — and none of it could reach a cable: a controller stands in a
	 * wall, so its side faces are buried and its back face opens into a sealed room. "Produces energy"
	 * and "delivers energy" are different claims, and only the second is worth anything to a player.
	 * The answer is a socket set into the shell, and this is what proves the socket works.
	 */
	public static void poweredReactorFeedsACableOutsideTheShell(GameTestHelper helper) {
		buildRoom(helper);
		// Swap one casing block of the east wall for a socket, and hang a cable on its outer face.
		BlockPos outlet = new BlockPos(SHELL_MAX, 2, 2);
		helper.setBlock(outlet, ModContent.REACTOR_OUTLET.get().defaultBlockState());
		BlockPos cable = outlet.east();
		helper.setBlock(cable, ModContent.COPPER_CABLE.get().defaultBlockState());

		ReactorControllerBlockEntity brain = controller(helper);
		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		drive(helper, brain, 120);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("a socket in the wall broke the seal: " + brain.getStatus());
		}
		if (brain.getLastOutput() <= 0) {
			helper.fail("reactor never started, so delivery cannot be under test");
		}

		ReactorOutletBlockEntity socket =
				helper.getBlockEntity(outlet, ReactorOutletBlockEntity.class);
		if (socket == null) {
			helper.fail("reactor outlet has no block entity");
			return;
		}
		if (socket.getEnergyStorage().getAmount() <= 0) {
			helper.fail("the controller never fed the socket");
		}

		// The cable has to tick to enrol itself in the energy graph, and the socket has to tick for the
		// graph to see it as a source: driving only the network manager leaves both invisible to it.
		CableBlockEntity wireTick = helper.getBlockEntity(cable, CableBlockEntity.class);
		for (int i = 0; i < 60; i++) {
			drive(helper, brain, 1);
			socket.serverTick(helper.getLevel(), helper.absolutePos(outlet),
					helper.getBlockState(outlet));
			if (wireTick != null) {
				wireTick.serverTick(helper.getLevel(), helper.absolutePos(cable),
						helper.getBlockState(cable));
			}
			NetworkManager.tickAll(helper.getLevel());
		}
		CableBlockEntity wire = helper.getBlockEntity(cable, CableBlockEntity.class);
		if (wire == null) {
			helper.fail("cable has no block entity");
			return;
		}
		if (wire.getEnergyStorage().getAmount() <= 0) {
			helper.fail("cable on the socket received nothing — the reactor cannot be plugged in");
		}
		helper.succeed();
	}

	/**
	 * All four racked rods wear together, and a spent one becomes a casing rather than vanishing.
	 *
	 * <p>Reported from play: four rods visibly working, one of them ageing. They sit in the same flux
	 * and they all count towards the room's output, so burning them in turn was simply wrong — and it
	 * was invisible from the code, because the totals came out identical either way.
	 */
	public static void everyRackedRodWearsTogether(GameTestHelper helper) {
		buildRoom(helper);
		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		long perRod = ReactorCore.rodEnergy(ReactorConfig.reactorEuPerRod, ReactorConfig.reactorRodBurnTicks);

		// A quarter of one rod's energy, handed to a rack of four: every rod should show the same small
		// amount of wear, and none should be spent.
		column.burn(perRod / 4);
		List<ItemStack> racked = column.contents();
		if (racked.size() != FuelRodAssemblyBlock.MAX_RODS) {
			helper.fail("expected four rods racked, found " + racked.size());
		}
		int first = racked.get(0).getDamageValue();
		if (first <= 0) {
			helper.fail("no rod wore at all after a quarter of a rod's energy");
		}
		for (ItemStack rod : racked) {
			if (rod.getDamageValue() != first) {
				helper.fail("rods wore unevenly: " + first + " vs " + rod.getDamageValue());
			}
		}
		if (column.getRods() != FuelRodAssemblyBlock.MAX_RODS) {
			helper.fail("a rod was spent far too early");
		}

		// Now the rest of the rack's whole charge: everything must end up as casings, not as nothing.
		column.burn(perRod * FuelRodAssemblyBlock.MAX_RODS);
		if (column.getRods() != 0) {
			helper.fail("rods survived their own charge: " + column.getRods() + " still fuelled");
		}
		List<ItemStack> spent = column.contents();
		if (spent.size() != FuelRodAssemblyBlock.MAX_RODS) {
			helper.fail("spent rods vanished instead of leaving casings: " + spent.size());
		}
		for (ItemStack rod : spent) {
			if (!rod.is(ModContent.EMPTY_FUEL_ROD.get())) {
				helper.fail("expected an empty casing, got " + rod);
			}
		}
		helper.succeed();
	}

	/** The nozzle destroys steam when it faces open air, and stalls when it does not. */
	public static void nozzleVentsIntoAirAndStallsAgainstAWall(GameTestHelper helper) {
		BlockPos open = new BlockPos(1, 1, 1);
		helper.setBlock(open, ModContent.STEAM_NOZZLE.get().defaultBlockState()
				.setValue(SteamNozzleBlock.FACING, Direction.UP));
		SteamNozzleBlockEntity venting = helper.getBlockEntity(open, SteamNozzleBlockEntity.class);
		if (venting == null) {
			helper.fail("steam nozzle has no block entity");
			return;
		}
		venting.tank.fluid = FluidHolder.of(ModContent.STEAM.get());
		venting.tank.amount = venting.tank.capacity;
		long before = venting.tank.amount;
		for (int i = 0; i < 10; i++) {
			venting.tick(helper.getLevel(), helper.absolutePos(open), helper.getBlockState(open));
		}
		if (venting.tank.amount >= before) {
			helper.fail("nozzle facing open air vented nothing");
		}

		BlockPos buried = new BlockPos(3, 1, 1);
		helper.setBlock(buried, ModContent.STEAM_NOZZLE.get().defaultBlockState()
				.setValue(SteamNozzleBlock.FACING, Direction.UP));
		helper.setBlock(buried.above(), Blocks.STONE.defaultBlockState());
		SteamNozzleBlockEntity blocked = helper.getBlockEntity(buried, SteamNozzleBlockEntity.class);
		if (blocked == null) {
			helper.fail("buried steam nozzle has no block entity");
			return;
		}
		blocked.tank.fluid = FluidHolder.of(ModContent.STEAM.get());
		blocked.tank.amount = blocked.tank.capacity;
		for (int i = 0; i < 10; i++) {
			blocked.tick(helper.getLevel(), helper.absolutePos(buried), helper.getBlockState(buried));
		}
		if (blocked.tank.amount != blocked.tank.capacity) {
			helper.fail("nozzle facing a solid block released steam anyway");
		}
		helper.succeed();
	}

	/**
	 * An overheating room melts its CONTENTS and keeps its SHELL — the containment earning its cost.
	 *
	 * <p>The ordinary fluid pipe is chosen as the victim deterministically (it is the first thing the
	 * picker looks for), which is what makes this scenario an assertion rather than a coin toss. It also
	 * happens to be the criterion the design asked for by name: the pipe a player already had lying
	 * around is the first thing the room takes, because it is the failure that best explains itself.
	 */
	public static void anOverheatingRoomMeltsItsContentsAndKeepsItsShell(GameTestHelper helper) {
		// NOT ONE CONFIG VALUE IS TOUCHED, and that is the point of building five packed racks instead of
		// one: turning reactorMeltdownStartPercent down would have been quicker to write and would have
		// applied to every reactor ticking concurrently in the same batch. It did, when this scenario was
		// first written — the neighbouring coolant runaway test started melting its own columns and
		// stopped at 67% instead of running away, which read as a regression in code that had not
		// changed. A core that genuinely pins the gauge needs no global to be bent.
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		// MOD-473: the hidden meltdown step is asserted HERE rather than in a rig of its own, because a
		// rig of its own would be a second writer of the meltdown config keys — and the paragraph above
		// is the story of what that costs. An owner is all this scenario needs to also answer "was the
		// advancement handed out", and it changes nothing about what the reactor does.
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		brain.setOwner(owner.getUUID(), owner.getName().getString());
		// Five racks packed on the floor: adjacency multiplies heat harder than output (that is the whole
		// density trade), so this core sits at the top of the scale within a few dozen ticks.
		BlockPos[] racks = {
			new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
			new BlockPos(1, 1, 2), new BlockPos(2, 1, 2),
		};
		for (BlockPos at : racks) {
			FuelRodAssemblyBlockEntity column = placeColumnAt(helper, at);
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
		}
		// The one meltable thing in the whole interior: the racks are exempt everywhere (they are built
		// around a shielding plate) and the shell is not in the box, so a runaway room eats the plumbing
		// and whatever else was carried in — which is exactly what this asserts.
		BlockPos pipe = new BlockPos(3, 1, 3);
		helper.setBlock(pipe, ModContent.FLUID_PIPE.get().defaultBlockState());
		// MOD-514: the emergency stop lives inside the room it stops, so it has to outlast the room's
		// worst state. The pipe two cells away is the control — same interior, same rounds of melting,
		// no meltproof tag — which is what makes the lever's survival below mean something.
		helper.setBlock(LEVER, ModContent.REACTOR_LEVER.get().defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		BlockPos floor = new BlockPos(2, 0, 2);
		// Nothing has run yet, so the hidden step must NOT be there. Without this line the assertion
		// after the first sixty ticks could not tell "the meltdown awarded it" from "earned() always
		// says yes" — the two are the same green.
		assertNotEarned(helper, owner, "reactor_meltdown", "before the room ever overheated");
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		// The flag is checked EARLY and the damage LATE, because the two can be true at different times.
		// A meltdown sheds heat with every block it takes, so a room with a lot to eat cools itself back
		// under the line as it works; asserting both at the end once failed on exactly that.
		driveUnderLoad(helper, brain, 60);
		if (!brain.isMeltingDown()) {
			helper.fail("a room at " + ReactorCore.heatPercent(brain.getHeat(), ReactorConfig.reactorHeatCapacity)
					+ "% of the heat scale did not report melting down");
		}
		assertEarned(helper, owner, "reactor_meltdown");
		// MOD-622: one line for the meltdown, however often a melted block drops the room back under the line.
		if (logCount(brain, ReactorLog.Kind.MELTDOWN_STARTED) != 1 || logCount(brain, ReactorLog.Kind.OVERHEAT) != 1) {
			helper.fail("sixty ticks of meltdown logged " + logKinds(brain)
					+ "; expected one overheat and one meltdown line");
		}

		driveUnderLoad(helper, brain, 340);
		if (!helper.getBlockState(pipe).is(Blocks.LAVA)) {
			helper.fail("the ordinary fluid pipe inside an overheating room did not melt first, it was "
					+ helper.getBlockState(pipe));
		}
		if (!helper.getBlockState(LEVER).is(ModContent.REACTOR_LEVER.get())) {
			helper.fail("the meltdown ate the shielded lever that stops it, leaving "
					+ helper.getBlockState(LEVER));
		}
		// The shell is the whole reason the room was built. If it goes, so does the feature.
		if (!helper.getBlockState(floor).is(ModContent.REACTOR_CASING.get())) {
			helper.fail("the meltdown ate the shell — the containment failed to contain");
		}
		if (!helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
			helper.fail("the meltdown ate its own controller");
		}
		// MOD-622: every melted block drops the room back under the line; it is still one meltdown until the room
		// cools.
		if (logCount(brain, ReactorLog.Kind.MELTDOWN_STARTED) != 1) {
			helper.fail("four hundred ticks of one meltdown logged " + logKinds(brain));
		}
		helper.succeed();
	}

	/**
	 * A WORKING room melts its ordinary fluid pipes and spares the reinforced ones (MOD-660).
	 *
	 * <p><b>Three things are asserted, and each could pass without the others.</b> A room with no signal
	 * melts nothing — the hazard hangs on the reaction, so the lever stays a real safety measure. A room
	 * that is running but kept cold by water melts the plain pipe — the new rule, and it has to happen
	 * with the core far below the meltdown line, or the meltdown pass would be taking the credit. And
	 * the reinforced pipe survives while standing FIRST in the picker's walk of the box: before the fix
	 * the walk returned it every round, {@code melt} refused it every round, and the plain pipe behind
	 * it never went — a hazard stuck on the one block it cannot touch.
	 */
	public static void aWorkingRoomMeltsPlainPipesAndSparesReinforcedOnes(GameTestHelper helper) {
		workingRoomMeltsPlainAndSparesReinforced(helper, ModContent.REINFORCED_FLUID_PIPE.get(),
				ModContent.FLUID_PIPE.get());
	}

	/**
	 * The same rule for the steam family (MOD-662): a plain steam pipe inside a working room melts like a
	 * plain fluid pipe, and the reinforced steam pipe — in {@code #alaindustrial:meltproof} — survives.
	 * Both are {@code FluidPipeBlock}s, so the picker finds them without being told about steam.
	 */
	public static void aWorkingRoomMeltsPlainSteamPipesAndSparesReinforcedOnes(GameTestHelper helper) {
		workingRoomMeltsPlainAndSparesReinforced(helper, ModContent.REINFORCED_STEAM_PIPE.get(),
				ModContent.STEAM_PIPE.get());
	}

	private static void workingRoomMeltsPlainAndSparesReinforced(GameTestHelper helper, Block reinforcedPipe,
			Block plainPipe) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		// (1,1,1) is the first cell of the walk (y, then z, then x); (3,3,3) is the last.
		BlockPos reinforced = new BlockPos(1, 1, 1);
		BlockPos plain = new BlockPos(3, 3, 3);
		helper.setBlock(reinforced, reinforcedPipe.defaultBlockState());
		helper.setBlock(plain, plainPipe.defaultBlockState());

		// No signal: a sealed, fuelled room that is not reacting harms nothing, however long it stands.
		int quiet = 2 * (ReactorConfig.reactorPipeMeltIntervalTicks + ReactorConfig.reactorMeltWarnTicks) + 80;
		driveCooled(helper, brain, column, quiet);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("room did not seal: " + brain.getStatus());
		}
		if (!helper.getBlockState(plain).is(plainPipe)) {
			helper.fail("a room with no signal melted its pipe, leaving " + helper.getBlockState(plain));
		}

		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		driveCooled(helper, brain, column, quiet);
		if (brain.isMeltingDown()) {
			helper.fail("the rig overheated into a meltdown at "
					+ ReactorCore.heatPercent(brain.getHeat(), ReactorConfig.reactorHeatCapacity)
					+ "% — the working-room rule was never isolated");
		}
		if (brain.getIdleReason() != ReactorIdleReason.RUNNING) {
			helper.fail("reactor idle: " + brain.getIdleReason());
		}
		if (!helper.getBlockState(plain).is(Blocks.LAVA)) {
			helper.fail("a working room left its ordinary pipe standing: " + helper.getBlockState(plain));
		}
		if (!helper.getBlockState(reinforced).is(reinforcedPipe)) {
			helper.fail("a working room melted the reinforced pipe, leaving " + helper.getBlockState(reinforced));
		}
		helper.succeed();
	}

	/**
	 * A shielded lever hung on the shell from the inside runs the reactor and stops it — and the room
	 * still seals around it.
	 *
	 * <p><b>Both halves matter and neither is the obvious one.</b> The room half answers the question the
	 * block exists for: a lever hangs on a face rather than filling a cell, so the scanner must keep
	 * seeing a sealed shell with one bolted to the controller from the inside. The signal half is what
	 * separates it from the {@link dev.alaindustrial.block.ReactorButtonBlock button} standing beside it
	 * in the same room: the controller reads a HELD signal, so a scram switch has to latch. Flicking the
	 * lever off and watching the reactor report {@code NO_SIGNAL} is the emergency stop the design
	 * promised, measured rather than assumed.
	 *
	 * <p>The meltproof pair at the end is deliberately asserted against the VANILLA lever too. "Ours is
	 * in the tag" alone would pass just as happily against a tag that swallowed every lever in the game,
	 * which would quietly hand the player a wooden switch that survives a meltdown.
	 */
	public static void aShieldedLeverInsideTheRoomSealsAndScrams(GameTestHelper helper) {
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));

		// FACING is the direction the lever looks; it attaches to the block on the OPPOSITE side, so
		// EAST bolts it to the controller standing in the west wall.
		BlockState lever = ModContent.REACTOR_LEVER.get().defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
		helper.setBlock(LEVER, lever.setValue(LeverBlock.POWERED, true));
		drive(helper, brain, 120);

		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("a lever hanging on the shell from the inside broke the room: " + brain.getStatus());
		}
		if (brain.getIdleReason() != ReactorIdleReason.RUNNING) {
			helper.fail("the reactor did not accept the lever's held signal: " + brain.getIdleReason());
		}
		if (brain.getLastOutput() <= 0) {
			helper.fail("a reactor switched on by the lever produced nothing");
		}

		// The scram: the lever latches off, and the reaction stops. A button cannot express this.
		helper.setBlock(LEVER, lever.setValue(LeverBlock.POWERED, false));
		drive(helper, brain, 5);
		if (brain.getLastOutput() != 0) {
			helper.fail("the reactor kept producing " + brain.getLastOutput() + " EU/t after the scram");
		}
		if (brain.getIdleReason() != ReactorIdleReason.NO_SIGNAL) {
			helper.fail("expected NO_SIGNAL after pulling the lever, got " + brain.getIdleReason());
		}
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("the room stopped being sealed once the lever was off: " + brain.getStatus());
		}

		if (!ReactorMeltdown.isMeltproof(helper.getBlockState(LEVER))) {
			helper.fail("the reactor lever is not meltproof — a meltdown would eat the emergency stop");
		}
		if (ReactorMeltdown.isMeltproof(Blocks.LEVER.defaultBlockState())) {
			helper.fail("a VANILLA lever counts as meltproof — the tag is too wide to mean anything");
		}
		helper.succeed();
	}

	/**
	 * A rack broken while it still holds fuel gives the rods back.
	 *
	 * <p><b>Written because reading the code said it could not work.</b> The rack hands its contents
	 * back from {@code affectNeighborsAfterRemoval}, and in 26.2 {@code LevelChunk.setBlockState}
	 * detaches the block entity BEFORE calling that hook — so {@code getBlockEntity} inside it should
	 * come back null and the uranium should vanish on every break, by a player or by an explosion. The
	 * repository already knows the ordering (it is documented on the incubator, which uses
	 * {@code preRemoveSideEffects} for exactly this reason), the loot table returns only the rack, and
	 * no scenario had ever broken a loaded one.
	 *
	 * <p>It also guards something MOD-471 depends on: an accident is supposed to scatter the core's
	 * uranium across the crater, where MOD-470 makes it radioactive. If the rods never drop, that
	 * aftermath silently does not exist.
	 */
	public static void aBrokenRackGivesItsRodsBack(GameTestHelper helper) {
		BlockPos at = new BlockPos(3, 1, 3);
		helper.setBlock(at.below(), Blocks.STONE.defaultBlockState());
		helper.setBlock(at, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
		FuelRodAssemblyBlockEntity rack = helper.getBlockEntity(at, FuelRodAssemblyBlockEntity.class);
		if (rack == null) {
			helper.fail("fuel rack has no block entity");
			return;
		}
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			rack.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		helper.setBlock(at, Blocks.AIR.defaultBlockState());
		// Two blocks of slack, not six: the rigs sit about six apart and a wider box would count the
		// neighbour's drops as ours (MOD-280's lesson, in the direction that produces false passes).
		AABB box = new AABB(helper.absolutePos(at)).inflate(2.0);
		int rods = 0;
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (item.getItem().is(ModContent.URANIUM_FUEL_ROD.get())) {
				rods += item.getItem().getCount();
			}
		}
		if (rods < FuelRodAssemblyBlock.MAX_RODS) {
			helper.fail("breaking a loaded rack returned " + rods + " of " + FuelRodAssemblyBlock.MAX_RODS
					+ " rods. The uranium a player racked is being destroyed, and the crater MOD-471 is "
					+ "meant to scatter it across would come out clean.");
		}
		helper.succeed();
	}
}

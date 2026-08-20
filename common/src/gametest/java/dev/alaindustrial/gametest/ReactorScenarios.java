package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorIdleReason;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.ReactorOutletBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * World scenarios for the nuclear reactor (MOD-468).
 *
 * <p><b>Written because a player could not start one and neither of us could say why.</b> Every
 * condition the reactor checks is invisible from outside — sealed shell, racked rods, a redstone
 * signal, an open throttle, room in the buffer — and reasoning about them from the source had already
 * been wrong twice. A scenario that builds the room, fuels it, powers it and reads the output settles
 * the question instead of arguing it, and keeps it settled.
 *
 * <p>The room here is the smallest the scan accepts: a 5x5x5 shell around a 3x3x3 interior, with the
 * controller standing in the middle of the west wall and looking in.
 */
public final class ReactorScenarios {

	private ReactorScenarios() {
	}

	/** Shell spans 0…4 on every axis; the interior is 1…3. */
	private static final int SHELL_MAX = 4;

	/** Middle of the west wall, the one face a controller can occupy in a room this size. */
	private static final BlockPos CONTROLLER = new BlockPos(0, 2, 2);

	/** Interior floor, in the middle. */
	private static final BlockPos COLUMN = new BlockPos(2, 1, 2);

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
		if (brain.getLastOutput() <= 0) {
			helper.fail("sealed, fuelled and powered reactor produced nothing");
		}
		// Four rods with no neighbours: 4 x Config.reactorEuPerRod, and nothing may inflate that.
		int expected = 4 * Config.reactorEuPerRod;
		if (brain.getLastOutput() != expected) {
			helper.fail("expected " + expected + " EU/t from four lone rods, got " + brain.getLastOutput());
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

		driveUnderLoad(helper, brain, 1000);
		if (brain.getHeat() < Config.reactorHeatCapacity) {
			helper.fail("three loaded columns should run away dry, stopped at " + brain.getHeat());
		}

		long warnAt = (long) Config.reactorHeatCapacity * Config.reactorHeatWarnPercent / 100;
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
		// Held, not cooled to nothing: the loop is a safety system that stops the climb, and the shell's
		// own losses bring the rest down slowly. Anything at or under the ceiling means it caught.
		if (brain.getHeat() >= Config.reactorHeatCapacity) {
			helper.fail("coolant did not hold the core: still at " + brain.getHeat());
		}
		if (brain.getHeat() > warnAt + Config.reactorHeatCapacity / 20) {
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
		long perRod = ReactorCore.rodEnergy(Config.reactorEuPerRod, Config.reactorRodBurnTicks);

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

	// --- rig ---

	/** The smallest room the scan accepts, with the controller in the middle of the west wall. */
	private static void buildRoom(GameTestHelper helper) {
		for (int x = 0; x <= SHELL_MAX; x++) {
			for (int y = 0; y <= SHELL_MAX; y++) {
				for (int z = 0; z <= SHELL_MAX; z++) {
					boolean shell = x == 0 || y == 0 || z == 0
							|| x == SHELL_MAX || y == SHELL_MAX || z == SHELL_MAX;
					BlockPos at = new BlockPos(x, y, z);
					if (!shell) {
						helper.setBlock(at, Blocks.AIR.defaultBlockState());
					} else if (!at.equals(CONTROLLER)) {
						helper.setBlock(at, ModContent.REACTOR_CASING.get().defaultBlockState());
					}
				}
			}
		}
		// WEST, not EAST: RoomValidator scans INWARD along facing.getOpposite(), so the property names
		// the way the controller's face looks OUT of the room. Getting this backwards is why the first
		// run of this scenario reported ROOM_UNBOUNDED — the scan walked away from the shell.
		helper.setBlock(CONTROLLER, ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
	}

	private static ReactorControllerBlockEntity controller(GameTestHelper helper) {
		ReactorControllerBlockEntity brain =
				helper.getBlockEntity(CONTROLLER, ReactorControllerBlockEntity.class);
		if (brain == null) {
			helper.fail("reactor controller has no block entity");
			throw new IllegalStateException("unreachable");
		}
		return brain;
	}

	private static FuelRodAssemblyBlockEntity placeColumn(GameTestHelper helper) {
		return placeColumnAt(helper, COLUMN);
	}

	private static FuelRodAssemblyBlockEntity placeColumnAt(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
		FuelRodAssemblyBlockEntity column = helper.getBlockEntity(at, FuelRodAssemblyBlockEntity.class);
		if (column == null) {
			helper.fail("reactor column has no block entity at " + at);
			throw new IllegalStateException("unreachable");
		}
		return column;
	}

	/**
	 * Ticks the controller by hand. The room is rescanned on its own timer inside that tick, so the
	 * count has to clear {@code Config.reactorScanIntervalTicks} for the shell to be recognised at all.
	 */
	/**
	 * Ticks the reactor with something actually drawing from it.
	 *
	 * <p>Without a load the buffer fills in about seven hundred ticks and the reactor stops — correctly,
	 * since idling costs no uranium — and a long run then measures a machine that spent most of it
	 * switched off. Any scenario about heat has to keep the buffer open.
	 */
	private static void driveUnderLoad(GameTestHelper helper, ReactorControllerBlockEntity brain,
			int ticks) {
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		for (int i = 0; i < ticks; i++) {
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(CONTROLLER));
			brain.getEnergyStorage().setAmountUntracked(0);
		}
	}

	private static void drive(GameTestHelper helper, ReactorControllerBlockEntity brain, int ticks) {
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		for (int i = 0; i < ticks; i++) {
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(CONTROLLER));
		}
	}
}

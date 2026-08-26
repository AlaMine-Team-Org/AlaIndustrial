package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
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

		// Still a full 1000 dry ticks against a full gauge. That survived MOD-469 only because the fuel
		// racks are exempt from melting everywhere: a runaway room eats its floor and its plumbing, never
		// the columns making the heat, so a long dry run still ends pinned at the top instead of melting
		// its way back down. An interim version of the meltdown DID eat them, and this assertion caught it.
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

	// --- MOD-469: the bare reactor and the meltdown ---

	/**
	 * Where the bare-mode rig stands, well inside the 8-block test structure.
	 *
	 * <p><b>Every radius in these scenarios is turned right down, and that is not tidiness.</b> The
	 * gametest grid puts neighbouring structures roughly thirteen blocks apart but only guarantees one
	 * block of cleared padding around each, so a scan that reaches out at the shipped radius of 8 would
	 * be reading — and melting — inside somebody else's test. The lesson is
	 * {@code wide-radius-scan-gametest-crosses-into-neighbours}, and it has bitten this repo before.
	 */
	private static final BlockPos BARE_CONTROLLER = new BlockPos(3, 2, 3);
	private static final BlockPos BARE_RACK = new BlockPos(3, 3, 3);
	private static final BlockPos BARE_SIGNAL = new BlockPos(3, 1, 3);

	/**
	 * A wire on the controller's own east face.
	 *
	 * <p>A bare reactor has no shell, so it has no {@code reactor_outlet} either — its power leaves
	 * through the controller's own faces, every one of which publishes {@code OUT} except the screen.
	 * That claim is the whole "bare mode is a real generator" promise and it is worth a wire rather than
	 * a comment: MOD-468 shipped a reactor that produced, showed a figure, filled a buffer and could not
	 * be plugged into anything.
	 */
	private static final BlockPos BARE_CABLE = new BlockPos(4, 2, 3);

	/** Melt reach used by the bare scenarios: one block, so the hazard cannot leave the rig. */
	private static final int TEST_MELT_RADIUS = 1;

	/**
	 * A reactor with no room around it makes power, obeys the switch, and eats the scenery.
	 *
	 * <p><b>All three in ONE scenario, and that is a correctness requirement rather than tidiness.</b>
	 * {@code Config} is process-global and gametests in a batch run CONCURRENTLY, so a second scenario
	 * that turned {@code reactorMeltdownMeltsBlocks} off would be turning it off for every other reactor
	 * ticking at that moment. Keeping the switch's two positions inside a single test means exactly one
	 * scenario ever writes it, and the window it is off for is this test's own.
	 *
	 * <p>Phase one proves the switch protects the WORLD and not the reactor: output must survive it. A
	 * version that quietly stopped producing would pass a weaker test while breaking the promise made to
	 * the operator who set the flag.
	 *
	 * <p>Phase two proves the hazard is real, and that rule 7 holds — the reactor never melts itself.
	 * Without that, "a bare station can run indefinitely in a wasteland" is not a strategy the player can
	 * choose, it is a fuse.
	 */
	public static void bareReactorProducesMeltsAndObeysTheSwitch(GameTestHelper helper) {
		boolean meltsBefore = Config.reactorMeltdownMeltsBlocks;
		int searchBefore = Config.reactorBareSearchRadius;
		int meltBefore = Config.reactorBareMeltRadius;
		int intervalBefore = Config.reactorBareMeltIntervalTicks;
		int minIntervalBefore = Config.reactorBareMeltMinIntervalTicks;
		int warnBefore = Config.reactorMeltWarnTicks;
		try {
			Config.reactorBareSearchRadius = 2;
			Config.reactorBareMeltRadius = TEST_MELT_RADIUS;
			Config.reactorBareMeltIntervalTicks = 4;
			Config.reactorBareMeltMinIntervalTicks = 1;
			Config.reactorMeltWarnTicks = 2;

			ReactorControllerBlockEntity brain = buildBareRig(helper);

			// ── phase one: the switch is off ──
			Config.reactorMeltdownMeltsBlocks = false;
			driveAt(helper, brain, BARE_CONTROLLER, 160);

			if (!brain.isBare()) {
				helper.fail("a controller with racks in reach and no room did not enter bare mode");
			}
			if (brain.getLastOutput() <= 0) {
				helper.fail("bare reactor produced nothing — the whole point is that it is a generator");
			}
			// Scaled and capped: never as much as the same rods would give inside a sealed shell.
			long room = (long) FuelRodAssemblyBlock.MAX_RODS * Config.reactorEuPerRod;
			if (brain.getLastOutput() >= room) {
				helper.fail("bare output " + brain.getLastOutput() + " was not below the room figure " + room);
			}
			if (countLava(helper) != 0) {
				helper.fail("reactorMeltdownMeltsBlocks=false still melted " + countLava(helper) + " block(s)");
			}
			// Delivery, not merely production — checked HERE, in the phase where nothing melts, so the
			// wire is still standing. The cable and the network both have to tick to enrol in the graph;
			// driving the manager alone leaves them invisible to it.
			CableBlockEntity wireTick = helper.getBlockEntity(BARE_CABLE, CableBlockEntity.class);
			for (int i = 0; i < 60; i++) {
				driveAt(helper, brain, BARE_CONTROLLER, 1);
				if (wireTick != null) {
					wireTick.serverTick(helper.getLevel(), helper.absolutePos(BARE_CABLE),
							helper.getBlockState(BARE_CABLE));
				}
				NetworkManager.tickAll(helper.getLevel());
			}
			CableBlockEntity wire = helper.getBlockEntity(BARE_CABLE, CableBlockEntity.class);
			if (wire == null) {
				helper.fail("the cable on the bare reactor has no block entity");
				return;
			}
			if (wire.getEnergyStorage().getAmount() <= 0) {
				helper.fail("a cable on a bare reactor received nothing — bare power cannot be plugged in");
			}

			// ── phase two: the switch is on ──
			Config.reactorMeltdownMeltsBlocks = true;
			driveAt(helper, brain, BARE_CONTROLLER, 160);

			if (countLava(helper) == 0) {
				helper.fail("a working bare reactor melted nothing within " + TEST_MELT_RADIUS + " block(s)");
			}
			// Rule 7: the reactor's own blocks survive its own hazard, or "run it forever" is a lie.
			if (!helper.getBlockState(BARE_CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
				helper.fail("the bare reactor melted its own controller");
			}
			if (!helper.getBlockState(BARE_RACK).is(ModContent.FUEL_ROD_ASSEMBLY.get())) {
				helper.fail("the bare reactor melted its own fuel rack");
			}
			helper.succeed();
		} finally {
			Config.reactorMeltdownMeltsBlocks = meltsBefore;
			Config.reactorBareSearchRadius = searchBefore;
			Config.reactorBareMeltRadius = meltBefore;
			Config.reactorBareMeltIntervalTicks = intervalBefore;
			Config.reactorBareMeltMinIntervalTicks = minIntervalBefore;
			Config.reactorMeltWarnTicks = warnBefore;
		}
	}

	/**
	 * A wall broken on a RUNNING reactor drops it softly into bare mode instead of stopping it.
	 *
	 * <p>The design asks for no separate emergency path: a breach is the same transition as "never built
	 * a room", just with a different history. What makes that possible is that the shielding-alloy shell
	 * CONDUCTS — the bare-mode walk reaches the columns through the walls that are still standing, so a
	 * room with a hole in it keeps driving its own racks at reduced power rather than going dark.
	 *
	 * <p>Worth a scenario because the two halves are easy to get separately right and jointly wrong: a
	 * connectivity walk that only stepped through racks would leave every breached room dead, and the
	 * player would read a deliberate design decision as the feature breaking.
	 */
	public static void breachingAWallDropsTheReactorIntoBareMode(GameTestHelper helper) {
		boolean meltsBefore = Config.reactorMeltdownMeltsBlocks;
		try {
			// The rig is 8 blocks wide and this room fills five of them; lava here would eat the very
			// walls whose conduction the scenario is measuring.
			Config.reactorMeltdownMeltsBlocks = false;

			buildRoom(helper);
			ReactorControllerBlockEntity brain = controller(helper);
			FuelRodAssemblyBlockEntity column = placeColumn(helper);
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

			driveUnderLoad(helper, brain, 120);
			if (brain.getStatus() != ReactorRoomStatus.FORMED) {
				helper.fail("room did not seal, so a breach cannot be under test: " + brain.getStatus());
			}
			int sealedOutput = brain.getLastOutput();
			if (sealedOutput <= 0) {
				helper.fail("sealed reactor produced nothing, so the fall to bare mode proves nothing");
			}

			// A hole in the ceiling, far from the controller and not in the floor the column stands on.
			helper.setBlock(new BlockPos(2, SHELL_MAX, 2), Blocks.AIR.defaultBlockState());
			driveUnderLoad(helper, brain, 120);

			if (brain.getStatus() == ReactorRoomStatus.FORMED) {
				helper.fail("a hole in the shell left the room reporting itself sealed");
			}
			if (!brain.isBare()) {
				helper.fail("a breached room did not fall into bare mode — the shell stopped conducting");
			}
			if (brain.getLastOutput() <= 0) {
				helper.fail("a breached reactor went dark instead of degrading; the fall must be SOFT");
			}
			if (brain.getLastOutput() >= sealedOutput) {
				helper.fail("bare output " + brain.getLastOutput() + " was not below the sealed "
						+ sealedOutput + " — the breach cost the player nothing");
			}
			helper.succeed();
		} finally {
			Config.reactorMeltdownMeltsBlocks = meltsBefore;
		}
	}

	/**
	 * Two controllers touching one stack of racks: exactly ONE of them burns it.
	 *
	 * <p><b>The test that should have existed from the start.</b> The acceptance criteria named this
	 * rule and the code implemented it, but nothing exercised it — and the player promptly asked the
	 * right question: what stops someone studding a shell with controllers and collecting the same rods
	 * with each? The answer has to be a scenario, not a paragraph.
	 *
	 * <p>Also pins the tie-break. The two controllers here are deliberately EQUIDISTANT from the rack,
	 * so distance alone cannot decide and the coordinate ordering has to. A rule that resolved a tie
	 * differently on each side would hand the rack to both — which is precisely the free energy the rule
	 * exists to prevent.
	 */
	public static void onlyOneControllerBurnsASharedRack(GameTestHelper helper) {
		int searchBefore = Config.reactorBareSearchRadius;
		boolean meltsBefore = Config.reactorMeltdownMeltsBlocks;
		try {
			Config.reactorBareSearchRadius = 4;
			// Nothing may melt here: a lava source in the middle of this rig would rewrite the very
			// adjacency the scenario is measuring.
			Config.reactorMeltdownMeltsBlocks = false;

			BlockPos rack = new BlockPos(3, 2, 3);
			BlockPos west = rack.west();
			BlockPos east = rack.east();
			helper.setBlock(rack, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
			FuelRodAssemblyBlockEntity fuel =
					helper.getBlockEntity(rack, FuelRodAssemblyBlockEntity.class);
			if (fuel == null) {
				helper.fail("shared rack has no block entity");
				return;
			}
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				fuel.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			// Both touch the rack, so both are legitimately connected to it — the arbitration cannot be
			// dodged by one of them simply being out of reach.
			for (BlockPos at : new BlockPos[] {west, east}) {
				helper.setBlock(at, ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
				helper.setBlock(at.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
			}
			ReactorControllerBlockEntity a = helper.getBlockEntity(west, ReactorControllerBlockEntity.class);
			ReactorControllerBlockEntity b = helper.getBlockEntity(east, ReactorControllerBlockEntity.class);
			if (a == null || b == null) {
				helper.fail("one of the two controllers has no block entity");
				return;
			}
			// Interleaved, because that is how the server ticks them: alternating exposes any rule whose
			// verdict depends on which machine looked first.
			for (int i = 0; i < 120; i++) {
				driveAt(helper, a, west, 1);
				driveAt(helper, b, east, 1);
			}

			int producing = (a.getLastOutput() > 0 ? 1 : 0) + (b.getLastOutput() > 0 ? 1 : 0);
			if (producing == 2) {
				helper.fail("both controllers burnt the same rack — " + a.getLastOutput() + " and "
						+ b.getLastOutput() + " EU/t out of one stack of rods");
			}
			if (producing == 0) {
				helper.fail("neither controller took the rack they are both touching");
			}
			int counted = a.getRods() + b.getRods();
			if (counted != FuelRodAssemblyBlock.MAX_RODS) {
				helper.fail("one stack of " + FuelRodAssemblyBlock.MAX_RODS + " rods was counted as "
						+ counted + " across the two controllers");
			}
			helper.succeed();
		} finally {
			Config.reactorBareSearchRadius = searchBefore;
			Config.reactorMeltdownMeltsBlocks = meltsBefore;
		}
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
		BlockPos floor = new BlockPos(2, 0, 2);
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		// The flag is checked EARLY and the damage LATE, because the two can be true at different times.
		// A meltdown sheds heat with every block it takes, so a room with a lot to eat cools itself back
		// under the line as it works; asserting both at the end once failed on exactly that.
		driveUnderLoad(helper, brain, 60);
		if (!brain.isMeltingDown()) {
			helper.fail("a room at " + ReactorCore.heatPercent(brain.getHeat(), Config.reactorHeatCapacity)
					+ "% of the heat scale did not report melting down");
		}

		driveUnderLoad(helper, brain, 340);
		if (!helper.getBlockState(pipe).is(Blocks.LAVA)) {
			helper.fail("the ordinary fluid pipe inside an overheating room did not melt first, it was "
					+ helper.getBlockState(pipe));
		}
		// The shell is the whole reason the room was built. If it goes, so does the feature.
		if (!helper.getBlockState(floor).is(ModContent.REACTOR_CASING.get())) {
			helper.fail("the meltdown ate the shell — the containment failed to contain");
		}
		if (!helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
			helper.fail("the meltdown ate its own controller");
		}
		helper.succeed();
	}

	/**
	 * A controller, a rack, a signal and a solid block of scenery to eat — no room anywhere.
	 *
	 * <p>The melt cube is packed with stone rather than left mostly empty so the picker cannot spend all
	 * of its attempts on air. A rig where the hazard only <em>usually</em> fires is a flaky test, and this
	 * repo has paid for those before.
	 */
	private static ReactorControllerBlockEntity buildBareRig(GameTestHelper helper) {
		// Packed around the RACK, because that is what the hazard now radiates from (MOD-469). The
		// controller and the cable sit inside this cube deliberately — the controller to prove it is
		// exempt, the cable to prove the player's wiring is not. The redstone block is one block BELOW
		// the cube on purpose: it powers the reactor and must not be eaten mid-test.
		forEachMeltCell(helper, (x, y, z) ->
				helper.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState()));
		// No shell of any kind: the controller stands in the open, which is precisely the state the
		// scan must recognise. Facing is irrelevant here — bare mode never walks a wall.
		helper.setBlock(BARE_CONTROLLER, ModContent.REACTOR_CONTROLLER.get().defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
		helper.setBlock(BARE_RACK, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
		helper.setBlock(BARE_SIGNAL, Blocks.REDSTONE_BLOCK.defaultBlockState());
		helper.setBlock(BARE_CABLE, ModContent.COPPER_CABLE.get().defaultBlockState());

		FuelRodAssemblyBlockEntity rack =
				helper.getBlockEntity(BARE_RACK, FuelRodAssemblyBlockEntity.class);
		if (rack == null) {
			helper.fail("bare fuel rack has no block entity");
			throw new IllegalStateException("unreachable");
		}
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			rack.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		ReactorControllerBlockEntity brain =
				helper.getBlockEntity(BARE_CONTROLLER, ReactorControllerBlockEntity.class);
		if (brain == null) {
			helper.fail("bare reactor controller has no block entity");
			throw new IllegalStateException("unreachable");
		}
		return brain;
	}

	/** Lava inside the bare rig's melt cube — counted rather than sampled, so nothing is missed. */
	private static int countLava(GameTestHelper helper) {
		int[] lava = {0};
		forEachMeltCell(helper, (x, y, z) -> {
			if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.LAVA)) {
				lava[0]++;
			}
		});
		return lava[0];
	}

	/** Every cell the bare hazard can reach in this rig — the cube around the RACK, not the controller. */
	private static void forEachMeltCell(GameTestHelper helper, CellAction action) {
		for (int x = BARE_RACK.getX() - TEST_MELT_RADIUS; x <= BARE_RACK.getX() + TEST_MELT_RADIUS; x++) {
			for (int y = BARE_RACK.getY() - TEST_MELT_RADIUS; y <= BARE_RACK.getY() + TEST_MELT_RADIUS; y++) {
				for (int z = BARE_RACK.getZ() - TEST_MELT_RADIUS; z <= BARE_RACK.getZ() + TEST_MELT_RADIUS; z++) {
					action.at(x, y, z);
				}
			}
		}
	}

	@FunctionalInterface
	private interface CellAction {
		void at(int x, int y, int z);
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
		driveAt(helper, brain, CONTROLLER, ticks);
	}

	/**
	 * Drives a controller standing somewhere OTHER than the room rig's own wall slot.
	 *
	 * <p>The position has to be passed rather than assumed. {@link #drive} used to read the block state
	 * at {@link #CONTROLLER} unconditionally, so a bare-mode rig — which puts its controller in open
	 * ground, nowhere near that cell — handed the tick a state of {@code minecraft:air} and the scan
	 * died asking air which way it was facing.
	 */
	private static void driveAt(GameTestHelper helper, ReactorControllerBlockEntity brain, BlockPos at,
			int ticks) {
		BlockPos absolute = helper.absolutePos(at);
		for (int i = 0; i < ticks; i++) {
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(at));
		}
	}
}

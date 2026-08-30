package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
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
import dev.alaindustrial.core.structure.ReactorMeltdown;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;

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

	/** Interior cell in front of the controller — where a lever hangs on the wall from the inside. */
	private static final BlockPos LEVER = new BlockPos(1, 2, 2);

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
			helper.fail("a room at " + ReactorCore.heatPercent(brain.getHeat(), Config.reactorHeatCapacity)
					+ "% of the heat scale did not report melting down");
		}
		assertEarned(helper, owner, "reactor_meltdown");

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

	/**
	 * The three reactor advancement steps are actually awarded, and to the controller's owner (MOD-473).
	 *
	 * <p><b>Written because nothing else can check them.</b> Two of the five nodes hang on a vanilla
	 * {@code inventory_changed} and are self-evident; these three fire from inside the controller's own
	 * tick, at moments no player is standing in — the scan that seals the room, the tick that first pays
	 * out power, and the first water the loop boils. Reading the source proves only that a call exists.
	 *
	 * <p>The owner is set by hand rather than by placing the block: {@code setPlacedBy} never runs here,
	 * because the rig builds the room with {@code helper.setBlock} instead of {@code BlockItem.place} —
	 * the same gap that keeps the whole advancement suite manual.
	 */
	public static void reactorMilestonesReachTheControllersOwner(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		brain.setOwner(owner.getUUID(), owner.getName().getString());

		// Three loaded columns, the same core the coolant scenario uses: one or two settle at a safe
		// temperature with no plumbing at all, so the loop would never boil and the steam step could
		// never be reached.
		List<FuelRodAssemblyBlockEntity> row = new ArrayList<>();
		for (int x = 1; x <= 3; x++) {
			FuelRodAssemblyBlockEntity column = placeColumnAt(helper, new BlockPos(x, 1, 2));
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			row.add(column);
		}
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		// Long enough to clear the scan interval (the room is not FORMED before the first sweep) and to
		// run the core past the coolant target, which is what makes the loop boil.
		driveUnderLoad(helper, brain, 1000);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("room did not seal: " + brain.getStatus());
		}
		assertEarned(helper, owner, "reactor_room");
		assertEarned(helper, owner, "reactor_power");

		// The steam step needs coolant to exist. Kept topped up and drained, exactly as a plumbed loop
		// behaves — see coolantCatchesACoreTheShellCannotHold for why filling once measures tank size.
		for (int i = 0; i < 200; i++) {
			for (FuelRodAssemblyBlockEntity column : row) {
				column.setTank(true, column.waterTank.capacity);
				column.setTank(false, 0);
			}
			driveUnderLoad(helper, brain, 1);
		}
		assertEarned(helper, owner, "reactor_steam");
		helper.succeed();
	}

	/**
	 * A reactor with no owner awards nobody and throws nothing (MOD-473).
	 *
	 * <p>Not a hypothetical: the {@code /ala demo} stand runs a reactor no player ever placed, and the
	 * fire path dereferences an owner. This is the negative half of the scenario above — without it,
	 * "the advancement was awarded" and "the code did not crash" are the same assertion.
	 */
	public static void anUnownedReactorAwardsNobody(GameTestHelper helper) {
		ServerPlayer bystander = AlaGameTestHelper.survivalPlayer(helper);
		buildRoom(helper);
		ReactorControllerBlockEntity brain = controller(helper);
		brain.setOwner(null, null);

		FuelRodAssemblyBlockEntity column = placeColumn(helper);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		driveUnderLoad(helper, brain, 1000);

		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("room did not seal, so the negative case is not under test: " + brain.getStatus());
		}
		if (brain.getLastOutput() <= 0) {
			helper.fail("reactor produced nothing, so the power step was never reached");
		}
		assertNotEarned(helper, bystander, "reactor_room");
		assertNotEarned(helper, bystander, "reactor_power");
		helper.succeed();
	}

	/** True when {@code player} has completed the mod advancement {@code name}. */
	private static boolean earned(GameTestHelper helper, ServerPlayer player, String name) {
		Identifier id = Industrialization.id(name);
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(id);
		if (holder == null) {
			helper.fail("no such advancement: " + id + " — the datapack did not load it");
			throw new IllegalStateException("unreachable");
		}
		return player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	private static void assertEarned(GameTestHelper helper, ServerPlayer player, String name) {
		if (!earned(helper, player, name)) {
			helper.fail("advancement " + name + " was not awarded to the controller's owner");
		}
	}

	private static void assertNotEarned(GameTestHelper helper, ServerPlayer player, String name) {
		assertNotEarned(helper, player, name, "by an UNOWNED reactor");
	}

	private static void assertNotEarned(GameTestHelper helper, ServerPlayer player, String name, String why) {
		if (earned(helper, player, name)) {
			helper.fail("advancement " + name + " was awarded " + why);
		}
	}

	@FunctionalInterface
	private interface CellAction {
		void at(int x, int y, int z);
	}

	// ── MOD-471: the accident at the top of the scale ──

	/**
	 * A room left at the top of its scale counts down, can be talked out of it, and finally blows up
	 * inside its own shell.
	 *
	 * <p><b>All three phases in ONE scenario, and that is a correctness requirement rather than
	 * tidiness.</b> {@code Config} is process-global and gametests in a batch run CONCURRENTLY, so a
	 * second scenario toggling {@code reactorBlastEnabled} would be toggling it for every other reactor
	 * ticking at that moment. One scenario means exactly one writer.
	 *
	 * <p><b>The countdown length is never touched.</b> Two neighbouring scenarios deliberately pin their
	 * cores at a hundred percent — {@code coolantCatchesACoreTheShellCannotHold} runs a thousand dry
	 * ticks against a full gauge — and they survive only because the shipped countdown is longer than
	 * their run. Shortening it here would blow up THEIR rigs, and the failure would be reported against
	 * their file. Instead this scenario drives past the real countdown, which costs nothing: the ticks
	 * are driven by hand, not waited for.
	 *
	 * <p><b>Why this can be tested at all:</b> five racks give a blast power of about 14, and a reactor
	 * wall absorbs 28–37 power per cell, so the shell contains it completely. The rig is 8x8x8 and the
	 * room fills five of that; an explosion that could leave the shell would be destroying the
	 * neighbouring tests' structures instead. Anyone raising the power constants must re-read this
	 * paragraph before assuming the test still isolates.
	 */
	public static void aCoreAtFullScaleCountsDownAndBlowsItsRoomApart(GameTestHelper helper) {
		boolean blastBefore = Config.reactorBlastEnabled;
		boolean fireBefore = Config.reactorBlastFire;
		int lavaBefore = Config.reactorBlastLavaCells;
		try {
			// Fire and lava are muted for the run, and this is the ONE mutation the concurrency rule
			// allows: no other rig in the batch has an exploding reactor, so no other rig reads these two
			// keys. They have to go, because both spread FURTHER than the blast — lava flows and fire
			// jumps — and the shell is full of holes by the time they appear.
			Config.reactorBlastFire = false;
			Config.reactorBlastLavaCells = 0;
			Config.reactorBlastEnabled = false;

			buildRoom(helper);
			ReactorControllerBlockEntity brain = controller(helper);
			// MOD-473: same reasoning as the meltdown scenario — this is the only rig in the batch allowed
			// to touch the blast keys, so the hidden accident step is asserted here. Phase one doubles as
			// the control: a countdown that runs out with the switch off must award nothing.
			ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
			brain.setOwner(owner.getUUID(), owner.getName().getString());
			for (BlockPos at : HOT_CORE) {
				FuelRodAssemblyBlockEntity column = placeColumnAt(helper, at);
				for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
					column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
				}
			}
			helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
			guardCountdownFitsThisRig(helper);

			// Phase one: the switch is off. The core still pins, still counts down, and still arrives at
			// zero — and the world is untouched. An operator who turned the damage off is entitled to see
			// their reactor's condition, not to have the whole mechanic go silent.
			driveUnderLoad(helper, brain, 200);
			if (brain.getBlastCountdown() <= 0) {
				helper.fail("a core at " + ReactorCore.heatPercent(brain.getHeat(), Config.reactorHeatCapacity)
						+ "% of the heat scale never armed the countdown");
			}
			int rolled = brain.getBlastCountdownTotal();
			if (rolled < Config.reactorBlastCountdownMinTicks
					|| rolled > Config.reactorBlastCountdownMaxTicks) {
				helper.fail("the countdown rolled " + rolled + ", outside its own configured range");
			}
			driveUnderLoad(helper, brain, rolled + 20);
			if (!helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
				helper.fail("the blast switch was off and the reactor exploded anyway");
			}
			assertNotEarned(helper, owner, "reactor_blast",
					"by a countdown that ran out with the blast switch off");

			// Phase two: pull the lever. The gauge comes off a hundred and the countdown must clear — this
			// is the promise that there is no point of no return.
			helper.setBlock(CONTROLLER.west(), Blocks.AIR.defaultBlockState());
			// Long enough to clear the release window, plus margin. Cancelling deliberately costs a few
			// seconds of genuinely cooler core — that is what stops a redstone clock resetting the timer —
			// so a scram followed by a couple of ticks proves nothing any more.
			drive(helper, brain, Config.reactorBlastReleaseTicks + 40);
			if (brain.getBlastCountdown() != 0) {
				helper.fail("scramming the reactor and holding it cool for "
						+ (Config.reactorBlastReleaseTicks + 40) + " ticks left the countdown running at "
						+ brain.getBlastCountdown());
			}

			// Phase three: switch the damage back on, re-arm, and let it finish.
			//
			// Refuelled first, and that is not tidiness. Phase one deliberately sits through a WHOLE
			// countdown to prove the switch protects the world, and a second full countdown after it puts
			// the run past seven thousand ticks of full-power operation — while twenty rods are good for
			// about five and a half thousand. The core ran dry mid-phase-three, cooled to 17 %, and the
			// release window quite correctly called the accident off; the scenario then reported it as
			// "the controller survived its own explosion", which is a true sentence about the wrong thing.
			refuelHotCore(helper);
			Config.reactorBlastEnabled = true;
			helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
			driveUnderLoad(helper, brain, 200);
			if (brain.getBlastCountdown() <= 0) {
				helper.fail("the reactor did not re-arm after the signal came back");
			}
			driveUntilItBlows(helper, brain, brain.getBlastCountdown() + 5);

			if (helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
				helper.fail("the countdown ran out and the controller survived its own explosion. heat="
						+ ReactorCore.heatPercent(brain.getHeat(), Config.reactorHeatCapacity) + "% countdown="
						+ brain.getBlastCountdown() + "/" + brain.getBlastCountdownTotal()
						+ " meltdown=" + brain.isMeltingDown() + " melts=" + brain.getMeltsScheduled());
			}
			// The containment is the whole reason the room costs what it costs. The far wall is four
			// blocks from the epicentre through solid shell; if that has gone, the blast is not being
			// contained and the neighbouring tests are next.
			BlockPos farWall = new BlockPos(SHELL_MAX, 2, 2);
			if (!helper.getBlockState(farWall).is(ModContent.REACTOR_CASING.get())) {
				helper.fail("the blast blew through the far wall — containment failed, and in a live world "
						+ "this rig's neighbours would be gone too");
			}
			assertEarned(helper, owner, "reactor_blast");
			// And the racks the drone was painted onto must not be left humming: the controller is gone,
			// so nothing in the world could ever switch them off again (the reason unformOnRemoval runs
			// before the blast rather than from a removal hook that never fires for an explosion).
			for (BlockPos at : HOT_CORE) {
				BlockState state = helper.getBlockState(at);
				if (state.getBlock() instanceof FuelRodAssemblyBlock
						&& state.getValue(FuelRodAssemblyBlock.ACTIVE)) {
					helper.fail("a rack at " + at + " survived the blast still marked ACTIVE, and with the "
							+ "controller gone nothing can ever silence it");
				}
			}
			helper.succeed();
		} finally {
			Config.reactorBlastEnabled = blastBefore;
			Config.reactorBlastFire = fireBefore;
			Config.reactorBlastLavaCells = lavaBefore;
		}
	}

	/**
	 * A full buffer does not stop the reactor cooking itself.
	 *
	 * <p><b>Straight from a playtest screenshot.</b> The player built a sealed room, racked twelve rods,
	 * powered it, plumbed no coolant — and the panel read "Room sealed / Rods: 12 / Output: buffer full /
	 * Heat 0%". Nobody had switched the reactor off. It had simply run out of somewhere to put the
	 * energy, and with heat charged against the energy actually banked, that made the temperature fall to
	 * zero and stay there. A reactor that goes cold the moment its warehouse fills is not a reactor.
	 *
	 * <p>So this drives a core with a DELIBERATELY full buffer and nothing drawing from it, and demands
	 * that the gauge climbs anyway. It is the third time this mod has had to learn that a hazard must key
	 * on the reaction rather than on the sale — MOD-469 learned it for the bare core's melting, MOD-471
	 * for the bare core's instability, and now for the room's own heat. A test rather than a comment,
	 * because the tidy-up that would undo it ("charge heat against what was produced") looks like a
	 * simplification from every angle except this one.
	 *
	 * <p>Fuel is asserted NOT to burn in the same breath. The two rules are deliberately different — a
	 * rod is an amount of energy, so uranium is spent only on energy delivered — and pinning them
	 * together is what stops a future change from "fixing" the asymmetry by moving the wrong one.
	 */
	public static void aFullBufferStillCooksTheCore(GameTestHelper helper) {
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

		// Filled to the brim and topped back up every tick: this is "the grid is full and nothing is
		// drawing", the exact state of the screenshot.
		int wearBefore = 0;
		for (FuelRodAssemblyBlockEntity column : row) {
			wearBefore += totalDamage(column.contents());
		}
		BlockPos absolute = helper.absolutePos(CONTROLLER);
		for (int tick = 0; tick < 400; tick++) {
			brain.getEnergyStorage().setAmountUntracked(brain.getEnergyStorage().getCapacity());
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(CONTROLLER));
		}

		if (brain.getIdleReason() != ReactorIdleReason.BUFFER_FULL) {
			helper.fail("expected the panel to report BUFFER_FULL, got " + brain.getIdleReason()
					+ " — the scenario is not in the state it means to test");
		}
		int heat = ReactorCore.heatPercent(brain.getHeat(), Config.reactorHeatCapacity);
		if (heat <= 0) {
			helper.fail("a sealed, fuelled, powered reactor with a full buffer sat at " + heat
					+ "% heat. Nobody switched it off — a full warehouse is not a scram, and a core with "
					+ "no coolant has to cook itself whether or not anyone is buying the power.");
		}
		if (brain.getBlastCountdown() <= 0) {
			helper.fail("a dry core ran 400 ticks against a full buffer and reached only " + heat
					+ "% — it must still reach the top of the scale and arm the accident");
		}
		int wearAfter = 0;
		for (FuelRodAssemblyBlockEntity column : row) {
			wearAfter += totalDamage(column.contents());
		}
		if (wearAfter != wearBefore) {
			helper.fail("uranium was spent while nothing drew a single EU: wear moved from " + wearBefore
					+ " to " + wearAfter + ". Heat follows the reaction, fuel follows the sale — moving "
					+ "the second one breaks the rod-is-an-amount-of-energy invariant the fuel cycle "
					+ "rests on.");
		}
		// Left safe: an armed countdown in a shared world is how a test grows a blast radius nobody
		// asked for.
		helper.setBlock(CONTROLLER.west(), Blocks.AIR.defaultBlockState());
		drive(helper, brain, 40);
		helper.succeed();
	}

	/**
	 * A redstone clock on the controller does not save the reactor.
	 *
	 * <p><b>The player's own exploit, turned into a test.</b> Heat is clamped at the top of the scale,
	 * so a single tick without a signal is enough to read 99 % — and while the countdown was cleared on
	 * that reading, cutting the redstone for one tick in twenty reset a three-minute timer while the
	 * reactor went on running at ninety-five percent duty. Free power, no consequence, one repeater.
	 *
	 * <p>This drives exactly that pattern and demands the reactor still dies. Its partner is
	 * {@link #aCoreAtFullScaleCountsDownAndBlowsItsRoomApart}, which proves the honest scram still works
	 * — the two together are the whole rule: cancelling costs seconds of genuinely cooler core, and a
	 * blink buys nothing.
	 */
	public static void aRedstoneClockDoesNotSaveTheReactor(GameTestHelper helper) {
		boolean fireBefore = Config.reactorBlastFire;
		int lavaBefore = Config.reactorBlastLavaCells;
		try {
			Config.reactorBlastFire = false;
			Config.reactorBlastLavaCells = 0;

			buildRoom(helper);
			ReactorControllerBlockEntity brain = controller(helper);
			// MOD-473: same reasoning as the meltdown scenario — this is the only rig in the batch allowed
			// to touch the blast keys, so the hidden accident step is asserted here. Phase one doubles as
			// the control: a countdown that runs out with the switch off must award nothing.
			ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
			brain.setOwner(owner.getUUID(), owner.getName().getString());
			for (BlockPos at : HOT_CORE) {
				FuelRodAssemblyBlockEntity column = placeColumnAt(helper, at);
				for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
					column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
				}
			}
			BlockPos signal = CONTROLLER.west();
			helper.setBlock(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
			guardCountdownFitsThisRig(helper);
			driveUnderLoad(helper, brain, 200);
			if (brain.getBlastCountdown() <= 0) {
				helper.fail("the core never armed, so the clock has nothing to defeat");
			}

			// Nineteen ticks on, one tick off, over and over — the cheapest repeater loop a player can
			// build, and the one that used to make a reactor immortal.
			int limit = Config.reactorBlastCountdownMaxTicks * 30;
			for (int tick = 0; tick < limit; tick++) {
				boolean on = tick % 20 != 19;
				helper.setBlock(signal, on ? Blocks.REDSTONE_BLOCK.defaultBlockState()
						: Blocks.AIR.defaultBlockState());
				driveUnderLoad(helper, brain, 1);
				if (!helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
					helper.succeed();
					return;
				}
			}
			helper.fail("a 95 %-duty redstone clock kept the reactor alive for " + limit + " ticks — "
					+ "more than thirty times its own countdown. Blinking the signal must not buy "
					+ "immunity; only holding the core under the line for reactorBlastReleaseTicks may.");
		} finally {
			Config.reactorBlastFire = fireBefore;
			Config.reactorBlastLavaCells = lavaBefore;
		}
	}

	/**
	 * A bare cluster settles below the ceiling while it is small, and runs away once it is not.
	 *
	 * <p><b>Deliberately stops at the armed countdown and never lets the blast happen.</b> A bare core
	 * has no shell, so the same power that a room swallows whole would throw debris about sixteen blocks
	 * — twice the rig — straight into the neighbouring tests. What needs proving here is the SCALE and
	 * the arming; the blast itself is proven next door, inside a shell that can hold it.
	 *
	 * <p>This is the lava farm's contract, written as a test. Players build a bare reactor over a
	 * cobblestone platform and pump the lava it melts; the small-cluster half of this scenario is the
	 * promise that such a farm keeps working for ever, and the large-cluster half is the promise that it
	 * has a limit they can see coming.
	 */
	public static void aBareClusterSettlesUntilItIsTooBig(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = buildBareRig(helper);
		powerBareRigWithAMeltproofLever(helper);
		driveAt(helper, brain, BARE_CONTROLLER, 600);
		if (!brain.isBare()) {
			helper.fail("the bare rig did not enter bare mode, so nothing below is under test");
		}
		int settled = ReactorCore.heatPercent(brain.getInstability(), Config.reactorBareInstabilityCapacity);
		if (settled >= 100) {
			helper.fail("a single rack reached " + settled + "% instability — the lava farm players "
					+ "already built would explode, which is exactly what this feature promised not to do");
		}
		if (brain.getBlastCountdown() != 0) {
			helper.fail("a single rack armed the countdown at " + settled + "% instability");
		}
		if (settled <= 0) {
			helper.fail("a working bare reactor showed no instability at all — the scale is not running");
		}
		// Now make the pile too big. Three more racks stacked on the first, still inside the melt cube
		// so nothing new leaves the rig.
		for (BlockPos at : BARE_EXTRA_RACKS) {
			helper.setBlock(at, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState());
			FuelRodAssemblyBlockEntity rack = helper.getBlockEntity(at, FuelRodAssemblyBlockEntity.class);
			if (rack == null) {
				helper.fail("extra bare rack has no block entity at " + at);
				return;
			}
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				rack.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
		}
		driveAt(helper, brain, BARE_CONTROLLER, 900);
		if (brain.getBlastCountdown() <= 0) {
			helper.fail("four racks in the open reached only "
					+ ReactorCore.heatPercent(brain.getInstability(), Config.reactorBareInstabilityCapacity)
					+ "% instability on " + brain.getRods() + " rods and never armed — a bare pile would "
					+ "have no limit at all. A figure BELOW the one-rack settle means the reactor was "
					+ "switched off for part of the run, not that the scale is mistuned.");
		}
		// Wound back down before the scenario ends: the countdown is armed, and leaving a live one in a
		// shared world is how a test grows a blast radius nobody asked for.
		scramBareRig(helper);
		driveAt(helper, brain, BARE_CONTROLLER, Config.reactorBlastReleaseTicks + 200);
		if (brain.getBlastCountdown() != 0) {
			helper.fail("scramming a bare pile and holding it down left the countdown running at "
					+ brain.getBlastCountdown());
		}
		helper.succeed();
	}

	/**
	 * The lava farm costs no fuel, and this test exists to keep it that way.
	 *
	 * <p><b>A pin on a feature, not on a bug.</b> Melting hangs on the reaction rather than on the sale
	 * (MOD-469's own playtest finding), while fuel is spent only when energy is actually produced. Put
	 * together, a bare core with a full buffer melts the scenery for ever and burns nothing — and players
	 * turned that into lava farms, which the design has now blessed.
	 *
	 * <p>That makes it fragile in a specific way: it is not written down in any single place, it emerges
	 * from two decisions in different files, and the obvious tidy-up ("everything dangerous should cost
	 * something") would silently delete it. This scenario is what turns that from a comment into a red
	 * build.
	 */
	public static void aLavaFarmBurnsNoFuel(GameTestHelper helper) {
		ReactorControllerBlockEntity brain = buildBareRig(helper);
		powerBareRigWithAMeltproofLever(helper);
		buryBareRigInStone(helper);
		FuelRodAssemblyBlockEntity rack =
				helper.getBlockEntity(BARE_RACK, FuelRodAssemblyBlockEntity.class);
		if (rack == null) {
			helper.fail("bare rack has no block entity");
			return;
		}
		// The buffer is filled and left full: nothing is drawing, so nothing is produced, so — by the
		// fuel rule — nothing is burnt. The hazard is supposed to carry on regardless.
		brain.getEnergyStorage().setAmountUntracked(brain.getEnergyStorage().getCapacity());
		List<ItemStack> before = rack.contents();
		int wearBefore = totalDamage(before);
		driveAt(helper, brain, BARE_CONTROLLER, 600);
		brain.getEnergyStorage().setAmountUntracked(brain.getEnergyStorage().getCapacity());
		driveAt(helper, brain, BARE_CONTROLLER, 600);

		// Asked of the reactor, not of the world. The melt reaches five blocks from the rack, the rig is
		// eight across, and where the victims land differs between the loaders — a version of this that
		// counted lava passed on Fabric and failed on NeoForge with nothing between them but structure
		// layout. Shrinking reactorBareMeltRadius to make it countable is not available either: the
		// neighbouring bare scenario already writes that key, and Config is process-global.
		if (brain.getMeltsScheduled() <= 0) {
			helper.fail("a bare reactor with a full buffer melted nothing — the lava farm players built "
					+ "on this mechanic has stopped working. bare=" + brain.isBare() + " rods="
					+ brain.getRods() + " output=" + brain.getLastOutput());
		}
		int wearAfter = totalDamage(rack.contents());
		if (wearAfter != wearBefore) {
			helper.fail("the lava farm started costing fuel: rod wear moved from " + wearBefore + " to "
					+ wearAfter + ". That is a deliberate feature being deleted, not a bug being fixed — "
					+ "see MOD-471 and the design note on the bare reactor.");
		}
		helper.succeed();
	}

	/**
	 * With the blast switched off there is no crater — and therefore no lava, no fire and no fallout
	 * either.
	 *
	 * <p><b>This is the land-claim rule, tested through the only lever a gametest has.</b> The aftermath
	 * is placed exclusively in cells the explosion actually emptied, so if anything refuses the blast —
	 * a protection mod on a server, or this config key here — the diff is empty and nothing is left
	 * behind. Without that rule the lava would pour into a neighbour's claim through an explosion that
	 * mod had just blocked, which is a griefing tool rather than a hazard.
	 */
	public static void aBlockedExplosionLeavesNoAftermath(GameTestHelper helper) {
		boolean blastBefore = Config.reactorBlastEnabled;
		try {
			Config.reactorBlastEnabled = false;
			buildRoom(helper);
			ReactorControllerBlockEntity brain = controller(helper);
			// MOD-473: same reasoning as the meltdown scenario — this is the only rig in the batch allowed
			// to touch the blast keys, so the hidden accident step is asserted here. Phase one doubles as
			// the control: a countdown that runs out with the switch off must award nothing.
			ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
			brain.setOwner(owner.getUUID(), owner.getName().getString());
			for (BlockPos at : HOT_CORE) {
				FuelRodAssemblyBlockEntity column = placeColumnAt(helper, at);
				for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
					column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
				}
			}
			helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
			guardCountdownFitsThisRig(helper);
			driveUnderLoad(helper, brain, 200);
			driveUnderLoad(helper, brain, brain.getBlastCountdown() + 5);

			for (int x = 0; x <= SHELL_MAX; x++) {
				for (int y = 0; y <= SHELL_MAX; y++) {
					for (int z = 0; z <= SHELL_MAX; z++) {
						BlockState state = helper.getBlockState(new BlockPos(x, y, z));
						if (state.is(ModContent.IRRADIATED_SOIL.get())) {
							helper.fail("fallout appeared at " + x + "," + y + "," + z + " through a blast "
									+ "that never happened — the aftermath is not keyed to the damage");
						}
					}
				}
			}
			helper.succeed();
		} finally {
			Config.reactorBlastEnabled = blastBefore;
		}
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

	/**
	 * Fails loudly if the shipped countdown is ever shortened below what the 100 %-pinning scenarios
	 * need.
	 *
	 * <p>Three scenarios in this file deliberately hold a core at the top of its scale, two of them for
	 * hundreds of ticks. They are safe only because the countdown outlasts them. If somebody lowers it,
	 * the failure would otherwise surface as an unexplained explosion in a NEIGHBOURING test's rig — the
	 * exact shape of debugging this repository has already paid for once with a config mutation.
	 */
	private static void guardCountdownFitsThisRig(GameTestHelper helper) {
		if (Config.reactorBlastCountdownMinTicks < PINNED_RUN_TICKS) {
			helper.fail("reactorBlastCountdownMinTicks is " + Config.reactorBlastCountdownMinTicks
					+ ", shorter than the " + PINNED_RUN_TICKS + " ticks the scenarios that pin a core at "
					+ "100 % run for. Raise it or shorten them — do NOT mutate it per scenario: the "
					+ "neighbours in this batch read the same global and would explode instead.");
		}
	}

	/**
	 * Drives the reactor until it explodes, and then stops.
	 *
	 * <p><b>Stopping matters.</b> A gametest holds the block entity by reference, so it can go on
	 * ticking one the world has already removed — and a controller ticked after its own death happily
	 * re-paints the drone flag onto the racks that survived, which is precisely the state this scenario
	 * asserts must never be left behind. In a real world the block entity is detached and never ticks
	 * again; the loop has to model that rather than out-tick reality.
	 */
	private static void driveUntilItBlows(GameTestHelper helper, ReactorControllerBlockEntity brain,
			int limit) {
		for (int i = 0; i < limit; i++) {
			driveUnderLoad(helper, brain, 1);
			if (!helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
				return;
			}
		}
	}

	/**
	 * Powers the bare rig with a MELTPROOF lever instead of the redstone block the rig ships with.
	 *
	 * <p><b>Written after the NeoForge lane failed and the Fabric lane passed on the same code.</b> The
	 * bare hazard melts within {@code reactorBareMeltRadius} — five blocks, the shipped value — of any
	 * charged rack, and in an 8x8x8 rig that sphere covers everything, the rig's own redstone block
	 * included. Once it melts, the reactor scrams, the instability decays, and the scenario measures a
	 * switched-off reactor while reporting it as a design failure: the run that caught this said "four
	 * racks reached only 12 %", which is BELOW where one rack settles.
	 *
	 * <p>The neighbouring bare scenario solves it by shrinking the melt radius through {@code Config},
	 * which is not available here: {@code Config} is process-global and gametests run concurrently, so a
	 * second writer of the same key would be corrupting that scenario's run (the MOD-469 lesson, from
	 * the other side). A reactor lever is made of shielding alloy and carries the {@code meltproof} tag,
	 * so it powers the reactor without anything being able to take it away — no mutation, no race.
	 */
	private static void powerBareRigWithAMeltproofLever(GameTestHelper helper) {
		helper.setBlock(BARE_SIGNAL, Blocks.AIR.defaultBlockState());
		// Hangs on the controller's west face: FACING is the way the lever LOOKS, so it attaches to the
		// block on the opposite side — the controller itself, which is meltproof too.
		helper.setBlock(BARE_LEVER, ModContent.REACTOR_LEVER.get().defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST)
				.setValue(LeverBlock.POWERED, true));
	}

	/** Flips that lever off — the scram, without giving the hazard anything to destroy. */
	private static void scramBareRig(GameTestHelper helper) {
		BlockState lever = helper.getBlockState(BARE_LEVER);
		if (lever.getBlock() instanceof LeverBlock) {
			helper.setBlock(BARE_LEVER, lever.setValue(LeverBlock.POWERED, false));
		}
	}

	/**
	 * Packs every empty cell of the rig with stone, so the hazard cannot miss.
	 *
	 * <p><b>Determinism, not scenery.</b> The melt draws sixteen random positions from a sphere of
	 * {@code reactorBareMeltRadius} — five blocks, the shipped value — and gives up if none of them
	 * holds anything meltable. The bare rig is mostly air, so a round hits its little 3x3x3 stone cube
	 * about a quarter of the time and the scenario passes or fails on the dice. Shrinking the radius is
	 * how the neighbouring bare scenario solves it, and that door is closed here: {@code Config} is
	 * process-global and the two would race. Filling the rig instead makes every round land, without
	 * touching a shared key.
	 */
	private static void buryBareRigInStone(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int y = 0; y <= 7; y++) {
				for (int z = 0; z <= 7; z++) {
					BlockPos at = new BlockPos(x, y, z);
					if (helper.getBlockState(at).isAir()) {
						helper.setBlock(at, Blocks.STONE.defaultBlockState());
					}
				}
			}
		}
	}

	/** The controller's west face, where the meltproof lever hangs. Inside the melt cube on purpose. */
	private static final BlockPos BARE_LEVER = new BlockPos(2, 2, 3);

	/**
	 * Puts fresh rods back in the packed core, so a long scenario does not quietly run out of fuel.
	 *
	 * <p>Clears the cell to air FIRST. A burnt-out rack keeps the spent casings, so
	 * {@code insertRod} on it refuses and quietly changes nothing; and re-placing the same block state
	 * over itself is a no-op, so the old block entity — with its four empty casings — survives. The
	 * first version of this helper did exactly that and the core stayed dry.
	 */
	private static void refuelHotCore(GameTestHelper helper) {
		for (BlockPos at : HOT_CORE) {
			helper.setBlock(at, Blocks.AIR.defaultBlockState());
			FuelRodAssemblyBlockEntity column = placeColumnAt(helper, at);
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
		}
	}

	/** Total durability spent across a rack's rods — the ledger the lava-farm pin watches. */
	private static int totalDamage(List<ItemStack> stacks) {
		int damage = 0;
		for (ItemStack stack : stacks) {
			damage += stack.getDamageValue();
		}
		return damage;
	}

	/** Five racks packed on the floor: enough adjacency to pin the gauge within a couple of hundred ticks. */
	private static final BlockPos[] HOT_CORE = {
		new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
		new BlockPos(1, 1, 2), new BlockPos(2, 1, 2),
	};

	/** Three more racks stacked on the bare rig's own, taking the pile past its equilibrium. */
	private static final BlockPos[] BARE_EXTRA_RACKS = {
		new BlockPos(3, 4, 3), new BlockPos(2, 3, 3), new BlockPos(4, 3, 3),
	};

	/** The longest a scenario here holds a pinned core; the countdown must outlast it. */
	private static final int PINNED_RUN_TICKS = 1400;

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

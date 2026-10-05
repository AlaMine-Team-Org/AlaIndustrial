package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.ReactorConsoleScenarios.logCount;
import static dev.alaindustrial.gametest.ReactorConsoleScenarios.logKinds;
import static dev.alaindustrial.gametest.ReactorRig.BARE_CABLE;
import static dev.alaindustrial.gametest.ReactorRig.BARE_CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.BARE_EXTRA_RACKS;
import static dev.alaindustrial.gametest.ReactorRig.BARE_LEVER;
import static dev.alaindustrial.gametest.ReactorRig.BARE_RACK;
import static dev.alaindustrial.gametest.ReactorRig.BARE_SIGNAL;
import static dev.alaindustrial.gametest.ReactorRig.CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.SHELL_MAX;
import static dev.alaindustrial.gametest.ReactorRig.TEST_MELT_RADIUS;
import static dev.alaindustrial.gametest.ReactorRig.buildBareRig;
import static dev.alaindustrial.gametest.ReactorRig.buildRoom;
import static dev.alaindustrial.gametest.ReactorRig.controller;
import static dev.alaindustrial.gametest.ReactorRig.driveAt;
import static dev.alaindustrial.gametest.ReactorRig.driveUnderLoad;
import static dev.alaindustrial.gametest.ReactorRig.forEachMeltCell;
import static dev.alaindustrial.gametest.ReactorRig.placeColumn;
import static dev.alaindustrial.gametest.ReactorRig.placeColumnAt;
import static dev.alaindustrial.gametest.ReactorRig.totalDamage;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.reactor.ReactorChannels;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.core.structure.ReactorZone;
import dev.alaindustrial.network.ReactorZonePayload;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * World scenarios of the bare reactor (MOD-469, MOD-471): a controller with no room drives the racks it
 * reaches, melts the scenery, falls softly into bare mode on a breach, shares no rack with a rival, settles
 * while its pile is small and runs away once it is not, and farms lava for no fuel. Reached through the
 * {@link ReactorScenarios} facade, whose names the two lanes run (MOD-713, TST-2).
 */
final class ReactorBareModeScenarios {

	private ReactorBareModeScenarios() {}

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
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("reactorBareSearchRadius", 2);
			o.set("reactorBareMeltRadius", TEST_MELT_RADIUS);
			o.set("reactorBareMeltIntervalTicks", 4);
			o.set("reactorBareMeltMinIntervalTicks", 1);
			o.set("reactorMeltWarnTicks", 2);

			ReactorControllerBlockEntity brain = buildBareRig(helper);

			// ── phase one: the switch is off ──
			o.set("reactorMeltdownMeltsBlocks", false);
			// MOD-662: a rack that reports boiling, so the only thing between it and a steam puff is the rule
			// that a bare core has no loop to show. A bare core never boils by itself — without this the
			// "no puffs" check below would pass on an empty question.
			FuelRodAssemblyBlockEntity bareRack = helper.getBlockEntity(BARE_RACK, FuelRodAssemblyBlockEntity.class);
			if (bareRack == null) {
				helper.fail("bare fuel rack has no block entity");
				return;
			}
			bareRack.setTank(true, bareRack.waterTank.capacity);
			if (bareRack.boil(1) != 1 || !bareRack.isBoiling()) {
				helper.fail("the bare rack could not be made to report boiling, so the puff check proves nothing");
			}
			driveAt(helper, brain, BARE_CONTROLLER, 160);

			if (!brain.isBare()) {
				helper.fail("a controller with racks in reach and no room did not enter bare mode");
			}
			if (brain.getSteamPuffsSent() != 0) {
				helper.fail("a bare core puffed steam " + brain.getSteamPuffsSent()
						+ " time(s): it has no loop, and a puff over it reports one that does not exist");
			}
			if (brain.getLastOutput() <= 0) {
				helper.fail("bare reactor produced nothing — the whole point is that it is a generator");
			}
			// Scaled and capped: never as much as the same rods would give inside a sealed shell.
			long room = (long) FuelRodAssemblyBlock.MAX_RODS * ReactorConfig.reactorEuPerRod;
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
			// The Core tab shows a rack of spent casings too (review, MOD-620). It burns nothing, so it is not among
			// the racks the reactor drives, and a tab built from those alone lost the rack exactly when it needed a
			// player. Checked in this phase, where nothing melts; the cell is stone again before phase two counts lava.
			BlockPos spentAt = BARE_RACK.north();
			placeColumnAt(helper, spentAt).insertRod(new ItemStack(ModContent.EMPTY_FUEL_ROD.get()));
			driveAt(helper, brain, BARE_CONTROLLER, ReactorConfig.reactorScanIntervalTicks + 1);
			ReactorZonePayload zone = brain.zoneSnapshot(3);
			if (zone.stacks().size() != 2 || zone.width() != 1 || zone.depth() != 2) {
				helper.fail("a bare pile of a fuelled rack and a rack of spent casings is a 1 x 2 zone of two stacks,"
						+ " got " + zone.width() + " x " + zone.depth() + " with " + zone.stacks().size()
						+ " stack(s)");
				return;
			}
			ReactorZone.Stack spent = zone.stacks().get(0);
			if (spent.fuelledRods() != 0 || spent.spentRods() != 1) {
				helper.fail("the northern stack should be the spent casing, got " + spent.fuelledRods()
						+ " fuelled and " + spent.spentRods() + " spent");
			}
			if (zone.originDx() != spentAt.getX() - BARE_CONTROLLER.getX()
					|| zone.originDz() != spentAt.getZ() - BARE_CONTROLLER.getZ()) {
				helper.fail("bare zone corner is " + zone.originDx() + ", " + zone.originDz()
						+ " from the controller; the spent rack stands at " + (spentAt.getX() - BARE_CONTROLLER.getX())
						+ ", " + (spentAt.getZ() - BARE_CONTROLLER.getZ()));
			}
			helper.setBlock(spentAt, Blocks.STONE.defaultBlockState());
			driveAt(helper, brain, BARE_CONTROLLER, ReactorConfig.reactorScanIntervalTicks + 1);

			// ── phase two: the switch is on ──
			o.set("reactorMeltdownMeltsBlocks", true);
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
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			// The rig is 8 blocks wide and this room fills five of them; lava here would eat the very
			// walls whose conduction the scenario is measuring.
			o.set("reactorMeltdownMeltsBlocks", false);

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
			// The box travels with the breach (MOD-619): the «Room» tab draws the walls around the hole.
			ContainerData data = brain.getDataAccess();
			int inner = SHELL_MAX - 1;
			if (data.get(ReactorChannels.SIZE_X.ordinal()) != inner
					|| data.get(ReactorChannels.SIZE_Y.ordinal()) != inner
					|| data.get(ReactorChannels.SIZE_Z.ordinal()) != inner) {
				helper.fail("a breached room did not report the box the hole was found in");
			}
			if (data.get(ReactorChannels.BOX_WEST.ordinal()) != 1 - CONTROLLER.getX()
					|| data.get(ReactorChannels.BOX_NORTH.ordinal()) != 1 - CONTROLLER.getZ()) {
				helper.fail("the box's edges read " + data.get(ReactorChannels.BOX_WEST.ordinal()) + " west, "
						+ data.get(ReactorChannels.BOX_NORTH.ordinal()) + " north of the controller; expected "
						+ (1 - CONTROLLER.getX()) + ", " + (1 - CONTROLLER.getZ()));
			}
			int holeDx = data.get(ReactorChannels.HOLE_FIRST);
			int holeDy = data.get(ReactorChannels.HOLE_FIRST + 1);
			int holeDz = data.get(ReactorChannels.HOLE_FIRST + 2);
			if (data.get(ReactorChannels.HOLE_COUNT.ordinal()) != 1 || holeDx != 2 - CONTROLLER.getX()
					|| holeDy != SHELL_MAX - CONTROLLER.getY() || holeDz != 2 - CONTROLLER.getZ()) {
				helper.fail("the breach listed " + data.get(ReactorChannels.HOLE_COUNT.ordinal())
						+ " holes, the first at " + holeDx + ", " + holeDy + ", " + holeDz
						+ " from the controller; expected the one hole in the ceiling");
			}
			if (brain.getLastOutput() <= 0) {
				helper.fail("a breached reactor went dark instead of degrading; the fall must be SOFT");
			}
			if (brain.getLastOutput() >= sealedOutput) {
				helper.fail("bare output " + brain.getLastOutput() + " was not below the sealed "
						+ sealedOutput + " — the breach cost the player nothing");
			}
			// MOD-622: the fall is one line for the wall and one for bare mode, and the reaction never stopped.
			if (logCount(brain, ReactorLog.Kind.ROOM_UNSEALED) != 1
					|| logCount(brain, ReactorLog.Kind.BARE_ENTERED) != 1
					|| logCount(brain, ReactorLog.Kind.REACTION_STARTED) != 1 || brain.logEntries().stream()
							.anyMatch(e -> e.kind() == ReactorLog.Kind.REACTION_STOPPED
									|| e.kind() == ReactorLog.Kind.REACTION_SCRAMMED)) {
				helper.fail("the breach logged " + logKinds(brain)
						+ "; expected one unsealed, one bare-mode line and no stop");
			}
			helper.succeed();
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
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("reactorBareSearchRadius", 4);
			// Nothing may melt here: a lava source in the middle of this rig would rewrite the very
			// adjacency the scenario is measuring.
			o.set("reactorMeltdownMeltsBlocks", false);

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
		int settled = ReactorCore.heatPercent(brain.getInstability(), ReactorConfig.reactorBareInstabilityCapacity);
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
		// One rack settles well under the warning line, so the siren has had nothing to say.
		if (brain.hasSoundedOverheatAlarm()) {
			helper.fail("a single rack at " + settled + "% instability sounded the warning siren");
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
					+ ReactorCore.heatPercent(brain.getInstability(), ReactorConfig.reactorBareInstabilityCapacity)
					+ "% instability on " + brain.getRods() + " rods and never armed — a bare pile would "
					+ "have no limit at all. A figure BELOW the one-rack settle means the reactor was "
					+ "switched off for part of the run, not that the scale is mistuned.");
		}
		// The playtest finding (MOD-623): the siren read the room's heat, which a bare pile does not have, so
		// the one reactor with no walls counted down to its accident in silence.
		if (!brain.hasSoundedOverheatAlarm()) {
			helper.fail("a bare pile armed its countdown without ever sounding the warning siren");
		}
		// Wound back down before the scenario ends: the countdown is armed, and leaving a live one in a
		// shared world is how a test grows a blast radius nobody asked for.
		scramBareRig(helper);
		driveAt(helper, brain, BARE_CONTROLLER, ReactorConfig.reactorBlastReleaseTicks + 200);
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
}

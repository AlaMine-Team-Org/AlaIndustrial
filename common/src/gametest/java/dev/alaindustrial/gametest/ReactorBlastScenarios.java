package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.ReactorAdvancementScenarios.assertEarned;
import static dev.alaindustrial.gametest.ReactorAdvancementScenarios.assertNotEarned;
import static dev.alaindustrial.gametest.ReactorConsoleScenarios.logKinds;
import static dev.alaindustrial.gametest.ReactorRig.CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.SHELL_MAX;
import static dev.alaindustrial.gametest.ReactorRig.buildRoom;
import static dev.alaindustrial.gametest.ReactorRig.controller;
import static dev.alaindustrial.gametest.ReactorRig.drive;
import static dev.alaindustrial.gametest.ReactorRig.driveUnderLoad;
import static dev.alaindustrial.gametest.ReactorRig.placeColumnAt;
import static dev.alaindustrial.gametest.ReactorRig.totalDamage;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorIdleReason;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * World scenarios of the accident at the top of the scale (MOD-471): the countdown, the scram that calls it
 * off, the redstone clock that does not, the full buffer that still cooks the core, and the blast that is
 * blocked. Reached through the {@link ReactorScenarios} facade, whose names the two lanes run (MOD-713,
 * TST-2).
 */
final class ReactorBlastScenarios {

	private ReactorBlastScenarios() {}

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
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			// Fire and lava are muted for the run, and this is the ONE mutation the concurrency rule
			// allows: no other rig in the batch has an exploding reactor, so no other rig reads these two
			// keys. They have to go, because both spread FURTHER than the blast — lava flows and fire
			// jumps — and the shell is full of holes by the time they appear.
			o.set("reactorBlastFire", false);
			o.set("reactorBlastLavaCells", 0);
			o.set("reactorBlastEnabled", false);

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
				helper.fail("a core at " + ReactorCore.heatPercent(brain.getHeat(), ReactorConfig.reactorHeatCapacity)
						+ "% of the heat scale never armed the countdown");
			}
			int rolled = brain.getBlastCountdownTotal();
			if (rolled < ReactorConfig.reactorBlastCountdownMinTicks
					|| rolled > ReactorConfig.reactorBlastCountdownMaxTicks) {
				helper.fail("the countdown rolled " + rolled + ", outside its own configured range");
			}
			driveUnderLoad(helper, brain, rolled + 20);
			if (!helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
				helper.fail("the blast switch was off and the reactor exploded anyway");
			}
			assertNotEarned(helper, owner, "reactor_blast",
					"by a countdown that ran out with the blast switch off");
			// MOD-622: armed, then ran out — written even with the switch off, since that is the state an operator
			// reads.
			List<ReactorLog.Kind> phaseOne = logKinds(brain);
			int armed = phaseOne.indexOf(ReactorLog.Kind.COUNTDOWN_ARMED);
			int expired = phaseOne.indexOf(ReactorLog.Kind.COUNTDOWN_EXPIRED);
			if (armed < 0 || expired < 0 || armed > expired) {
				helper.fail("a countdown that armed and ran out logged " + phaseOne);
			}

			// Phase two: pull the lever. The gauge comes off a hundred and the countdown must clear — this
			// is the promise that there is no point of no return.
			helper.setBlock(CONTROLLER.west(), Blocks.AIR.defaultBlockState());
			// Long enough to clear the release window, plus margin. Cancelling deliberately costs a few
			// seconds of genuinely cooler core — that is what stops a redstone clock resetting the timer —
			// so a scram followed by a couple of ticks proves nothing any more.
			drive(helper, brain, ReactorConfig.reactorBlastReleaseTicks + 40);
			if (brain.getBlastCountdown() != 0) {
				helper.fail("scramming the reactor and holding it cool for "
						+ (ReactorConfig.reactorBlastReleaseTicks + 40) + " ticks left the countdown running at "
						+ brain.getBlastCountdown());
			}
			List<ReactorLog.Kind> phaseTwo = logKinds(brain).stream()
					.filter(k -> k == ReactorLog.Kind.COUNTDOWN_ARMED || k == ReactorLog.Kind.COUNTDOWN_CANCELLED
							|| k == ReactorLog.Kind.COUNTDOWN_EXPIRED)
					.toList();
			if (phaseTwo.isEmpty() || phaseTwo.get(phaseTwo.size() - 1) != ReactorLog.Kind.COUNTDOWN_CANCELLED) {
				helper.fail("a countdown called off by a scram logged " + phaseTwo);
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
			o.set("reactorBlastEnabled", true);
			helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
			driveUnderLoad(helper, brain, 200);
			if (brain.getBlastCountdown() <= 0) {
				helper.fail("the reactor did not re-arm after the signal came back");
			}
			driveUntilItBlows(helper, brain, brain.getBlastCountdown() + 5);

			if (helper.getBlockState(CONTROLLER).is(ModContent.REACTOR_CONTROLLER.get())) {
				helper.fail("the countdown ran out and the controller survived its own explosion. heat="
						+ ReactorCore.heatPercent(brain.getHeat(), ReactorConfig.reactorHeatCapacity) + "% countdown="
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
		int heat = ReactorCore.heatPercent(brain.getHeat(), ReactorConfig.reactorHeatCapacity);
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

		// Now plumbed, buffer still full (MOD-623): the water carries the heat and the core comes down, but
		// nothing is sold, so the reason stays BUFFER_FULL and the rods still do not wear.
		for (int tick = 0; tick < 200; tick++) {
			for (FuelRodAssemblyBlockEntity column : row) {
				column.setTank(true, column.waterTank.capacity);
				column.setTank(false, 0);
			}
			brain.getEnergyStorage().setAmountUntracked(brain.getEnergyStorage().getCapacity());
			brain.serverTick(helper.getLevel(), absolute, helper.getBlockState(CONTROLLER));
		}
		if (brain.getIdleReason() != ReactorIdleReason.BUFFER_FULL) {
			helper.fail("a plumbed room with a full buffer reported " + brain.getIdleReason()
					+ " instead of BUFFER_FULL");
		}
		int wearPlumbed = 0;
		for (FuelRodAssemblyBlockEntity column : row) {
			wearPlumbed += totalDamage(column.contents());
		}
		if (wearPlumbed != wearBefore) {
			helper.fail("uranium was spent by a plumbed room whose buffer was full: wear moved from "
					+ wearBefore + " to " + wearPlumbed);
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
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("reactorBlastFire", false);
			o.set("reactorBlastLavaCells", 0);

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
			int limit = ReactorConfig.reactorBlastCountdownMaxTicks * 30;
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
		}
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
		try (ConfigOverrides o = ConfigOverrides.sync().set("reactorBlastEnabled", false)) {
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
		}
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
		if (ReactorConfig.reactorBlastCountdownMinTicks < PINNED_RUN_TICKS) {
			helper.fail("reactorBlastCountdownMinTicks is " + ReactorConfig.reactorBlastCountdownMinTicks
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

	/** Five racks packed on the floor: enough adjacency to pin the gauge within a couple of hundred ticks. */
	static final BlockPos[] HOT_CORE = {
		new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(3, 1, 1),
		new BlockPos(1, 1, 2), new BlockPos(2, 1, 2),
	};

	/** The longest a scenario here holds a pinned core; the countdown must outlast it. */
	static final int PINNED_RUN_TICKS = 1400;
}

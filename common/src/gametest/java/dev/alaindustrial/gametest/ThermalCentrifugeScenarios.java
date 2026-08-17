package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.storage.TagValueInput;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

/**
 * Loader-neutral MOD-425 world scenarios for the Thermal Centrifuge. Fabric and NeoForge wrappers run
 * these same bodies, so the four gates cannot drift by loader.
 *
 * <p>MOD-424 shipped the machine with L1 coverage only ({@code ThermalCentrifugeStatusTest} and the
 * rotor arithmetic); the mechanics were checked by hand. That mattered more here than for a typical
 * machine, because the redstone gate is the mod's only one with this polarity — signal means START,
 * whereas the Mob Repeller reads a signal as PAUSE. A regression would be silent: the machine would
 * simply stop starting, or start burning EU while switched off, and nothing would go red.
 *
 * <p><b>Two traps these scenarios are built around.</b> First, a full spin-up costs
 * {@code spinupTicks * euPerTick} = 400 * 4 = 1600 EU while {@code Config.machineBuffer} is 800 — a
 * rotor cannot reach working speed out of one buffer, so any scenario that drives without refunding
 * stalls at roughly half speed and then passes a weak assertion. Hence {@link #driveFunded}. Second,
 * {@code status} is initialised to {@code NO_SIGNAL}, so "no signal, everything cold, nothing spent"
 * is also exactly what a completely dead rig looks like; {@link #neg01NoSignalCostsNothingAnywhere}
 * therefore carries a positive control in the same body.
 */
public final class ThermalCentrifugeScenarios {
	private static final BlockPos MACHINE = new BlockPos(1, 2, 1);
	private static final BlockPos HEAT = MACHINE.below();
	/**
	 * The redstone source. Deliberately beside the machine and only diagonal to {@link #HEAT}, so the
	 * signal that starts the centrifuge cannot be mistaken for something acting on the heater.
	 */
	private static final BlockPos SIGNAL = MACHINE.east();

	private ThermalCentrifugeScenarios() {
	}

	private static int spinupTicks() {
		return Math.max(1, Config.thermalCentrifugeSpinupTicks);
	}

	private static int workRate() {
		return Math.max(1, Config.thermalCentrifugeEuPerTick);
	}

	private static int idleRate() {
		return Math.max(1, Config.thermalCentrifugeIdleEuPerTick);
	}

	/** Operation length in ticks, derived from the recipe's own price the way the machine derives it. */
	private static int operationTicks() {
		return Config.scaledDuration(Config.thermalCentrifugeDuration) + 2;
	}

	/** Progress channel of the shared machine data block; there is no named constant for it. */
	private static final int DATA_PROGRESS = 2;

	private static ThermalCentrifugeBlockEntity placeMachine(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, MACHINE, ModContent.THERMAL_CENTRIFUGE.get(),
				ThermalCentrifugeBlockEntity.class);
	}

	/**
	 * Raise or drop the redstone signal, and nothing else.
	 *
	 * <p><b>Deliberately no manual {@code wake()} here.</b> An earlier draft called
	 * {@code onHeatNeighbourChanged()} right after {@code setBlock} "to be safe", and that quietly
	 * disabled half of what these scenarios exist to check: with the wake done by hand, deleting
	 * {@code ThermalCentrifugeBlock#neighborChanged} left all nine tests green, while in game a machine
	 * would sit up to {@code IDLE_SLEEP_TICKS} (two seconds) after the player flips the lever, because
	 * {@code stopped()} puts it to sleep. Letting {@code setBlock}'s own neighbour update travel the
	 * real path means the block's callback is under test too, not just {@code hasNeighborSignal}.
	 */
	private static void signal(GameTestHelper helper, boolean on) {
		helper.setBlock(SIGNAL, on ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
	}

	/**
	 * Drive the machine with its buffer refilled before every tick.
	 *
	 * <p>Mandatory for anything that needs working speed: the ramp costs 1600 EU against an 800 EU
	 * buffer, so an unfunded drive freezes the rotor half-way ({@code spinUp} holds rather than spends
	 * when it cannot pay) and a scenario asserting only "spin went up" would pass on a rotor that never
	 * arrived. When a heater is supplied it is topped up too, so a 200-tick operation is not silently
	 * cut short by the heater running dry.
	 */
	private static void driveFunded(ThermalCentrifugeBlockEntity be, ElectricHeaterBlockEntity heater,
			GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
			if (heater != null) {
				heater.getEnergyStorage().setAmountUntracked(Config.electricHeaterBuffer);
			}
			drive(be, helper, 1);
		}
	}

	private static void stock(ThermalCentrifugeBlockEntity be, int dust) {
		be.setItem(ThermalCentrifugeBlockEntity.INPUT_SLOT,
				new ItemStack(ModContent.URANIUM_DUST.get(), dust));
	}

	private static ElectricHeaterBlockEntity poweredHeater(GameTestHelper helper) {
		ElectricHeaterBlockEntity heater = AlaGameTestHelper.place(helper, HEAT,
				ModContent.ELECTRIC_HEATER.get(), ElectricHeaterBlockEntity.class);
		heater.getEnergyStorage().setAmountUntracked(Config.electricHeaterBuffer);
		return heater;
	}

	/**
	 * A heater warmed the way the game warms it — its own ticks, its own EU, while the machine above
	 * reports that it is waiting on heat. Never by poking the field: the demand-driven warm-up IS one of
	 * the four gates, so a scenario wanting hot heat has to prove that gate can be passed.
	 */
	private static ElectricHeaterBlockEntity hotHeater(GameTestHelper helper,
			ThermalCentrifugeBlockEntity machine) {
		ElectricHeaterBlockEntity heater = poweredHeater(helper);
		machine.onHeatNeighbourChanged();
		drive(machine, helper, 1); // the machine asks for heat, which is what lights the heater
		drive(heater, helper, Config.electricHeaterWarmupTicks + 2);
		if (!heater.isHot()) {
			helper.fail("heater is not hot after " + Config.electricHeaterWarmupTicks
					+ " warming ticks; permille=" + heater.heatPermille());
		}
		heater.getEnergyStorage().setAmountUntracked(Config.electricHeaterBuffer);
		return heater;
	}

	private static int outputCount(ThermalCentrifugeBlockEntity be) {
		ItemStack out = be.getItem(ThermalCentrifugeBlockEntity.OUTPUT_SLOT);
		return out.is(ModContent.URANIUM_SHAVINGS.get()) ? out.getCount() : 0;
	}

	/**
	 * Without a redstone signal the machine spends nothing AND the heater below stays cold and spends
	 * nothing either.
	 *
	 * <p>The most valuable scenario of the set: it guards MOD-424's promise that a heater with nobody to
	 * serve stands still for free, from the side of the newest consumer. A switched-off centrifuge that
	 * still made its heater burn 1200 EU of warm-up would be a pure, invisible energy leak.
	 *
	 * <p><b>Why the second half exists.</b> {@code status} starts life as {@code NO_SIGNAL} and a heater
	 * only warms on demand, so every assertion in the first half is also satisfied by a rig that is
	 * simply dead — wrong block, missing recipe, a BE that never ticks. The positive control proves the
	 * rig can move at all, which is the only thing that makes the negative half mean anything.
	 *
	 * @implements TC-CENTR-001-NEG01
	 * @covers R-NRG-10
	 */
	public static void neg01NoSignalCostsNothingAnywhere(GameTestHelper helper) {
		helper.setBlock(SIGNAL, Blocks.AIR);
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		ElectricHeaterBlockEntity heater = poweredHeater(helper);
		stock(be, 1);
		be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);

		long machineEu0 = be.getEnergyStorage().getAmount();
		long heaterEu0 = heater.getEnergyStorage().getAmount();

		int ticks = Config.electricHeaterWarmupTicks + 2;
		drive(be, helper, ticks);
		drive(heater, helper, ticks);

		if (be.getEnergyStorage().getAmount() != machineEu0) {
			helper.fail("an unpowered-by-redstone centrifuge spent "
					+ (machineEu0 - be.getEnergyStorage().getAmount()) + " EU; it must spend exactly none");
			return;
		}
		if (heater.getEnergyStorage().getAmount() != heaterEu0) {
			helper.fail("the heater under a switched-off centrifuge spent "
					+ (heaterEu0 - heater.getEnergyStorage().getAmount())
					+ " EU; nobody asked it for heat");
			return;
		}
		if (heater.heatPermille() != 0 || heater.isHot()) {
			helper.fail("the heater warmed up for a switched-off machine; permille="
					+ heater.heatPermille());
			return;
		}
		if (be.spinPermille() != 0 || be.status() != ThermalCentrifugeStatus.NO_SIGNAL) {
			helper.fail("expected a stopped rotor reporting NO_SIGNAL, got spin=" + be.spinPermille()
					+ " status=" + be.status());
			return;
		}
		if (outputCount(be) != 0) {
			helper.fail("a switched-off centrifuge produced output");
			return;
		}

		// Positive control: the very same rig, one redstone block later, must move.
		signal(helper, true);
		drive(be, helper, 10);
		if (be.spinPermille() <= 0) {
			helper.fail("the rig cannot spin up even with a signal — the negative half above proved "
					+ "nothing (status=" + be.status() + ")");
			return;
		}
		if (be.getEnergyStorage().getAmount() >= machineEu0) {
			helper.fail("a spinning rotor spent no EU — the buffer assertions above are vacuous");
			return;
		}
		// wake() first, and a margin covering a whole idle-sleep. Unasked, the heater returns
		// IDLE_SLEEP_TICKS and serverTick spends the calls decrementing it. Five "revival" ticks used to
		// be enough only because the sleep remainder happens to be three at the current
		// electricHeaterWarmupTicks; at another config value this control would go FALSELY RED.
		heater.wake();
		drive(heater, helper, 5);
		if (heater.heatPermille() <= 0) {
			helper.fail("the heater never starts warming even on demand — the cold-heater assertion "
					+ "above proved nothing");
			return;
		}
		helper.succeed();
	}

	/**
	 * With a signal the rotor accelerates, paying the full working rate per tick and reporting
	 * SPINNING_UP.
	 *
	 * <p>The status assertion is not decoration. Spin-up is checked before the recipe diagnosis short
	 * circuits, so an unstocked machine also accelerates — a scenario asserting only the EU drop would
	 * pass while testing a different code path than it claims.
	 *
	 * @implements TC-CENTR-001-FUN01
	 * @covers R-NRG-10
	 */
	public static void fun01SpinUpCostsTheWorkingRate(GameTestHelper helper) {
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		poweredHeater(helper);
		stock(be, 1);
		be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
		signal(helper, true);

		int ticks = 10;
		drive(be, helper, ticks);

		long expected = Config.machineBuffer - (long) ticks * workRate();
		if (be.getEnergyStorage().getAmount() != expected) {
			helper.fail("spin-up must cost " + workRate() + " EU/t: expected " + expected + " EU left, got "
					+ be.getEnergyStorage().getAmount());
			return;
		}
		// The RATE, not merely "it moved": one revolution per paid tick. "spin > 0" would stay green if
		// spin-up started adding two revolutions a tick — the price and the status would be unchanged,
		// and only fun02 would notice, a hundred times further down the ramp.
		int expectedPermille = Math.max(1, Math.min(ticks * 1000 / spinupTicks(), 1000));
		if (be.spinPermille() != expectedPermille) {
			helper.fail("after " + ticks + " paid ticks the rotor must read " + expectedPermille
					+ " permille (one revolution per tick), got " + be.spinPermille());
			return;
		}
		if (be.status() != ThermalCentrifugeStatus.SPINNING_UP) {
			helper.fail("expected SPINNING_UP while accelerating, got " + be.status());
			return;
		}
		helper.succeed();
	}

	/**
	 * A machine already asleep reacts on the FIRST tick after the lever, not two seconds later.
	 *
	 * <p>This exists because removing the scenarios' hand-written {@code wake()} was not enough. With no
	 * signal, {@code stopped()} returns {@code IDLE_SLEEP_TICKS} and {@code serverTick} then swallows
	 * that many calls, so the only thing that makes a switched-off machine notice its own start button
	 * is {@code ThermalCentrifugeBlock#neighborChanged}. Deleting that callback left all nine other
	 * scenarios green — every one of them raises the signal before the machine has ever slept — while in
	 * game the player would flip the lever and watch a dead machine for up to two seconds.
	 *
	 * <p>The timing here is deterministic on purpose: exactly one unsignalled tick puts the sleep counter
	 * at a known {@code IDLE_SLEEP_TICKS}, so the check does not depend on how the config's warm-up
	 * length happens to divide.
	 *
	 * @implements TC-CENTR-001-FUN05
	 * @covers R-NRG-10
	 */
	public static void fun05SignalWakesTheMachineImmediately(GameTestHelper helper) {
		signal(helper, false);
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		poweredHeater(helper);
		stock(be, 1);
		be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);

		// One unsignalled tick: the machine stops and goes to sleep for IDLE_SLEEP_TICKS.
		drive(be, helper, 1);
		if (be.status() != ThermalCentrifugeStatus.NO_SIGNAL) {
			helper.fail("setup failed: expected NO_SIGNAL before the lever, got " + be.status());
			return;
		}

		signal(helper, true);
		drive(be, helper, 1);

		if (be.spinPermille() <= 0) {
			helper.fail("the machine ignored the first tick after the signal — it is still sleeping off "
					+ "stopped()'s idle interval. The block's neighborChanged must wake it, or the player "
					+ "flips the lever and watches nothing happen for up to two seconds (status="
					+ be.status() + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * Working speed arrives on exactly the configured tick — not before, and then at once.
	 *
	 * <p>Both sides of the boundary are asserted on purpose: a one-sided check stays green even if the
	 * threshold moves the other way.
	 *
	 * @implements TC-CENTR-001-FUN02
	 * @covers R-NRG-10
	 */
	public static void fun02FullSpinTakesExactlyConfigTicks(GameTestHelper helper) {
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		ElectricHeaterBlockEntity heater = poweredHeater(helper);
		stock(be, 1);
		signal(helper, true);

		driveFunded(be, heater, helper, spinupTicks() - 1);
		if (be.isAtSpeed()) {
			helper.fail("the rotor reached working speed one tick early (spin=" + be.spinPermille() + ")");
			return;
		}
		driveFunded(be, heater, helper, 1);
		if (!be.isAtSpeed()) {
			helper.fail("the rotor is still not at speed after " + spinupTicks() + " paid ticks (spin="
					+ be.spinPermille() + ")");
			return;
		}
		if (be.spinPermille() != 1000) {
			helper.fail("a fully spun rotor must read 1000 permille, got " + be.spinPermille());
			return;
		}
		helper.succeed();
	}

	/**
	 * Cutting the signal drops the revolutions outright rather than coasting them down — the lever is
	 * the motor switch, and a rotor still at speed after it was thrown would make the signal look
	 * optional.
	 *
	 * @implements TC-CENTR-001-FUN03
	 * @covers R-NRG-10
	 */
	public static void fun03CutSignalDropsTheSpinOutright(GameTestHelper helper) {
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		ElectricHeaterBlockEntity heater = poweredHeater(helper);
		stock(be, 1);
		signal(helper, true);
		driveFunded(be, heater, helper, spinupTicks());

		// Control: without this, "spin is zero afterwards" is also true of a rotor that never moved.
		if (be.spinPermille() != 1000) {
			helper.fail("setup failed: expected a fully spun rotor before cutting the signal, got "
					+ be.spinPermille());
			return;
		}

		signal(helper, false);
		drive(be, helper, 1);

		if (be.spinPermille() != 0 || be.status() != ThermalCentrifugeStatus.NO_SIGNAL) {
			helper.fail("cutting the signal must stop the rotor at once: spin=" + be.spinPermille()
					+ " status=" + be.status());
			return;
		}
		helper.succeed();
	}

	/**
	 * A starved rotor loses one revolution at the documented half rate — and then freezes there.
	 *
	 * <p><b>This pins what the machine actually does, which is not what its javadoc promises</b>, and the
	 * difference was found by this test rather than by reading. {@code coast()} — the only code that
	 * sheds revolutions — is reachable only while {@code isAtSpeed()} holds. The instant the first
	 * revolution is lost, {@code spin} drops below {@code spinupTicks} and the next tick takes the
	 * {@code spinUp()} branch instead; unable to pay, that branch holds rather than sheds. So the
	 * "sheds at half the rate it was gained" wording describes a slope that can never be walked past its
	 * first step: 400 → 399, then nothing, forever.
	 *
	 * <p>Measured, not inferred: starved, the rotor reads 997 permille on tick 2 and still 997 on tick
	 * 20. Fed 3 EU a tick, it holds a full 1000 and spends the idle rate — so the rig is demonstrably
	 * alive and the freeze is the machine's behaviour, not a dead test.
	 *
	 * <p>The freeze is arguably the more consistent reading of R-NRG-10 ("freeze, don't waste") than the
	 * javadoc's ramp, so this scenario deliberately does NOT assert the documented slope — that would be
	 * a red test pinning a promise the code never kept. It asserts today's behaviour on both sides and
	 * names the discrepancy, so whoever reconciles the two gets a failing test as the signal to update
	 * this one on purpose.
	 *
	 * @implements TC-CENTR-001-NEG02
	 * @covers R-NRG-10
	 */
	public static void neg02StarvedRotorShedsOneRevolutionThenFreezes(GameTestHelper helper) {
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		ElectricHeaterBlockEntity heater = poweredHeater(helper);
		stock(be, 1);
		signal(helper, true);
		driveFunded(be, heater, helper, spinupTicks());
		if (!be.isAtSpeed()) {
			helper.fail("setup failed: the rotor never reached working speed");
			return;
		}

		// Empty the slot so the machine coasts instead of working, then cut the power entirely.
		be.setItem(ThermalCentrifugeBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
		be.getEnergyStorage().setAmountUntracked(0L);

		// One revolution costs two starved ticks — the half rate, on the one step where it is reachable.
		int oneShedPermille = Math.max(1, Math.min((spinupTicks() - 1) * 1000 / spinupTicks(), 1000));
		drive(be, helper, 1);
		if (be.spinPermille() != 1000) {
			helper.fail("the first starved tick must not shed yet (half rate = one revolution per two "
					+ "ticks), got " + be.spinPermille() + " permille");
			return;
		}
		drive(be, helper, 1);
		if (be.spinPermille() != oneShedPermille) {
			helper.fail("the second starved tick must shed exactly one revolution: expected "
					+ oneShedPermille + " permille, got " + be.spinPermille());
			return;
		}

		// And there it stays: below full speed the machine takes the spin-up branch, which freezes
		// rather than sheds when it cannot pay. Eighteen more starved ticks must change nothing.
		drive(be, helper, 18);
		if (be.spinPermille() != oneShedPermille) {
			helper.fail("a starved rotor is expected to freeze after the first lost revolution, but it "
					+ "moved to " + be.spinPermille() + " permille — if the spin-down ramp was made "
					+ "reachable on purpose, update this scenario and the class javadoc together");
			return;
		}

		// Liveness control: without it, "nothing changed for 18 ticks" is also true of a dead rig.
		//
		// Note WHICH rate proves it. Fed the idle rate the machine would still not move, and that is the
		// finding above in miniature: a rotor below full speed is no longer coasting, it is spinning up,
		// and spin-up is billed at the working rate. So the control funds exactly one working tick and
		// demands the revolution back — which is both a liveness proof and a demonstration of the branch
		// switch that causes the freeze.
		be.getEnergyStorage().setAmountUntracked(workRate());
		drive(be, helper, 1);
		if (be.getEnergyStorage().getAmount() != 0L || be.spinPermille() != 1000) {
			helper.fail("the machine is not ticking at all — the freeze assertion above proves nothing "
					+ "(funded one working tick, expected the buffer emptied and the revolution regained, "
					+ "got eu=" + be.getEnergyStorage().getAmount() + " spin=" + be.spinPermille() + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * Lava, magma, campfires and a lava cauldron do not run this machine — only a hot electric heater
	 * does.
	 *
	 * <p>This is the difference from the Vulcanizer, which accepts every passive source, and it is easy
	 * to lose in a future refactor of the heat core: the two machines share {@code WorldHeatSources} and
	 * differ by a single line of {@code diagnose}. Each source is driven past full spin-up first —
	 * asserting NO_HEATER on a rotor that has not arrived yet would be testing SPINNING_UP instead. The
	 * closing half swaps in a real heater and demands the output, so "produced nothing" cannot be a
	 * property of the rig.
	 *
	 * @implements TC-CENTR-001-NEG03
	 * @covers R-NRG-10
	 */
	public static void neg03PassiveHeatDoesNotStartIt(GameTestHelper helper) {
		Block[] passive = {Blocks.LAVA, Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
				Blocks.LAVA_CAULDRON};
		for (Block block : passive) {
			helper.setBlock(MACHINE, Blocks.AIR);
			helper.setBlock(HEAT, Blocks.AIR);
			ThermalCentrifugeBlockEntity be = placeMachine(helper);
			if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
				helper.setBlock(HEAT, block.defaultBlockState().setValue(CampfireBlock.LIT, true));
			} else {
				helper.setBlock(HEAT, block);
			}
			stock(be, 1);
			signal(helper, true);

			driveFunded(be, null, helper, spinupTicks() + operationTicks());

			if (!be.isAtSpeed()) {
				helper.fail("setup failed for " + block.getName().getString()
						+ ": the rotor never reached speed, so NO_HEATER would prove nothing");
				return;
			}
			if (outputCount(be) != 0) {
				helper.fail(block.getName().getString() + " ran the centrifuge — only a hot electric "
						+ "heater may, unlike the Vulcanizer");
				return;
			}
			if (be.getItem(ThermalCentrifugeBlockEntity.INPUT_SLOT).getCount() != 1) {
				helper.fail(block.getName().getString() + " consumed input without producing anything");
				return;
			}
			if (be.status() != ThermalCentrifugeStatus.NO_HEATER) {
				helper.fail("expected NO_HEATER over " + block.getName().getString() + ", got "
						+ be.status());
				return;
			}
		}

		// Positive half: the same rig with a real heater must deliver, or all of the above is vacuous.
		helper.setBlock(MACHINE, Blocks.AIR);
		helper.setBlock(HEAT, Blocks.AIR);
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		stock(be, 1);
		signal(helper, true);
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);
		driveFunded(be, heater, helper, spinupTicks() + operationTicks());
		if (outputCount(be) != 2) {
			helper.fail("a hot electric heater must run the machine, but it produced "
					+ be.getItem(ThermalCentrifugeBlockEntity.OUTPUT_SLOT) + " (status=" + be.status()
					+ ") — the passive-source assertions above proved nothing");
			return;
		}
		helper.succeed();
	}

	/**
	 * One dust becomes two shavings over a hot heater, and the input is charged exactly once.
	 *
	 * @implements TC-CENTR-001-FUN04
	 * @covers R-NRG-10
	 */
	public static void fun04HotHeaterMakesTwoShavings(GameTestHelper helper) {
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		stock(be, 2);
		signal(helper, true);
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);

		// One tick past the ramp on purpose. diagnose() runs BEFORE the isAtSpeed() branch, so on the
		// very tick that completes the spin-up the status is still SPINNING_UP — it describes the state
		// the tick STARTED in. The rotor is at speed; the label catches up next tick.
		driveFunded(be, heater, helper, spinupTicks() + 1);
		if (!be.isAtSpeed() || be.status() != ThermalCentrifugeStatus.READY) {
			helper.fail("setup failed: expected READY at working speed over a hot heater, got "
					+ be.status() + " (atSpeed=" + be.isAtSpeed() + ")");
			return;
		}

		// The WORKING rate, measured on its own. Every other scenario funds the machine before each
		// tick, so none of them can see energy.drainInternal on the working path at all: deleting that
		// line left all nine green. Three unfunded working ticks pin it. It has to be a short window --
		// a whole operation costs operationTicks * workRate, which is the entire buffer to the EU.
		be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
		long before = be.getEnergyStorage().getAmount();
		int workTicks = 3;
		for (int i = 0; i < workTicks; i++) {
			heater.getEnergyStorage().setAmountUntracked(Config.electricHeaterBuffer);
			drive(be, helper, 1);
		}
		long spent = before - be.getEnergyStorage().getAmount();
		if (spent != (long) workTicks * workRate()) {
			helper.fail("a working tick must cost " + workRate() + " EU: expected "
					+ (long) workTicks * workRate() + " EU over " + workTicks + " ticks, got " + spent
					+ " (status=" + be.status() + ")");
			return;
		}

		driveFunded(be, heater, helper, operationTicks());

		if (outputCount(be) != 2) {
			helper.fail("expected 2 uranium shavings, got "
					+ be.getItem(ThermalCentrifugeBlockEntity.OUTPUT_SLOT) + " (status=" + be.status() + ")");
			return;
		}
		if (be.getItem(ThermalCentrifugeBlockEntity.INPUT_SLOT).getCount() != 1) {
			helper.fail("one operation must consume exactly one dust, slot now holds "
					+ be.getItem(ThermalCentrifugeBlockEntity.INPUT_SLOT).getCount());
			return;
		}
		helper.succeed();
	}

	/**
	 * A blocked output stops the work, names itself in the status, and lets the heater stand down.
	 *
	 * <p>Note what is asserted about energy: the machine is <em>not</em> free while blocked. A spun-up
	 * rotor keeps turning at the idle rate, so the correct expectation is a drain of exactly
	 * {@code idleEuPerTick} per tick — asserting "spent nothing" would fail for the right reason and
	 * teach the wrong rule. The heater, which nobody is asking for heat, does spend nothing.
	 *
	 * @implements TC-CENTR-001-CON01
	 * @covers R-NRG-10
	 */
	public static void con01BlockedOutputStopsWorkAndNamesIt(GameTestHelper helper) {
		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		stock(be, 1);
		signal(helper, true);
		ElectricHeaterBlockEntity heater = hotHeater(helper, be);
		driveFunded(be, heater, helper, spinupTicks());

		be.setItem(ThermalCentrifugeBlockEntity.OUTPUT_SLOT,
				new ItemStack(ModContent.URANIUM_SHAVINGS.get(), 64));
		be.getEnergyStorage().setAmountUntracked(Config.machineBuffer);
		heater.getEnergyStorage().setAmountUntracked(Config.electricHeaterBuffer);
		long heaterEu0 = heater.getEnergyStorage().getAmount();

		int ticks = 3;
		drive(be, helper, ticks);

		if (be.status() != ThermalCentrifugeStatus.OUTPUT_BLOCKED) {
			helper.fail("expected OUTPUT_BLOCKED with a full output slot, got " + be.status());
			return;
		}
		if (be.getDataAccess().get(DATA_PROGRESS) != 0) {
			helper.fail("progress advanced against a blocked output: "
					+ be.getDataAccess().get(DATA_PROGRESS));
			return;
		}
		long expected = Config.machineBuffer - (long) ticks * idleRate();
		if (be.getEnergyStorage().getAmount() != expected) {
			helper.fail("a blocked but spinning rotor must coast at " + idleRate() + " EU/t: expected "
					+ expected + " EU left, got " + be.getEnergyStorage().getAmount());
			return;
		}
		if (heater.getEnergyStorage().getAmount() != heaterEu0) {
			helper.fail("the machine drew heat while its output was blocked — canWork must gate "
					+ "consumeForProgress, and a blocked machine must not bill the heater");
			return;
		}
		helper.succeed();
	}

	/**
	 * The rotor's speed survives a chunk unload / rejoin, and a tampered save is clamped rather than
	 * trusted.
	 *
	 * <p>A partial spin is saved on purpose: a round-trip of zero, or of a full rotor, would pass even
	 * if the value were hardcoded at either end.
	 *
	 * @implements TC-CENTR-001-STA01
	 * @covers R-PER-01
	 */
	public static void sta01SpinSurvivesReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(MACHINE);

		ThermalCentrifugeBlockEntity be = placeMachine(helper);
		ElectricHeaterBlockEntity heater = poweredHeater(helper);
		stock(be, 1);
		signal(helper, true);
		int partial = Math.max(1, spinupTicks() / 3);
		driveFunded(be, heater, helper, partial);

		int spin0 = be.spinPermille();
		if (spin0 <= 0 || spin0 >= 1000) {
			helper.fail("setup failed: need a partially spun rotor to make the round-trip meaningful, got "
					+ spin0 + " permille");
			return;
		}

		CompoundTag tag = be.saveCustomOnly(registries);
		ThermalCentrifugeBlockEntity restored =
				new ThermalCentrifugeBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		if (restored.spinPermille() != spin0) {
			helper.fail("rotor speed did not survive the round-trip: " + spin0 + " -> "
					+ restored.spinPermille() + " permille");
			return;
		}

		// A save claiming an impossible spin must be clamped, not believed.
		tag.putInt("Spin", 999_999);
		ThermalCentrifugeBlockEntity tampered =
				new ThermalCentrifugeBlockEntity(abs, level.getBlockState(abs));
		tampered.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		if (tampered.spinPermille() != 1000 || !tampered.isAtSpeed()) {
			helper.fail("an out-of-range saved spin must clamp to a full rotor, got "
					+ tampered.spinPermille() + " permille");
			return;
		}
		helper.succeed();
	}
}

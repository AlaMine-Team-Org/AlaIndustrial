package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.SprinklerBlock;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-neutral gametest bodies for the Sprinkler (MOD-525, suite TC-SPRINK-001). Wrapped by the
 * Fabric {@code SprinklerGameTest} suite and registered on the NeoForge lane
 * ({@code NeoForgeGameTests}, {@code sprinkler_*}), so both loaders run the SAME bodies.
 *
 * <p><b>These run on REAL ticks, not {@link AlaGameTestHelper#drive}.</b> That is the point of the
 * suite rather than a detail of it. The machine schedules itself against
 * {@code ServerLevel#getGameTime}, and — more importantly — it decides how long the base class may
 * SKIP its tick. {@code drive} calls {@code serverTick} in a loop inside one game tick, so it can
 * neither advance the clock nor exercise the sleep gate, and a suite built on it would have stayed
 * green through the exact defect this one exists to pin: the sprinkler once returned
 * {@code IDLE_SLEEP_TICKS} from every tick that merely counted down, which multiplied the configured
 * interval by 41 and gave one spray every three and a half minutes. Nothing in the recipe system,
 * the config or the L1 lane can see that — only a clock can.
 */
public final class SprinklerScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SprinklerScenarios::fun01SpraysOncePerIntervalAndPays,
								"sprinkler_sprays_once_per_interval_and_pays")
						.fabricId("SprinklerGameTest", "tcSprink001Fun01_spraysOncePerIntervalAndPays").ticks(400)
						.environment(Industrialization.id("config_overrides")),
				RosterEntry.of(SprinklerScenarios::fun02HangingReachesTheFieldBelow,
								"sprinkler_hanging_reaches_the_field_below")
						.fabricId("SprinklerGameTest", "tcSprink001Fun02_hangingReachesTheFieldBelow").ticks(300)
						.environment(Industrialization.id("config_overrides_2")),
				RosterEntry.of(SprinklerScenarios::con01TankBelowPriceNeverFires,
								"sprinkler_tank_below_price_never_fires")
						.fabricId("SprinklerGameTest", "tcSprink001Con01_tankBelowPriceNeverFires").ticks(300)
						.environment(Industrialization.id("config_overrides_3")),
				RosterEntry.of(SprinklerScenarios::con02NothingToWaterCostsNothing,
								"sprinkler_nothing_to_water_costs_nothing")
						.fabricId("SprinklerGameTest", "tcSprink001Con02_nothingToWaterCostsNothing").ticks(300)
						.environment(Industrialization.id("config_overrides_4")));

		private Roster() {}
	}

	private SprinklerScenarios() {
	}

	/** The block under test. Everything else is laid out around it inside the 8³ rig. */
	private static final BlockPos SPRINKLER = new BlockPos(1, 3, 1);

	/** A hanging rig and a standing one, three blocks above their own crop (see FUN02). */
	private static final BlockPos HANGING = new BlockPos(5, 6, 5);
	private static final BlockPos HANGING_CROP = new BlockPos(5, 3, 5);
	private static final BlockPos STANDING_HIGH = new BlockPos(2, 6, 5);
	private static final BlockPos STANDING_CROP = new BlockPos(2, 3, 5);

	/**
	 * Intervals to watch in FUN01, and the slack that makes the count a floor rather than a race.
	 *
	 * <p>Four due times fall inside four intervals; the assertion demands three, so a single tick of
	 * scheduling jitter cannot turn a working machine red — while the defect this pins produced
	 * exactly one.
	 */
	private static final int WATCHED_INTERVALS = 4;
	private static final int REQUIRED_SPRAYS = 3;

	// Every scenario below runs with the spray radius pinned to one block:
	// ConfigOverrides.forTest(helper).set("sprinklerRange", 1).
	//
	// Gametests share one world and the shipped radius is 4, which reaches out of this rig and into
	// whatever scenario the server laid out next door — the sprinkler would then fertilise a
	// neighbour's crops, spend solution nobody asked it to, and make CON02 fail (or, worse, pass for
	// the wrong reason). Radius 1 keeps every target inside the cell this scenario built. Same trick,
	// same reason as GardenDroneScenarios.withIsolatedZone.
	//
	// The override outlives the sequence builder, so it is held by the test's forTest handle: the radius
	// goes back when the test ENDS — passed, failed or timed out — with no restore written in the
	// scenarios. The four scenarios pin the same knob, so each runs in its own ConfigOverrides test
	// environment (batch): two holders of one key in one batch would be refused by the handle's owner
	// registry, and before MOD-710 they silently restored each other's value. The environment of each
	// scenario is named at its call and listed in ArchitectureRules.FOR_TEST_USERS.

	private static SprinklerBlockEntity place(GameTestHelper helper, BlockPos pos, boolean hanging) {
		helper.setBlock(pos, ModContent.SPRINKLER.get().defaultBlockState()
				.setValue(SprinklerBlock.HANGING, hanging));
		SprinklerBlockEntity be = helper.getBlockEntity(pos, SprinklerBlockEntity.class);
		if (be == null) {
			helper.fail("sprinkler block entity missing after placement at " + pos);
		}
		return be;
	}

	/** Solution straight into the tank — these scenarios are about what the machine does with it. */
	private static void fill(SprinklerBlockEntity be, long mb) {
		be.tank.fluid = FluidHolder.of(ModContent.NUTRIENT_SOLUTION.get());
		be.tank.amount = mb;
	}

	/** Farmland with young wheat on it, which is what {@code BonemealableBlock} accepts. */
	private static void plantWheat(GameTestHelper helper, BlockPos cropPos) {
		helper.setBlock(cropPos.below(), Blocks.FARMLAND);
		helper.setBlock(cropPos, Blocks.WHEAT);
	}

	private static long pricePerSpray() {
		return Math.max(1, Config.sprinklerSolutionPerActionMb);
	}

	// ── FUN01: the cadence is real, and every spray is paid for ─────────────────────────────────────

	/**
	 * The sprinkler fires about once per configured interval and charges the tank each time.
	 *
	 * <p><b>The one assertion that would have caught the shipped defect.</b> It counts sprays over a
	 * span of real ticks rather than checking that any spray happened at all: "it watered something"
	 * was true even when the machine fired once every 3½ minutes, which is what a player reported as
	 * "the crops barely move and the tank never empties".
	 *
	 * <p>Eight crops surround the block so the zone cannot run out of targets mid-count — wheat maxes
	 * out after two or three bonemeals, and a single plant would stop being a valid target long before
	 * the interval count was done, turning a cadence test into a crop-capacity test.
	 *
	 * @implements TC-SPRINK-001-FUN01 — the spray happens about once per configured interval and is
	 * charged to the tank each time. The one test that can see a machine which sleeps between
	 * attempts and so honours 41× its configured interval.
	 */
	public static void fun01SpraysOncePerIntervalAndPays(GameTestHelper helper) {
		ConfigOverrides.forTest(helper).set("sprinklerRange", 1); // environment alaindustrial:config_overrides
		SprinklerBlockEntity be = place(helper, SPRINKLER, false);
		fill(be, Config.sprinklerTankMb);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				plantWheat(helper, SPRINKLER.offset(dx, 0, dz));
			}
		}
		long before = be.tank.amount;
		int interval = Math.max(1, Config.sprinklerIntervalTicks);

		helper.startSequence()
				.thenExecuteFor(interval * WATCHED_INTERVALS, () -> {
				})
				.thenExecute(() -> {
					long spent = before - be.tank.amount;
					long sprays = spent / pricePerSpray();
					if (spent % pricePerSpray() != 0) {
						helper.fail("the tank moved by " + spent + " mB, which is not a whole number of "
								+ pricePerSpray() + " mB sprays");
					}
					if (sprays < REQUIRED_SPRAYS) {
						helper.fail("expected at least " + REQUIRED_SPRAYS + " sprays across "
								+ WATCHED_INTERVALS + " intervals of " + interval + " ticks, got " + sprays
								+ " — the machine is almost certainly sleeping between attempts, which "
								+ "multiplies the configured interval instead of honouring it");
					}
				})
				.thenSucceed();
	}

	// ── FUN02: hanging reaches the field below it, standing does not ───────────────────────────────

	/**
	 * A ceiling-mounted sprinkler waters a crop three blocks under it; a floor-standing one at the
	 * same height does not.
	 *
	 * <p>Both halves matter. The first is the fix: the zone was a symmetric ±1 box, so a sprinkler
	 * hung from a ceiling — the whole reason the {@code hanging} state exists — watered the ceiling it
	 * was screwed to and nothing else. The second is what keeps the fix honest: without it, making the
	 * box a symmetric ±3 would pass just as well, and the asymmetry that ties the zone to how the
	 * block is mounted would quietly disappear.
	 *
	 * <p><b>Measured on the tanks, not on the crops</b> — see {@link #con01TankBelowPriceNeverFires}.
	 * A spray is the only thing that can move a tank, so "the hung one paid and the standing one did
	 * not" is exactly the zone question and nothing else; "the crop grew" would also be answered by
	 * vanilla's own random tick.
	 *
	 * @implements TC-SPRINK-001-FUN02 — a hanging sprinkler reaches three blocks down and a standing
	 * one does not, which is the whole point of the ceiling mount.
	 */
	public static void fun02HangingReachesTheFieldBelow(GameTestHelper helper) {
		ConfigOverrides.forTest(helper).set("sprinklerRange", 1); // environment alaindustrial:config_overrides_2
		SprinklerBlockEntity hung = place(helper, HANGING, true);
		SprinklerBlockEntity stood = place(helper, STANDING_HIGH, false);
		fill(hung, Config.sprinklerTankMb);
		fill(stood, Config.sprinklerTankMb);
		plantWheat(helper, HANGING_CROP);
		plantWheat(helper, STANDING_CROP);
		long hungBefore = hung.tank.amount;
		long stoodBefore = stood.tank.amount;

		helper.startSequence()
				.thenExecuteFor(Math.max(1, Config.sprinklerIntervalTicks) * 2, () -> {
				})
				.thenExecute(() -> {
					if (hung.tank.amount >= hungBefore) {
						helper.fail("a hanging sprinkler spent nothing on the crop three blocks below it — "
								+ "it did not reach, so ceiling mounting waters the ceiling again");
					}
					if (stood.tank.amount != stoodBefore) {
						helper.fail("a floor-standing sprinkler paid " + (stoodBefore - stood.tank.amount)
								+ " mB for something three blocks down; its zone is supposed to be ±1, and "
								+ "the deeper reach belongs to the hanging one only");
					}
				})
				.thenSucceed();
	}

	// ── CON01/CON02: the two ways it must cost nothing ─────────────────────────────────────────────

	/**
	 * A tank holding one millibucket less than a spray costs never fires: solution is the only
	 * currency this machine has, and it does not run on credit.
	 *
	 * <p><b>Why the tank and not the crop.</b> The first version planted wheat, ran the clock and
	 * asserted the crop had not grown — and it failed in CI on a machine that had passed it minutes
	 * before, because <em>vanilla</em> grows wheat on its own random tick. The assertion was a coin
	 * flip on the game's dice, not a statement about the sprinkler: green while the block worked,
	 * green while it was broken, red when the weather in the rig happened to change. A tank moves
	 * only when this machine spends, so measuring it asks the question and nothing else.
	 *
	 * <p>{@code price − 1} rather than zero on purpose: an empty tank cannot lose anything, so
	 * "unchanged" would be true no matter what the code did. One millibucket short is the smallest
	 * amount that makes the refusal a real decision.
	 *
	 * @implements TC-SPRINK-001-CON01 — a tank one millibucket short of a spray never fires. Asserted
	 * on the tank rather than on a crop: vanilla grows wheat on its own random tick, so "the crop did
	 * not grow" was a coin flip that went red in CI minutes after passing here.
	 */
	public static void con01TankBelowPriceNeverFires(GameTestHelper helper) {
		ConfigOverrides.forTest(helper).set("sprinklerRange", 1); // environment alaindustrial:config_overrides_3
		SprinklerBlockEntity be = place(helper, SPRINKLER, false);
		long stocked = pricePerSpray() - 1;
		fill(be, stocked);
		plantWheat(helper, SPRINKLER.offset(1, 0, 0));

		helper.startSequence()
				.thenExecuteFor(Math.max(1, Config.sprinklerIntervalTicks) * 2, () -> {
				})
				.thenExecute(() -> {
					if (be.tank.amount != stocked) {
						helper.fail("a sprinkler one millibucket short of a spray spent "
								+ (stocked - be.tank.amount) + " mB anyway — the price is not a gate");
					}
				})
				.thenSucceed();
	}

	/**
	 * A full tank over a zone with nothing to grow spends nothing.
	 *
	 * <p>The demand-driven half of the contract, and the half a careless rewrite breaks first: paying
	 * per attempt instead of per landed spray drains a tank over an empty field, and the only symptom
	 * is a player wondering where the solution went.
	 *
	 * @implements TC-SPRINK-001-CON02 — a zone with nothing to grow costs nothing, so a finished
	 * field does not quietly drain the tank.
	 */
	public static void con02NothingToWaterCostsNothing(GameTestHelper helper) {
		ConfigOverrides.forTest(helper).set("sprinklerRange", 1); // environment alaindustrial:config_overrides_4
		SprinklerBlockEntity be = place(helper, SPRINKLER, false);
		fill(be, Config.sprinklerTankMb);
		long before = be.tank.amount;

		helper.startSequence()
				.thenExecuteFor(Math.max(1, Config.sprinklerIntervalTicks) * 3, () -> {
				})
				.thenExecute(() -> {
					if (be.tank.amount != before) {
						helper.fail("a sprinkler over an empty zone spent " + (before - be.tank.amount)
								+ " mB on nothing");
					}
				})
				.thenSucceed();
	}
}

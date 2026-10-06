package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.KokSagyzBlock;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStatus;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral gametest bodies for the Garden Drone Station (MOD-277, suite TC-DRONE-001).
 *
 * <p>The station is driven through {@link AlaGameTestHelper#drive} rather than real ticks so the
 * scenarios stay deterministic. Every number comes from {@link Config} — asserting against a literal
 * would let a balance change pass a green test while the machine behaves differently in game.
 *
 * <p>The assertions deliberately check the <b>station's own inventory</b> rather than the world: the
 * whole point of the harvest path is that no {@code ItemEntity} is ever created, so a test that
 * looked for a dropped item would pass for the wrong reason (or fail for the right one, silently).
 */
public final class GardenDroneScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(GardenDroneScenarios::fun01TillsDirtAndSpendsEu, "garden_drone_tills_dirt")
						.fabricId("AlaCommonGameTest", "gardenDroneTillsDirt").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun02PlantsSeedOnFarmland, "garden_drone_plants_seed")
						.fabricId("AlaCommonGameTest", "gardenDronePlantsSeed").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun03HarvestsRipeCropIntoStation,
								"garden_drone_harvests_into_station")
						.fabricId("AlaCommonGameTest", "gardenDroneHarvestsIntoStation").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun04NoEnergyLeavesCropUntouched,
								"garden_drone_without_energy_does_nothing")
						.fabricId("AlaCommonGameTest", "gardenDroneWithoutEnergyDoesNothing").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun05FullOutputLeavesCropStanding,
								"garden_drone_full_output_keeps_crop")
						.fabricId("AlaCommonGameTest", "gardenDroneFullOutputKeepsCrop").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun07WithoutDroneNothingHappens,
								"garden_drone_without_drone_is_inert")
						.fabricId("AlaCommonGameTest", "gardenDroneWithoutDroneIsInert").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun08HoeBreaksOnItsLastUse, "garden_drone_hoe_breaks_on_last_use")
						.fabricId("AlaCommonGameTest", "gardenDroneHoeBreaksOnLastUse").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun09HoeSurvivesUntilItsLastUse,
								"garden_drone_hoe_survives_until_last_use")
						.fabricId("AlaCommonGameTest", "gardenDroneHoeSurvivesUntilLastUse").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun06FlightDelaysTheAction, "garden_drone_flight_delays_action")
						.fabricId("AlaCommonGameTest", "gardenDroneFlightDelaysAction").ticks(20, 300),
				RosterEntry.of(GardenDroneScenarios::fun10StandsOnTheTileBeforeFlyingHome,
								"garden_drone_stands_on_tile_before_flying_home")
						.fabricId("AlaCommonGameTest", "gardenDroneStandsOnTileBeforeFlyingHome").ticks(20, 300),
				RosterEntry.of(GardenDroneScenarios::fun11PlantsOnTaggedSoil, "garden_drone_plants_on_tagged_soil")
						.fabricId("AlaCommonGameTest", "gardenDronePlantsOnTaggedSoil").ticks(20, 40),
				RosterEntry.of(GardenDroneScenarios::fun12ClearsGrassForOneHoePoint, "garden_drone_clears_grass")
						.ticks(40),
				RosterEntry.of(GardenDroneScenarios::fun13ClearedBushDropLandsInStation,
								"garden_drone_cleared_bush_drop_lands_in_station")
						.ticks(40),
				RosterEntry.of(GardenDroneScenarios::fun14WithoutHoeGrassStays, "garden_drone_without_hoe_keeps_grass")
						.ticks(40),
				RosterEntry.of(GardenDroneScenarios::fun15LeavesCropsAndFlowersAlone,
								"garden_drone_clears_grass_not_crops_or_flowers")
						.ticks(40),
				RosterEntry.of(GardenDroneScenarios::fun16FullOutputKeepsGrass, "garden_drone_full_output_keeps_grass")
						.ticks(40),
				RosterEntry.of(GardenDroneScenarios::fun17TallGrassClearedWhole, "garden_drone_clears_tall_grass_whole")
						.ticks(40));

		private Roster() {}
	}

	private GardenDroneScenarios() {}

	/** The station sits here; the farm plot is laid out beside it, inside the scan radius. */
	private static final BlockPos STATION = new BlockPos(1, 2, 1);
	/** One tile of the plot — adjacent, so it is in range at any sane radius. */
	static final BlockPos PLOT = new BlockPos(2, 2, 1);

	static GardenDroneStationBlockEntity place(GameTestHelper helper) {
		helper.setBlock(STATION, ModContent.GARDEN_DRONE_STATION.get());
		GardenDroneStationBlockEntity station =
				helper.getBlockEntity(STATION, GardenDroneStationBlockEntity.class);
		// The dock is only half the machine: without a drone in its bay it has nothing to send out, so
		// every scenario that expects work to happen has to install one first.
		station.setItem(GardenDroneStationBlockEntity.DRONE_SLOT,
				new ItemStack(ModContent.GARDEN_DRONE.get()));
		// Tilling is a tool job, so the dock needs a hoe as well as a drone.
		station.setItem(GardenDroneStationBlockEntity.HOE_SLOT, new ItemStack(Items.IRON_HOE));
		return station;
	}

	/** Enough EU for many actions, so a scenario never stalls on an empty buffer mid-way. */
	static void charge(GardenDroneStationBlockEntity station) {
		station.getEnergyStorage().setAmountUntracked(Config.gardenDroneBuffer);
	}

	/**
	 * Runs {@code body} with the scan radius pinned to 1 block, then restores the configured value.
	 *
	 * <p><b>Why this exists.</b> Gametests share one world, and the shipped radius
	 * ({@link Config#gardenDroneRange} = 4) reaches well past this test's own structure into whatever
	 * scenario is running next door. The station would then dutifully till a neighbour's dirt or plant
	 * into a neighbour's farmland, and the assertions here would fail — or, far worse, pass for the
	 * wrong reason — depending on how the shared server happened to lay the structures out. That is the
	 * exact failure this suite hit first on NeoForge only, while Fabric stayed green: same code, different
	 * neighbours. Radius 1 keeps every action inside the cell this scenario built, so the two loaders
	 * assert the same thing.
	 */
	static void withIsolatedZone(Runnable body) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("gardenDroneRange", 1);
			// Flight is a visual concern; pinning it to the shortest possible hop makes "one job" a fixed
			// number of ticks ({@link #TICKS_PER_JOB}) so the EU assertions stay exact. FUN06 covers the
			// flight delay itself.
			o.set("gardenDroneFlightTicksPerBlock", 0);
			body.run();
		}
	}

	/**
	 * Ticks to drive for exactly one completed action: the flight's hard floor plus a tick to launch
	 * and a tick to land — and short of the return leg, which is what keeps a second job from starting
	 * and spoiling the "exactly one action" assertions.
	 *
	 * <p>Derived from the machine's own constant rather than copied. It was a literal once, and raising
	 * the flight floor turned every scenario red at the same moment for a reason none of them named.
	 */
	static final int TICKS_PER_JOB = GardenDroneStationBlockEntity.MIN_FLIGHT_TICKS + 2;

	/**
	 * TC-DRONE-001-FUN01 — the station tills bare dirt into farmland and spends exactly one action's
	 * worth of EU doing it.
	 *
	 * @implements TC-DRONE-001-FUN01 — bare dirt is tilled, one action's EU is spent
	 */
	public static void fun01TillsDirtAndSpendsEu(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			long before = station.getEnergyStorage().getAmount();
			helper.setBlock(PLOT, Blocks.DIRT);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			// Exactly one job's worth of ticks, so "one action's worth of EU" is a fact rather than a
			// race with however many ticks the scenario happens to grant.
			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			BlockState tilled = helper.getLevel().getBlockState(helper.absolutePos(PLOT));
			if (!tilled.is(Blocks.FARMLAND)) {
				helper.fail("the station did not till bare dirt; found " + tilled.getBlock() + " at " + PLOT);
			}
			long spent = before - station.getEnergyStorage().getAmount();
			if (spent != Config.gardenDroneEuPerAction) {
				helper.fail("tilling spent " + spent + " EU, expected exactly one action ("
						+ Config.gardenDroneEuPerAction + ")");
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN02 — a seed from the seed slot is planted onto bare farmland and is consumed.
	 *
	 * @implements TC-DRONE-001-FUN02 — a seed is planted on bare farmland and consumed
	 */
	public static void fun02PlantsSeedOnFarmland(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			station.setItem(GardenDroneStationBlockEntity.SEED_SLOT, new ItemStack(Items.WHEAT_SEEDS, 4));
			helper.setBlock(PLOT, Blocks.FARMLAND);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			BlockState planted = helper.getLevel().getBlockState(helper.absolutePos(PLOT.above()));
			if (!planted.is(Blocks.WHEAT)) {
				helper.fail("the station did not plant wheat on farmland; found " + planted.getBlock());
			}
			int left = station.getItem(GardenDroneStationBlockEntity.SEED_SLOT).getCount();
			if (left != 3) {
				helper.fail("planting consumed " + (4 - left) + " seeds, expected exactly 1");
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN03 — the headline behaviour: a <b>ripe</b> crop ends up in the station's output
	 * slots, the crop block is gone, and nothing was dropped into the world.
	 *
	 * @implements TC-DRONE-001-FUN03 — a ripe crop lands in the station, never in the world
	 */
	public static void fun03HarvestsRipeCropIntoStation(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			helper.setBlock(PLOT, Blocks.FARMLAND);
			// Age 7 = ripe wheat. Set through the blockstate rather than by waiting for growth so the
			// scenario is deterministic and instant.
			helper.setBlock(PLOT.above(), Blocks.WHEAT.defaultBlockState()
					.setValue(CropBlock.AGE, CropBlock.MAX_AGE));

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			BlockState after = helper.getLevel().getBlockState(helper.absolutePos(PLOT.above()));
			if (after.is(Blocks.WHEAT)) {
				helper.fail("the ripe crop is still standing — the station did not harvest it");
			}
			if (!stationHolds(station, Items.WHEAT)) {
				helper.fail("harvested wheat did not reach the station's output slots");
			}
			// The anti-drop contract: the harvest must never spawn an ItemEntity.
			helper.assertItemEntityNotPresent(Items.WHEAT, PLOT.above(), 2.0);
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN04 — with no EU the station does nothing at all: the ripe crop stays in the
	 * ground and the station reports why. The "nothing is lost when the power dies" half of the
	 * anti-dupe contract.
	 *
	 * @implements TC-DRONE-001-FUN04 — an unpowered station leaves the crop alone
	 */
	public static void fun04NoEnergyLeavesCropUntouched(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			station.getEnergyStorage().setAmountUntracked(0);
			helper.setBlock(PLOT, Blocks.FARMLAND);
			helper.setBlock(PLOT.above(), Blocks.WHEAT.defaultBlockState()
					.setValue(CropBlock.AGE, CropBlock.MAX_AGE));

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (!helper.getLevel().getBlockState(helper.absolutePos(PLOT.above())).is(Blocks.WHEAT)) {
				helper.fail("an unpowered station harvested the crop anyway");
			}
			if (station.status() != GardenDroneStatus.NO_ENERGY) {
				helper.fail("an unpowered station reported " + station.status() + ", expected NO_ENERGY");
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN05 — a full output slot set blocks the harvest instead of voiding the crop.
	 * This is the case that a naive "compute drops, then remove the block" implementation gets wrong,
	 * and the reason the insertion is two-pass.
	 *
	 * @implements TC-DRONE-001-FUN05 — a full output blocks the harvest instead of voiding it
	 */
	public static void fun05FullOutputLeavesCropStanding(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			// Fill every output slot with something the wheat drop cannot merge into.
			for (int i = 0; i < GardenDroneStationBlockEntity.OUTPUT_SLOT_COUNT; i++) {
				station.setItem(GardenDroneStationBlockEntity.OUTPUT_SLOT_START + i,
						new ItemStack(Items.COBBLESTONE, 64));
			}
			helper.setBlock(PLOT, Blocks.FARMLAND);
			helper.setBlock(PLOT.above(), Blocks.WHEAT.defaultBlockState()
					.setValue(CropBlock.AGE, CropBlock.MAX_AGE));

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (!helper.getLevel().getBlockState(helper.absolutePos(PLOT.above())).is(Blocks.WHEAT)) {
				helper.fail("the station harvested a ripe crop with no room to put it — the drop was voided");
			}
			helper.assertItemEntityNotPresent(Items.WHEAT, PLOT.above(), 2.0);
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN06 — the drone flies before it works. With the shipped flight speed the action
	 * must NOT land on the tick the target is chosen; the farm is tended at the speed of a machine
	 * moving, not instantly. This is the one scenario that keeps the configured flight speed.
	 *
	 * @implements TC-DRONE-001-FUN06 — the drone flies before its action lands
	 */
	public static void fun06FlightDelaysTheAction(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("gardenDroneRange", 1)) {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			helper.setBlock(PLOT, Blocks.DIRT);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			// One tick only: the target is picked and the drone launches, but it has not arrived.
			AlaGameTestHelper.drive(station, helper, 1);

			if (helper.getLevel().getBlockState(helper.absolutePos(PLOT)).is(Blocks.FARMLAND)) {
				helper.fail("the action landed on the launch tick — the drone teleported instead of flying");
			}
			if (station.droneTarget() == null) {
				helper.fail("the drone did not take off: no target was recorded");
			}
			// Now give it the whole flight; the same action must land.
			AlaGameTestHelper.drive(station, helper, 200);
			if (!helper.getLevel().getBlockState(helper.absolutePos(PLOT)).is(Blocks.FARMLAND)) {
				helper.fail("the drone never completed the flight — the dirt was left untilled");
			}
			helper.succeed();
		}
	}

	/**
	 * TC-DRONE-001-FUN07 — an empty dock is inert. The station and the drone are two halves: the pad
	 * on its own must not tend anything, and must say so rather than looking idle-because-finished.
	 *
	 * @implements TC-DRONE-001-FUN07 — an empty dock tends nothing and says why
	 */
	public static void fun07WithoutDroneNothingHappens(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			// Take the drone back out — this is the difference from every other scenario.
			station.setItem(GardenDroneStationBlockEntity.DRONE_SLOT, ItemStack.EMPTY);
			helper.setBlock(PLOT, Blocks.DIRT);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (helper.getLevel().getBlockState(helper.absolutePos(PLOT)).is(Blocks.FARMLAND)) {
				helper.fail("a station with no drone tilled the ground anyway");
			}
			if (station.status() != GardenDroneStatus.NO_DRONE) {
				helper.fail("an empty dock reported " + station.status() + ", expected NO_DRONE");
			}
			helper.succeed();
		});
	}

	/**
	 * Loads the hoe slot with a hoe that is {@code pointsLeft} uses away from breaking.
	 *
	 * <p>Derived from the item's own {@code maxDamage} rather than a literal, so the pair below keeps
	 * testing the boundary if the hoe material ever changes.
	 */
	private static ItemStack wornHoe(GardenDroneStationBlockEntity station, int pointsLeft) {
		ItemStack hoe = new ItemStack(Items.IRON_HOE);
		hoe.setDamageValue(hoe.getMaxDamage() - pointsLeft);
		station.setItem(GardenDroneStationBlockEntity.HOE_SLOT, hoe);
		return hoe;
	}

	/**
	 * TC-DRONE-001-FUN08 — the hoe's last point is actually spent: the station tills, the hoe breaks and
	 * the slot ends up <b>empty</b>.
	 *
	 * <p>This is the MOD-319 regression. The guard used to refuse the very use that would have broken the
	 * tool, so the hoe froze one point short of breaking and sat in the slot forever — and because
	 * {@code canPlaceItem} needs an empty slot, that killed hopper-fed hoe replacement outright. The
	 * assertion is on the <b>slot</b>, not on the damage value: "wore down" was never the contract, "frees
	 * the slot for the next hoe" is.
	 *
	 * @implements TC-DRONE-001-FUN08 — the hoe's last point is spent and the slot frees up
	 */
	public static void fun08HoeBreaksOnItsLastUse(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			wornHoe(station, 1); // one point left: this till must be its last
			helper.setBlock(PLOT, Blocks.DIRT);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			BlockState tilled = helper.getLevel().getBlockState(helper.absolutePos(PLOT));
			if (!tilled.is(Blocks.FARMLAND)) {
				helper.fail("a hoe with one point left refused to till; found " + tilled.getBlock());
			}
			ItemStack left = station.getItem(GardenDroneStationBlockEntity.HOE_SLOT);
			if (!left.isEmpty()) {
				helper.fail("the hoe survived its last use with damage " + left.getDamageValue()
						+ "/" + left.getMaxDamage() + "; the slot must be empty so a hopper can refill it");
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN09 — the other half of the boundary: two points left means the hoe <b>survives</b>
	 * with exactly one left.
	 *
	 * <p>Without this, FUN08 alone would stay green if the threshold were over-corrected the other way and
	 * the station started destroying hoes one use early. A one-sided boundary test cannot tell "breaks at
	 * the right moment" from "breaks too eagerly".
	 *
	 * @implements TC-DRONE-001-FUN09 — the hoe is not destroyed one use early
	 */
	public static void fun09HoeSurvivesUntilItsLastUse(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			ItemStack hoe = wornHoe(station, 2); // two points left: this till must NOT be the last
			int maxDamage = hoe.getMaxDamage();
			helper.setBlock(PLOT, Blocks.DIRT);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			ItemStack left = station.getItem(GardenDroneStationBlockEntity.HOE_SLOT);
			if (left.isEmpty()) {
				helper.fail("the hoe broke one use early — two points were left before this till");
			} else if (left.getDamageValue() != maxDamage - 1) {
				helper.fail("tilling moved the hoe to damage " + left.getDamageValue()
						+ ", expected exactly " + (maxDamage - 1));
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN10 — the drone stands on the tile it worked before flying home (MOD-317).
	 *
	 * <p>The action used to land and the return leg used to start in the same tick, so the drone touched
	 * down and lifted off inside one frame. {@link GardenDroneStationBlockEntity#LANDING_PAUSE_TICKS} now
	 * sits between the two, and this pins it by <em>timing the homeward leg</em> rather than by reading a
	 * position: {@code drive()} advances the block entity without advancing the world clock, so the
	 * renderer's own interpolation (which is what the pause is ultimately for) cannot be observed here —
	 * the tick budget can.
	 *
	 * <p>The assertion is a negative control by construction. One full leg after the action lands, the
	 * drone must still be on its way home; with the pause removed the leg would already have ended by
	 * that tick and the station would be parked or outbound on the next errand — so deleting the pause
	 * turns this red instead of leaving it quietly green.
	 *
	 * @implements TC-DRONE-001-FUN10 — the drone stands on the tile it worked before flying home
	 */
	public static void fun10StandsOnTheTileBeforeFlyingHome(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			helper.setBlock(PLOT, Blocks.DIRT);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			// Up to and including the tick the action lands on; the homeward leg starts here.
			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			// A whole leg's worth of ticks later the drone would already be home if the leg had started
			// immediately — the pause is exactly what must still be holding it out here. The margin is
			// two ticks rather than one so the assertion also catches a pause whittled down to a single
			// tick, not only one deleted outright.
			AlaGameTestHelper.drive(station, helper, GardenDroneStationBlockEntity.MIN_FLIGHT_TICKS + 2);
			if (station.droneTarget() == null || !station.isReturning()) {
				helper.fail("the drone was already off its homeward leg one leg after the action landed —"
						+ " the landing pause is not delaying the return (target="
						+ station.droneTarget() + ", returning=" + station.isReturning() + ")");
				return;
			}

			// …and the pause ends: it is a beat, not a stall.
			AlaGameTestHelper.drive(station, helper, GardenDroneStationBlockEntity.LANDING_PAUSE_TICKS + 2);
			if (station.isReturning()) {
				helper.fail("the drone never finished its homeward leg — the landing pause does not end");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN11 — the drone plants on whatever soil the vanilla
	 * {@code #minecraft:supports_crops} tag blesses, not only on vanilla farmland (MOD-538: Farmer's
	 * Delight adds its rich soil farmland to that tag, and the station must treat it as a planting
	 * spot).
	 *
	 * <p>Each loader's gametest mod widens the tag with {@code minecraft:rooted_dirt} via
	 * {@code data/minecraft/tags/block/supports_crops.json} — the same datapack move Farmer's Delight
	 * makes in a real pack, so the scenario simulates the mod without taking a dependency on it.
	 * Rooted dirt also asserts the tilling policy: it is soil the tag accepts but
	 * {@code isTillable} refuses, so a drone that "fixed" planting by tilling foreign soil flat
	 * would fail the second assertion.
	 *
	 * @implements TC-DRONE-001-FUN11 — any {@code #minecraft:supports_crops} soil is a planting spot
	 * (MOD-538: Farmer's Delight rich soil farmland joins the tag by datapack, the gametest mods do
	 * the same with rooted dirt)
	 */
	public static void fun11PlantsOnTaggedSoil(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			station.setItem(GardenDroneStationBlockEntity.SEED_SLOT, new ItemStack(Items.WHEAT_SEEDS, 2));
			helper.setBlock(PLOT, Blocks.ROOTED_DIRT);
			helper.setBlock(PLOT.above(), Blocks.AIR);

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			BlockState planted = helper.getLevel().getBlockState(helper.absolutePos(PLOT.above()));
			if (!planted.is(Blocks.WHEAT)) {
				helper.fail("the station did not plant on tagged soil; found " + planted.getBlock());
			}
			BlockState soil = helper.getLevel().getBlockState(helper.absolutePos(PLOT));
			if (!soil.is(Blocks.ROOTED_DIRT)) {
				helper.fail("the station reworked tagged soil into " + soil.getBlock()
						+ "; foreign soil must never be tilled over");
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- clearing weeds (MOD-779)

	/** Ticks for one full errand there and back, landing pause included, with a margin of two. */
	private static final int TICKS_PER_ROUND_TRIP = TICKS_PER_JOB
			+ GardenDroneStationBlockEntity.MIN_FLIGHT_TICKS + GardenDroneStationBlockEntity.LANDING_PAUSE_TICKS + 2;

	/** Puts {@code plant} on a grass block at {@code ground}, so the plant stands where a field would. */
	private static void plantOnGrass(GameTestHelper helper, BlockPos ground, BlockState plant) {
		helper.setBlock(ground, Blocks.GRASS_BLOCK);
		helper.setBlock(ground.above(), plant);
	}

	/** The block now at {@code pos}, read from the world rather than from anything the station holds. */
	private static BlockState at(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockState(helper.absolutePos(pos));
	}

	/**
	 * TC-DRONE-001-FUN12 — grass on a grass block is pulled up for one point of the hoe and exactly one
	 * action's EU (MOD-779). The red-before-green case of the weeding action: without CLEAR the grass
	 * stands, because the tile under it is not tillable while something grows on it.
	 *
	 * @implements TC-DRONE-001-FUN12 — grass is cleared for one hoe point and one action's EU
	 */
	public static void fun12ClearsGrassForOneHoePoint(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			long before = station.getEnergyStorage().getAmount();
			plantOnGrass(helper, PLOT, Blocks.SHORT_GRASS.defaultBlockState());

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (!at(helper, PLOT.above()).isAir()) {
				helper.fail("the drone did not clear the grass; found " + at(helper, PLOT.above()).getBlock());
			}
			int wear = station.getItem(GardenDroneStationBlockEntity.HOE_SLOT).getDamageValue();
			if (wear != 1) {
				helper.fail("clearing the grass wore the hoe by " + wear + " points, expected exactly 1");
			}
			long spent = before - station.getEnergyStorage().getAmount();
			if (spent != Config.gardenDroneEuPerAction) {
				helper.fail("clearing spent " + spent + " EU, expected exactly one action ("
						+ Config.gardenDroneEuPerAction + ")");
			}
			helper.assertItemEntityNotPresent(Items.WHEAT_SEEDS, PLOT.above(), 2.0);
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN13 — a weed that always drops something (the firefly bush drops itself) lands in
	 * the output slots, never in the world: weeding keeps the harvest's no-{@code ItemEntity} contract.
	 *
	 * @implements TC-DRONE-001-FUN13 — a cleared firefly bush lands in the station's output slots
	 */
	public static void fun13ClearedBushDropLandsInStation(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			plantOnGrass(helper, PLOT, Blocks.FIREFLY_BUSH.defaultBlockState());

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (at(helper, PLOT.above()).is(Blocks.FIREFLY_BUSH)) {
				helper.fail("the firefly bush is still standing — the drone did not clear it");
			}
			if (!stationHolds(station, Items.FIREFLY_BUSH)) {
				helper.fail("the cleared firefly bush did not reach the station's output slots");
			}
			helper.assertItemEntityNotPresent(Items.FIREFLY_BUSH, PLOT.above(), 2.0);
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN14 — no hoe, no weeding: clearing is a tool job like tilling and harvesting.
	 *
	 * @implements TC-DRONE-001-FUN14 — without a hoe the grass stays and no EU is spent
	 */
	public static void fun14WithoutHoeGrassStays(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			station.setItem(GardenDroneStationBlockEntity.HOE_SLOT, ItemStack.EMPTY);
			long before = station.getEnergyStorage().getAmount();
			plantOnGrass(helper, PLOT, Blocks.SHORT_GRASS.defaultBlockState());

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (!at(helper, PLOT.above()).is(Blocks.SHORT_GRASS)) {
				helper.fail("a station with no hoe cleared the grass anyway");
			}
			if (station.getEnergyStorage().getAmount() != before) {
				helper.fail("a station with no hoe spent EU with nothing to do");
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN15 — the weed list is a tag, so the drone tells grass from a planted field: an
	 * unripe kok-sagyz, a trellis and a poppy in the zone stay, while grass beside them goes. The grass
	 * is what keeps this case from passing vacuously — a drone that could not weed at all would also
	 * leave the three alone.
	 *
	 * @implements TC-DRONE-001-FUN15 — kok-sagyz, a trellis and a poppy are never weeded
	 */
	public static void fun15LeavesCropsAndFlowersAlone(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			BlockPos kokSagyzGround = new BlockPos(2, 2, 0);
			BlockPos trellisGround = new BlockPos(2, 2, 2);
			BlockPos grassGround = new BlockPos(0, 2, 1);
			plantOnGrass(helper, PLOT, Blocks.POPPY.defaultBlockState());
			plantOnGrass(helper, kokSagyzGround, ModContent.KOK_SAGYZ.get().defaultBlockState()
					.setValue(KokSagyzBlock.AGE, KokSagyzBlock.AGE_ROSETTE));
			helper.setBlock(trellisGround, Blocks.FARMLAND);
			DoublePlantBlock.placeAt(helper.getLevel(), ModContent.TRELLIS.get().defaultBlockState(),
					helper.absolutePos(trellisGround.above()), 3);
			plantOnGrass(helper, grassGround, Blocks.SHORT_GRASS.defaultBlockState());

			// Several errands: the grass, then the ground it stood on — and time for a third, wrong one.
			AlaGameTestHelper.drive(station, helper, 3 * TICKS_PER_ROUND_TRIP);

			if (at(helper, grassGround.above()).is(Blocks.SHORT_GRASS)) {
				helper.fail("the grass beside the field is still standing — the drone did not weed at all");
			}
			if (!at(helper, PLOT.above()).is(Blocks.POPPY)) {
				helper.fail("the drone pulled up a poppy; found " + at(helper, PLOT.above()).getBlock());
			}
			if (!at(helper, kokSagyzGround.above()).is(ModContent.KOK_SAGYZ.get())) {
				helper.fail("the drone pulled up an unripe kok-sagyz; found "
						+ at(helper, kokSagyzGround.above()).getBlock());
			}
			if (!at(helper, trellisGround.above()).is(ModContent.TRELLIS.get())
					|| !at(helper, trellisGround.above(2)).is(ModContent.TRELLIS.get())) {
				helper.fail("the drone pulled up a trellis; found " + at(helper, trellisGround.above()).getBlock()
						+ " / " + at(helper, trellisGround.above(2)).getBlock());
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN16 — with every output slot full the drone does not weed: the grass stays, the
	 * hoe and the buffer are untouched. Short grass drops nothing seven times in eight, so without the
	 * full-output check this case would clear it most runs — the check is what makes it hold every run.
	 *
	 * @implements TC-DRONE-001-FUN16 — a full output keeps the grass, the hoe and the EU untouched
	 */
	public static void fun16FullOutputKeepsGrass(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			for (int i = 0; i < GardenDroneStationBlockEntity.OUTPUT_SLOT_COUNT; i++) {
				station.setItem(GardenDroneStationBlockEntity.OUTPUT_SLOT_START + i,
						new ItemStack(Items.COBBLESTONE, 64));
			}
			long before = station.getEnergyStorage().getAmount();
			plantOnGrass(helper, PLOT, Blocks.SHORT_GRASS.defaultBlockState());

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (!at(helper, PLOT.above()).is(Blocks.SHORT_GRASS)) {
				helper.fail("the drone weeded with every output slot full");
			}
			if (station.getItem(GardenDroneStationBlockEntity.HOE_SLOT).getDamageValue() != 0) {
				helper.fail("the hoe wore down although nothing was weeded");
			}
			if (station.getEnergyStorage().getAmount() != before) {
				helper.fail("the station spent EU although nothing was weeded");
			}
			helper.succeed();
		});
	}

	/**
	 * TC-DRONE-001-FUN17 — tall grass goes whole in one action: both halves stand inside the scan here,
	 * the drone takes it from the lower half, and the upper one goes with it for one hoe point and one
	 * action's EU.
	 *
	 * @implements TC-DRONE-001-FUN17 — tall grass is cleared whole, from its lower half, in one action
	 */
	public static void fun17TallGrassClearedWhole(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			charge(station);
			long before = station.getEnergyStorage().getAmount();
			helper.setBlock(PLOT.below(), Blocks.GRASS_BLOCK);
			DoublePlantBlock.placeAt(helper.getLevel(), Blocks.TALL_GRASS.defaultBlockState(),
					helper.absolutePos(PLOT), 3);

			AlaGameTestHelper.drive(station, helper, TICKS_PER_JOB);

			if (!at(helper, PLOT).isAir() || !at(helper, PLOT.above()).isAir()) {
				helper.fail("tall grass was not cleared whole; found " + at(helper, PLOT).getBlock()
						+ " / " + at(helper, PLOT.above()).getBlock());
			}
			int wear = station.getItem(GardenDroneStationBlockEntity.HOE_SLOT).getDamageValue();
			long spent = before - station.getEnergyStorage().getAmount();
			if (wear != 1 || spent != Config.gardenDroneEuPerAction) {
				helper.fail("tall grass cost " + wear + " hoe points and " + spent + " EU, expected 1 and "
						+ Config.gardenDroneEuPerAction + " (one action)");
			}
			helper.succeed();
		});
	}

	/** Whether any output slot holds the given item. */
	private static boolean stationHolds(GardenDroneStationBlockEntity station, net.minecraft.world.item.Item item) {
		for (int i = 0; i < GardenDroneStationBlockEntity.OUTPUT_SLOT_COUNT; i++) {
			if (station.getItem(GardenDroneStationBlockEntity.OUTPUT_SLOT_START + i).is(item)) {
				return true;
			}
		}
		return false;
	}
}

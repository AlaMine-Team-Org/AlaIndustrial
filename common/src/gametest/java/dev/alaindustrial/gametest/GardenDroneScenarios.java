package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
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

	private GardenDroneScenarios() {}

	/** The station sits here; the farm plot is laid out beside it, inside the scan radius. */
	private static final BlockPos STATION = new BlockPos(1, 2, 1);
	/** One tile of the plot — adjacent, so it is in range at any sane radius. */
	private static final BlockPos PLOT = new BlockPos(2, 2, 1);

	private static GardenDroneStationBlockEntity place(GameTestHelper helper) {
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
	private static void charge(GardenDroneStationBlockEntity station) {
		station.getEnergyStorage().amount = Config.gardenDroneBuffer;
	}

	/**
	 * Runs {@code body} with the scan radius pinned to 1 block, then restores the configured value.
	 *
	 * <p><b>Why this exists.</b> Gametests share one world, and the shipped radius
	 * ({@link Config#gardenDroneRange} = 9) reaches well past this test's own structure into whatever
	 * scenario is running next door. The station would then dutifully till a neighbour's dirt or plant
	 * into a neighbour's farmland, and the assertions here would fail — or, far worse, pass for the
	 * wrong reason — depending on how the shared server happened to lay the structures out. That is the
	 * exact failure this suite hit first on NeoForge only, while Fabric stayed green: same code, different
	 * neighbours. Radius 1 keeps every action inside the cell this scenario built, so the two loaders
	 * assert the same thing.
	 */
	private static void withIsolatedZone(Runnable body) {
		int configuredRange = Config.gardenDroneRange;
		int configuredFlight = Config.gardenDroneFlightTicksPerBlock;
		Config.gardenDroneRange = 1;
		// Flight is a visual concern; pinning it to the shortest possible hop makes "one job" a fixed
		// number of ticks ({@link #TICKS_PER_JOB}) so the EU assertions stay exact. FUN06 covers the
		// flight delay itself.
		Config.gardenDroneFlightTicksPerBlock = 0;
		try {
			body.run();
		} finally {
			Config.gardenDroneRange = configuredRange;
			Config.gardenDroneFlightTicksPerBlock = configuredFlight;
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
	private static final int TICKS_PER_JOB = GardenDroneStationBlockEntity.MIN_FLIGHT_TICKS + 2;

	/**
	 * TC-DRONE-001-FUN01 — the station tills bare dirt into farmland and spends exactly one action's
	 * worth of EU doing it.
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

	/** TC-DRONE-001-FUN02 — a seed from the seed slot is planted onto bare farmland and is consumed. */
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
	 */
	public static void fun04NoEnergyLeavesCropUntouched(GameTestHelper helper) {
		withIsolatedZone(() -> {
			GardenDroneStationBlockEntity station = place(helper);
			station.getEnergyStorage().amount = 0;
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
	 */
	public static void fun06FlightDelaysTheAction(GameTestHelper helper) {
		int configuredRange = Config.gardenDroneRange;
		Config.gardenDroneRange = 1;
		try {
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
		} finally {
			Config.gardenDroneRange = configuredRange;
		}
	}

	/**
	 * TC-DRONE-001-FUN07 — an empty dock is inert. The station and the drone are two halves: the pad
	 * on its own must not tend anything, and must say so rather than looking idle-because-finished.
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

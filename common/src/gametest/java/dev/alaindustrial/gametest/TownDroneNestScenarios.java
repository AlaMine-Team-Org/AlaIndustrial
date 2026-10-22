package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.DroneNestBlockEntity;
import dev.alaindustrial.item.misc.ConstructionDroneItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import dev.alaindustrial.settlement.drone.DroneNestRules.Status;
import dev.alaindustrial.settlement.drone.TownHall;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * L2 suite of the drone nest as a platform and the builder drone as an item (MOD-858, owner's round 1), replayed by
 * both loaders through the roster (ADR-038), on the rig of {@link TownDroneScenarios}: a drone taken out and put
 * back keeps its charge; an empty nest charges nothing and counts for nothing; a nest anywhere over the town hall's
 * footprint counts, one beside it does not.
 */
public final class TownDroneNestScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		private Roster() {
		}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownDroneNestScenarios::droneItemKeepsItsCharge, "town_drone_item_keeps_its_charge")
						.ticks(60),
				RosterEntry.of(TownDroneNestScenarios::emptyNestDoesNotCharge, "town_drone_empty_nest_does_not_charge")
						.ticks(60),
				RosterEntry.of(TownDroneNestScenarios::nestAnywhereOverTheHallCounts,
						"town_drone_nest_anywhere_over_the_hall_counts").ticks(60));
	}

	private TownDroneNestScenarios() {
	}

	/**
	 * @implements MOD-858-DRONE-ITEM — a drone taken out of its nest keeps its charge on the item, and the same
	 *     item put into a nest brings that charge back; the emptied nest stands with «no drone».
	 */
	public static void droneItemKeepsItsCharge(GameTestHelper helper) {
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 1);
		DroneNestBlockEntity nest = rig.nest(0);
		long charge = DroneNestRules.batteryCapacity() / 3;
		nest.setBattery(charge);
		long taken = nest.removeDrone();
		helper.assertTrue(taken == charge, "the drone must leave with its " + charge + " EU, it left with " + taken);
		ItemStack item = ConstructionDroneItem.withCharge(taken);
		helper.assertTrue(ConstructionDroneItem.charge(item) == charge, "the item must carry the charge, it carries "
				+ ConstructionDroneItem.charge(item));
		helper.assertTrue(!nest.hasDrone() && nest.battery() == 0L, "the emptied nest must hold no drone and no EU");
		helper.assertTrue(nest.removeDrone() < 0, "an empty nest must give no second drone");
		rig.tick();
		helper.assertTrue(nest.status() == Status.NO_DRONE, "an empty nest must say «no drone», it says "
				+ nest.status());
		helper.assertTrue(nest.insertDrone(ConstructionDroneItem.charge(item)) && nest.battery() == charge,
				"the drone put back must bring its charge, the nest holds " + nest.battery());
		helper.assertTrue(!nest.insertDrone(charge), "a nest with a drone must refuse a second one");
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-858-EMPTY-NEST — an empty nest takes nothing from the lit core and is not counted among the
	 *     town's nests; the dispatcher counts it again once a drone is in.
	 */
	public static void emptyNestDoesNotCharge(GameTestHelper helper) {
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 1);
		DroneNestBlockEntity nest = rig.nest(0);
		nest.removeDrone();
		for (int t = 0; t < 45; t++) {
			rig.tick();
		}
		helper.assertTrue(nest.battery() == 0L, "an empty nest must charge nothing, it holds " + nest.battery());
		helper.assertTrue(rig.dispatcher().readyNests() == 0, "the dispatcher must count no nest without a drone, it "
				+ "counts " + rig.dispatcher().readyNests());
		nest.insertDrone(0L);
		for (int t = 0; t < 45; t++) {
			rig.tick();
		}
		helper.assertTrue(nest.battery() > 0L, "control: a docked drone must charge from the lit core");
		helper.assertTrue(rig.dispatcher().readyNests() == 1, "the dispatcher must count the nest with a drone, it "
				+ "counts " + rig.dispatcher().readyNests());
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-858-NEST-OVER-HALL — a nest with a drone anywhere over the town hall's footprint counts, not only
	 *     on a yellow carpet; one beside the footprint stands «not over the town hall» and counts for nothing.
	 */
	public static void nestAnywhereOverTheHallCounts(GameTestHelper helper) {
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 0);
		int top = Integer.MIN_VALUE;
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < rig.plan().size(); i++) {
			BlockPos pos = rig.plan().pos(i);
			top = Math.max(top, pos.getY());
			minX = Math.min(minX, pos.getX());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		BlockPos over = new BlockPos((minX + maxX) / 2, top + 2, (minZ + maxZ) / 2);
		BlockPos beside = new BlockPos(maxX + 3, top + 2, maxZ + 3);
		helper.assertTrue(TownHall.over(rig.level(), rig.town(), over)
				&& !TownHall.over(rig.level(), rig.town(), beside),
				"control: the rule must put " + over + " over the hall and " + beside + " beside it");
		DroneNestBlockEntity onRoof = place(rig, over);
		DroneNestBlockEntity outside = place(rig, beside);
		for (int t = 0; t < 45; t++) {
			rig.tick();
			onRoof.serverTick(rig.level(), over, rig.level().getBlockState(over));
			outside.serverTick(rig.level(), beside, rig.level().getBlockState(beside));
		}
		helper.assertTrue(!onRoof.status().blocking(), "a nest over the hall must work, it says " + onRoof.status());
		helper.assertTrue(outside.status() == Status.OUTSIDE_HALL, "a nest beside the hall must say so, it says "
				+ outside.status());
		helper.assertTrue(rig.dispatcher().readyNests() == 1, "the dispatcher must count the one nest over the hall, "
				+ "it counts " + rig.dispatcher().readyNests());
		rig.level().setBlock(over, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		rig.level().setBlock(beside, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		rig.release();
		helper.succeed();
	}

	private static DroneNestBlockEntity place(TownDroneScenarios.Rig rig, BlockPos at) {
		rig.level().setBlock(at, ModContent.DRONE_NEST.get().defaultBlockState(), Block.UPDATE_ALL);
		DroneNestBlockEntity nest = (DroneNestBlockEntity) rig.level().getBlockEntity(at);
		nest.insertDrone(DroneNestRules.batteryCapacity());
		return nest;
	}
}

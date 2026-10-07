package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.PiezoPlateBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.PiezoPlateBlockEntity;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the piezo plate (MOD-764): one pulse per new press and none for holding; a crowd is one press;
 * an item presses for the smaller pulse; a group of touching plates hands its store to one outlet beside it, and
 * to a cable hidden under the floor block; the plates never carry energy from somebody else; water does not
 * flow through a plate.
 *
 * <p>Every rig fits the 8×8×8 template: the floor is stone at y=1, the plates stand at y=2.
 */
public final class PiezoPlateScenarios {

	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PiezoPlateScenarios::newPressGivesOnePulseHoldingGivesNone,
						"piezo_plate_new_press_one_pulse_holding_none").ticks(120),
				RosterEntry.of(PiezoPlateScenarios::crowdOnOnePlateIsOnePress, "piezo_plate_crowd_is_one_press")
						.ticks(40),
				RosterEntry.of(PiezoPlateScenarios::itemPressGivesTheSmallPulse, "piezo_plate_item_press_small_pulse")
						.ticks(40),
				RosterEntry.of(PiezoPlateScenarios::groupHandsItsStoreToOneSideOutlet,
						"piezo_plate_group_hands_store_to_side_outlet").ticks(80),
				RosterEntry.of(PiezoPlateScenarios::cableUnderTheFloorTakesThePlatesEnergy,
						"piezo_plate_cable_under_floor_takes_energy").ticks(100),
				RosterEntry.of(PiezoPlateScenarios::platesNeverCarryForeignEnergy,
						"piezo_plate_never_carries_foreign_energy").ticks(60),
				RosterEntry.of(PiezoPlateScenarios::waterDoesNotFlowThroughAPlate, "piezo_plate_blocks_water_flow")
						.ticks(60));

		private Roster() {
		}
	}

	private static final int FLOOR_Y = 1;
	private static final int PLATE_Y = 2;

	private PiezoPlateScenarios() {
	}

	// --- rig helpers -------------------------------------------------------------------------------

	private static BlockPos plateAt(int x, int z) {
		return new BlockPos(x, PLATE_Y, z);
	}

	/** Stone floor under a piezo plate at {@code (x, PLATE_Y, z)}. */
	private static PiezoPlateBlockEntity placePlate(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
		helper.setBlock(plateAt(x, z), ModContent.PIEZO_PLATE.get());
		return plate(helper, x, z);
	}

	private static PiezoPlateBlockEntity plate(GameTestHelper helper, int x, int z) {
		PiezoPlateBlockEntity plate = helper.getBlockEntity(plateAt(x, z), PiezoPlateBlockEntity.class);
		if (plate == null) {
			helper.fail("no piezo plate block entity at " + plateAt(x, z));
		}
		return plate;
	}

	private static long stored(GameTestHelper helper, int x, int z) {
		return plate(helper, x, z).getEnergyStorage().getAmount();
	}

	private static boolean pressed(GameTestHelper helper, int x, int z) {
		return helper.getBlockState(plateAt(x, z)).getValue(PiezoPlateBlock.POWERED);
	}

	private static Pig pigOn(GameTestHelper helper, int x, int z) {
		return helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(x + 0.5, PLATE_Y, z + 0.5));
	}

	private static void batteryBox(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, facing));
	}

	private static long batteryCharge(GameTestHelper helper, BlockPos pos) {
		BatteryBoxBlockEntity box = helper.getBlockEntity(pos, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery box missing at " + pos);
		}
		return box.getEnergyStorage().getAmount();
	}

	// --- scenarios -----------------------------------------------------------------------------------

	/**
	 * A pig stepping on the plate gives one living pulse; standing on it for two more re-check periods gives
	 * nothing more; once it is gone and the plate has released, a new pig gives the second pulse.
	 */
	public static void newPressGivesOnePulseHoldingGivesNone(GameTestHelper helper) {
		placePlate(helper, 3, 3);
		long pulse = GeneratorConfig.piezoPlateLivingPressEu;
		Pig[] pig = {pigOn(helper, 3, 3)};
		helper.startSequence()
				.thenExecuteAfter(5, () -> {
					if (!pressed(helper, 3, 3)) {
						helper.fail("a pig on the plate did not press it");
					}
					if (stored(helper, 3, 3) != pulse) {
						helper.fail("first press stored " + stored(helper, 3, 3) + " EU, expected " + pulse);
					}
				})
				.thenExecuteAfter(45, () -> {
					if (stored(helper, 3, 3) != pulse) {
						helper.fail("holding the plate for 45 ticks changed the store to " + stored(helper, 3, 3));
					}
					pig[0].discard();
				})
				.thenExecuteAfter(25, () -> {
					if (pressed(helper, 3, 3)) {
						helper.fail("the plate stayed pressed 25 ticks after the pig was gone");
					}
					pig[0] = pigOn(helper, 3, 3);
				})
				.thenExecuteAfter(5, () -> {
					if (stored(helper, 3, 3) != 2 * pulse) {
						helper.fail("second press after release stored " + stored(helper, 3, 3) + " EU, expected "
								+ 2 * pulse);
					}
				})
				.thenSucceed();
	}

	/** Three pigs stepping on together are one press. */
	public static void crowdOnOnePlateIsOnePress(GameTestHelper helper) {
		placePlate(helper, 3, 3);
		pigOn(helper, 3, 3);
		pigOn(helper, 3, 3);
		pigOn(helper, 3, 3);
		helper.runAfterDelay(10, () -> {
			long stored = stored(helper, 3, 3);
			if (stored != GeneratorConfig.piezoPlateLivingPressEu) {
				helper.fail("three pigs on one plate stored " + stored + " EU — a crowd must be one press");
			}
			helper.succeed();
		});
	}

	/** An item alone presses the plate for the non-living pulse. */
	public static void itemPressGivesTheSmallPulse(GameTestHelper helper) {
		placePlate(helper, 3, 3);
		helper.spawnItem(Items.COBBLESTONE, new Vec3(3.5, PLATE_Y + 0.2, 3.5));
		helper.runAfterDelay(15, () -> {
			long stored = stored(helper, 3, 3);
			if (stored != GeneratorConfig.piezoPlateObjectPressEu) {
				helper.fail("an item press stored " + stored + " EU, expected "
						+ GeneratorConfig.piezoPlateObjectPressEu);
			}
			helper.succeed();
		});
	}

	/** Three plates in a row hand everything they hold to the battery box beside the last one. */
	public static void groupHandsItsStoreToOneSideOutlet(GameTestHelper helper) {
		PiezoPlateBlockEntity first = placePlate(helper, 1, 3);
		PiezoPlateBlockEntity middle = placePlate(helper, 2, 3);
		PiezoPlateBlockEntity last = placePlate(helper, 3, 3);
		BlockPos box = new BlockPos(4, PLATE_Y, 3);
		batteryBox(helper, box, Direction.WEST);
		first.getEnergyStorage().produceInternal(40);
		middle.getEnergyStorage().produceInternal(30);
		last.getEnergyStorage().produceInternal(20);
		first.onNeighbourChanged();
		helper.runAfterDelay(60, () -> {
			long charged = batteryCharge(helper, box);
			if (charged != 90) {
				helper.fail("the battery box beside the path got " + charged + " EU of the 90 the path held");
			}
			if (stored(helper, 1, 3) + stored(helper, 2, 3) + stored(helper, 3, 3) != 0) {
				helper.fail("energy stayed in the plates although the outlet had room");
			}
			helper.succeed();
		});
	}

	/** A cable under the plain floor block sees the plate through it; the line charges the battery box. */
	public static void cableUnderTheFloorTakesThePlatesEnergy(GameTestHelper helper) {
		helper.setBlock(new BlockPos(3, 0, 3), ModContent.COPPER_CABLE.get());
		BlockPos box = new BlockPos(4, 0, 3);
		batteryBox(helper, box, Direction.WEST);
		PiezoPlateBlockEntity plate = placePlate(helper, 3, 3);
		plate.getEnergyStorage().produceInternal(60);
		plate.onNeighbourChanged();
		helper.runAfterDelay(80, () -> {
			long charged = batteryCharge(helper, box);
			if (charged <= 0) {
				helper.fail("the cable under the floor block took nothing from the piezo plate above it");
			}
			if (charged > 60) {
				helper.fail("the battery box got " + charged + " EU, more than the plate ever held");
			}
			helper.succeed();
		});
	}

	/** A creative source beside one end of the path never reaches a battery box at the other end. */
	public static void platesNeverCarryForeignEnergy(GameTestHelper helper) {
		helper.setBlock(new BlockPos(0, PLATE_Y, 3), ModContent.CREATIVE_ENERGY_SOURCE.get());
		placePlate(helper, 1, 3);
		placePlate(helper, 2, 3);
		BlockPos box = new BlockPos(3, PLATE_Y, 3);
		batteryBox(helper, box, Direction.WEST);
		helper.runAfterDelay(40, () -> {
			long charged = batteryCharge(helper, box);
			if (charged != 0) {
				helper.fail("the plates carried " + charged + " EU from a creative source — they are not cables");
			}
			if (stored(helper, 1, 3) != 0) {
				helper.fail("a piezo plate accepted energy from a neighbour");
			}
			helper.succeed();
		});
	}

	/** A water source beside the plate does not flow into it and does not wash it away. */
	public static void waterDoesNotFlowThroughAPlate(GameTestHelper helper) {
		placePlate(helper, 3, 3);
		helper.setBlock(new BlockPos(2, FLOOR_Y, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(2, PLATE_Y, 3), Blocks.WATER);
		helper.runAfterDelay(40, () -> {
			if (!helper.getBlockState(plateAt(3, 3)).is(ModContent.PIEZO_PLATE.get())) {
				helper.fail("water washed the piezo plate away");
			}
			if (!helper.getBlockState(plateAt(3, 3)).getFluidState().isEmpty()) {
				helper.fail("water flowed into the piezo plate's cell");
			}
			helper.succeed();
		});
	}
}

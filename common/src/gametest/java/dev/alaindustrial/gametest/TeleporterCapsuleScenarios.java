package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CapsuleGlass;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.TeleporterCapsuleBlock;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.teleporter.TeleportEngine;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * L2 suite for the teleporter capsule (MOD-112): two glass blocks on a station become its capsule, the
 * door works and waits for the doorway, taking the capsule apart hands back exactly the glass that went
 * in, and a jump refuses a station that has no capsule.
 *
 * <p>How the capsule and its door look is checked in the dev client; everything here is decided by the
 * server state alone.
 */
public final class TeleporterCapsuleScenarios {

	private TeleporterCapsuleScenarios() {}

	private static final BlockPos STATION = new BlockPos(1, 2, 1);
	private static final BlockPos MIDDLE = STATION.above();
	private static final BlockPos TOP = STATION.above(2);
	private static final Block RED = Blocks.STAINED_GLASS.pick(DyeColor.RED);
	private static final Block BLUE = Blocks.STAINED_GLASS.pick(DyeColor.BLUE);

	private static TeleporterBlockEntity placeStation(GameTestHelper helper, Direction facing) {
		helper.setBlock(STATION, ModContent.TELEPORTER.get().defaultBlockState().setValue(TeleporterBlock.FACING, facing));
		return (TeleporterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(STATION));
	}

	/** A station facing {@code facing} with red glass under blue, assembled. */
	private static TeleporterBlockEntity capsule(GameTestHelper helper, Direction facing) {
		TeleporterBlockEntity station = placeStation(helper, facing);
		helper.setBlock(MIDDLE, RED);
		helper.setBlock(TOP, BLUE);
		TeleporterBlock.tryAssemble(helper.getLevel(), helper.absolutePos(STATION));
		return station;
	}

	private static BlockState at(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockState(helper.absolutePos(rel));
	}

	private static long itemsOf(GameTestHelper helper, Block block) {
		AABB around = new AABB(helper.absolutePos(STATION)).inflate(3.0);
		List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
		return items.stream().map(ItemEntity::getItem).filter(s -> s.is(block.asItem()))
				.mapToLong(ItemStack::getCount).sum();
	}

	/**
	 * @implements TC-TELE-005-FUN01 — two glass on a station become a capsule: the station is formed, the
	 *     cells carry the station's facing and each its own glass, a click on either cell leads back to
	 *     the station, and the floor is low enough to walk onto.
	 */
	public static void tcTele005Fun01_twoGlassFormTheCapsule(GameTestHelper helper) {
		capsule(helper, Direction.EAST);

		if (!TeleporterBlock.isFormed(at(helper, STATION))) {
			helper.fail("the station did not assemble with two glass blocks on top");
			return;
		}
		BlockState middle = at(helper, MIDDLE);
		BlockState top = at(helper, TOP);
		if (!(middle.getBlock() instanceof TeleporterCapsuleBlock) || !(top.getBlock() instanceof TeleporterCapsuleBlock)) {
			helper.fail("the glass was not replaced by capsule cells: " + middle + " / " + top);
			return;
		}
		if (middle.getValue(TeleporterCapsuleBlock.PART) != TeleporterCapsuleBlock.Part.MIDDLE
				|| top.getValue(TeleporterCapsuleBlock.PART) != TeleporterCapsuleBlock.Part.TOP) {
			helper.fail("cell parts are wrong: " + middle + " / " + top);
		}
		if (middle.getValue(TeleporterCapsuleBlock.GLASS) != CapsuleGlass.RED
				|| top.getValue(TeleporterCapsuleBlock.GLASS) != CapsuleGlass.BLUE) {
			helper.fail("each cell must remember its own glass: " + middle + " / " + top);
		}
		if (middle.getValue(TeleporterCapsuleBlock.FACING) != Direction.EAST
				|| top.getValue(TeleporterCapsuleBlock.FACING) != Direction.EAST) {
			helper.fail("the door must face where the station faces: " + middle + " / " + top);
		}
		BlockPos station = helper.absolutePos(STATION);
		if (!TeleporterCapsuleBlock.stationPos(helper.getLevel(), helper.absolutePos(TOP)).equals(station)
				|| !TeleporterCapsuleBlock.stationPos(helper.getLevel(), helper.absolutePos(MIDDLE)).equals(station)) {
			helper.fail("a capsule cell does not lead back to its station");
		}
		// A player steps up 0.6 of a block; a higher floor turns the capsule into something to jump into.
		double floor = at(helper, STATION).getCollisionShape(helper.getLevel(), station).max(Direction.Axis.Y);
		if (floor > 0.6 + 1.0e-6) {
			helper.fail("capsule floor at " + floor + " is higher than a player's step");
		}
		helper.succeed();
	}

	/**
	 * Vanilla tells a block about its six neighbours only. Glass placed on top of the first glass
	 * notifies that glass — which does nothing — and never the station two blocks down, so without the
	 * station's poll this capsule would never form. The first assertion proves the gap is real; the
	 * second that the poll closes it.
	 *
	 * @implements TC-TELE-005-FUN02 — glass placed one block at a time still forms the capsule.
	 */
	public static void tcTele005Fun02_secondGlassFoundByPolling(GameTestHelper helper) {
		placeStation(helper, Direction.NORTH);
		helper.setBlock(MIDDLE, Blocks.GLASS);
		helper.setBlock(TOP, Blocks.GLASS);
		if (TeleporterBlock.isFormed(at(helper, STATION))) {
			helper.fail("the capsule formed synchronously — the scenario no longer exercises the poll");
			return;
		}
		helper.runAfterDelay(10, () -> {
			if (!TeleporterBlock.isFormed(at(helper, STATION))) {
				helper.fail("the second glass was never noticed: the station did not assemble");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements TC-TELE-005-FUN03 — a door opened by hand shuts itself after
	 *     {@code teleporterCapsuleDoorOpenTicks}, both cells together.
	 */
	public static void tcTele005Fun03_doorOpensAndClosesItself(GameTestHelper helper) {
		capsule(helper, Direction.NORTH);
		TeleporterCapsuleBlock.setDoor(helper.getLevel(), helper.absolutePos(MIDDLE), true);
		if (!at(helper, MIDDLE).getValue(TeleporterCapsuleBlock.OPEN) || !at(helper, TOP).getValue(TeleporterCapsuleBlock.OPEN)) {
			helper.fail("both cells must open together");
			return;
		}
		if (!TeleporterCapsuleBlock.isDoorOpen(helper.getLevel(), helper.absolutePos(STATION))) {
			helper.fail("the station does not see its own door open");
			return;
		}
		helper.runAfterDelay(Config.teleporterCapsuleDoorOpenTicks + 2, () -> {
			if (at(helper, MIDDLE).getValue(TeleporterCapsuleBlock.OPEN) || at(helper, TOP).getValue(TeleporterCapsuleBlock.OPEN)) {
				helper.fail("the door did not close itself after " + Config.teleporterCapsuleDoorOpenTicks + " ticks");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements TC-TELE-005-NEG01 — the door does not close on someone standing in the doorway, and
	 *     closes once they have gone.
	 */
	public static void tcTele005Neg01_doorWaitsForAnOccupiedDoorway(GameTestHelper helper) {
		capsule(helper, Direction.NORTH);
		BlockPos station = helper.absolutePos(STATION);
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		// Half in, half out of a north-facing door: the front edge of the cell.
		player.snapTo(station.getX() + 0.5, station.getY() + TeleporterBlock.CAPSULE_FLOOR, station.getZ() + 0.1);
		TeleporterCapsuleBlock.setDoor(helper.getLevel(), helper.absolutePos(MIDDLE), true);

		int past = Config.teleporterCapsuleDoorOpenTicks + Config.teleporterCapsuleDoorRecheckTicks + 2;
		helper.runAfterDelay(past, () -> {
			if (!at(helper, MIDDLE).getValue(TeleporterCapsuleBlock.OPEN)) {
				helper.fail("the door closed on a player standing in the doorway");
				return;
			}
			player.snapTo(station.getX() + 5.5, station.getY(), station.getZ() + 5.5);
			helper.runAfterDelay(Config.teleporterCapsuleDoorRecheckTicks + 2, () -> {
				if (at(helper, MIDDLE).getValue(TeleporterCapsuleBlock.OPEN)) {
					helper.fail("the door stayed open after the doorway emptied");
					return;
				}
				helper.succeed();
			});
		});
	}

	/**
	 * The playtest's rapid clicking reversed the door every few ticks, so it never finished a slide. A
	 * click while the panels are still travelling is now ignored; the scenario clicks through the real
	 * server path ({@code useItemOn → useWithoutItem}), because {@code setDoor} bypasses the guard.
	 *
	 * @implements TC-TELE-005-NEG02 — a second click during the door's slide does nothing; a click after
	 *     the slide works the door again.
	 */
	public static void tcTele005Neg02_clickDuringSlideIsIgnored(GameTestHelper helper) {
		capsule(helper, Direction.NORTH);
		helper.useBlock(MIDDLE, helper.makeMockPlayer(GameType.SURVIVAL));
		if (!at(helper, MIDDLE).getValue(TeleporterCapsuleBlock.OPEN)) {
			helper.fail("a click on the capsule glass must open the door");
			return;
		}
		helper.useBlock(TOP, helper.makeMockPlayer(GameType.SURVIVAL));
		if (!at(helper, MIDDLE).getValue(TeleporterCapsuleBlock.OPEN)) {
			helper.fail("a click in the same tick reversed the door mid-slide");
			return;
		}
		helper.runAfterDelay(Config.teleporterCapsuleDoorSlideTicks + 1, () -> {
			helper.useBlock(MIDDLE, helper.makeMockPlayer(GameType.SURVIVAL));
			if (at(helper, MIDDLE).getValue(TeleporterCapsuleBlock.OPEN)) {
				helper.fail("once the slide is over, a click must close the door again");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * @implements TC-TELE-005-BRK01 — breaking the top cell drops its own glass once; the middle cell
	 *     turns back into its own glass and the station comes apart.
	 */
	public static void tcTele005Brk01_breakingACellReturnsItsGlass(GameTestHelper helper) {
		capsule(helper, Direction.NORTH);
		helper.getLevel().destroyBlock(helper.absolutePos(TOP), true);

		if (!at(helper, MIDDLE).is(RED)) {
			helper.fail("the middle cell must turn back into its red glass, got " + at(helper, MIDDLE));
			return;
		}
		if (TeleporterBlock.isFormed(at(helper, STATION))) {
			helper.fail("the station is still formed with its capsule broken");
			return;
		}
		long blue = itemsOf(helper, BLUE);
		long red = itemsOf(helper, RED);
		if (blue != 1 || red != 0) {
			helper.fail("expected exactly the broken cell's blue glass to drop, got blue=" + blue + " red=" + red);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-005-BRK02 — breaking the station leaves both glasses standing as glass: nothing is
	 *     duplicated into item form and nothing is lost.
	 */
	public static void tcTele005Brk02_breakingTheStationFreesBothGlasses(GameTestHelper helper) {
		capsule(helper, Direction.NORTH);
		helper.getLevel().destroyBlock(helper.absolutePos(STATION), true);

		if (!at(helper, MIDDLE).is(RED) || !at(helper, TOP).is(BLUE)) {
			helper.fail("both cells must return to their glass: " + at(helper, MIDDLE) + " / " + at(helper, TOP));
			return;
		}
		if (itemsOf(helper, RED) != 0 || itemsOf(helper, BLUE) != 0) {
			helper.fail("glass that stayed in the world must not also drop as an item");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-005-SEC01 — a charged public station without its capsule refuses a jump with
	 *     {@code NOT_FORMED}, keeps its EU, and accepts once the capsule is built.
	 */
	public static void tcTele005Sec01_stationWithoutCapsuleRefusesJump(GameTestHelper helper) {
		TeleporterBlockEntity station = placeStation(helper, Direction.NORTH);
		station.setPrivate(false);
		station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		BlockPos near = helper.absolutePos(STATION.offset(3, 0, 0));
		player.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		player.getInventory().clearContent();
		TeleportPoint point = new TeleportPoint(helper.getLevel().dimension(), helper.absolutePos(STATION), "home");
		ItemStack remote = new ItemStack(ModContent.TELEPORTER_REMOTE.get());

		if (TeleportEngine.checkPolicy(player, remote, point) != TeleportEngine.Denial.NOT_FORMED) {
			helper.fail("a station without a capsule must refuse the jump as NOT_FORMED, got "
					+ TeleportEngine.checkPolicy(player, remote, point));
			return;
		}
		long before = station.getEnergyStorage().getAmount();
		if (TeleportEngine.execute(player, point, TeleportEngine.computeCost(player, point))) {
			helper.fail("the jump fired into a station with no capsule");
			return;
		}
		if (station.getEnergyStorage().getAmount() != before) {
			helper.fail("a refused jump must not touch the station's EU");
			return;
		}

		helper.setBlock(MIDDLE, Blocks.GLASS);
		helper.setBlock(TOP, Blocks.GLASS);
		TeleporterBlock.tryAssemble(helper.getLevel(), helper.absolutePos(STATION));
		if (!TeleportEngine.checkPolicy(player, remote, point).allowed()) {
			helper.fail("the same station must accept the jump once its capsule stands, got "
					+ TeleportEngine.checkPolicy(player, remote, point));
			return;
		}
		helper.succeed();
	}
}

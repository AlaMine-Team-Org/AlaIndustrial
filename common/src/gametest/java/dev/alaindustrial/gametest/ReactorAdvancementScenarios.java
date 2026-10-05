package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.ReactorRig.CONTROLLER;
import static dev.alaindustrial.gametest.ReactorRig.buildRoom;
import static dev.alaindustrial.gametest.ReactorRig.controller;
import static dev.alaindustrial.gametest.ReactorRig.driveUnderLoad;
import static dev.alaindustrial.gametest.ReactorRig.placeColumn;
import static dev.alaindustrial.gametest.ReactorRig.placeColumnAt;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * World scenarios of the reactor advancement branch (MOD-473): the steps reach the controller's owner, and an
 * unowned reactor awards nobody. The assertions are shared with the meltdown and blast scenarios, which
 * check the hidden steps. Reached through the {@link ReactorScenarios} facade, whose names the two lanes run
 * (MOD-713, TST-2).
 */
final class ReactorAdvancementScenarios {

	private ReactorAdvancementScenarios() {}

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

		List<FuelRodAssemblyBlockEntity> row = new ArrayList<>();
		for (int x = 1; x <= 3; x++) {
			FuelRodAssemblyBlockEntity column = placeColumnAt(helper, new BlockPos(x, 1, 2));
			for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
				column.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
			}
			row.add(column);
		}
		helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		// Long enough to clear the scan interval: the room is not FORMED before the first sweep.
		driveUnderLoad(helper, brain, 1000);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("room did not seal: " + brain.getStatus());
		}
		assertEarned(helper, owner, "reactor_room");
		assertEarned(helper, owner, "reactor_power");

		// The steam step needs coolant to exist. Kept topped up and drained, exactly as a plumbed loop behaves
		// — see coolantCatchesACoreTheShellCannotHold for why filling once measures tank size.
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
	static boolean earned(GameTestHelper helper, ServerPlayer player, String name) {
		Identifier id = Industrialization.id(name);
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(id);
		if (holder == null) {
			helper.fail("no such advancement: " + id + " — the datapack did not load it");
			throw new IllegalStateException("unreachable");
		}
		return player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	static void assertEarned(GameTestHelper helper, ServerPlayer player, String name) {
		if (!earned(helper, player, name)) {
			helper.fail("advancement " + name + " was not awarded to the controller's owner");
		}
	}

	static void assertNotEarned(GameTestHelper helper, ServerPlayer player, String name) {
		assertNotEarned(helper, player, name, "by an UNOWNED reactor");
	}

	static void assertNotEarned(GameTestHelper helper, ServerPlayer player, String name, String why) {
		if (earned(helper, player, name)) {
			helper.fail("advancement " + name + " was awarded " + why);
		}
	}
}

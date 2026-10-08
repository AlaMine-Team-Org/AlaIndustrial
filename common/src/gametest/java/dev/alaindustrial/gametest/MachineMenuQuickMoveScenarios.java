package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import dev.alaindustrial.menu.MaceratorMenu;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * L2 suite for shift-click on machine menus (MOD-784): a stack from the player's inventory goes only into
 * slots that would take it.
 *
 * <p>Vanilla's {@code moveItemStackTo} tops up a matching stack in its first pass without asking
 * {@code Slot#mayPlace}, so a take-only result slot used to swallow the player's own wheat or dust.
 * Driven through {@code menu.clicked(..., QUICK_MOVE, ...)}, the path a real shift-click takes.
 */
public final class MachineMenuQuickMoveScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MachineMenuQuickMoveScenarios::quickMove_gardenDroneOutputTakesNothingFromThePlayer,
								"quick_move_garden_drone_output_takes_nothing_from_the_player")
						.ticks(40),
				RosterEntry.of(MachineMenuQuickMoveScenarios::quickMove_maceratorOutputTakesNothingFromThePlayer,
								"quick_move_macerator_output_takes_nothing_from_the_player")
						.ticks(40));

		private Roster() {}
	}

	private MachineMenuQuickMoveScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * The Garden Drone Station: wheat matching a partial harvest stack stays in the inventory; seeds top up
	 * the seed slot and do not spill into the matching seed stack in the output; the harvest still
	 * shift-clicks out into the inventory.
	 *
	 * @implements MOD-784-OUTPUT — shift-click from the inventory never tops up a result slot
	 * @covers MOD-784
	 */
	public static void quickMove_gardenDroneOutputTakesNothingFromThePlayer(GameTestHelper helper) {
		GardenDroneStationBlockEntity station = GardenDroneScenarios.place(helper);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		GardenDroneStationMenu menu = new GardenDroneStationMenu(1, player.getInventory(), station,
				ContainerLevelAccess.create(helper.getLevel(), station.getBlockPos()));
		int wheatOut = GardenDroneStationBlockEntity.OUTPUT_SLOT_START;
		int seedsOut = GardenDroneStationBlockEntity.OUTPUT_SLOT_START + 1;
		station.setItem(wheatOut, new ItemStack(Items.WHEAT, 10));
		station.setItem(seedsOut, new ItemStack(Items.WHEAT_SEEDS, 10));
		station.setItem(GardenDroneStationBlockEntity.SEED_SLOT, new ItemStack(Items.WHEAT_SEEDS, 62));

		shiftClickFromInventory(helper, menu, player, Items.WHEAT, 5);
		expect(helper, station, wheatOut, Items.WHEAT, 10, "the player's wheat was poured into the harvest slot");
		expectHeld(helper, player, Items.WHEAT, 5);

		// Two seeds fit the seed slot; the other three must stay with the player, not join the output.
		shiftClickFromInventory(helper, menu, player, Items.WHEAT_SEEDS, 5);
		expect(helper, station, GardenDroneStationBlockEntity.SEED_SLOT, Items.WHEAT_SEEDS, 64,
				"seeds no longer top up the seed slot");
		expect(helper, station, seedsOut, Items.WHEAT_SEEDS, 10, "the seed overflow was poured into the output");
		expectHeld(helper, player, Items.WHEAT_SEEDS, 3);

		// The other way still works: the harvest shift-clicks out into the inventory.
		menu.clicked(menuSlot(helper, menu, station, wheatOut), 0, ContainerInput.QUICK_MOVE, player);
		if (!station.getItem(wheatOut).isEmpty()) {
			helper.fail("the harvest no longer shift-clicks out of the output: " + station.getItem(wheatOut));
		}
		expectHeld(helper, player, Items.WHEAT, 10);
		helper.succeed();
	}

	/**
	 * A processing machine: iron dust from the inventory does not top up the dust in the result slot. The
	 * input holds ore, so the dust has no slot to go to and stays with the player.
	 *
	 * @implements MOD-784-OUTPUT — shift-click from the inventory never tops up a result slot
	 * @covers MOD-784
	 */
	public static void quickMove_maceratorOutputTakesNothingFromThePlayer(GameTestHelper helper) {
		MaceratorBlockEntity be = AlaGameTestHelper.place(helper, POS, ModContent.MACERATOR.get(),
				MaceratorBlockEntity.class);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		MaceratorMenu menu = new MaceratorMenu(1, player.getInventory(), be,
				ContainerLevelAccess.create(helper.getLevel(), be.getBlockPos()));
		Item dust = ModContent.IRON_DUST.get();
		be.setItem(0, new ItemStack(Items.IRON_ORE, 8));
		be.setItem(1, new ItemStack(dust, 10));

		shiftClickFromInventory(helper, menu, player, dust, 5);
		expect(helper, be, 1, dust, 10, "the player's dust was poured into the result slot");
		expect(helper, be, 0, Items.IRON_ORE, 8, "the input changed");
		expectHeld(helper, player, dust, 5);
		helper.succeed();
	}

	/** Give the player exactly {@code count} of {@code item} and shift-click that stack. */
	private static void shiftClickFromInventory(GameTestHelper helper, MachineMenu menu, ServerPlayer player,
			Item item, int count) {
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(item, count));
		int slot = menu.findSlot(player.getInventory(),
				player.getInventory().findSlotMatchingItem(new ItemStack(item))).orElse(-1);
		if (slot < 0) {
			helper.fail("the player's " + item + " is not bound to a menu slot");
			return;
		}
		menu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
	}

	private static int menuSlot(GameTestHelper helper, MachineMenu menu, Container container, int index) {
		int slot = menu.findSlot(container, index).orElse(-1);
		if (slot < 0) {
			helper.fail("container slot " + index + " is not bound to a menu slot");
		}
		return slot;
	}

	private static void expect(GameTestHelper helper, Container container, int index, Item item, int count,
			String why) {
		ItemStack stack = container.getItem(index);
		if (!stack.is(item) || stack.getCount() != count) {
			helper.fail(why + ": slot " + index + " holds " + stack + ", expected " + count + " " + item);
		}
	}

	private static void expectHeld(GameTestHelper helper, ServerPlayer player, Item item, int count) {
		int held = player.getInventory().countItem(item);
		if (held != count) {
			helper.fail("the player holds " + held + " " + item + ", expected " + count);
		}
	}
}

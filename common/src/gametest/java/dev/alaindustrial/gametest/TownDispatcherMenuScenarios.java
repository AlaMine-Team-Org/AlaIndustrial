package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.menu.TownDispatcherMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.RoadWork;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * L2 of the dispatcher's shift-click on the «Materials» tab (owner's round 2 of 0.1.219, R2-1): a stack from the
 * inventory puts into the buffer only what the stage still asks for, the rest stays with the player, an item the
 * stage does not ask for stays put, and on the other tabs the whole stack goes as before. Replayed by both loaders
 * through the roster (ADR-038).
 */
public final class TownDispatcherMenuScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		private Roster() {
		}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownDispatcherMenuScenarios::materialsShiftClickPutsOnlyWhatIsAskedFor,
						"town_dispatcher_materials_shift_click_puts_only_what_is_asked_for").ticks(20));
	}

	private TownDispatcherMenuScenarios() {
	}

	/** Menu index of the player's first main-inventory slot: after the dispatcher's slots. */
	private static final int INVENTORY_FIRST = TownDispatcherBlockEntity.SLOT_COUNT;

	/**
	 * @implements MOD-824-R2-1 — on the «Materials» tab a shift-clicked stack of 64 road blocks against a bill of 55
	 *     with 10 already in the buffer puts 45 and leaves 19; an item the bill does not ask for stays put; on the
	 *     «Buffer» tab the whole stack goes.
	 */
	public static void materialsShiftClickPutsOnlyWhatIsAskedFor(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		TownDispatcherMenu menu = new TownDispatcherMenu(0, player.getInventory());
		menu.injectTestChannel(TownDispatcherBlockEntity.Channel.CONTROL.ordinal(), 1);
		menu.billSource(() -> List.of(new DispatcherView.BillLine(RoadWork.ROAD_ITEM, 55, 0)));
		ItemStack road = new ItemStack(ModContent.TOWN_ROAD.get().asItem());
		menu.slots.get(TownDispatcherBlockEntity.BUFFER_FIRST).set(road.copyWithCount(10));
		player.getInventory().setItem(9, road.copyWithCount(64));
		player.getInventory().setItem(10, new ItemStack(Items.OAK_PLANKS, 32));

		helper.assertTrue(menu.clickMenuButton(player, TownDispatcherMenu.BUTTON_MATERIALS_SHOWN),
				"the «Materials» button must be handled");
		menu.quickMoveStack(player, INVENTORY_FIRST);
		menu.quickMoveStack(player, INVENTORY_FIRST);
		helper.assertTrue(player.getInventory().getItem(9).getCount() == 19,
				"64 road blocks against 55 asked with 10 in the buffer must leave 19: "
						+ player.getInventory().getItem(9));
		helper.assertTrue(bufferCount(menu, road) == 55,
				"the buffer must hold the 55 asked: " + bufferCount(menu, road));
		menu.quickMoveStack(player, INVENTORY_FIRST + 1);
		helper.assertTrue(player.getInventory().getItem(10).getCount() == 32,
				"planks the bill does not ask for must stay with the player");

		helper.assertTrue(menu.clickMenuButton(player, TownDispatcherMenu.BUTTON_MATERIALS_HIDDEN),
				"control: the «hidden» button must be handled");
		menu.quickMoveStack(player, INVENTORY_FIRST + 1);
		helper.assertTrue(player.getInventory().getItem(10).isEmpty(),
				"control: off the «Materials» tab the whole stack goes into the buffer");
		helper.succeed();
	}

	private static int bufferCount(TownDispatcherMenu menu, ItemStack like) {
		int total = 0;
		for (int i = TownDispatcherBlockEntity.BUFFER_FIRST; i < TownDispatcherBlockEntity.SLOT_COUNT; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (ItemStack.isSameItem(stack, like)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}

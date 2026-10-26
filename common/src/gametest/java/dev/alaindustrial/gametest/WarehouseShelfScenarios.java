package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.WarehouseShelfBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The warehouse shelf (MOD-885, R157): a 27-slot chest whose four cards show the kinds it holds the most of. The
 * cards follow every change of the contents, reach the client in the update tag without the contents, and a hopper
 * fills the shelf like any chest.
 */
public final class WarehouseShelfScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(WarehouseShelfScenarios::tcWarehouseShelf001Fun01CardsFollowTheContents,
						"warehouse_shelf_cards_follow_the_contents").ticks(20),
				RosterEntry.of(WarehouseShelfScenarios::tcWarehouseShelf001Fun02UpdateTagCarriesCardsNotContents,
						"warehouse_shelf_update_tag_carries_cards_not_contents").ticks(20),
				RosterEntry.of(WarehouseShelfScenarios::tcWarehouseShelf001Fun03HopperFillsTheShelf,
						"warehouse_shelf_hopper_fills_the_shelf").ticks(100));

		private Roster() {}
	}

	private static final BlockPos SHELF = new BlockPos(1, 2, 1);

	private WarehouseShelfScenarios() {}

	private static WarehouseShelfBlockEntity shelf(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModContent.WAREHOUSE_SHELF.get());
		return helper.getBlockEntity(at, WarehouseShelfBlockEntity.class);
	}

	private static List<Item> cardItems(WarehouseShelfBlockEntity shelf) {
		return shelf.cards().stream().map(ItemStack::getItem).toList();
	}

	/**
	 * @implements TC-WAREHOUSE-SHELF-001-FUN01 — five kinds with different totals (cobblestone in two slots) give the
	 *     four biggest, biggest first; taking the biggest out moves the rest up and brings the fifth in; an emptied
	 *     shelf shows no card.
	 */
	public static void tcWarehouseShelf001Fun01CardsFollowTheContents(GameTestHelper helper) {
		WarehouseShelfBlockEntity shelf = shelf(helper, SHELF);
		if (!shelf.cards().isEmpty()) {
			helper.fail("a new shelf must show no card, shows " + cardItems(shelf));
			return;
		}
		shelf.setItem(0, new ItemStack(Items.REDSTONE, 5));
		shelf.setItem(1, new ItemStack(Items.IRON_INGOT, 32));
		shelf.setItem(2, new ItemStack(Items.COBBLESTONE, 40));
		shelf.setItem(5, new ItemStack(Items.COAL, 20));
		shelf.setItem(9, new ItemStack(Items.OAK_PLANKS, 12));
		shelf.setItem(26, new ItemStack(Items.COBBLESTONE, 40));
		List<Item> want = List.of(Items.COBBLESTONE, Items.IRON_INGOT, Items.COAL, Items.OAK_PLANKS);
		if (!cardItems(shelf).equals(want)) {
			helper.fail("cards must be the four biggest kinds " + want + ", are " + cardItems(shelf));
			return;
		}
		shelf.setItem(2, ItemStack.EMPTY);
		shelf.setItem(26, ItemStack.EMPTY);
		want = List.of(Items.IRON_INGOT, Items.COAL, Items.OAK_PLANKS, Items.REDSTONE);
		if (!cardItems(shelf).equals(want)) {
			helper.fail("with the cobblestone taken the cards must be " + want + ", are " + cardItems(shelf));
			return;
		}
		shelf.removeItem(1, 32);
		if (!cardItems(shelf).equals(List.of(Items.COAL, Items.OAK_PLANKS, Items.REDSTONE))) {
			helper.fail("three kinds must give three cards, got " + cardItems(shelf));
			return;
		}
		for (int i = 0; i < shelf.getContainerSize(); i++) {
			shelf.setItem(i, ItemStack.EMPTY);
		}
		if (!shelf.cards().isEmpty()) {
			helper.fail("an empty shelf must show no card, shows " + cardItems(shelf));
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WAREHOUSE-SHELF-001-FUN02 — the update tag carries the card list and no slot; a shelf loaded
	 *     from it (as the client is) shows the same cards; a save on disk carries the slots and no card list, and a
	 *     shelf loaded from it works the same cards out again.
	 */
	public static void tcWarehouseShelf001Fun02UpdateTagCarriesCardsNotContents(GameTestHelper helper) {
		WarehouseShelfBlockEntity shelf = shelf(helper, SHELF);
		shelf.setItem(0, new ItemStack(Items.COAL, 3));
		shelf.setItem(1, new ItemStack(Items.IRON_INGOT, 9));
		var access = helper.getLevel().registryAccess();
		CompoundTag update = shelf.getUpdateTag(access);
		if (update.contains("Items") || !update.contains("cards")) {
			helper.fail("the update tag must carry the cards and not the slots: keys " + update.keySet());
			return;
		}
		WarehouseShelfBlockEntity client = shelf(helper, SHELF.east(2));
		client.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, access, update));
		if (!cardItems(client).equals(List.of(Items.IRON_INGOT, Items.COAL)) || !client.isEmpty()) {
			helper.fail("loaded from the update tag a shelf must show iron, coal and hold nothing: cards "
					+ cardItems(client) + ", empty " + client.isEmpty());
			return;
		}
		CompoundTag disk = shelf.saveCustomOnly(access);
		if (!disk.contains("Items") || disk.contains("cards")) {
			helper.fail("the save must carry the slots and not the cards: keys " + disk.keySet());
			return;
		}
		WarehouseShelfBlockEntity loaded = shelf(helper, SHELF.east(4));
		loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, access, disk));
		if (!cardItems(loaded).equals(List.of(Items.IRON_INGOT, Items.COAL))) {
			helper.fail("loaded from the save a shelf must work its cards out again, shows " + cardItems(loaded));
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WAREHOUSE-SHELF-001-FUN03 — a vanilla hopper on top of the shelf moves its iron in, as into a
	 *     chest, and the shelf's first card becomes iron.
	 */
	public static void tcWarehouseShelf001Fun03HopperFillsTheShelf(GameTestHelper helper) {
		WarehouseShelfBlockEntity shelf = shelf(helper, SHELF);
		helper.setBlock(SHELF.above(), Blocks.HOPPER);
		HopperBlockEntity hopper = helper.getBlockEntity(SHELF.above(), HopperBlockEntity.class);
		hopper.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(shelf.countItem(Items.IRON_INGOT) == 3,
					"the hopper must move its three ingots into the shelf, moved " + shelf.countItem(Items.IRON_INGOT));
			helper.assertTrue(cardItems(shelf).equals(List.of(Items.IRON_INGOT)),
					"the shelf's one card must be iron, cards " + cardItems(shelf));
		});
	}
}

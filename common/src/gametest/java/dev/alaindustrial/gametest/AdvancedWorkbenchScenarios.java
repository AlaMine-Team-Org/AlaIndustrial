package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.AdvancedIndustrialWorkbenchBlockEntity;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.item.ItemMover;
import dev.alaindustrial.core.item.ItemPort;
import dev.alaindustrial.menu.AdvancedIndustrialWorkbenchMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral gametests of the Advanced Industrial Workbench (MOD-810), run by both loaders from the roster.
 *
 * <p>The sided tests go through {@code ItemLookup} and {@code ItemMover}, the very path the mod's item pipes use,
 * so they exercise each loader's own transfer adapter - the Fabric Transfer API on one side, NeoForge's resource
 * handler on the other - and not a shortcut around them. The menu tests drive the real menu with real mock
 * players, two of them where sharing matters.
 */
public final class AdvancedWorkbenchScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AdvancedWorkbenchScenarios::gridSurvivesReload, "mod810_grid_survives_reload"),
				RosterEntry.of(AdvancedWorkbenchScenarios::breakingDropsGridAndBuffer,
						"mod810_breaking_drops_grid_and_buffer"),
				RosterEntry.of(AdvancedWorkbenchScenarios::clearButtonMovesGridToInventory,
						"mod810_clear_button_moves_grid_to_inventory"),
				RosterEntry.of(AdvancedWorkbenchScenarios::clearButtonKeepsWhatDoesNotFit,
						"mod810_clear_button_keeps_what_does_not_fit"),
				RosterEntry.of(AdvancedWorkbenchScenarios::shiftClickCraftsUntilExhausted,
						"mod810_shift_click_crafts_until_exhausted"),
				RosterEntry.of(AdvancedWorkbenchScenarios::twoPlayersShareOneGridWithoutDupes,
						"mod810_two_players_share_one_grid"),
				RosterEntry.of(AdvancedWorkbenchScenarios::pipeFillsOnlyTheRememberedRecipe,
						"mod810_pipe_fills_only_the_remembered_recipe"),
				RosterEntry.of(AdvancedWorkbenchScenarios::pipeRefusesWithoutARememberedRecipe,
						"mod810_pipe_refuses_without_a_remembered_recipe"),
				RosterEntry.of(AdvancedWorkbenchScenarios::pipeTakesFromBelowOnlyACompleteRecipe,
						"mod810_pipe_takes_from_below_only_a_complete_recipe"),
				RosterEntry.of(AdvancedWorkbenchScenarios::abortedExtractionLosesNothing,
						"mod810_aborted_extraction_loses_nothing"),
				RosterEntry.of(AdvancedWorkbenchScenarios::hopperBelowAutoCrafts, "mod810_hopper_below_auto_crafts")
						.ticks(80, 80),
				RosterEntry.of(AdvancedWorkbenchScenarios::comparatorReadsGridFill,
						"mod810_comparator_reads_grid_fill"));

		private Roster() {}
	}

	private AdvancedWorkbenchScenarios() {}

	private static final BlockPos BENCH = new BlockPos(1, 4, 1);
	private static final BlockPos ABOVE = BENCH.above();
	private static final BlockPos BELOW = BENCH.below();

	private static final ResourceKey<Recipe<?>> STICK_RECIPE =
			ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("stick"));

	private static AdvancedIndustrialWorkbenchBlockEntity bench(GameTestHelper helper) {
		helper.setBlock(BENCH, ModContent.ADVANCED_INDUSTRIAL_WORKBENCH.get().defaultBlockState());
		return helper.getBlockEntity(BENCH, AdvancedIndustrialWorkbenchBlockEntity.class);
	}

	private static AdvancedIndustrialWorkbenchMenu open(AdvancedIndustrialWorkbenchBlockEntity bench,
			ServerPlayer player, int id) {
		return (AdvancedIndustrialWorkbenchMenu) bench.createMenu(id, player.getInventory(), player);
	}

	private static int total(AdvancedIndustrialWorkbenchBlockEntity bench) {
		int sum = 0;
		for (ItemStack stack : bench.grid()) {
			sum += stack.getCount();
		}
		return sum;
	}

	private static AbstractChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModContent.IRON_CHEST.get().defaultBlockState());
		return helper.getBlockEntity(pos, AbstractChestBlockEntity.class);
	}

	private static int count(net.minecraft.world.Container container, net.minecraft.world.item.Item item) {
		int sum = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				sum += container.getItem(i).getCount();
			}
		}
		return sum;
	}

	/** @implements MOD-810-AC02 - the grid, the buffered craft and the remembered recipe survive a save/load. */
	public static void gridSurvivesReload(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		bench.setItem(0, new ItemStack(Items.APPLE, 5));
		bench.setItem(4, new ItemStack(Items.DIAMOND, 2));
		bench.setItem(AdvancedIndustrialWorkbenchBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OAK_PLANKS, 3));
		bench.remember(STICK_RECIPE);

		ServerLevel level = helper.getLevel();
		CompoundTag tag = bench.saveCustomOnly(level.registryAccess());
		// Wipe first, so a load that silently does nothing cannot pass.
		bench.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(),
				new CompoundTag()));
		helper.assertTrue(total(bench) == 0 && bench.lastRecipe().isEmpty(),
				"negative control: an empty tag must leave the bench empty and forgetful");
		bench.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));

		helper.assertTrue(bench.getItem(0).is(Items.APPLE) && bench.getItem(0).getCount() == 5
						&& bench.getItem(4).is(Items.DIAMOND) && bench.getItem(4).getCount() == 2,
				"the grid must come back as it was, got " + bench.grid());
		helper.assertTrue(bench.getItem(AdvancedIndustrialWorkbenchBlockEntity.OUTPUT_SLOT).getCount() == 3,
				"the buffered craft must come back");
		helper.assertTrue(STICK_RECIPE.equals(bench.lastRecipe().orElse(null)),
				"the remembered recipe must come back, got " + bench.lastRecipe());
		helper.succeed();
	}

	/** @implements MOD-810-AC02 - breaking the block drops the grid and the buffer, and the bench itself. */
	public static void breakingDropsGridAndBuffer(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		bench.setItem(0, new ItemStack(Items.APPLE, 3));
		bench.setItem(8, new ItemStack(Items.GOLD_INGOT, 1));
		bench.setItem(AdvancedIndustrialWorkbenchBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OAK_PLANKS, 4));

		helper.getLevel().destroyBlock(helper.absolutePos(BENCH), true);

		helper.assertBlockPresent(Blocks.AIR, BENCH);
		helper.assertItemEntityPresent(Items.APPLE, BENCH, 2.0);
		helper.assertItemEntityPresent(Items.GOLD_INGOT, BENCH, 2.0);
		helper.assertItemEntityPresent(Items.OAK_PLANKS, BENCH, 2.0);
		helper.assertItemEntityPresent(ModContent.ADVANCED_INDUSTRIAL_WORKBENCH_ITEM.get(), BENCH, 2.0);
		helper.succeed();
	}

	/** @implements MOD-810-AC03 - Clear moves the whole grid into the player's inventory. */
	public static void clearButtonMovesGridToInventory(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		bench.setItem(1, new ItemStack(Items.IRON_INGOT, 7));
		bench.setItem(5, new ItemStack(Items.DIAMOND, 1));
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		AdvancedIndustrialWorkbenchMenu menu = open(bench, player, 1);

		boolean moved = menu.clickMenuButton(player, AdvancedIndustrialWorkbenchMenu.BUTTON_CLEAR);

		helper.assertTrue(moved, "the clear button must report that something moved");
		helper.assertTrue(bench.isGridEmpty(), "the grid must be empty, got " + bench.grid());
		helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 7
						&& player.getInventory().countItem(Items.DIAMOND) == 1,
				"everything must arrive in the inventory");
		helper.succeed();
	}

	/** @implements MOD-810-AC03 - what does not fit stays in the grid, nothing is lost. */
	public static void clearButtonKeepsWhatDoesNotFit(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		// Every main-inventory slot holds a full stack of stone: no room for a diamond, no merge for stone.
		for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
			player.getInventory().getNonEquipmentItems().set(i, new ItemStack(Items.STONE, 64));
		}
		bench.setItem(2, new ItemStack(Items.DIAMOND, 1));
		AdvancedIndustrialWorkbenchMenu menu = open(bench, player, 1);

		boolean moved = menu.clickMenuButton(player, AdvancedIndustrialWorkbenchMenu.BUTTON_CLEAR);

		helper.assertTrue(!moved, "nothing fit, so nothing must be reported as moved");
		helper.assertTrue(bench.getItem(2).is(Items.DIAMOND) && bench.getItem(2).getCount() == 1,
				"the diamond must stay in the grid, got " + bench.getItem(2));
		helper.succeed();
	}

	/** @implements MOD-810-AC04 - shift-click on the result crafts until the grid runs dry, with exact counts. */
	public static void shiftClickCraftsUntilExhausted(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		bench.setItem(0, new ItemStack(Items.OAK_LOG, 3));
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		AdvancedIndustrialWorkbenchMenu menu = open(bench, player, 1);
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS),
				"the window must open with the result already on show, got " + menu.getSlot(0).getItem());

		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);

		helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 12,
				"three logs are twelve planks, got " + player.getInventory().countItem(Items.OAK_PLANKS));
		helper.assertTrue(bench.isGridEmpty(), "the logs must be used up, got " + bench.grid());
		helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "the result must clear once the grid is empty");
		helper.assertTrue(bench.lastRecipe().isPresent(), "a craft must be remembered");
		helper.succeed();
	}

	/** @implements MOD-810-AC05 - two windows on one grid: the second player cannot take a result that is gone. */
	public static void twoPlayersShareOneGridWithoutDupes(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		bench.setItem(0, new ItemStack(Items.OAK_LOG, 1));
		ServerPlayer first = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer second = AlaGameTestHelper.survivalPlayer(helper);
		AdvancedIndustrialWorkbenchMenu firstMenu = open(bench, first, 1);
		AdvancedIndustrialWorkbenchMenu secondMenu = open(bench, second, 2);
		helper.assertTrue(secondMenu.getSlot(1).getItem().is(Items.OAK_LOG)
						&& secondMenu.getSlot(0).getItem().is(Items.OAK_PLANKS),
				"both windows must show the same grid and result");

		firstMenu.clicked(0, 0, ContainerInput.QUICK_MOVE, first);
		// The second window has NOT been ticked since: its result slot is stale, it still shows planks.
		helper.assertTrue(secondMenu.getSlot(0).getItem().is(Items.OAK_PLANKS),
				"negative control: the second window's result must still be the stale one");
		secondMenu.clicked(0, 0, ContainerInput.QUICK_MOVE, second);

		helper.assertTrue(first.getInventory().countItem(Items.OAK_PLANKS) == 4,
				"the first player crafted one log, got " + first.getInventory().countItem(Items.OAK_PLANKS));
		helper.assertTrue(second.getInventory().countItem(Items.OAK_PLANKS) == 0,
				"the second player must get nothing from a result that no longer exists, got "
						+ second.getInventory().countItem(Items.OAK_PLANKS));
		helper.assertTrue(secondMenu.getSlot(1).getItem().isEmpty(),
				"the second window must see the grid the first one emptied");
		helper.succeed();
	}

	/** @implements MOD-810-AC06 - a pipe fills the cells of the remembered recipe, and only with wanted items. */
	public static void pipeFillsOnlyTheRememberedRecipe(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		bench.remember(STICK_RECIPE);
		AbstractChestBlockEntity source = chest(helper, ABOVE);
		source.setItem(0, new ItemStack(Items.OAK_PLANKS, 20));
		source.setItem(1, new ItemStack(Items.COBBLESTONE, 10));
		ItemPort from = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(ABOVE), Direction.DOWN);
		ItemPort into = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(BENCH), Direction.UP);
		helper.assertTrue(from != null && into != null, "both ports must exist on this loader");

		// A pipe moves a few items at a time; two at a time is what the basic pipe does.
		int moved = 0;
		for (int i = 0; i < 12; i++) {
			moved += ItemMover.move(from, into, 2);
		}

		helper.assertTrue(moved == 20, "all twenty planks (and no cobblestone) must go in, moved " + moved);
		helper.assertTrue(count(source, Items.COBBLESTONE) == 10, "cobblestone is not in the recipe and must stay");
		// Stick = two planks in a column: the cells are 0 and 3 at the top left, and nothing else.
		for (int slot = 0; slot < AdvancedIndustrialWorkbenchBlockEntity.GRID_SIZE; slot++) {
			boolean wanted = slot == 0 || slot == 3;
			helper.assertTrue(wanted == !bench.getItem(slot).isEmpty(),
					"only cells 0 and 3 may hold planks, slot " + slot + " holds " + bench.getItem(slot));
		}
		int a = bench.getItem(0).getCount();
		int b = bench.getItem(3).getCount();
		helper.assertTrue(a + b == 20 && Math.abs(a - b) <= 2,
				"the planks must be spread evenly, got " + a + " and " + b);
		helper.succeed();
	}

	/** @implements MOD-810-AC06 - with no recipe on record, nothing goes in. */
	public static void pipeRefusesWithoutARememberedRecipe(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		AbstractChestBlockEntity source = chest(helper, ABOVE);
		source.setItem(0, new ItemStack(Items.OAK_PLANKS, 20));
		ItemPort from = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(ABOVE), Direction.DOWN);
		ItemPort into = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(BENCH), Direction.UP);

		int moved = ItemMover.move(from, into, 64);

		helper.assertTrue(moved == 0 && bench.isGridEmpty() && count(source, Items.OAK_PLANKS) == 20,
				"a bench that remembers nothing must take nothing, moved " + moved);
		helper.succeed();
	}

	/** @implements MOD-810-AC06 - the bottom gives a result only for a complete recipe and spends the ingredients. */
	public static void pipeTakesFromBelowOnlyACompleteRecipe(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		AbstractChestBlockEntity sink = chest(helper, BELOW);

		// An incomplete grid: nothing comes out.
		bench.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
		ItemPort out = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(BENCH), Direction.DOWN);
		ItemPort sinkPort = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(BELOW), Direction.UP);
		helper.assertTrue(out != null && sinkPort != null, "both ports must exist on this loader");
		helper.assertTrue(ItemMover.move(out, sinkPort, 64) == 0 && sink.isEmpty(),
				"a grid that crafts nothing must give nothing");
		helper.assertTrue(bench.getItem(0).is(Items.COBBLESTONE), "and must keep its cobblestone");

		// A complete one: one log is four planks, and the log is spent.
		bench.setItem(0, new ItemStack(Items.OAK_LOG, 2));
		out = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(BENCH), Direction.DOWN);
		int moved = ItemMover.move(out, sinkPort, 64);

		helper.assertTrue(moved == 4 && count(sink, Items.OAK_PLANKS) == 4,
				"one craft's worth must come out, moved " + moved);
		helper.assertTrue(bench.getItem(0).getCount() == 1, "exactly one log must be spent, grid " + bench.grid());
		helper.assertTrue(!(bench.getItem(1).is(Items.OAK_PLANKS)), "the bottom must never give grid contents away");
		helper.succeed();
	}

	/**
	 * @implements MOD-810-AC06 - a transaction that is rolled back (a simulated pull) leaves the bench with
	 *     everything it had: the ingredients are either still in the grid or the finished craft is still in the
	 *     buffer, never gone.
	 */
	public static void abortedExtractionLosesNothing(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		AbstractChestBlockEntity sink = chest(helper, BELOW);
		bench.setItem(0, new ItemStack(Items.OAK_LOG, 1));
		ItemPort out = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(BENCH), Direction.DOWN);
		ItemPort sinkPort = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(BELOW), Direction.UP);

		int simulated = EnergyTransactions.get().simulate(txn -> out.moveTo(sinkPort, 64, txn));

		helper.assertTrue(simulated == 4, "the dry run must see the four planks, saw " + simulated);
		helper.assertTrue(sink.isEmpty(), "a rolled-back move must put nothing in the sink");
		int planks = bench.getItem(AdvancedIndustrialWorkbenchBlockEntity.OUTPUT_SLOT).getCount();
		int logs = bench.getItem(0).getCount();
		helper.assertTrue((planks == 4 && logs == 0) || (planks == 0 && logs == 1),
				"after the rollback the craft must be in the buffer or the log still in the grid, got planks "
						+ planks + " and logs " + logs);

		int moved = ItemMover.move(out, sinkPort, 64);
		helper.assertTrue(moved == 4 && count(sink, Items.OAK_PLANKS) == 4,
				"the real move afterwards must still deliver the four planks, moved " + moved);
		helper.succeed();
	}

	/** @implements MOD-810-AC06 - a vanilla hopper under the bench pulls the crafted result out. */
	public static void hopperBelowAutoCrafts(GameTestHelper helper) {
		BlockPos hopperPos = BELOW;
		BlockPos chestPos = BELOW.below();
		helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState());
		AbstractChestBlockEntity sink = chest(helper, chestPos);
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		bench.setItem(0, new ItemStack(Items.OAK_LOG, 1));

		helper.succeedWhen(() -> {
			helper.assertTrue(count(sink, Items.OAK_PLANKS) == 4,
					"the hopper must carry the four planks of one log down, got " + count(sink, Items.OAK_PLANKS));
			helper.assertTrue(bench.isGridEmpty(), "the log must be spent");
		});
	}

	/** @implements MOD-810-AC07 - the comparator reads how full the grid is: 0 empty, 1 for a crumb, 15 for full. */
	public static void comparatorReadsGridFill(GameTestHelper helper) {
		AdvancedIndustrialWorkbenchBlockEntity bench = bench(helper);
		BlockPos abs = helper.absolutePos(BENCH);
		BlockState state = helper.getBlockState(BENCH);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(state.getAnalogOutputSignal(level, abs, Direction.NORTH) == 0, "an empty grid reads 0");

		bench.setItem(0, new ItemStack(Items.APPLE, 1));
		int crumb = state.getAnalogOutputSignal(level, abs, Direction.NORTH);
		helper.assertTrue(crumb == 1, "a single item must read 1, got " + crumb);

		for (int i = 0; i < AdvancedIndustrialWorkbenchBlockEntity.GRID_SIZE; i++) {
			bench.setItem(i, new ItemStack(Items.STONE, 64));
		}
		int full = state.getAnalogOutputSignal(level, abs, Direction.NORTH);
		helper.assertTrue(full == 15, "a full grid must read 15, got " + full);
		helper.succeed();
	}
}

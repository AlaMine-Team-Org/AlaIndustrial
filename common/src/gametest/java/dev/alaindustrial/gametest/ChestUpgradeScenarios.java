package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.survivalPlayer;

import dev.alaindustrial.block.AbstractModChestBlock;
import dev.alaindustrial.block.ChestUpgrade;
import dev.alaindustrial.block.ChestUpgrades;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The in-place chest upgrade (MOD-944, suite TC-CHUP-001): a Shift+right-click with a chest upgrade raises a
 * placed chest one tier and nothing is lost, duplicated or moved. Every scenario goes through the real click
 * path, {@code player.gameMode.useItemOn}, except the negative control, which calls the swap helper directly
 * to drop its one safety flag.
 *
 * <p>"Nothing is lost or duplicated" is checked twice over: slot by slot against a copy taken before the
 * click, and as "no item entity anywhere near" — the spill a missing
 * {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS} would cause. The negative control proves the second check sees
 * that spill.
 */
public final class ChestUpgradeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ChestUpgradeScenarios::fun01FullSingleKeepsEverything,
								"chest_upgrade_full_single")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::fun02PartialSingleKeepsSlots,
								"chest_upgrade_partial_single")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::fun03PairUpgradesWholeForTwo,
								"chest_upgrade_pair_for_two")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::neg01PairWithOneItemRefused,
								"chest_upgrade_pair_one_item_refused")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::neg02WrongRungAndShieldingRefused,
								"chest_upgrade_wrong_rung_refused")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::fun04CreativeKeepsTheItem,
								"chest_upgrade_creative_keeps_item")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::fun05ViewerWindowClosesCursorReturns,
								"chest_upgrade_viewer_closed")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::fun06HopperAndComparatorNeighbours,
								"chest_upgrade_hopper_neighbour")
						.ticks(100, 120),
				RosterEntry.of(ChestUpgradeScenarios::fun07CopperChestLootUnpackedForPlayer,
								"chest_upgrade_copper_loot")
						.ticks(20, 40),
				RosterEntry.of(ChestUpgradeScenarios::neg03WithoutSkipFlagContentsSpill,
								"chest_upgrade_negative_control")
						.ticks(20, 40));

		private Roster() {}
	}

	private ChestUpgradeScenarios() {}

	private static final BlockPos A = new BlockPos(1, 2, 1);
	/** A's partner: facing SOUTH, a LEFT half's partner sits WEST, so B (east of A) is LEFT and A is RIGHT. */
	private static final BlockPos B = new BlockPos(2, 2, 1);
	/** How far from the chest a spilled item would be looked for. */
	private static final double SPILL_RADIUS = 4.0;

	// ── fixtures ──────────────────────────────────────────────────────────────────────────────────

	private static BlockState chest(Block block, Direction facing, ChestType type, boolean waterlogged) {
		return block.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, facing)
				.setValue(AbstractModChestBlock.TYPE, type)
				.setValue(AbstractModChestBlock.WATERLOGGED, waterlogged);
	}

	private static Container containerAt(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
		if (!(be instanceof Container container)) {
			helper.fail("no container block entity at " + pos + ", got " + be);
			throw new IllegalStateException("unreachable");
		}
		return container;
	}

	/** Fill every {@code stride}-th slot with a stack that differs per slot, so a shifted slot cannot pass. */
	private static void fill(Container container, int stride) {
		Item[] palette = {Items.COBBLESTONE, Items.IRON_INGOT, Items.REDSTONE, Items.OAK_LOG, Items.DIAMOND};
		for (int slot = 0; slot < container.getContainerSize(); slot += stride) {
			container.setItem(slot, new ItemStack(palette[slot % palette.length], 1 + slot % 16));
		}
	}

	private static List<ItemStack> copyOf(Container container) {
		List<ItemStack> copy = new ArrayList<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			copy.add(container.getItem(slot).copy());
		}
		return copy;
	}

	/** Every slot of {@code before} is in the same slot of {@code after}; the slots past it are empty. */
	private static void assertSameSlots(GameTestHelper helper, String label, List<ItemStack> before, Container after) {
		for (int slot = 0; slot < after.getContainerSize(); slot++) {
			ItemStack want = slot < before.size() ? before.get(slot) : ItemStack.EMPTY;
			if (!ItemStack.matches(want, after.getItem(slot))) {
				helper.fail(label + ": slot " + slot + " held " + want + " before, " + after.getItem(slot) + " after");
			}
		}
	}

	private static int spilledNear(GameTestHelper helper, BlockPos near) {
		AABB around = new AABB(helper.absolutePos(near)).inflate(SPILL_RADIUS);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, entity -> true).size();
	}

	private static void assertNothingSpilled(GameTestHelper helper, BlockPos near) {
		int spilled = spilledNear(helper, near);
		if (spilled != 0) {
			helper.fail(spilled + " item entities spilled next to the upgraded chest — contents were duplicated");
		}
	}

	private static ServerPlayer playerHolding(GameTestHelper helper, Supplier<Item> upgrade, int count) {
		ServerPlayer player = survivalPlayer(helper);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(upgrade.get(), count));
		return player;
	}

	private static InteractionResult click(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND),
				InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
	}

	private static void assertBlock(GameTestHelper helper, BlockPos pos, Block want, ChestType type) {
		BlockState state = helper.getBlockState(pos);
		if (!state.is(want) || state.getValue(AbstractModChestBlock.TYPE) != type) {
			helper.fail("at " + pos + " expected " + want + " " + type + ", got " + state);
		}
	}

	private static void assertHand(GameTestHelper helper, ServerPlayer player, int want) {
		int count = player.getItemInHand(InteractionHand.MAIN_HAND).getCount();
		if (count != want) {
			helper.fail("upgrade items left in hand: expected " + want + ", got " + count);
		}
	}

	// ── single chests ─────────────────────────────────────────────────────────────────────────────

	/**
	 * A full iron chest, named, facing east and waterlogged, becomes a silver chest with every stack in its
	 * slot, the name, the facing and the water kept; one upgrade is spent and nothing spills.
	 *
	 * @implements TC-CHUP-001-FUN01 — a full single chest is upgraded in place, every slot, name, facing, water kept.
	 */
	public static void fun01FullSingleKeepsEverything(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.IRON_CHEST.get(), Direction.EAST, ChestType.SINGLE, true));
		Container old = containerAt(helper, A);
		fill(old, 1);
		ItemStack label = new ItemStack(Items.PAPER);
		label.set(DataComponents.CUSTOM_NAME, Component.literal("Stash"));
		((BlockEntity) old).applyComponentsFromItemStack(label);
		List<ItemStack> before = copyOf(old);
		ServerPlayer player = playerHolding(helper, () -> ModContent.IRON_TO_SILVER_CHEST_UPGRADE.get(), 3);

		InteractionResult result = click(helper, player, A);

		if (!result.consumesAction()) {
			helper.fail("the upgrade click must be accepted, got " + result);
		}
		assertBlock(helper, A, ModContent.SILVER_CHEST.get(), ChestType.SINGLE);
		BlockState state = helper.getBlockState(A);
		if (state.getValue(HorizontalMachineBlock.FACING) != Direction.EAST
				|| !state.getValue(AbstractModChestBlock.WATERLOGGED)) {
			helper.fail("facing and water must survive the upgrade, got " + state);
		}
		Container upgraded = containerAt(helper, A);
		if (upgraded.getContainerSize() != 45) {
			helper.fail("a silver chest holds 45 slots, got " + upgraded.getContainerSize());
		}
		assertSameSlots(helper, "full single", before, upgraded);
		AbstractChestBlockEntity named = (AbstractChestBlockEntity) upgraded;
		if (named.getCustomName() == null || !"Stash".equals(named.getCustomName().getString())) {
			helper.fail("the anvil name must survive the upgrade, got " + named.getCustomName());
		}
		assertHand(helper, player, 2);
		assertNothingSpilled(helper, A);
		helper.succeed();
	}

	/**
	 * A partly filled gold chest becomes an electrum chest with the gaps where they were.
	 *
	 * @implements TC-CHUP-001-FUN02 — a partly filled chest keeps each stack in its own slot, gaps included.
	 */
	public static void fun02PartialSingleKeepsSlots(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.GOLD_CHEST.get(), Direction.NORTH, ChestType.SINGLE, false));
		Container old = containerAt(helper, A);
		fill(old, 7);
		List<ItemStack> before = copyOf(old);
		ServerPlayer player = playerHolding(helper, () -> ModContent.GOLD_TO_ELECTRUM_CHEST_UPGRADE.get(), 1);

		click(helper, player, A);

		assertBlock(helper, A, ModContent.ELECTRUM_CHEST.get(), ChestType.SINGLE);
		assertSameSlots(helper, "partial single", before, containerAt(helper, A));
		assertHand(helper, player, 0);
		assertNothingSpilled(helper, A);
		helper.succeed();
	}

	// ── double chests ─────────────────────────────────────────────────────────────────────────────

	private static void placeIronPair(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.IRON_CHEST.get(), Direction.SOUTH, ChestType.SINGLE, false));
		helper.setBlock(B, chest(ModContent.IRON_CHEST.get(), Direction.SOUTH, ChestType.LEFT, false));
	}

	/**
	 * A full iron double chest, clicked on its LEFT half, is raised as a whole: both halves silver, the pair
	 * intact, both halves' contents in place, two upgrades spent.
	 *
	 * @implements TC-CHUP-001-FUN03 — a double chest is upgraded as a pair for two items, halves and contents kept.
	 */
	public static void fun03PairUpgradesWholeForTwo(GameTestHelper helper) {
		placeIronPair(helper);
		fill(containerAt(helper, A), 1);
		fill(containerAt(helper, B), 2);
		List<ItemStack> beforeA = copyOf(containerAt(helper, A));
		List<ItemStack> beforeB = copyOf(containerAt(helper, B));
		ServerPlayer player = playerHolding(helper, () -> ModContent.IRON_TO_SILVER_CHEST_UPGRADE.get(), 2);

		click(helper, player, B);

		assertBlock(helper, A, ModContent.SILVER_CHEST.get(), ChestType.RIGHT);
		assertBlock(helper, B, ModContent.SILVER_CHEST.get(), ChestType.LEFT);
		assertSameSlots(helper, "pair right half", beforeA, containerAt(helper, A));
		assertSameSlots(helper, "pair left half", beforeB, containerAt(helper, B));
		BlockState state = helper.getBlockState(A);
		Container joined = ((AbstractModChestBlock) state.getBlock())
				.combinedContainer(state, helper.getLevel(), helper.absolutePos(A));
		if (joined == null || joined.getContainerSize() != 90) {
			helper.fail("the upgraded pair must still join into 90 slots, got " + joined);
		}
		assertHand(helper, player, 0);
		assertNothingSpilled(helper, A);
		helper.succeed();
	}

	/**
	 * One upgrade on a double chest is refused: nothing changes and the item stays in the hand.
	 *
	 * @implements TC-CHUP-001-NEG01 — a double chest with one upgrade in hand is refused, nothing changes.
	 */
	public static void neg01PairWithOneItemRefused(GameTestHelper helper) {
		placeIronPair(helper);
		fill(containerAt(helper, A), 3);
		List<ItemStack> before = copyOf(containerAt(helper, A));
		ServerPlayer player = playerHolding(helper, () -> ModContent.IRON_TO_SILVER_CHEST_UPGRADE.get(), 1);

		InteractionResult result = click(helper, player, A);

		if (result.consumesAction()) {
			helper.fail("one upgrade on a double chest must be refused, got " + result);
		}
		assertBlock(helper, A, ModContent.IRON_CHEST.get(), ChestType.RIGHT);
		assertBlock(helper, B, ModContent.IRON_CHEST.get(), ChestType.LEFT);
		assertSameSlots(helper, "refused pair", before, containerAt(helper, A));
		assertHand(helper, player, 1);
		helper.succeed();
	}

	/**
	 * An upgrade of the wrong rung, and any upgrade on the shielding chest, are refused and not spent.
	 *
	 * @implements TC-CHUP-001-NEG02 — a wrong-rung upgrade and the shielding chest are refused, the item kept.
	 */
	public static void neg02WrongRungAndShieldingRefused(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.IRON_CHEST.get(), Direction.SOUTH, ChestType.SINGLE, false));
		helper.setBlock(B.east(), chest(ModContent.SHIELDING_CHEST.get(), Direction.SOUTH, ChestType.SINGLE, false));
		ServerPlayer wrongRung = playerHolding(helper, () -> ModContent.SILVER_TO_GOLD_CHEST_UPGRADE.get(), 1);
		click(helper, wrongRung, A);
		assertBlock(helper, A, ModContent.IRON_CHEST.get(), ChestType.SINGLE);
		assertHand(helper, wrongRung, 1);

		ServerPlayer onShielding = playerHolding(helper, () -> ModContent.IRON_TO_SILVER_CHEST_UPGRADE.get(), 1);
		click(helper, onShielding, B.east());
		assertBlock(helper, B.east(), ModContent.SHIELDING_CHEST.get(), ChestType.SINGLE);
		assertHand(helper, onShielding, 1);
		helper.succeed();
	}

	/**
	 * In creative the chest is upgraded and the item is not spent.
	 *
	 * @implements TC-CHUP-001-FUN04 — in creative the upgrade works and the item is not spent.
	 */
	public static void fun04CreativeKeepsTheItem(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.ELECTRUM_CHEST.get(), Direction.SOUTH, ChestType.SINGLE, false));
		ServerPlayer player = playerHolding(helper, () -> ModContent.ELECTRUM_TO_DIAMOND_CHEST_UPGRADE.get(), 1);
		player.setGameMode(GameType.CREATIVE);

		click(helper, player, A);

		assertBlock(helper, A, ModContent.DIAMOND_CHEST.get(), ChestType.SINGLE);
		assertHand(helper, player, 1);
		helper.succeed();
	}

	// ── what surrounds the chest ──────────────────────────────────────────────────────────────────

	/**
	 * A second player looking into the chest has the window closed and gets the stack on the cursor back.
	 *
	 * @implements TC-CHUP-001-FUN05 — a viewer's window is closed and the cursor stack goes back to the viewer.
	 */
	public static void fun05ViewerWindowClosesCursorReturns(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.IRON_CHEST.get(), Direction.SOUTH, ChestType.SINGLE, false));
		AbstractChestBlockEntity old = (AbstractChestBlockEntity) containerAt(helper, A);
		fill(old, 4);
		List<ItemStack> before = copyOf(old);
		ServerPlayer viewer = survivalPlayer(helper);
		BlockPos stand = helper.absolutePos(A.south());
		viewer.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		viewer.openMenu(old);
		if (viewer.containerMenu == viewer.inventoryMenu) {
			helper.fail("the viewer's chest window did not open — the check below would be vacuous");
		}
		viewer.containerMenu.setCarried(new ItemStack(Items.EMERALD, 5));
		ServerPlayer player = playerHolding(helper, () -> ModContent.IRON_TO_SILVER_CHEST_UPGRADE.get(), 1);

		click(helper, player, A);

		if (viewer.containerMenu != viewer.inventoryMenu) {
			helper.fail("the viewer's window into the replaced chest must be closed");
		}
		if (viewer.getInventory().countItem(Items.EMERALD) != 5) {
			helper.fail("the viewer's cursor stack must come back to the viewer's inventory, found "
					+ viewer.getInventory().countItem(Items.EMERALD));
		}
		assertSameSlots(helper, "viewed chest", before, containerAt(helper, A));
		assertNothingSpilled(helper, A);
		helper.succeed();
	}

	/**
	 * A hopper feeding the chest and a comparator reading it: the upgrade spills nothing, and the hopper goes on
	 * feeding the new, bigger chest.
	 *
	 * @implements TC-CHUP-001-FUN06 — with a hopper and a comparator beside it nothing spills, the hopper feeds on.
	 */
	public static void fun06HopperAndComparatorNeighbours(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.IRON_CHEST.get(), Direction.SOUTH, ChestType.SINGLE, false));
		fill(containerAt(helper, A), 2);
		List<ItemStack> before = copyOf(containerAt(helper, A));
		helper.setBlock(A.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		// A comparator needs a block under it, or it pops off as an item and reads as a spill.
		helper.setBlock(A.south().below(), Blocks.STONE.defaultBlockState());
		helper.setBlock(A.south(), Blocks.COMPARATOR.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		assertNothingSpilled(helper, A);
		ServerPlayer player = playerHolding(helper, () -> ModContent.IRON_TO_SILVER_CHEST_UPGRADE.get(), 1);

		click(helper, player, A);

		assertBlock(helper, A, ModContent.SILVER_CHEST.get(), ChestType.SINGLE);
		assertSameSlots(helper, "hopper neighbour", before, containerAt(helper, A));
		assertNothingSpilled(helper, A);
		HopperBlockEntity hopper = (HopperBlockEntity) containerAt(helper, A.above());
		hopper.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 1));
		helper.succeedWhen(() -> {
			Container upgraded = containerAt(helper, A);
			if (upgraded.countItem(Items.GLOWSTONE_DUST) != 1) {
				helper.fail("the hopper must feed the upgraded chest");
			}
		});
	}

	/**
	 * A vanilla copper chest that still holds an unrolled loot table: the upgrade rolls it for the player and
	 * the loot moves into the iron chest.
	 *
	 * @implements TC-CHUP-001-FUN07 — a copper chest's pending loot is rolled for the player, moves to iron.
	 */
	public static void fun07CopperChestLootUnpackedForPlayer(GameTestHelper helper) {
		Block copper = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
		helper.setBlock(A, copper.defaultBlockState());
		RandomizableContainer pending = (RandomizableContainer) containerAt(helper, A);
		pending.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 944L);
		ServerPlayer player = playerHolding(helper, () -> ModContent.COPPER_TO_IRON_CHEST_UPGRADE.get(), 1);

		click(helper, player, A);

		assertBlock(helper, A, ModContent.IRON_CHEST.get(), ChestType.SINGLE);
		if (containerAt(helper, A).isEmpty()) {
			helper.fail("the copper chest's loot must be rolled and carried into the iron chest");
		}
		assertHand(helper, player, 0);
		assertNothingSpilled(helper, A);
		if (!ChestUpgrade.COPPER_TO_IRON.accepts(copper.defaultBlockState())) {
			helper.fail("the copper rung must accept the vanilla copper chest");
		}
		helper.succeed();
	}

	/**
	 * Negative control: the same swap without {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS} spills the old
	 * chest's contents on the ground while the new chest gets them too. This scenario asserts that spill, so it
	 * shows the "nothing spilled" check of the others can see one.
	 *
	 * @implements TC-CHUP-001-NEG03 — without the skip flag the old chest spills: the spill detector can fail.
	 */
	public static void neg03WithoutSkipFlagContentsSpill(GameTestHelper helper) {
		helper.setBlock(A, chest(ModContent.IRON_CHEST.get(), Direction.SOUTH, ChestType.SINGLE, false));
		fill(containerAt(helper, A), 1);
		ServerPlayer player = playerHolding(helper, () -> ModContent.IRON_TO_SILVER_CHEST_UPGRADE.get(), 1);
		int unsafe = ChestUpgrades.SWAP_FLAGS & ~Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

		ChestUpgrades.upgrade(helper.getLevel(), helper.absolutePos(A), player,
				player.getItemInHand(InteractionHand.MAIN_HAND), Direction.UP, ChestUpgrade.IRON_TO_SILVER, unsafe);

		if (spilledNear(helper, A) == 0) {
			helper.fail("without the skip flag the old chest must spill — otherwise the spill check proves nothing");
		}
		helper.succeed();
	}
}

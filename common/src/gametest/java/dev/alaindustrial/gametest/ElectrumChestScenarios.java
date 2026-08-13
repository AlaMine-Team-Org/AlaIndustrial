package dev.alaindustrial.gametest;

import dev.alaindustrial.block.AbstractModChestBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.menu.ElectrumChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Loader-neutral gametest bodies for the electrum chest (MOD-409, suite TC-CHEST-002). Wrapped by the
 * Fabric {@code ElectrumChestGameTest} suite and registered on the NeoForge lane via
 * {@code NeoForgeGameTests}.
 *
 * <p><b>What is actually at risk here, and what is not.</b> The scrolling machinery itself
 * ({@code AbstractScrollingChestMenu}, {@code StorageWindow}) is shared with the warehouse and the
 * double chest and is already covered by their suites — these scenarios do not re-test it. What is
 * new in MOD-409, and covered nowhere else, is the WIRING of a SINGLE chest into that machinery:
 * this is the mod's first single chest whose container is taller than its window. Get the assembly
 * wrong — hand the menu the block entity instead of a {@code StorageWindow}, or size the window from
 * the container — and the chest silently behaves as a 54-slot one: the window still opens, items
 * still go in, and nothing in the build turns red. Both scenarios below fail in exactly that case.
 */
public final class ElectrumChestScenarios {

	private ElectrumChestScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/** Slots the window can show at once — the rest exist only behind the scrollbar. */
	private static final int VISIBLE_SLOTS = ElectrumChestMenu.VISIBLE_ROWS * 9;

	private static void placeChest(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.ELECTRUM_CHEST.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH)
				.setValue(AbstractModChestBlock.TYPE, ChestType.SINGLE));
	}

	private static ElectrumChestBlockEntity chest(GameTestHelper helper) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
		if (!(be instanceof ElectrumChestBlockEntity electrum)) {
			helper.fail("no electrum chest block entity at " + POS);
			throw new IllegalStateException("unreachable");
		}
		return electrum;
	}

	/**
	 * TC-CHEST-002-FUN01 — a SINGLE electrum chest reports nine rows behind a six-row window, and
	 * scrolling to the bottom brings the last row into view.
	 *
	 * <p>The marked stack sits in the very last row, which no amount of clicking can reach without
	 * the window sliding: if the menu were built over the block entity directly, or sized from the
	 * container, this row would either not exist or never move into a visible slot.
	 */
	public static void fun01SingleChestWindowScrolls(GameTestHelper helper) {
		placeChest(helper);
		ElectrumChestBlockEntity be = chest(helper);
		if (be.getContainerSize() != ElectrumChestBlockEntity.CONTAINER_SIZE) {
			helper.fail("electrum chest must hold " + ElectrumChestBlockEntity.CONTAINER_SIZE
					+ " slots, got " + be.getContainerSize());
		}
		// Mark the first slot of the LAST row — unreachable at top row 0.
		int lastRowFirstSlot = ElectrumChestBlockEntity.CONTAINER_SIZE - 9;
		be.setItem(lastRowFirstSlot, new ItemStack(Items.LAPIS_LAZULI, 5));

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ElectrumChestMenu menu = ElectrumChestMenu.server(1, player.getInventory(), be);
		if (menu.getTotalRows() != 9 || menu.getMaxTopRow() != 3 || !menu.isScrollable()) {
			helper.fail("single electrum chest must report 9 total rows / max top row 3 / scrollable, got "
					+ menu.getTotalRows() + "/" + menu.getMaxTopRow() + "/" + menu.isScrollable());
		}
		// Before scrolling the marked row is invisible: no window slot may show it.
		for (int slot = 0; slot < VISIBLE_SLOTS; slot++) {
			if (menu.slots.get(slot).getItem().getItem() == Items.LAPIS_LAZULI) {
				helper.fail("the last row must NOT be visible at top row 0 — window slot " + slot
						+ " already shows it, so the window is not six rows tall");
			}
		}
		menu.clickMenuButton(player, 3);
		// Window slot 0 now answers for container slot 27, so slot 72 is window slot 72-27=45.
		ItemStack seen = menu.slots.get(45).getItem();
		if (seen.getItem() != Items.LAPIS_LAZULI) {
			helper.fail("after scrolling to row 3, window slot 45 must show the last row's lapis, got " + seen);
		}
		menu.removed(player);
		helper.succeed();
	}

	/**
	 * TC-CHEST-002-FUN02 — shift-click fills the rows the window does not show.
	 *
	 * <p>Everything the window can display is filled first, so a shift-click that only knows about
	 * visible slots has nowhere to put the diamonds and would report the chest full while a third of
	 * it is empty — the exact defect the scrolling base exists to prevent, here proven through the
	 * single chest's own wiring.
	 */
	public static void fun02ShiftClickReachesHiddenRows(GameTestHelper helper) {
		placeChest(helper);
		ElectrumChestBlockEntity be = chest(helper);
		for (int slot = 0; slot < VISIBLE_SLOTS; slot++) {
			be.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));

		ElectrumChestMenu menu = ElectrumChestMenu.server(1, player.getInventory(), be);
		// Hotbar slot 0 in the menu's slot list: 54 window slots + 27 inventory = index 81.
		ItemStack moved = menu.quickMoveStack(player, 81);
		if (moved.isEmpty()) {
			helper.fail("shift-click must report the stack as moved");
		}
		boolean landed = false;
		for (int slot = VISIBLE_SLOTS; slot < be.getContainerSize(); slot++) {
			if (be.getItem(slot).getItem() == Items.DIAMOND) {
				landed = true;
				break;
			}
		}
		if (!landed) {
			helper.fail("the diamonds must land in a hidden row (container slot >= " + VISIBLE_SLOTS + ")");
		}
		menu.removed(player);
		helper.succeed();
	}

	/**
	 * TC-CHEST-002-FUN03 — an electrum chest never pairs with the gold tier below it.
	 *
	 * <p>Tier isolation comes from block identity, so this is really a guard against a future tier
	 * being added by copying the block and forgetting that the pair check is {@code state.is(this)}.
	 */
	public static void fun03NeverPairsWithGold(GameTestHelper helper) {
		BlockPos goldPos = POS.east();
		placeChest(helper);
		helper.setBlock(goldPos, ModContent.GOLD_CHEST.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH)
				.setValue(AbstractModChestBlock.TYPE, ChestType.SINGLE));

		BlockState state = helper.getBlockState(POS);
		if (state.getValue(AbstractModChestBlock.TYPE) != ChestType.SINGLE) {
			helper.fail("electrum chest must stay SINGLE next to a gold chest, got "
					+ state.getValue(AbstractModChestBlock.TYPE));
		}
		if (!(state.getBlock() instanceof AbstractModChestBlock electrum)) {
			helper.fail("no chest block at " + POS);
			throw new IllegalStateException("unreachable");
		}
		var combined = electrum.combinedContainer(state, helper.getLevel(), helper.absolutePos(POS));
		if (combined == null || combined.getContainerSize() != ElectrumChestBlockEntity.CONTAINER_SIZE) {
			helper.fail("a lone electrum chest must resolve to its own "
					+ ElectrumChestBlockEntity.CONTAINER_SIZE + " slots, got "
					+ (combined == null ? "null" : combined.getContainerSize()));
		}
		helper.succeed();
	}
}

package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.QuarryFrameScenarios.BASE;
import static dev.alaindustrial.gametest.QuarryFrameScenarios.FAR;
import static dev.alaindustrial.gametest.QuarryFrameScenarios.HEAD;
import static dev.alaindustrial.gametest.QuarryFrameScenarios.T;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.item.ItemMover;
import dev.alaindustrial.core.item.ItemPort;
import dev.alaindustrial.gametest.QuarryFrameScenarios.Rig;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The quarry digs (MOD-951, stage B of MOD-637): a formed frame with an owner, a drill bit and energy takes the pit's
 * blocks one by one into its buffer, passes over what the spec skips, stands for the reasons it names, and lends its
 * buffer and bit slot to automation through the socket foundation.
 *
 * <p>The frame is the stage A rig ({@link QuarryFrameScenarios#rig}): a 5 x 5 pit between the towers, its first layer
 * at the foundations' level, the start corner at the controller's tower — so the first cell is one tower's width in
 * from the frame's corner, and the first row runs along Z (the bridge's way, the paths running along X). The
 * controller is ticked by hand, so the world itself does not move during a scenario: water and lava stay put.
 */
public final class QuarryDigScenarios {

	private QuarryDigScenarios() {
	}

	/** Gametest roster entries for this scenario class (MOD-669). */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(QuarryDigScenarios::theFirstBlockGoesIntoTheBuffer, "quarry_dig_first_block").ticks(40),
				RosterEntry.of(QuarryDigScenarios::skippedCellsAreLeftAndCostNothing, "quarry_dig_skips").ticks(40),
				RosterEntry.of(QuarryDigScenarios::itStandsForItsReasonsAndGoesOn, "quarry_dig_stops").ticks(40),
				RosterEntry.of(QuarryDigScenarios::theSocketLendsTheBufferAndTheBitSlot,
						"quarry_dig_socket_lends_items").ticks(40),
				RosterEntry.of(QuarryDigScenarios::progressOutlivesTheFrameComingApart, "quarry_dig_progress_kept")
						.ticks(40),
				RosterEntry.of(QuarryDigScenarios::itDoesNotDigWhileDiggingIsOff, "quarry_dig_off_by_switch")
						.ticks(40));

		private Roster() {
		}
	}

	private static final int FULL = 40_000;
	/** Enough controller ticks for any one operation of the rig's pit. */
	static final int OPERATION_TICKS = 400;

	/** The pit's first cell, and the one {@code along} steps further down the first row (along Z). */
	static BlockPos pit(Rig rig, int along) {
		return rig.at(T, BASE, T + along);
	}

	/**
	 * Runs {@code body} with digging switched on, whatever an operator's config or a concurrent scenario holds: the
	 * scenarios are synchronous, so the switch covers exactly their one call.
	 */
	static void withDigging(Runnable body) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("quarryDiggingEnabled", true)) {
			body.run();
		}
	}

	/**
	 * A rig with an owner, a drill bit and a full buffer of energy, not scanned yet: the caller lays the pit's blocks
	 * first and then scans, or the first tick would pass the empty layer over before they are there.
	 */
	static Rig ready(GameTestHelper helper) {
		Rig rig = QuarryFrameScenarios.rig(helper, true);
		QuarryControllerBlockEntity controller = rig.controller();
		controller.setOwner(UUID.nameUUIDFromBytes("quarry-tester".getBytes()), "QuarryTester");
		controller.setItem(QuarryControllerBlockEntity.BIT_SLOT, new ItemStack(ModContent.QUARRY_DRILL_BIT.get()));
		controller.getEnergyStorage().setAmountUntracked(FULL);
		return rig;
	}

	/**
	 * Ticks the controller until {@code pos} is air, at most one operation's worth; whether it became air. It stops
	 * the tick the block goes, at the end of the drilling, before the next operation could start and be paid for.
	 */
	static boolean digUntilGone(Rig rig, BlockPos pos) {
		for (int i = 0; i < OPERATION_TICKS; i++) {
			rig.tick(rig.controllerPos());
			if (rig.state(pos).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** Ticks the controller until the raise is over and the step has moved past {@code step}. */
	private static void finishOperation(Rig rig, int step) {
		for (int i = 0; i < OPERATION_TICKS && rig.controller().digger().index() == step; i++) {
			rig.tick(rig.controllerPos());
		}
	}

	private static int count(Container container, net.minecraft.world.item.Item item, int from, int to) {
		int n = 0;
		for (int i = from; i < to; i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int bufferCount(QuarryControllerBlockEntity c, net.minecraft.world.item.Item item) {
		return count(c, item, QuarryControllerBlockEntity.BUFFER_START, QuarryControllerBlockEntity.SLOT_COUNT);
	}

	/**
	 * The first stone of the pit breaks into the buffer as cobblestone, the bit loses one point, and the operation is
	 * paid exactly: no travel (the drill starts over the first cell), the rope from the head down to it and one block
	 * back up, and stone's drilling.
	 *
	 * @implements MOD-951 — the quarry breaks a block of the pit as its owner and the drop goes into the buffer
	 */
	public static void theFirstBlockGoesIntoTheBuffer(GameTestHelper helper) {
		withDigging(() -> theFirstBlockGoesIntoTheBufferBody(helper));
	}

	private static void theFirstBlockGoesIntoTheBufferBody(GameTestHelper helper) {
		Rig rig = ready(helper);
		BlockPos first = pit(rig, 0);
		rig.set(first, Blocks.STONE.defaultBlockState());
		QuarryControllerBlockEntity controller = rig.controller();
		rig.scanNow();
		if (!digUntilGone(rig, first)) {
			helper.fail("the first stone of the pit was not dug: " + controller.workStatus());
			return;
		}
		if (bufferCount(controller, Items.COBBLESTONE) != 1) {
			helper.fail("the stone's cobblestone is not in the buffer");
			return;
		}
		ItemStack bit = controller.getItem(QuarryControllerBlockEntity.BIT_SLOT);
		if (bit.getDamageValue() != 1) {
			helper.fail("the drill bit should have lost one point, it shows " + bit.getDamageValue());
			return;
		}
		long rope = (long) Config.quarryRopeEuPerBlock * (HEAD - BASE + 1);
		long drill = Math.round(Config.quarryDrillEuPerHardness * 1.5D);
		long spent = FULL - controller.getEnergyStorage().getAmount();
		if (spent != rope + drill) {
			helper.fail("the operation should cost " + (rope + drill) + " EU (rope " + rope + ", drill " + drill
					+ "), it cost " + spent);
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * Water, a chest with something in it, bedrock and a stone beside lava are passed over and left as they are; the
	 * stone after them is the first block dug, and only it is paid for — the air and the skipped cells cost nothing.
	 *
	 * @implements MOD-951 — skips: liquids, contents, unbreakable, beside lava; air is free
	 */
	public static void skippedCellsAreLeftAndCostNothing(GameTestHelper helper) {
		withDigging(() -> skippedCellsAreLeftAndCostNothingBody(helper));
	}

	private static void skippedCellsAreLeftAndCostNothingBody(GameTestHelper helper) {
		Rig rig = ready(helper);
		rig.set(pit(rig, 0), Blocks.WATER.defaultBlockState());
		rig.set(pit(rig, 1), Blocks.CHEST.defaultBlockState());
		if (rig.level().getBlockEntity(pit(rig, 1)) instanceof ChestBlockEntity chest) {
			chest.setItem(0, new ItemStack(Items.DIAMOND));
		}
		rig.set(pit(rig, 2), Blocks.BEDROCK.defaultBlockState());
		rig.set(pit(rig, 3), Blocks.STONE.defaultBlockState());
		rig.set(pit(rig, 3).above(), Blocks.LAVA.defaultBlockState());
		BlockPos target = pit(rig, 4);
		rig.set(target, Blocks.STONE.defaultBlockState());
		QuarryControllerBlockEntity controller = rig.controller();
		rig.scanNow();
		if (!digUntilGone(rig, target)) {
			helper.fail("the stone after the skipped cells was not dug: " + controller.workStatus());
			return;
		}
		boolean kept = rig.state(pit(rig, 0)).is(Blocks.WATER) && rig.state(pit(rig, 2)).is(Blocks.BEDROCK)
				&& rig.state(pit(rig, 3)).is(Blocks.STONE)
				&& rig.level().getBlockEntity(pit(rig, 1)) instanceof ChestBlockEntity chest
				&& chest.getItem(0).is(Items.DIAMOND);
		if (!kept) {
			helper.fail("a skipped cell was touched: water, the chest and its diamond, bedrock and the stone by the"
					+ " lava must all stay");
			return;
		}
		// The drill takes 2 x 2 tiles (MOD-952): the stone at cell 4 of the 5-cell row is in the row's last tile, moved
		// back to start at cell 3, so the bridge travels three cells.
		long travel = (long) Config.quarryTravelEuPerBlock * 3;
		long rope = (long) Config.quarryRopeEuPerBlock * (HEAD - BASE + 1);
		long drill = Math.round(Config.quarryDrillEuPerHardness * 1.5D);
		long spent = FULL - controller.getEnergyStorage().getAmount();
		if (spent != travel + rope + drill) {
			helper.fail("only the dug stone is paid for: expected " + (travel + rope + drill) + " EU, spent " + spent);
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * With no bit, then no energy, then a full buffer, the quarry stands and names why, touching nothing; each fixed,
	 * it digs the same block, and nothing in the buffer is lost or doubled.
	 *
	 * @implements MOD-951 — stops: no bit, no power, output full; resumed on the same cell
	 */
	public static void itStandsForItsReasonsAndGoesOn(GameTestHelper helper) {
		withDigging(() -> itStandsForItsReasonsAndGoesOnBody(helper));
	}

	private static void itStandsForItsReasonsAndGoesOnBody(GameTestHelper helper) {
		Rig rig = ready(helper);
		QuarryControllerBlockEntity controller = rig.controller();
		BlockPos first = pit(rig, 0);
		rig.set(first, Blocks.STONE.defaultBlockState());
		ItemStack bit = controller.removeItemNoUpdate(QuarryControllerBlockEntity.BIT_SLOT);
		rig.scanNow();
		if (digUntilGone(rig, first) || controller.workStatus() != QuarryWorkStatus.NO_BIT) {
			helper.fail("with no bit the quarry must stand on NO_BIT, it is " + controller.workStatus());
			return;
		}
		controller.setItem(QuarryControllerBlockEntity.BIT_SLOT, bit);
		controller.getEnergyStorage().setAmountUntracked(0);
		if (digUntilGone(rig, first) || controller.workStatus() != QuarryWorkStatus.NO_POWER) {
			helper.fail("with no energy the quarry must stand on NO_POWER, it is " + controller.workStatus());
			return;
		}
		controller.getEnergyStorage().setAmountUntracked(FULL);
		for (int i = QuarryControllerBlockEntity.BUFFER_START; i < QuarryControllerBlockEntity.SLOT_COUNT; i++) {
			controller.setItem(i, new ItemStack(Items.DIRT, 64));
		}
		if (digUntilGone(rig, first) || controller.workStatus() != QuarryWorkStatus.OUTPUT_FULL) {
			helper.fail("with a full buffer the quarry must stand on OUTPUT_FULL, it is " + controller.workStatus());
			return;
		}
		controller.setItem(QuarryControllerBlockEntity.BUFFER_START, ItemStack.EMPTY);
		if (!digUntilGone(rig, first)) {
			helper.fail("with room in the buffer the quarry must dig on, it is " + controller.workStatus());
			return;
		}
		int dirt = bufferCount(controller, Items.DIRT);
		if (dirt != 26 * 64 || bufferCount(controller, Items.COBBLESTONE) != 1) {
			helper.fail("the buffer must hold the 26 stacks of dirt and one cobblestone, it holds " + dirt
					+ " dirt and " + bufferCount(controller, Items.COBBLESTONE) + " cobblestone");
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * Through the socket foundation at the foot of the main tower, automation takes the haul out of the buffer and
	 * brings a drill bit into the bit slot; a loose frame's foundation lends nothing.
	 *
	 * @implements MOD-951 — the haul leaves through the main tower's foundation, the bit comes in the same way
	 */
	public static void theSocketLendsTheBufferAndTheBitSlot(GameTestHelper helper) {
		withDigging(() -> theSocketLendsTheBufferAndTheBitSlotBody(helper));
	}

	private static void theSocketLendsTheBufferAndTheBitSlotBody(GameTestHelper helper) {
		Rig rig = ready(helper);
		QuarryControllerBlockEntity controller = rig.controller();
		controller.removeItemNoUpdate(QuarryControllerBlockEntity.BIT_SLOT);
		controller.setItem(QuarryControllerBlockEntity.BUFFER_START, new ItemStack(Items.COBBLESTONE, 10));
		rig.scanNow();
		BlockPos chestPos = rig.at(-2, BASE, 0);
		rig.set(chestPos, Blocks.CHEST.defaultBlockState());
		if (!(rig.level().getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
			helper.fail("no chest beside the frame");
			return;
		}
		chest.setItem(0, new ItemStack(ModContent.QUARRY_DRILL_BIT.get()));
		ItemPort socket = ItemLookup.get().find(rig.level(), rig.socket(), Direction.WEST);
		ItemPort box = ItemLookup.get().find(rig.level(), chestPos, Direction.EAST);
		if (socket == null || box == null) {
			helper.fail("the formed frame's socket foundation must expose an item port, and the chest one");
			return;
		}
		int out = 0;
		for (int i = 0; i < 10; i++) {
			out += ItemMover.move(socket, box, 4);
		}
		int in = ItemMover.move(box, socket, 1);
		if (out != 10 || count(chest, Items.COBBLESTONE, 0, chest.getContainerSize()) != 10) {
			helper.fail("the ten cobblestone must leave the buffer through the socket, moved " + out);
			return;
		}
		ItemStack bit = controller.getItem(QuarryControllerBlockEntity.BIT_SLOT);
		if (in != 1 || !bit.is(ModContent.QUARRY_DRILL_BIT.get())) {
			helper.fail("the drill bit must go into the bit slot through the socket, moved " + in);
			return;
		}
		rig.set(rig.at(FAR + 1, BASE + 3, FAR + 1), Blocks.AIR.defaultBlockState());
		rig.scanNow();
		if (ItemLookup.get().find(rig.level(), rig.socket(), Direction.WEST) != null) {
			helper.fail("a loose frame's foundation must lend no inventory");
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * The step survives the frame coming apart and standing again: a scaffold taken out and put back does not send
	 * the quarry to the start of its pit.
	 *
	 * @implements MOD-951 — progress is kept by the controller across taking the frame apart and assembling it again
	 */
	public static void progressOutlivesTheFrameComingApart(GameTestHelper helper) {
		withDigging(() -> progressOutlivesTheFrameComingApartBody(helper));
	}

	private static void progressOutlivesTheFrameComingApartBody(GameTestHelper helper) {
		Rig rig = ready(helper);
		QuarryControllerBlockEntity controller = rig.controller();
		BlockPos first = pit(rig, 0);
		rig.set(first, Blocks.STONE.defaultBlockState());
		// In the next tile (MOD-952: the drill takes 2 x 2 at once), so step 1 has a block to wait on.
		rig.set(pit(rig, 2), Blocks.STONE.defaultBlockState());
		rig.scanNow();
		if (!digUntilGone(rig, first)) {
			helper.fail("the first stone was not dug: " + controller.workStatus());
			return;
		}
		finishOperation(rig, 0);
		int step = controller.digger().index();
		if (step < 1) {
			helper.fail("after the first block the step must have moved on, it is " + step);
			return;
		}
		BlockPos scaffold = rig.at(FAR + 1, BASE + 3, FAR + 1);
		net.minecraft.world.level.block.state.BlockState kept = rig.state(scaffold);
		rig.set(scaffold, Blocks.AIR.defaultBlockState());
		rig.scanNow();
		if (controller.workStatus() != QuarryWorkStatus.NOT_FORMED) {
			helper.fail("a frame with a scaffold gone must stand as not formed, it is " + controller.workStatus());
			return;
		}
		rig.set(scaffold, kept.getBlock().defaultBlockState());
		rig.scanNow();
		if (controller.digger().index() != step || controller.digger().layer() != 0) {
			helper.fail("the frame formed again must go on from step " + step + ", it is at layer "
					+ controller.digger().layer() + " step " + controller.digger().index());
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * With digging switched off by the operator a formed frame with an owner, a bit and energy leaves the pit alone,
	 * spends nothing and says so. Digging is on by default since stage C (MOD-952); the switch stays for operators.
	 *
	 * @implements MOD-952 — the operator's switch stands a formed quarry still
	 */
	public static void itDoesNotDigWhileDiggingIsOff(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("quarryDiggingEnabled", false)) {
			itDoesNotDigWhileDiggingIsOffBody(helper);
		}
	}

	private static void itDoesNotDigWhileDiggingIsOffBody(GameTestHelper helper) {
		Rig rig = ready(helper);
		BlockPos first = pit(rig, 0);
		rig.set(first, Blocks.STONE.defaultBlockState());
		rig.scanNow();
		QuarryControllerBlockEntity controller = rig.controller();
		if (digUntilGone(rig, first) || controller.workStatus() != QuarryWorkStatus.DIGGING_OFF) {
			helper.fail("with digging off the stone must stay and the status read DIGGING_OFF, it is "
					+ controller.workStatus());
			return;
		}
		if (controller.getEnergyStorage().getAmount() != FULL) {
			helper.fail("with digging off nothing may be spent, the buffer holds "
					+ controller.getEnergyStorage().getAmount());
			return;
		}
		rig.hold().release();
		helper.succeed();
	}
}

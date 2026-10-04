package dev.alaindustrial.gametest;

import dev.alaindustrial.block.AbstractModChestBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.item.ItemMover;
import dev.alaindustrial.core.item.ItemPort;
import dev.alaindustrial.menu.DoubleChestMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Loader-neutral gametest bodies for the double chest (MOD-391, suite TC-CHEST-001). Same pattern as
 * {@link StockDisplayFrameScenarios}: plain bodies over {@code GameTestHelper}, wrapped by the Fabric
 * {@code DoubleChestGameTest} suite and registered on the NeoForge lane via {@code NeoForgeGameTests}.
 *
 * <p>Geometry used throughout: facing SOUTH; the LEFT half's partner sits to its
 * {@code getClockWise()} = WEST, so the pair is RIGHT half at {@link #A}, LEFT half at {@link #B}
 * (east of A). The combined container joins them FIRST(RIGHT)-then-SECOND(LEFT) — vanilla order —
 * so combined slots 0..35 are A's and 36..71 are B's for the iron tier.
 */
public final class DoubleChestScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(DoubleChestScenarios::fun01PairFormsAndJoins, "double_chest_pair_forms_and_joins")
						.fabricId("DoubleChestGameTest", "tcChest001Fun01_pairFormsAndJoins").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun02CrossTierNeverPairs, "double_chest_cross_tier_never_pairs")
						.fabricId("DoubleChestGameTest", "tcChest001Fun02_crossTierNeverPairs").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun03PlacementSneakStaysSingle, "double_chest_sneak_stays_single")
						.fabricId("DoubleChestGameTest", "tcChest001Fun03_placementSneakStaysSingle").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun04BreakHalfRevertsPartner, "double_chest_break_reverts_partner")
						.fabricId("DoubleChestGameTest", "tcChest001Fun04_breakHalfRevertsPartner").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun05HopperOverflowsIntoSecondHalf, "double_chest_hopper_overflow")
						.fabricId("DoubleChestGameTest", "tcChest001Fun05_hopperOverflowsIntoSecondHalf")
						.ticks(20, 100),
				RosterEntry.of(DoubleChestScenarios::fun06ComparatorReadsJoined, "double_chest_comparator_joined")
						.fabricId("DoubleChestGameTest", "tcChest001Fun06_comparatorReadsJoined").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun07WindowScrollsHiddenRows, "double_chest_window_scrolls")
						.fabricId("DoubleChestGameTest", "tcChest001Fun07_windowScrollsHiddenRows").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun08ShiftClickReachesHiddenRows,
								"double_chest_shift_click_hidden_rows")
						.fabricId("DoubleChestGameTest", "tcChest001Fun08_shiftClickReachesHiddenRows").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun09WaterloggedSurvivesBreak, "double_chest_waterlogged_break")
						.fabricId("DoubleChestGameTest", "tcChest001Fun09_waterloggedSurvivesBreak").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun10BreakingHalfClosesWindow, "double_chest_break_closes_window")
						.fabricId("DoubleChestGameTest", "tcChest001Fun10_breakingHalfClosesWindow").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::per01ItemsSurviveNbtRoundTrip, "double_chest_nbt_round_trip")
						.fabricId("DoubleChestGameTest", "tcChest001Per01_itemsSurviveNbtRoundTrip").ticks(20, 40),
				RosterEntry.of(DoubleChestScenarios::fun11ItemLookupSeesJoinedPairEveryTier,
								"double_chest_item_lookup_every_tier")
						.fabricId("DoubleChestGameTest", "tcChest001Fun11_itemLookupSeesJoinedPairEveryTier")
						.ticks(20, 40));

		private Roster() {}
	}

	private DoubleChestScenarios() {}

	private static final BlockPos A = new BlockPos(1, 2, 1);
	private static final BlockPos B = new BlockPos(2, 2, 1);
	/** A lone iron chest two blocks south of the pair — never adjacent, so it can never join it. */
	private static final BlockPos SINK = new BlockPos(1, 2, 3);
	private static final int IRON_SLOTS = 36;

	private static Block ironChest() {
		return ModContent.IRON_CHEST.get();
	}

	private static BlockState single(Block block) {
		return block.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH)
				.setValue(AbstractModChestBlock.TYPE, ChestType.SINGLE);
	}

	private static BlockState half(Block block, ChestType type) {
		return single(block).setValue(AbstractModChestBlock.TYPE, type);
	}

	/**
	 * Place the standard pair: A (west) becomes RIGHT via updateShape when B arrives as LEFT — the
	 * same order of events real placement produces (the new block carries its half type from
	 * getStateForPlacement; the existing SINGLE joins through updateShape).
	 */
	private static void placePair(GameTestHelper helper) {
		helper.setBlock(A, single(ironChest()));
		helper.setBlock(B, half(ironChest(), ChestType.LEFT));
	}

	private static AbstractChestBlockEntity chestAt(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
		if (!(be instanceof AbstractChestBlockEntity chest)) {
			helper.fail("no chest block entity at " + pos);
			throw new IllegalStateException("unreachable");
		}
		return chest;
	}

	private static Container combinedFrom(GameTestHelper helper, BlockPos pos) {
		BlockState state = helper.getBlockState(pos);
		if (!(state.getBlock() instanceof AbstractModChestBlock chest)) {
			helper.fail("no chest block at " + pos);
			throw new IllegalStateException("unreachable");
		}
		Container combined = chest.combinedContainer(state, helper.getLevel(), helper.absolutePos(pos));
		if (combined == null) {
			helper.fail("combinedContainer returned null at " + pos);
		}
		return combined;
	}

	// ── pairing ──────────────────────────────────────────────────────────────────────────────────

	/**
	 * TC-CHEST-001-FUN01 — a LEFT half arriving next to a same-tier SINGLE pairs it up as RIGHT.
	 *
	 * @implements TC-CHEST-001-FUN01 — a LEFT half pairs a same-tier SINGLE up as RIGHT, 72 joined slots.
	 */
	public static void fun01PairFormsAndJoins(GameTestHelper helper) {
		placePair(helper);
		ChestType typeA = helper.getBlockState(A).getValue(AbstractModChestBlock.TYPE);
		if (typeA != ChestType.RIGHT) {
			helper.fail("existing single must join as RIGHT when a LEFT half arrives, got " + typeA);
		}
		chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 4));
		chestAt(helper, B).setItem(0, new ItemStack(Items.EMERALD, 6));
		Container combined = combinedFrom(helper, A);
		if (combined.getContainerSize() != IRON_SLOTS * 2) {
			helper.fail("combined double must hold 72 slots, got " + combined.getContainerSize());
		}
		// FIRST(RIGHT=A) half first: its slot 0 is combined slot 0; B's slot 0 is combined slot 36.
		if (combined.getItem(0).getItem() != Items.DIAMOND
				|| combined.getItem(IRON_SLOTS).getItem() != Items.EMERALD) {
			helper.fail("combined order must be RIGHT half first (A slot0=diamond, B slot0=emerald), got "
					+ combined.getItem(0) + " / " + combined.getItem(IRON_SLOTS));
		}
		// The combined view resolves identically from either half.
		if (combinedFrom(helper, B).getContainerSize() != IRON_SLOTS * 2) {
			helper.fail("combining from the LEFT half must resolve the same 72-slot pair");
		}
		helper.succeed();
	}

	/**
	 * TC-CHEST-001-FUN02 — a foreign-tier neighbour never joins: both sides stay functionally single.
	 *
	 * @implements TC-CHEST-001-FUN02 — a foreign-tier neighbour never joins.
	 */
	public static void fun02CrossTierNeverPairs(GameTestHelper helper) {
		helper.setBlock(A, single(ironChest()));
		// setBlock never runs updateShape on the NEW block itself, so the stranded silver LEFT keeps
		// its half state — a shape real gameplay cannot produce. What matters (and what the combiner
		// guards) is FUNCTIONAL isolation: neither side may resolve the foreign neighbour as a partner.
		helper.setBlock(B, half(ModContent.SILVER_CHEST.get(), ChestType.LEFT));
		if (helper.getBlockState(A).getValue(AbstractModChestBlock.TYPE) != ChestType.SINGLE) {
			helper.fail("iron chest must not pair with a silver half");
		}
		if (combinedFrom(helper, A).getContainerSize() != IRON_SLOTS) {
			helper.fail("iron chest next to a silver half must answer for its own 36 slots only");
		}
		// The stranded silver half walks toward A, finds a foreign block, degrades to single: 45 slots.
		if (combinedFrom(helper, B).getContainerSize() != 45) {
			helper.fail("a silver half whose partner position holds iron must combine to its own 45 slots, got "
					+ combinedFrom(helper, B).getContainerSize());
		}
		helper.succeed();
	}

	/**
	 * TC-CHEST-001-FUN03 — placement rules: auto-join without sneak, deliberately single with sneak.
	 *
	 * @implements TC-CHEST-001-FUN03 — auto-join without sneak; sneak placement stays single.
	 */
	public static void fun03PlacementSneakStaysSingle(GameTestHelper helper) {
		helper.setBlock(A, single(ironChest()));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos targetAbs = helper.absolutePos(B);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(targetAbs), Direction.UP, targetAbs, false);
		// The placed chest faces OPPOSITE the player's horizontal direction, and auto-join requires
		// that facing to match the neighbour's SOUTH — so the player must look NORTH: yaw 180
		// (yaw 0 is south; this exact off-by-180 made the first run of this test fail honestly).
		player.setYRot(180.0F);
		BlockState placed = ironChest().getStateForPlacement(
				new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(ironChest()), hit));
		if (placed == null || placed.getValue(AbstractModChestBlock.TYPE) == ChestType.SINGLE) {
			helper.fail("placing next to a same-facing single without sneak must auto-join, got "
					+ (placed == null ? "null" : placed.getValue(AbstractModChestBlock.TYPE)));
		}
		player.setShiftKeyDown(true);
		BlockState sneaked = ironChest().getStateForPlacement(
				new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(ironChest()), hit));
		if (sneaked == null || sneaked.getValue(AbstractModChestBlock.TYPE) != ChestType.SINGLE) {
			helper.fail("sneak-placing away from a chest's side must stay SINGLE, got "
					+ (sneaked == null ? "null" : sneaked.getValue(AbstractModChestBlock.TYPE)));
		}
		// Third branch: sneak-clicking the SIDE of the lone chest is a deliberate attach — the new
		// half takes the PARTNER's facing, not the player's. The player still looks north here, so a
		// facing that follows the partner (south) is what proves the branch ran.
		BlockHitResult sideHit = new BlockHitResult(
				Vec3.atCenterOf(targetAbs), Direction.EAST, targetAbs, false);
		BlockState attached = ironChest().getStateForPlacement(
				new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(ironChest()), sideHit));
		if (attached == null || attached.getValue(AbstractModChestBlock.TYPE) == ChestType.SINGLE) {
			helper.fail("sneak-clicking the side of a lone chest must attach to it, got "
					+ (attached == null ? "null" : attached.getValue(AbstractModChestBlock.TYPE)));
		}
		if (attached.getValue(HorizontalMachineBlock.FACING) != Direction.SOUTH) {
			helper.fail("the deliberately attached half must take the partner's facing (south), got "
					+ attached.getValue(HorizontalMachineBlock.FACING));
		}
		helper.succeed();
	}

	/**
	 * TC-CHEST-001-FUN10 — breaking either half invalidates an open double window (vanilla
	 * {@code ChestMenu} semantics: {@code CompoundContainer.stillValid} ANDs both halves).
	 *
	 * @implements TC-CHEST-001-FUN10 — breaking a half invalidates the open double window.
	 */
	public static void fun10BreakingHalfClosesWindow(GameTestHelper helper) {
		placePair(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// The mock player spawns outside the container reach, which would make the assertion below
		// pass for the wrong reason — stand him next to the pair first (the negative control caught
		// exactly this on the first run).
		BlockPos stand = helper.absolutePos(A.south());
		player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		Container joined = new CompoundContainer(chestAt(helper, A), chestAt(helper, B));
		DoubleChestMenu menu = DoubleChestMenu.server(1, player.getInventory(), joined);
		// Negative control: while both halves stand, the window MUST be valid — otherwise the
		// assertion below would pass for the wrong reason.
		if (!menu.stillValid(player)) {
			helper.fail("an intact pair must keep its window valid — the check below would be vacuous");
		}
		helper.destroyBlock(B);
		if (menu.stillValid(player)) {
			helper.fail("after one half is broken the double's window must close (stillValid=false)");
		}
		menu.removed(player);
		helper.succeed();
	}

	/**
	 * TC-CHEST-001-FUN04 — breaking one half reverts the partner to SINGLE with its items intact.
	 *
	 * @implements TC-CHEST-001-FUN04 — breaking one half reverts the partner to SINGLE, items intact.
	 */
	public static void fun04BreakHalfRevertsPartner(GameTestHelper helper) {
		placePair(helper);
		chestAt(helper, A).setItem(3, new ItemStack(Items.IRON_INGOT, 7));
		helper.destroyBlock(B);
		ChestType typeA = helper.getBlockState(A).getValue(AbstractModChestBlock.TYPE);
		if (typeA != ChestType.SINGLE) {
			helper.fail("surviving half must fall back to SINGLE, got " + typeA);
		}
		ItemStack kept = chestAt(helper, A).getItem(3);
		if (kept.getItem() != Items.IRON_INGOT || kept.getCount() != 7) {
			helper.fail("surviving half must keep its own items, got " + kept);
		}
		if (combinedFrom(helper, A).getContainerSize() != IRON_SLOTS) {
			helper.fail("after the break the survivor must answer for 36 slots only");
		}
		helper.succeed();
	}

	// ── automation ───────────────────────────────────────────────────────────────────────────────

	/**
	 * TC-CHEST-001-FUN05 — a hopper feeding a FULL first half overflows into the second: the vanilla
	 * hopper resolves the double through {@code WorldlyContainerHolder} as one 72-slot container.
	 *
	 * @implements TC-CHEST-001-FUN05 — a hopper overflows a full first half into the second.
	 */
	public static void fun05HopperOverflowsIntoSecondHalf(GameTestHelper helper) {
		placePair(helper);
		AbstractChestBlockEntity right = chestAt(helper, A);
		for (int slot = 0; slot < IRON_SLOTS; slot++) {
			right.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.setBlock(A.above(), Blocks.HOPPER);
		BlockEntity hopper = helper.getLevel().getBlockEntity(helper.absolutePos(A.above()));
		if (hopper instanceof Container hopperContainer) {
			hopperContainer.setItem(0, new ItemStack(Items.DIAMOND, 1));
		} else {
			helper.fail("no hopper container above the chest");
		}
		AbstractChestBlockEntity left = chestAt(helper, B);
		helper.succeedWhen(() -> {
			if (left.getItem(0).getItem() != Items.DIAMOND) {
				helper.fail("the diamond must overflow into the second half's first slot; still "
						+ left.getItem(0));
			}
		});
	}

	/**
	 * TC-CHEST-001-FUN06 — the comparator reads the pair as one container, the same from either half.
	 *
	 * @implements TC-CHEST-001-FUN06 — the comparator reads the pair as one, the same from either half.
	 */
	public static void fun06ComparatorReadsJoined(GameTestHelper helper) {
		placePair(helper);
		// Items only in the LEFT half: a per-half signal from A would be 0.
		AbstractChestBlockEntity left = chestAt(helper, B);
		for (int slot = 0; slot < IRON_SLOTS; slot++) {
			left.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		int fromA = helper.getBlockState(A).getAnalogOutputSignal(
				helper.getLevel(), helper.absolutePos(A), Direction.NORTH);
		int fromB = helper.getBlockState(B).getAnalogOutputSignal(
				helper.getLevel(), helper.absolutePos(B), Direction.NORTH);
		if (fromA == 0) {
			helper.fail("comparator on the empty half must still see the pair's items (joined signal), got 0");
		}
		if (fromA != fromB) {
			helper.fail("both halves must report the same signal, got " + fromA + " vs " + fromB);
		}
		helper.succeed();
	}

	/**
	 * TC-CHEST-001-FUN11 (MOD-690) — for EVERY chest tier, the loader's item lookup on the LEFT half
	 * answers for the whole pair, right half first: the view the mod's item pipes (and any other
	 * transfer-API mod) get. Fabric used to list the tiers by hand for its combined provider and the
	 * diamond tier was missing, so a pipe on one diamond half saw its own 108 slots instead of 216.
	 *
	 * <p>The tiers are enumerated from the block registry by class, independently of how either
	 * loader registers the capability, so a future tier is covered without touching this test.
	 * Every slot of both halves holds ONE item (diamonds right, emeralds left): draining through the
	 * left half must move {@code 2 × half} items — a per-half view moves exactly {@code half}, the
	 * left half's own emeralds. A single item pushed back through the left half must land in the
	 * RIGHT half's slot 0, which pins the slot order (right half first, as the menu and NeoForge).
	 *
	 * @implements TC-CHEST-001-FUN11 — every tier's pair is one container to the item lookup (MOD-690).
	 */
	public static void fun11ItemLookupSeesJoinedPairEveryTier(GameTestHelper helper) {
		List<Block> tiers = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block instanceof AbstractModChestBlock) {
				tiers.add(block);
			}
		}
		if (!tiers.contains(ModContent.DIAMOND_CHEST.get())) {
			helper.fail("the chest-tier enumeration must include the diamond chest, found " + tiers);
			return;
		}
		for (Block tier : tiers) {
			if (!joinedThroughItemLookup(helper, tier)) {
				return;
			}
		}
		helper.succeed();
	}

	/** One tier of {@link #fun11ItemLookupSeesJoinedPairEveryTier}; clears the rig afterwards. */
	private static boolean joinedThroughItemLookup(GameTestHelper helper, Block tier) {
		String name = BuiltInRegistries.BLOCK.getKey(tier).toString();
		helper.setBlock(A, single(tier));
		helper.setBlock(B, half(tier, ChestType.LEFT));
		helper.setBlock(SINK, single(ironChest()));
		if (helper.getBlockState(A).getValue(AbstractModChestBlock.TYPE) != ChestType.RIGHT) {
			helper.fail(name + ": the pair did not form (existing single must join as RIGHT)");
			return false;
		}
		AbstractChestBlockEntity right = chestAt(helper, A);
		AbstractChestBlockEntity left = chestAt(helper, B);
		AbstractChestBlockEntity sinkChest = chestAt(helper, SINK);
		int half = right.getContainerSize();
		for (int slot = 0; slot < half; slot++) {
			right.setItem(slot, new ItemStack(Items.DIAMOND, 1));
			left.setItem(slot, new ItemStack(Items.EMERALD, 1));
		}
		ItemPort pair = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(B), Direction.UP);
		ItemPort sink = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(SINK), Direction.UP);
		if (pair == null || sink == null) {
			helper.fail(name + ": no item port on the pair's left half or on the sink on this loader");
			return false;
		}
		// Ask for one item more than the pair holds, so the amount moved is decided by what the port sees.
		int drained = ItemMover.move(pair, sink, half * 2 + 1);
		if (drained != half * 2) {
			helper.fail(name + ": a pipe on the left half must reach all " + (half * 2)
					+ " slots of the pair (one item each), moved " + drained
					+ (drained == half ? " — only the left half's own slots" : ""));
			return false;
		}
		if (!right.isEmpty()) {
			helper.fail(name + ": draining through the left half must empty the right half too");
			return false;
		}
		int returned = ItemMover.move(sink, pair, 1);
		if (returned != 1 || right.getItem(0).isEmpty() || !left.isEmpty()) {
			helper.fail(name + ": one item inserted through the left half must land in the RIGHT half's"
					+ " slot 0 (right half first), moved " + returned + ", right slot 0 = "
					+ right.getItem(0) + ", left empty = " + left.isEmpty());
			return false;
		}
		right.clearContent();
		left.clearContent();
		sinkChest.clearContent();
		helper.setBlock(B, Blocks.AIR);
		helper.setBlock(A, Blocks.AIR);
		helper.setBlock(SINK, Blocks.AIR);
		return true;
	}

	// ── the shared window ────────────────────────────────────────────────────────────────────────

	/**
	 * TC-CHEST-001-FUN07 — the 6-row window scrolls over the double's 8 rows; the slots never move.
	 *
	 * @implements TC-CHEST-001-FUN07 — the 6-row window scrolls over the double's rows.
	 */
	public static void fun07WindowScrollsHiddenRows(GameTestHelper helper) {
		placePair(helper);
		Container joined = new CompoundContainer(chestAt(helper, A), chestAt(helper, B));
		// Mark the LAST row (combined slots 63..71), unreachable without scrolling.
		joined.setItem(IRON_SLOTS * 2 - 9, new ItemStack(Items.LAPIS_LAZULI, 5));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		DoubleChestMenu menu = DoubleChestMenu.server(1, player.getInventory(), joined);
		if (menu.getTotalRows() != 8 || menu.getMaxTopRow() != 2 || !menu.isScrollable()) {
			helper.fail("iron double must report 8 total rows / max top row 2 / scrollable, got "
					+ menu.getTotalRows() + "/" + menu.getMaxTopRow() + "/" + menu.isScrollable());
		}
		menu.clickMenuButton(player, 2);
		// Window slot 0 now answers for combined slot 18; the marked slot 63 is window slot 63-18=45.
		ItemStack seen = menu.slots.get(45).getItem();
		if (seen.getItem() != Items.LAPIS_LAZULI) {
			helper.fail("after scrolling to row 2 window slot 45 must show the last row's lapis, got " + seen);
		}
		menu.removed(player);
		helper.succeed();
	}

	/**
	 * TC-CHEST-001-FUN08 — shift-click reaches the rows the window does not show.
	 *
	 * @implements TC-CHEST-001-FUN08 — shift-click reaches the hidden rows.
	 */
	public static void fun08ShiftClickReachesHiddenRows(GameTestHelper helper) {
		placePair(helper);
		Container joined = new CompoundContainer(chestAt(helper, A), chestAt(helper, B));
		// Fill everything the window CAN show at top row 0 (slots 0..53) with filler.
		for (int slot = 0; slot < DoubleChestMenu.VISIBLE_ROWS * 9; slot++) {
			joined.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
		DoubleChestMenu menu = DoubleChestMenu.server(1, player.getInventory(), joined);
		// Hotbar slot 0 in the menu's slot list: 54 window + 27 inventory = index 81.
		ItemStack moved = menu.quickMoveStack(player, 81);
		if (moved.isEmpty()) {
			helper.fail("shift-click must report the stack as moved");
		}
		boolean landed = false;
		for (int slot = DoubleChestMenu.VISIBLE_ROWS * 9; slot < joined.getContainerSize(); slot++) {
			if (joined.getItem(slot).getItem() == Items.DIAMOND) {
				landed = true;
				break;
			}
		}
		if (!landed) {
			helper.fail("the diamonds must land in a hidden row (combined slot >= 54)");
		}
		menu.removed(player);
		helper.succeed();
	}

	// ── persistence & waterlogging ───────────────────────────────────────────────────────────────

	/**
	 * TC-CHEST-001-PER01 — a half's items survive the NBT round-trip (the chunk save/load path).
	 *
	 * @implements TC-CHEST-001-PER01 — a half's items survive the NBT round-trip.
	 */
	public static void per01ItemsSurviveNbtRoundTrip(GameTestHelper helper) {
		placePair(helper);
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		AbstractChestBlockEntity src = chestAt(helper, A);
		src.setItem(0, new ItemStack(Items.DIAMOND, 2));
		src.setItem(35, new ItemStack(Items.EMERALD, 9));
		CompoundTag tag = src.saveCustomOnly(registries);
		BlockPos abs = helper.absolutePos(A);
		AbstractChestBlockEntity restored = (AbstractChestBlockEntity)
				((net.minecraft.world.level.block.EntityBlock) ironChest())
						.newBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		if (restored.getItem(0).getCount() != 2 || restored.getItem(35).getCount() != 9) {
			helper.fail("items must survive the NBT round-trip: got " + restored.getItem(0)
					+ " / " + restored.getItem(35));
		}
		helper.succeed();
	}

	/**
	 * TC-CHEST-001-FUN09 — waterlogging survives the pair breaking apart.
	 *
	 * @implements TC-CHEST-001-FUN09 — waterlogging survives the pair breaking apart.
	 */
	public static void fun09WaterloggedSurvivesBreak(GameTestHelper helper) {
		helper.setBlock(A, single(ironChest()).setValue(AbstractModChestBlock.WATERLOGGED, true));
		helper.setBlock(B, half(ironChest(), ChestType.LEFT));
		if (!helper.getBlockState(A).getValue(AbstractModChestBlock.WATERLOGGED)) {
			helper.fail("pairing up must not dry the waterlogged half");
		}
		if (!helper.getBlockState(A).getFluidState().is(Fluids.WATER)) {
			helper.fail("the waterlogged half must report a water fluid state");
		}
		helper.destroyBlock(B);
		BlockState after = helper.getBlockState(A);
		if (after.getValue(AbstractModChestBlock.TYPE) != ChestType.SINGLE
				|| !after.getValue(AbstractModChestBlock.WATERLOGGED)) {
			helper.fail("after the partner breaks the survivor must be SINGLE and still waterlogged, got " + after);
		}
		helper.succeed();
	}
}

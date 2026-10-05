package dev.alaindustrial.gametest;

import dev.alaindustrial.entity.StockDisplayFrameEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Loader-neutral gametest bodies for the Stock Display Frame (MOD-066, suite TC-FRAME-001). Same
 * pattern as {@link PouchScenarios}: plain bodies over {@code GameTestHelper}, wrapped by the Fabric
 * {@code StockDisplayFrameGameTest} suite and registered on the NeoForge {@code gameTestServer} lane
 * via {@code NeoForgeGameTests}.
 *
 * <p>Scans are driven deterministically by calling {@code frame.tick()} directly (the repo idiom —
 * see {@code MachineEnergyScenarios.maceratorProcessesRecipe}); {@code SCAN_TICKS} covers one full
 * scan interval regardless of the configured value.
 */
public final class StockDisplayFrameScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(StockDisplayFrameScenarios::fun01CountsWholeContainer,
								"stock_frame_counts_whole_container")
						.fabricId("StockDisplayFrameGameTest", "tcFrame001Fun01_countsWholeContainer").ticks(20, 60),
				RosterEntry.of(StockDisplayFrameScenarios::fun02FilterCountsOnlyMatching,
								"stock_frame_filter_counts_matching")
						.fabricId("StockDisplayFrameGameTest", "tcFrame001Fun02_filterCountsOnlyMatching")
						.ticks(20, 60),
				RosterEntry.of(StockDisplayFrameScenarios::fun03DoubleChestCombined,
								"stock_frame_double_chest_combined")
						.fabricId("StockDisplayFrameGameTest", "tcFrame001Fun03_doubleChestCombined").ticks(20, 60),
				RosterEntry.of(StockDisplayFrameScenarios::fun04UpdatesAfterChange, "stock_frame_updates_after_change")
						.fabricId("StockDisplayFrameGameTest", "tcFrame001Fun04_updatesAfterChange").ticks(20, 60),
				RosterEntry.of(StockDisplayFrameScenarios::fun05NoContainer, "stock_frame_no_container")
						.fabricId("StockDisplayFrameGameTest", "tcFrame001Fun05_noContainer").ticks(20, 60),
				RosterEntry.of(StockDisplayFrameScenarios::fun06DropsOwnItem, "stock_frame_drops_own_item")
						.fabricId("StockDisplayFrameGameTest", "tcFrame001Fun06_dropsOwnItem").ticks(20, 60));

		private Roster() {}
	}

	private StockDisplayFrameScenarios() {}

	private static final BlockPos CHEST = new BlockPos(1, 2, 1);
	/** One block south of the chest; the frame hangs there facing away from the chest. */
	private static final BlockPos FRAME = new BlockPos(1, 2, 2);

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────

	/** Spawn the frame at {@code frameRel} attached to the block at {@code behindRel}. */
	private static StockDisplayFrameEntity placeFrame(GameTestHelper helper, BlockPos frameRel, BlockPos behindRel) {
		BlockPos frameAbs = helper.absolutePos(frameRel);
		BlockPos behindAbs = helper.absolutePos(behindRel);
		// The frame faces AWAY from its support: direction = behind -> frame delta. Deriving it from
		// the two absolute positions keeps the scenario correct under structure rotation.
		Direction facing = Direction.getApproximateNearest(
				frameAbs.getX() - behindAbs.getX(),
				frameAbs.getY() - behindAbs.getY(),
				frameAbs.getZ() - behindAbs.getZ());
		@SuppressWarnings("unchecked")
		EntityType<StockDisplayFrameEntity> type =
				(EntityType<StockDisplayFrameEntity>) ModContent.STOCK_DISPLAY_FRAME.get();
		StockDisplayFrameEntity frame =
				new StockDisplayFrameEntity(type, helper.getLevel(), frameAbs, facing);
		if (!frame.survives()) {
			helper.fail("frame does not survive on its support block — placement setup broken");
		}
		helper.getLevel().addFreshEntity(frame);
		return frame;
	}

	/** Tick the frame through at least one full scan interval. */
	private static void scan(StockDisplayFrameEntity frame) {
		int ticks = Math.max(1, dev.alaindustrial.Config.stockFrameScanIntervalTicks) + 1;
		for (int i = 0; i < ticks; i++) {
			frame.tick();
		}
	}

	// ── scenario 1: empty frame counts the whole container ────────────────────────────────────────

	/**
	 * TC-FRAME-001-FUN01 — an empty frame on a chest shows the total item count across all slots.
	 *
	 * @implements TC-FRAME-001-FUN01 — an empty frame counts the whole container.
	 */
	public static void fun01CountsWholeContainer(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		if (helper.getBlockEntity(CHEST, ChestBlockEntity.class) instanceof ChestBlockEntity chest) {
			chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
			chest.setItem(5, new ItemStack(Items.IRON_INGOT, 3));
		}
		StockDisplayFrameEntity frame = placeFrame(helper, FRAME, CHEST);
		scan(frame);
		if (frame.getStockCount() != 8) {
			helper.fail("empty frame must count all items (5 diamonds + 3 iron = 8), got " + frame.getStockCount());
		}
		helper.succeed();
	}

	// ── scenario 2: filter item counts only matching stacks ───────────────────────────────────────

	/**
	 * TC-FRAME-001-FUN02 — a diamond in the frame counts only the chest's diamonds.
	 *
	 * @implements TC-FRAME-001-FUN02 — a filter item counts only matching stacks.
	 */
	public static void fun02FilterCountsOnlyMatching(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		if (helper.getBlockEntity(CHEST, ChestBlockEntity.class) instanceof ChestBlockEntity chest) {
			chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
			chest.setItem(5, new ItemStack(Items.IRON_INGOT, 3));
			chest.setItem(9, new ItemStack(Items.DIAMOND, 2));
		}
		StockDisplayFrameEntity frame = placeFrame(helper, FRAME, CHEST);
		frame.setItem(new ItemStack(Items.DIAMOND));
		scan(frame);
		if (frame.getStockCount() != 7) {
			helper.fail("diamond filter must count only diamonds (5 + 2 = 7), got " + frame.getStockCount());
		}
		helper.succeed();
	}

	// ── scenario 3: double chest counts both halves from either half ──────────────────────────────

	/**
	 * TC-FRAME-001-FUN03 — a frame on one half of a double chest shows the combined total.
	 *
	 * @implements TC-FRAME-001-FUN03 — a double chest is counted as one combined container.
	 */
	public static void fun03DoubleChestCombined(GameTestHelper helper) {
		BlockPos west = CHEST;
		BlockPos east = CHEST.east();
		// A double chest facing south: from the chests' own point of view (facing the frame), the
		// west block is the RIGHT half and the east block the LEFT half.
		helper.setBlock(west, Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.setBlock(east, Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		if (helper.getBlockEntity(west, ChestBlockEntity.class) instanceof ChestBlockEntity a) {
			a.setItem(0, new ItemStack(Items.DIAMOND, 4));
		}
		if (helper.getBlockEntity(east, ChestBlockEntity.class) instanceof ChestBlockEntity b) {
			b.setItem(0, new ItemStack(Items.DIAMOND, 6));
		}
		StockDisplayFrameEntity frame = placeFrame(helper, west.south(), west);
		scan(frame);
		if (frame.getStockCount() != 10) {
			helper.fail("double chest must be counted as one container (4 + 6 = 10), got " + frame.getStockCount());
		}
		helper.succeed();
	}

	// ── scenario 4: the count follows inventory changes ───────────────────────────────────────────

	/**
	 * TC-FRAME-001-FUN04 — adding items to the chest updates the count within one scan interval.
	 *
	 * @implements TC-FRAME-001-FUN04 — the count follows inventory changes within one scan.
	 */
	public static void fun04UpdatesAfterChange(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
		StockDisplayFrameEntity frame = placeFrame(helper, FRAME, CHEST);
		scan(frame);
		if (frame.getStockCount() != 5) {
			helper.fail("initial count must be 5, got " + frame.getStockCount());
		}
		chest.setItem(1, new ItemStack(Items.DIAMOND, 12));
		scan(frame);
		if (frame.getStockCount() != 17) {
			helper.fail("count must follow inventory changes (5 + 12 = 17), got " + frame.getStockCount());
		}
		helper.succeed();
	}

	// ── scenario 5: no container behind the frame ─────────────────────────────────────────────────

	/**
	 * TC-FRAME-001-FUN05 — a frame on a plain block reports NO_CONTAINER (renderer hides the text).
	 *
	 * @implements TC-FRAME-001-FUN05 — a non-container support reports NO_CONTAINER.
	 */
	public static void fun05NoContainer(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.STONE);
		StockDisplayFrameEntity frame = placeFrame(helper, FRAME, CHEST);
		scan(frame);
		if (frame.getStockCount() != StockDisplayFrameEntity.NO_CONTAINER) {
			helper.fail("a non-container support must report NO_CONTAINER, got " + frame.getStockCount());
		}
		helper.succeed();
	}

	// ── scenario 6: breaking the frame drops the mod's own item ───────────────────────────────────

	/**
	 * TC-FRAME-001-FUN06 — dropItem yields alaindustrial:stock_display_frame, not the vanilla frame.
	 *
	 * @implements TC-FRAME-001-FUN06 — breaking the frame drops the mod's own item.
	 */
	public static void fun06DropsOwnItem(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		StockDisplayFrameEntity frame = placeFrame(helper, FRAME, CHEST);
		frame.dropItem(helper.getLevel(), null);
		helper.assertItemEntityPresent(ModContent.STOCK_DISPLAY_FRAME_ITEM.get(), FRAME, 2.0);
		helper.assertItemEntityNotPresent(Items.ITEM_FRAME, FRAME, 2.0);
		helper.succeed();
	}
}

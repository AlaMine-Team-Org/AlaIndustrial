package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.item.ItemMover;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.core.item.ItemPort;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageCluster;
import dev.alaindustrial.menu.StorageModuleMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral checks for the modular warehouse (MOD-287): which modules merge, which do not, and
 * whose items are whose. Suite contract and shared helpers: {@link EnergyScenarioSupport}.
 */
public final class StorageClusterScenarios {

	private StorageClusterScenarios() {}

	private static final int PER_MODULE = StorageModuleBlockEntity.CONTAINER_SIZE;

	/**
	 * Face-adjacent modules merge; a diagonal neighbour does not.
	 *
	 * <p>The diagonal half is the one that matters. Face-only contact is what lets a player butt two
	 * separate warehouses together, and "any block touching any block" would silently merge them —
	 * a bug the player would only notice as items appearing in the wrong window.
	 *
	 * @implements TC-STOR-001 — face contact merges, edge/corner contact does not.
	 */
	public static void faceAdjacentMergesDiagonalDoesNot(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 2, 1);   // face-adjacent to a
		BlockPos far = new BlockPos(3, 2, 2); // diagonal to b — touches only at an edge
		place(helper, a);

		if (StorageCluster.of(helper.getLevel(), helper.absolutePos(a)).moduleCount() != 1) {
			helper.fail("a lone module should be a one-module warehouse");
			return;
		}

		place(helper, b);
		StorageCluster pair = StorageCluster.of(helper.getLevel(), helper.absolutePos(a));
		if (pair.moduleCount() != 2 || pair.container().getContainerSize() != 2 * PER_MODULE) {
			helper.fail("two face-adjacent modules should form one " + (2 * PER_MODULE) + "-slot warehouse, got "
					+ pair.moduleCount() + " modules / " + pair.container().getContainerSize() + " slots");
			return;
		}

		place(helper, far);
		StorageCluster stillPair = StorageCluster.of(helper.getLevel(), helper.absolutePos(a));
		if (stillPair.moduleCount() != 2) {
			helper.fail("a diagonal module joined the warehouse (" + stillPair.moduleCount()
					+ " modules) — contact must be face-to-face only");
			return;
		}
		helper.succeed();
	}

	/**
	 * The warehouse stops growing at four modules — the 108-slot single-screen cap.
	 *
	 * @implements TC-STOR-002 — a fifth module adds no slots.
	 */
	public static void fifthModuleAddsNothing(GameTestHelper helper) {
		// Pinned to literals on purpose. Every other assertion here is written in terms of
		// CONTAINER_SIZE, so it would happily follow the constant if someone changed it — and the GUI
		// atlases cannot follow: they are two fixed images, one drawn for three rows and one for six.
		// Checking the numbers themselves is what keeps the code and the artwork in step.
		if (PER_MODULE != 27) {
			helper.fail("a module must hold 27 slots (3 rows of 9) — one drawer per row on the block, and "
					+ "the 3-row atlas is drawn for that; got " + PER_MODULE);
			return;
		}
		if (StorageCluster.MAX_MODULES * PER_MODULE != 108) {
			helper.fail("a full warehouse must be 108 slots; got "
					+ (StorageCluster.MAX_MODULES * PER_MODULE));
			return;
		}
		// The window, unlike the warehouse, is capped by the screen. 6 rows = 6*18 + 112 = 220 px,
		// which is the vanilla double chest; the game's own auto GUI scale never leaves more than
		// about 270 px of height and leaves exactly 240 on 1440p and 4K. The 12-row window this used
		// to open was 328 px and ran off both edges of the screen — the defect this literal exists to
		// stop coming back.
		if (StorageCluster.MAX_VISIBLE_ROWS != 6 || StorageCluster.MAX_VISIBLE_ROWS * 18 + 112 != 220) {
			helper.fail("the window must show 6 rows / 220 px — the 6-row atlas is drawn for that and "
					+ "the game gives a window no more than ~270 px at its own GUI scale; got "
					+ StorageCluster.MAX_VISIBLE_ROWS + " rows / "
					+ (StorageCluster.MAX_VISIBLE_ROWS * 18 + 112) + " px");
			return;
		}
		BlockPos first = new BlockPos(1, 2, 1);
		for (int i = 0; i < 5; i++) {
			place(helper, first.offset(i, 0, 0));
		}
		StorageCluster cluster = StorageCluster.of(helper.getLevel(), helper.absolutePos(first));
		int slots = cluster.container().getContainerSize();
		if (cluster.moduleCount() != StorageCluster.MAX_MODULES || slots != StorageCluster.MAX_MODULES * PER_MODULE) {
			helper.fail("a five-module row should cap at " + StorageCluster.MAX_MODULES + " modules / "
					+ (StorageCluster.MAX_MODULES * PER_MODULE) + " slots, got " + cluster.moduleCount()
					+ " / " + slots);
			return;
		}
		if (cluster.rows() != 12) {
			helper.fail("a full warehouse should be 12 rows, got " + cluster.rows());
			return;
		}
		helper.succeed();
	}

	/**
	 * An item written through the shared window lands in the module that owns that slot, and stays
	 * there — there is no master inventory and nothing migrates.
	 *
	 * <p>This is the invariant behind the whole design: it is why breaking one module cannot take a
	 * neighbour's items with it.
	 *
	 * @implements TC-STOR-003 — cluster slot N belongs to exactly one module.
	 */
	public static void itemsStayInTheirOwnModule(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 2, 1);
		place(helper, a);
		place(helper, b);

		Container warehouse = StorageCluster.of(helper.getLevel(), helper.absolutePos(a)).container();
		// First slot of the second module: index 27 with the first module owning 0..26.
		warehouse.setItem(PER_MODULE, new ItemStack(Items.DIAMOND, 5));

		StorageModuleBlockEntity moduleA = module(helper, a);
		StorageModuleBlockEntity moduleB = module(helper, b);
		if (moduleA == null || moduleB == null) {
			helper.fail("a module block entity went missing");
			return;
		}
		if (!moduleA.isEmpty()) {
			helper.fail("the first module received an item written to the second module's slot range");
			return;
		}
		ItemStack stored = moduleB.getItem(0);
		if (!stored.is(Items.DIAMOND) || stored.getCount() != 5) {
			helper.fail("the second module should hold 5 diamonds in its first slot, got " + stored);
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking a module in the middle of a row splits the warehouse and leaves the neighbours' items
	 * exactly where they were.
	 *
	 * <p>Checks the state of the surviving modules rather than counting dropped entities: a drop count
	 * needs an AABB, and a generous one picks up a neighbour's drops and turns a real failure into a
	 * pass. What matters here is that nothing moved and nothing vanished.
	 *
	 * @implements TC-STOR-004 — breaking a middle module splits the cluster without touching its neighbours.
	 */
	public static void breakingMiddleModuleSplitsWithoutLoss(GameTestHelper helper) {
		BlockPos left = new BlockPos(1, 2, 1);
		BlockPos mid = new BlockPos(2, 2, 1);
		BlockPos right = new BlockPos(3, 2, 1);
		place(helper, left);
		place(helper, mid);
		place(helper, right);

		Container warehouse = StorageCluster.of(helper.getLevel(), helper.absolutePos(left)).container();
		if (warehouse.getContainerSize() != 3 * PER_MODULE) {
			helper.fail("three modules in a row should give " + (3 * PER_MODULE) + " slots, got "
					+ warehouse.getContainerSize());
			return;
		}
		warehouse.setItem(0, new ItemStack(Items.DIAMOND, 3));                 // left module
		warehouse.setItem(2 * PER_MODULE, new ItemStack(Items.EMERALD, 7));    // right module

		helper.destroyBlock(mid);

		StorageModuleBlockEntity leftModule = module(helper, left);
		StorageModuleBlockEntity rightModule = module(helper, right);
		if (leftModule == null || rightModule == null) {
			helper.fail("breaking the middle module removed a neighbour as well");
			return;
		}
		if (!leftModule.getItem(0).is(Items.DIAMOND) || leftModule.getItem(0).getCount() != 3) {
			helper.fail("the left module lost its diamonds: " + leftModule.getItem(0));
			return;
		}
		if (!rightModule.getItem(0).is(Items.EMERALD) || rightModule.getItem(0).getCount() != 7) {
			helper.fail("the right module lost its emeralds: " + rightModule.getItem(0));
			return;
		}
		int leftCount = StorageCluster.of(helper.getLevel(), helper.absolutePos(left)).moduleCount();
		int rightCount = StorageCluster.of(helper.getLevel(), helper.absolutePos(right)).moduleCount();
		if (leftCount != 1 || rightCount != 1) {
			helper.fail("the row should have split into two one-module warehouses, got " + leftCount
					+ " and " + rightCount);
			return;
		}
		helper.succeed();
	}

	/**
	 * Every face of a module publishes an item port, so pipes and other mods' transport can see it.
	 *
	 * <p>Worth its own test because the two loaders disagree by construction: Fabric wraps any
	 * {@code Container} through a global fallback and shows the module for free, while NeoForge binds
	 * the capability per block entity — so a module missing from that list is invisible to the pipe on
	 * one loader and works fine on the other. Exactly the gap MOD-193 had to clean up before.
	 *
	 * @implements TC-STOR-005 — the module exposes an item port on both loaders.
	 */
	public static void moduleExposesItemPortOnEveryFace(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 2, 1);
		place(helper, pos);
		BlockPos world = helper.absolutePos(pos);

		for (Direction side : Direction.values()) {
			ItemPort port = ItemLookup.get().find(helper.getLevel(), world, side);
			if (port == null) {
				helper.fail("no item port on the " + side + " face — a pipe cannot fill or empty the "
						+ "warehouse from there on this loader");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The seam a module draws toward a neighbour is only drawn when the two really are one warehouse.
	 *
	 * <p>This is the honesty check for the connected textures. A seam says "these blocks are one
	 * cabinet"; the warehouse caps at {@link StorageCluster#MAX_MODULES}, so the claim has to be
	 * withdrawn the moment the group outgrows the cap — and withdrawn <b>everywhere in the group</b>,
	 * not only next to the module that was added. The far end of a row of four is three blocks away
	 * from the fifth module, well outside the range {@code updateShape} reaches, so this is also the
	 * regression guard for the hand-rolled refresh walk.
	 *
	 * <p>"One warehouse" is not asserted against a member list but against what the player actually
	 * gets: each module is given a different item, and the window opened on one module is checked for
	 * the items of the others. In the five-module row the windows opened at the two ends hold
	 * different sets — which is precisely why no seam in it may be drawn.
	 *
	 * @implements TC-STOR-006 — seams appear only while the whole group is one warehouse.
	 */
	public static void seamsMatchClusterMembership(GameTestHelper helper) {
		BlockPos first = new BlockPos(1, 2, 1);
		Item[] marks = {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT, Items.REDSTONE};
		for (int i = 0; i < 4; i++) {
			place(helper, first.offset(i, 0, 0));
			mark(helper, first.offset(i, 0, 0), marks[i]);
		}

		// Four in a row: one warehouse, and every window shows all four marks.
		for (int i = 0; i < 4; i++) {
			if (!windowHolds(helper, first.offset(i, 0, 0), marks[0], marks[1], marks[2], marks[3])) {
				helper.fail("the window opened on module " + i + " does not show all four modules");
				return;
			}
		}
		for (int i = 0; i < 4; i++) {
			BlockPos pos = first.offset(i, 0, 0);
			if (i < 3 && !seam(helper, pos, Direction.EAST)) {
				helper.fail("module " + i + " draws no seam toward module " + (i + 1)
						+ ", yet they are one warehouse");
				return;
			}
			if (i > 0 && !seam(helper, pos, Direction.WEST)) {
				helper.fail("module " + i + " draws no seam toward module " + (i - 1)
						+ ", yet they are one warehouse");
				return;
			}
			for (Direction dir : Direction.values()) {
				boolean inRow = (dir == Direction.EAST && i < 3) || (dir == Direction.WEST && i > 0);
				if (!inRow && seam(helper, pos, dir)) {
					helper.fail("module " + i + " draws a seam " + dir + " into thin air — the outer "
							+ "border of the warehouse must stay framed");
					return;
				}
			}
		}

		// A fifth module: the group no longer resolves to one warehouse from every member, so no pair
		// in it may claim to be joined — including the pair three blocks from the change.
		BlockPos fifth = first.offset(4, 0, 0);
		place(helper, fifth);
		mark(helper, fifth, marks[4]);
		if (windowHolds(helper, first, marks[4]) || windowHolds(helper, fifth, marks[0])) {
			helper.fail("the five-module row is supposed to be ambiguous — the windows at its two ends "
					+ "must not agree on the members");
			return;
		}
		for (int i = 0; i < 5; i++) {
			for (Direction dir : Direction.values()) {
				if (seam(helper, first.offset(i, 0, 0), dir)) {
					helper.fail("module " + i + " still draws a seam " + dir + " after the fifth module "
							+ "broke the group up — the blocks are claiming a warehouse that no longer exists");
					return;
				}
			}
		}

		// Take the fifth away and the row is a warehouse again, seams and all.
		helper.destroyBlock(fifth);
		for (int i = 0; i < 4; i++) {
			BlockPos pos = first.offset(i, 0, 0);
			if ((i < 3 && !seam(helper, pos, Direction.EAST)) || (i > 0 && !seam(helper, pos, Direction.WEST))) {
				helper.fail("module " + i + " did not get its seams back after the fifth module was broken");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * A wall joins in both axes, and a module touching only at a corner never joins.
	 *
	 * <p>The vertical half matters on its own: the up/down seams are drawn by different models than
	 * the horizontal ones, and a 2x2 wall is the shape that uses all four at once. The diagonal half
	 * repeats TC-STOR-001's rule at the level of the visuals — two warehouses butted corner to corner
	 * must keep their own frames, or the wall would look like one cabinet the game does not treat as
	 * one.
	 *
	 * @implements TC-STOR-007 — seams follow the wall in both axes and never cross a diagonal.
	 */
	public static void seamsJoinAWallAndNeverADiagonal(GameTestHelper helper) {
		BlockPos low = new BlockPos(1, 2, 1);
		BlockPos lowEast = low.east();
		BlockPos high = low.above();
		BlockPos highEast = lowEast.above();
		for (BlockPos pos : new BlockPos[] {low, lowEast, high, highEast}) {
			place(helper, pos);
		}

		BlockPos[][] joins = {
			{low, lowEast}, {lowEast, low}, {high, highEast}, {highEast, high},
			{low, high}, {high, low}, {lowEast, highEast}, {highEast, lowEast},
		};
		for (BlockPos[] pair : joins) {
			Direction dir = towards(pair[0], pair[1]);
			if (!seam(helper, pair[0], dir)) {
				helper.fail("the 2x2 wall is one warehouse but " + pair[0] + " draws no seam " + dir);
				return;
			}
		}

		// One module diagonally off the wall: it is a warehouse of its own and must look like one —
		// and it must not count against the wall's group either, or the wall would lose its seams.
		BlockPos diagonal = highEast.above().east();
		place(helper, diagonal);
		if (StorageCluster.of(helper.getLevel(), helper.absolutePos(diagonal)).moduleCount() != 1) {
			helper.fail("the corner-touching module joined the wall's warehouse");
			return;
		}
		for (Direction dir : Direction.values()) {
			if (seam(helper, diagonal, dir)) {
				helper.fail("a module touching the wall only at a corner drew a seam " + dir);
				return;
			}
		}
		if (seam(helper, highEast, Direction.UP) || seam(helper, highEast, Direction.EAST)) {
			helper.fail("the wall drew a seam toward the corner-touching module");
			return;
		}
		for (BlockPos[] pair : joins) {
			Direction dir = towards(pair[0], pair[1]);
			if (!seam(helper, pair[0], dir)) {
				helper.fail("the wall lost its seam " + dir + " at " + pair[0] + " when a module was "
						+ "placed diagonally off it — a corner neighbour is a different warehouse and "
						+ "must not count toward the cap");
				return;
			}
		}
		helper.succeed();
	}

	/** The seam flag {@code pos} carries toward {@code dir} — the blockstate the client renders from. */
	private static boolean seam(GameTestHelper helper, BlockPos pos, Direction dir) {
		BlockState state = helper.getBlockState(pos);
		return state.hasProperty(PipeBlock.PROPERTY_BY_DIRECTION.get(dir))
				&& state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir));
	}

	/** Put one telling item into a module's first slot so windows can be told apart by content. */
	/**
	 * A four-module warehouse opens a window of six rows, not twelve, and scrolling moves the window
	 * over the other six.
	 *
	 * <p>The size is the point: twelve rows is a 328 px panel and the game gives a window no more than
	 * about 270 px of height at the GUI scale it picks by itself, so the player saw the top row cut off
	 * and the hotbar clipped. The window is capped and the rest is scrolled.
	 *
	 * <p>The scroll half is asserted through the SLOTS, not through the view object: what the player
	 * sees is what the menu's slots answer, and a view that slides while the slots keep pointing at the
	 * old indices would pass any check written against the view alone. Row 6 of the warehouse — slot 54
	 * — has to become the menu's first slot after scrolling six rows down.
	 *
	 * @implements TC-STOR-008 — the window shows six rows and scrolls over the rest.
	 */
	public static void windowShowsSixRowsAndScrolls(GameTestHelper helper) {
		BlockPos first = new BlockPos(1, 2, 1);
		for (int i = 0; i < StorageCluster.MAX_MODULES; i++) {
			place(helper, first.offset(i, 0, 0));
		}
		StorageCluster cluster = StorageCluster.of(helper.getLevel(), helper.absolutePos(first));
		Container warehouse = cluster.container();
		// A different item per row, so "which row is on screen" is answerable from one slot.
		Item[] rowMarks = {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT,
				Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ,
				Items.COPPER_INGOT, Items.AMETHYST_SHARD, Items.FLINT, Items.CLAY_BALL};
		for (int row = 0; row < rowMarks.length; row++) {
			warehouse.setItem(row * 9, new ItemStack(rowMarks[row], 1));
		}

		if (cluster.rows() != 12 || cluster.visibleRows() != StorageCluster.MAX_VISIBLE_ROWS) {
			helper.fail("a full warehouse is 12 rows shown " + StorageCluster.MAX_VISIBLE_ROWS
					+ " at a time, got " + cluster.rows() + " / " + cluster.visibleRows());
			return;
		}

		Inventory inventory = helper.makeMockPlayer(GameType.SURVIVAL).getInventory();
		AbstractContainerMenu opened = cluster.createMenu(1, inventory);
		if (!(opened instanceof StorageModuleMenu menu)) {
			helper.fail("a warehouse must open a StorageModuleMenu, got " + opened.getClass().getSimpleName());
			return;
		}
		if (menu.getRows() != StorageCluster.MAX_VISIBLE_ROWS) {
			helper.fail("the window must show " + StorageCluster.MAX_VISIBLE_ROWS + " rows, got "
					+ menu.getRows());
			return;
		}
		if (menu.getTotalRows() != 12 || menu.getMaxTopRow() != 6 || !menu.isScrollable()) {
			helper.fail("a 12-row warehouse in a 6-row window scrolls 6 rows, got total="
					+ menu.getTotalRows() + " max=" + menu.getMaxTopRow());
			return;
		}
		if (!menu.getSlot(0).getItem().is(rowMarks[0])) {
			helper.fail("unscrolled, the window's first slot is the warehouse's first slot");
			return;
		}

		menu.clickMenuButton(inventory.player, 6);
		if (menu.getTopRow() != 6) {
			helper.fail("scrolling to row 6 should stick, got " + menu.getTopRow());
			return;
		}
		if (!menu.getSlot(0).getItem().is(rowMarks[6])) {
			helper.fail("after scrolling six rows the window's first slot must be warehouse row 6 ("
					+ rowMarks[6] + "), got " + menu.getSlot(0).getItem());
			return;
		}
		if (!menu.getSlot(45).getItem().is(rowMarks[11])) {
			helper.fail("the scrolled window's last row must be the warehouse's last row");
			return;
		}

		// A packet asking for a row past the end must be clamped, not obeyed: obeying it would read
		// past the warehouse and hand the player slots that belong to nothing.
		menu.clickMenuButton(inventory.player, 9999);
		if (menu.getTopRow() != 6) {
			helper.fail("a scroll past the end must clamp to row 6, got " + menu.getTopRow());
			return;
		}
		menu.clickMenuButton(inventory.player, -5);
		if (menu.getTopRow() != 0) {
			helper.fail("a negative scroll must clamp to row 0, got " + menu.getTopRow());
			return;
		}
		helper.succeed();
	}

	/**
	 * Shift-clicking into a scrolled warehouse fills the rows that are off screen too.
	 *
	 * <p>This is the trap the sliding window sets. A menu only has slots for the rows in view, and
	 * vanilla's shift-click moves between SLOTS — so the obvious implementation quietly stops after
	 * six rows and tells the player the warehouse is full while half of it is empty. The test fills
	 * every visible row to the brim first, so the only place the stack can go is behind the window.
	 *
	 * @implements TC-STOR-009 — shift-click reaches the rows the window is not showing.
	 */
	public static void shiftClickReachesScrolledAwayRows(GameTestHelper helper) {
		BlockPos first = new BlockPos(1, 2, 1);
		for (int i = 0; i < StorageCluster.MAX_MODULES; i++) {
			place(helper, first.offset(i, 0, 0));
		}
		StorageCluster cluster = StorageCluster.of(helper.getLevel(), helper.absolutePos(first));
		Container warehouse = cluster.container();
		int visible = StorageCluster.MAX_VISIBLE_ROWS * 9;
		// Every visible slot occupied by something else, so nothing can merge into them either.
		for (int slot = 0; slot < visible; slot++) {
			warehouse.setItem(slot, new ItemStack(Items.STONE, 64));
		}

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Inventory inventory = player.getInventory();
		AbstractContainerMenu opened = cluster.createMenu(1, inventory);
		if (!(opened instanceof StorageModuleMenu menu)) {
			helper.fail("a warehouse must open a StorageModuleMenu");
			return;
		}
		// First slot of the player's own inventory, i.e. the first index past the window's own rows.
		int playerSlot = menu.getRows() * 9;
		menu.getSlot(playerSlot).set(new ItemStack(Items.DIAMOND, 17));

		menu.quickMoveStack(player, playerSlot);

		if (!menu.getSlot(playerSlot).getItem().isEmpty()) {
			helper.fail("the whole stack should have moved, "
					+ menu.getSlot(playerSlot).getItem().getCount() + " left behind — a shift-click "
					+ "that only sees the visible rows reports a full warehouse while it is half empty");
			return;
		}
		int stored = 0;
		for (int slot = visible; slot < warehouse.getContainerSize(); slot++) {
			ItemStack stack = warehouse.getItem(slot);
			if (stack.is(Items.DIAMOND)) {
				stored += stack.getCount();
			}
		}
		if (stored != 17) {
			helper.fail("17 diamonds should have landed in the rows behind the window, found " + stored);
			return;
		}
		helper.succeed();
	}

	/**
	 * Taking the warehouse apart module by module walks the capacity down to zero and never past it.
	 *
	 * <p>A negative capacity is not a cosmetic slip: it is the crash
	 * <a href="https://github.com/AllTheMods/ATM-10/issues/2271">Functional Storage</a> shipped, where a
	 * count that went below zero was later used as an array size. Here the number that could go wrong is
	 * the size of the container the menu is built over, so a negative would surface as a thrown
	 * exception the first time a player opened what is left of the warehouse.
	 *
	 * <p>The expected sizes are written out as literals — 108, 81, 54, 27, 0 — rather than as
	 * {@code moduleCount() * CONTAINER_SIZE}. Derived from the same numbers the code uses, the check
	 * would agree with any capacity at all, including a wrong one, and would still pass with the
	 * subtraction broken as long as it was broken consistently.
	 *
	 * <p>Modules are taken from the far end of the row inward, so the walk always has its starting
	 * module until the very last step — a break in the middle would split the row rather than shorten
	 * it, and this test is about the count going down, not about splitting (TC-STOR-004 covers that).
	 * The last step is the interesting one: the starting module itself is gone, which is what a
	 * player's open window sees when the block under it is mined. That is the case that produces "no
	 * modules" — and it must read as an empty warehouse, not as a negative one.
	 *
	 * @implements TC-STOR-010 — capacity falls to zero as modules are removed and never goes negative.
	 */
	public static void capacityNeverGoesNegative(GameTestHelper helper) {
		BlockPos first = new BlockPos(1, 2, 1);
		for (int i = 0; i < StorageCluster.MAX_MODULES; i++) {
			place(helper, first.offset(i, 0, 0));
		}
		int[] expected = {108, 81, 54, 27, 0};
		for (int broken = 0; broken < expected.length; broken++) {
			// Always ask from the FIRST position — on the last pass that position is empty.
			StorageCluster cluster = StorageCluster.of(helper.getLevel(), helper.absolutePos(first));
			int slots = cluster.container().getContainerSize();
			if (slots < 0 || cluster.moduleCount() < 0 || cluster.rows() < 0) {
				helper.fail("after breaking " + broken + " modules the warehouse reports a NEGATIVE size: "
						+ slots + " slots / " + cluster.moduleCount() + " modules / " + cluster.rows()
						+ " rows — the count is used as a container size and a menu built over it throws");
				return;
			}
			if (slots != expected[broken]) {
				helper.fail("after breaking " + broken + " modules the warehouse should hold "
						+ expected[broken] + " slots, got " + slots);
				return;
			}
			// Reading past the end must be answered, not thrown: the menu keeps its slot objects for a
			// tick after the block under them is gone.
			if (!cluster.container().getItem(slots).isEmpty()
					|| !cluster.container().getItem(-1).isEmpty()) {
				helper.fail("a slot outside the shrunken warehouse must read as empty, not as an item");
				return;
			}
			if (broken < StorageCluster.MAX_MODULES) {
				helper.destroyBlock(first.offset(StorageCluster.MAX_MODULES - 1 - broken, 0, 0));
			}
		}
		helper.succeed();
	}

	/**
	 * A position the level reports as not loaded is left out of the walk, and asking about it does not
	 * drag its chunk into memory.
	 *
	 * <p><b>What this does and does not simulate.</b> It does <em>not</em> put a module in an unloaded
	 * chunk and then unload it: a gametest rig is held loaded for as long as the test runs, and there
	 * is no way from inside one to unload the ground it is standing on. What it does instead is
	 * exercise the seam itself — {@link StorageCluster} skips a position when
	 * {@code level.isLoaded(pos)} is false — by walking from a position four thousand blocks away that
	 * the level genuinely reports as not loaded. Read it as a guard on the rule, not as a reproduction
	 * of a player unloading a chunk.
	 *
	 * <p>It is nonetheless a real check rather than a restatement, because of what happens when the
	 * guard is deleted. Without the {@code isLoaded} test the walk falls straight into
	 * {@code level.getBlockEntity(pos)}, and on a server level that loads — and if need be generates —
	 * the chunk. So the assertion that the chunk is <em>still</em> absent afterwards goes red the moment
	 * the guard goes away. That is the criterion's "no force-load every tick" half, stated as something
	 * the test can see.
	 *
	 * <p>The loss/dupe half is checked against the loaded part of the warehouse: its contents are
	 * counted before and after the absent walk. A warehouse that lost or grew items because part of it
	 * was out of reach would show up here.
	 *
	 * @implements TC-STOR-011 — an unloaded position is skipped, not force-loaded, and costs no items.
	 */
	public static void unloadedPositionIsSkippedNotForceLoaded(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 2, 1);
		place(helper, a);
		place(helper, b);
		Container warehouse = StorageCluster.of(helper.getLevel(), helper.absolutePos(a)).container();
		warehouse.setItem(0, new ItemStack(Items.DIAMOND, 9));
		warehouse.setItem(PER_MODULE, new ItemStack(Items.EMERALD, 5));
		int before = totalItems(warehouse);

		ServerLevel level = helper.getLevel();
		// Far enough that no ticket keeps it around, near enough that a force-load (the failure this
		// guards) does not cost a world's worth of generation.
		BlockPos away = helper.absolutePos(a).offset(4096, 0, 4096);
		if (level.isLoaded(away)) {
			helper.fail("the rig picked a position that is already loaded (" + away + ") — the test "
					+ "cannot say anything about unloaded chunks from there");
			return;
		}

		StorageCluster absent = StorageCluster.of(level, away);
		if (absent.moduleCount() != 0 || absent.container().getContainerSize() != 0) {
			helper.fail("a warehouse walked from an unloaded position must come back empty, got "
					+ absent.moduleCount() + " modules / " + absent.container().getContainerSize()
					+ " slots");
			return;
		}
		if (level.isLoaded(away)) {
			helper.fail("walking a warehouse from an unloaded position LOADED the chunk at " + away
					+ " — the walk must skip what is away, not pull it in, or a player could hold "
					+ "chunks alive by leaving a window open");
			return;
		}

		// The loaded half of the warehouse is untouched by all of that: same items, same places.
		StorageCluster still = StorageCluster.of(level, helper.absolutePos(a));
		if (still.moduleCount() != 2) {
			helper.fail("the loaded modules must still be one warehouse, got " + still.moduleCount());
			return;
		}
		int after = totalItems(still.container());
		if (after != before) {
			helper.fail("the warehouse held " + before + " items before the absent walk and " + after
					+ " after — items were " + (after < before ? "lost" : "duplicated"));
			return;
		}
		if (still.container().getItem(0).getCount() != 9
				|| still.container().getItem(PER_MODULE).getCount() != 5) {
			helper.fail("the items moved between modules across the absent walk");
			return;
		}
		helper.succeed();
	}

	/**
	 * A pipe fills the warehouse from outside and empties it again, and a dry run of the same move
	 * reports exactly what the real one does.
	 *
	 * <p>Until now the only thing asserted about pipes and the warehouse was that a port could be
	 * <em>found</em> on each face. That is a long way from carrying an item: a port that answers the
	 * lookup and then moves nothing is precisely the shape of the bug, and the module is written
	 * against {@code Container} while the pipes speak each loader's own transfer API, so the two ends
	 * really can disagree.
	 *
	 * <p>The {@code simulate}/{@code execute} half is the dupe guard named in the criterion. A dry run
	 * that promises more than the commit delivers is
	 * <a href="https://github.com/McJtyMods/XNet/issues/173">XNet #173</a> — the caller books the
	 * promised amount, the commit moves less, and the difference is created out of nothing. So the
	 * simulation is asserted to move <em>nothing</em> in the world and to return the same number the
	 * committed move then really transfers.
	 *
	 * @implements TC-STOR-012 — pipes fill and drain the warehouse; simulate and execute agree.
	 */
	public static void pipeFillsAndDrainsTheWarehouse(GameTestHelper helper) {
		BlockPos chestPos = new BlockPos(1, 2, 1);
		BlockPos pipeA = new BlockPos(2, 2, 1);
		BlockPos pipeB = new BlockPos(3, 2, 1);
		BlockPos modulePos = new BlockPos(4, 2, 1);
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		helper.setBlock(pipeA, ModContent.ITEM_PIPE.get());
		helper.setBlock(pipeB, ModContent.ITEM_PIPE.get());
		place(helper, modulePos);

		Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
		StorageModuleBlockEntity store = module(helper, modulePos);
		ItemPipeBlockEntity west = (ItemPipeBlockEntity) helper.getLevel()
				.getBlockEntity(helper.absolutePos(pipeA));
		ItemPipeBlockEntity east = (ItemPipeBlockEntity) helper.getLevel()
				.getBlockEntity(helper.absolutePos(pipeB));
		if (chest == null || store == null || west == null || east == null) {
			helper.fail("the pipe rig is missing a block entity");
			return;
		}

		// -- simulate must not move anything, and must promise what the commit delivers --------------
		ItemPort from = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(chestPos), Direction.EAST);
		ItemPort into = ItemLookup.get().find(helper.getLevel(), helper.absolutePos(modulePos), Direction.WEST);
		if (from == null || into == null) {
			helper.fail("the chest or the warehouse module exposes no item port on this loader");
			return;
		}
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
		int promised = EnergyTransactions.get().simulate(txn -> from.moveTo(into, 7, txn));
		if (chest.getItem(0).getCount() != 16 || !store.isEmpty()) {
			helper.fail("a dry run moved real items: chest=" + chest.getItem(0).getCount()
					+ " warehouse empty=" + store.isEmpty());
			return;
		}
		int delivered = ItemMover.move(from, into, 7);
		if (delivered != promised) {
			helper.fail("the dry run promised " + promised + " items and the real move carried "
					+ delivered + " — the caller books the promise, so the difference is items made "
					+ "from nothing (XNet #173)");
			return;
		}
		if (chest.getItem(0).getCount() != 16 - delivered || count(store, Items.IRON_INGOT) != delivered) {
			helper.fail("the committed move did not conserve items: chest=" + chest.getItem(0).getCount()
					+ " warehouse=" + count(store, Items.IRON_INGOT) + " moved=" + delivered);
			return;
		}

		// -- the pipe itself: chest -> warehouse, over the real network ------------------------------
		store.clearContent();
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
		west.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);  // touches the chest
		east.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);   // touches the module
		west.serverTick(helper.getLevel(), west.getBlockPos(), west.getBlockState());
		east.serverTick(helper.getLevel(), east.getBlockPos(), east.getBlockState());
		for (int i = 0; i < 400; i++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		int inWarehouse = count(store, Items.IRON_INGOT);
		int inChest = count(chest, Items.IRON_INGOT);
		if (inWarehouse != 16 || inChest != 0) {
			helper.fail("the pipe should have carried all sixteen ingots into the warehouse; warehouse="
					+ inWarehouse + " chest=" + inChest);
			return;
		}

		// -- and back out again: warehouse -> chest --------------------------------------------------
		west.setFaceMode(Direction.WEST, PipeFaceMode.INSERT);   // now pushes into the chest
		east.setFaceMode(Direction.EAST, PipeFaceMode.EXTRACT);  // now pulls from the module
		west.serverTick(helper.getLevel(), west.getBlockPos(), west.getBlockState());
		east.serverTick(helper.getLevel(), east.getBlockPos(), east.getBlockState());
		for (int i = 0; i < 400; i++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		int backInChest = count(chest, Items.IRON_INGOT);
		int leftInWarehouse = count(store, Items.IRON_INGOT);
		if (backInChest != 16 || leftInWarehouse != 0) {
			helper.fail("the pipe should have pulled all sixteen ingots back out of the warehouse; chest="
					+ backInChest + " warehouse=" + leftInWarehouse
					+ " — a total other than 16 means the round trip lost or duplicated items");
			return;
		}
		helper.succeed();
	}

	/**
	 * A warehouse module's contents survive a save and reload.
	 *
	 * <p>The right API was in place from the start — {@code saveAdditional(ValueOutput)} /
	 * {@code loadAdditional(ValueInput)} — but nothing exercised it, so "it persists" rested on reading
	 * the code. It does not any more.
	 *
	 * <p>Slots are filled at both ends of the range and one is deliberately left empty between them.
	 * That is the failure the mod has already met once: a helper that skips empty stacks writes a list
	 * that comes back shifted, and a warehouse whose items all slid one slot toward the front looks
	 * fine until the player counts them.
	 *
	 * <p>Like the mod's other persistence cases this is the NBT round-trip — the same path a chunk
	 * save and reload takes — not a server restart, which a gametest cannot perform.
	 *
	 * @implements TC-STOR-013 — module contents round-trip through NBT, slot positions included.
	 */
	public static void moduleContentsSurviveReload(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 2, 1);
		place(helper, pos);
		StorageModuleBlockEntity src = module(helper, pos);
		if (src == null) {
			helper.fail("the module block entity is missing");
			return;
		}
		src.setItem(0, new ItemStack(Items.DIAMOND, 13));
		// slot 1 deliberately left empty — a shifted list would fill it
		src.setItem(2, new ItemStack(Items.EMERALD, 4));
		src.setItem(StorageModuleBlockEntity.CONTAINER_SIZE - 1, new ItemStack(Items.GOLD_INGOT, 64));

		BlockPos abs = helper.absolutePos(pos);
		RegistryAccess registries = helper.getLevel().registryAccess();
		CompoundTag tag = src.saveCustomOnly(registries);
		StorageModuleBlockEntity restored =
				new StorageModuleBlockEntity(abs, helper.getLevel().getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (!restored.getItem(0).is(Items.DIAMOND) || restored.getItem(0).getCount() != 13) {
			helper.fail("slot 0 did not survive the reload: " + restored.getItem(0));
			return;
		}
		if (!restored.getItem(1).isEmpty()) {
			helper.fail("slot 1 was empty before the reload and holds " + restored.getItem(1)
					+ " after — the contents came back shifted, which is what happens when the empty "
					+ "stacks are skipped on the way out");
			return;
		}
		if (!restored.getItem(2).is(Items.EMERALD) || restored.getItem(2).getCount() != 4) {
			helper.fail("slot 2 did not survive the reload: " + restored.getItem(2));
			return;
		}
		ItemStack last = restored.getItem(StorageModuleBlockEntity.CONTAINER_SIZE - 1);
		if (!last.is(Items.GOLD_INGOT) || last.getCount() != 64) {
			helper.fail("the last slot did not survive the reload: " + last);
			return;
		}
		if (restored.getContainerSize() != StorageModuleBlockEntity.CONTAINER_SIZE) {
			helper.fail("the reloaded module has " + restored.getContainerSize() + " slots");
			return;
		}
		helper.succeed();
	}

	/** How many items in total {@code container} holds, of any kind. */
	private static int totalItems(Container container) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			total += container.getItem(i).getCount();
		}
		return total;
	}

	/** How many of {@code item} {@code container} holds. */
	private static int count(Container container, Item item) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void mark(GameTestHelper helper, BlockPos pos, Item item) {
		StorageModuleBlockEntity module = module(helper, pos);
		if (module != null) {
			module.setItem(0, new ItemStack(item, 1));
		}
	}

	/** Whether the window opened on {@code pos} shows every one of {@code items}. */
	private static boolean windowHolds(GameTestHelper helper, BlockPos pos, Item... items) {
		Container window = StorageCluster.of(helper.getLevel(), helper.absolutePos(pos)).container();
		for (Item item : items) {
			boolean found = false;
			for (int slot = 0; slot < window.getContainerSize() && !found; slot++) {
				found = window.getItem(slot).is(item);
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private static Direction towards(BlockPos from, BlockPos to) {
		for (Direction dir : Direction.values()) {
			if (from.relative(dir).equals(to)) {
				return dir;
			}
		}
		throw new IllegalArgumentException(from + " and " + to + " are not face-adjacent");
	}

	private static void place(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModContent.STORAGE_MODULE.get().defaultBlockState());
	}

	private static StorageModuleBlockEntity module(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
		return be instanceof StorageModuleBlockEntity m ? m : null;
	}
}

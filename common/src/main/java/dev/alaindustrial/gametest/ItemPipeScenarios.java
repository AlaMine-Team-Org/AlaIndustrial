package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Cross-loader MOD-104 item-pipe scenarios. They deliberately use the mod's iron chests: the
 * source and destination therefore cross the real Fabric ItemStorage / NeoForge Item capability
 * seam while the pipe graph and face policy remain loader-neutral.
 */
public final class ItemPipeScenarios {
	private ItemPipeScenarios() { }

	private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
	private static final BlockPos PIPE_A = new BlockPos(2, 2, 1);
	private static final BlockPos PIPE_B = new BlockPos(3, 2, 1);
	private static final BlockPos TARGET = new BlockPos(4, 2, 1);

	private static BlockEntity be(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(relative));
	}

	private static ItemPipeBlockEntity pipe(GameTestHelper helper, BlockPos relative) {
		BlockEntity entity = be(helper, relative);
		return entity instanceof ItemPipeBlockEntity pipe ? pipe : null;
	}

	private static Container container(GameTestHelper helper, BlockPos relative) {
		BlockEntity entity = be(helper, relative);
		return entity instanceof Container container ? container : null;
	}

	private static boolean build(GameTestHelper helper) {
		helper.setBlock(SOURCE, ModContent.IRON_CHEST.get());
		helper.setBlock(PIPE_A, ModContent.ITEM_PIPE.get());
		helper.setBlock(PIPE_B, ModContent.ITEM_PIPE.get());
		helper.setBlock(TARGET, ModContent.IRON_CHEST.get());
		Container source = container(helper, SOURCE);
		ItemPipeBlockEntity a = pipe(helper, PIPE_A);
		ItemPipeBlockEntity b = pipe(helper, PIPE_B);
		Container target = container(helper, TARGET);
		if (source == null || a == null || b == null || target == null) {
			helper.fail("MOD-104 test rig block entity missing");
			return false;
		}
		source.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
		a.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		b.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		a.serverTick(helper.getLevel(), a.getBlockPos(), a.getBlockState());
		b.serverTick(helper.getLevel(), b.getBlockPos(), b.getBlockState());
		return true;
	}

	/** MOD-104: EXTRACT -> pipe graph -> INSERT preserves the exact transferred stack. */
	public static void transfersBetweenChests(GameTestHelper helper) {
		if (!build(helper)) return;
		ItemNetworkManager.tickAll(helper.getLevel());
		Container source = container(helper, SOURCE);
		Container target = container(helper, TARGET);
		int expected = Math.min(16, Config.itemPipeItemsPerTransfer);
		int sourceCount = source == null ? -1 : source.getItem(0).getCount();
		int targetCount = target == null ? -1 : target.getItem(0).getCount();
		if (sourceCount != 16 - expected || targetCount != expected) {
			helper.fail("MOD-104 transfer expected " + expected + " iron ingots; source=" + sourceCount
					+ " target=" + targetCount);
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-115 repro: a pipe UNDER a furnace (EXTRACT on its up-face) pulls the smelted RESULT out of the
	 * furnace's bottom (DOWN) face into a chest. This is the player-reported "pipe won't take from the
	 * furnace" case. Covers the WorldlyContainer sided path that chest↔chest and insert-into-machine
	 * never exercise. Runs for both the vanilla furnace and our iron furnace.
	 */
	public static void extractsFurnaceResultFromBottom(GameTestHelper helper, Block furnaceBlock, String label) {
		BlockPos furnacePos = new BlockPos(1, 3, 1);
		BlockPos under = new BlockPos(1, 2, 1);
		BlockPos mid = new BlockPos(2, 2, 1);
		BlockPos chestPos = new BlockPos(3, 2, 1);
		helper.setBlock(furnacePos, furnaceBlock);
		helper.setBlock(under, ModContent.ITEM_PIPE.get());
		helper.setBlock(mid, ModContent.ITEM_PIPE.get());
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		Container furnace = container(helper, furnacePos);
		ItemPipeBlockEntity underPipe = pipe(helper, under);
		ItemPipeBlockEntity midPipe = pipe(helper, mid);
		Container chest = container(helper, chestPos);
		if (furnace == null || underPipe == null || midPipe == null || chest == null) {
			helper.fail(label + ": rig block entity missing");
			return;
		}
		// Smelted result sits in the furnace RESULT slot (index 2 on every AbstractFurnace + iron furnace).
		furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 16));
		underPipe.setFaceMode(Direction.UP, PipeFaceMode.EXTRACT);   // up-face touches the furnace bottom
		midPipe.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);    // east-face touches the chest
		underPipe.serverTick(helper.getLevel(), underPipe.getBlockPos(), underPipe.getBlockState());
		midPipe.serverTick(helper.getLevel(), midPipe.getBlockPos(), midPipe.getBlockState());
		ItemNetworkManager.tickAll(helper.getLevel());
		int expected = Math.min(16, Config.itemPipeItemsPerTransfer);
		int delivered = chest.getItem(0).getCount();
		int remaining = furnace.getItem(2).getCount();
		if (delivered != expected || remaining != 16 - expected) {
			helper.fail(label + ": expected " + expected + " ingots pulled from the furnace bottom; chest="
					+ delivered + " furnaceResult=" + remaining);
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-115 repro: ONE chest source feeds TWO furnaces (both INSERT into their top/input face). The
	 * player report: items load into the first furnace but never the second, even though both are wired
	 * the same. Asserts the round-robin actually reaches BOTH targets. Ticks long enough for many
	 * transfer intervals. Furnaces have no fuel, so raw iron just piles up in their input slots.
	 */
	public static void distributesOneSourceToTwoFurnaces(GameTestHelper helper) {
		BlockPos chestPos = new BlockPos(1, 3, 1);
		BlockPos pipe1 = new BlockPos(2, 3, 1);
		BlockPos pipe2 = new BlockPos(3, 3, 1);
		BlockPos furnace1 = new BlockPos(2, 2, 1);
		BlockPos furnace2 = new BlockPos(3, 2, 1);
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		helper.setBlock(pipe1, ModContent.ITEM_PIPE.get());
		helper.setBlock(pipe2, ModContent.ITEM_PIPE.get());
		helper.setBlock(furnace1, net.minecraft.world.level.block.Blocks.FURNACE);
		helper.setBlock(furnace2, net.minecraft.world.level.block.Blocks.FURNACE);
		Container chest = container(helper, chestPos);
		ItemPipeBlockEntity p1 = pipe(helper, pipe1);
		ItemPipeBlockEntity p2 = pipe(helper, pipe2);
		Container f1 = container(helper, furnace1);
		Container f2 = container(helper, furnace2);
		if (chest == null || p1 == null || p2 == null || f1 == null || f2 == null) {
			helper.fail("MOD-115 two-furnace rig block entity missing");
			return;
		}
		chest.setItem(0, new ItemStack(Items.RAW_IRON, 64));
		p1.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT); // pull from the chest
		p1.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);  // push into furnace1 top (input)
		p2.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);  // push into furnace2 top (input)
		p1.serverTick(helper.getLevel(), p1.getBlockPos(), p1.getBlockState());
		p2.serverTick(helper.getLevel(), p2.getBlockPos(), p2.getBlockState());
		// Tick well past the transfer interval many times so both targets get multiple round-robin turns.
		for (int i = 0; i < 400; i++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		int in1 = f1.getItem(0).getCount();
		int in2 = f2.getItem(0).getCount();
		if (in1 <= 0 || in2 <= 0) {
			helper.fail("MOD-115 distribution: furnace1 input=" + in1 + ", furnace2 input=" + in2
					+ " — BOTH must receive items (second furnace starved = the reported bug)");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-115 repro (harder): one chest → two furnaces, but a network REBUILD is forced between transfers
	 * — exactly what happens in the player's factory when the furnaces are actively smelting and flicker
	 * their {@code lit} blockstate, which fires a neighbour update → {@code onNeighbourChanged} → rebuild.
	 * If the round-robin cursor is reset/disturbed by the rebuild, the second furnace starves. Both must
	 * still receive items.
	 */
	public static void distributesToTwoFurnacesAcrossRebuilds(GameTestHelper helper) {
		BlockPos chestPos = new BlockPos(1, 3, 1);
		BlockPos pipe1 = new BlockPos(2, 3, 1);
		BlockPos pipe2 = new BlockPos(3, 3, 1);
		BlockPos furnace1 = new BlockPos(2, 2, 1);
		BlockPos furnace2 = new BlockPos(3, 2, 1);
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		helper.setBlock(pipe1, ModContent.ITEM_PIPE.get());
		helper.setBlock(pipe2, ModContent.ITEM_PIPE.get());
		helper.setBlock(furnace1, net.minecraft.world.level.block.Blocks.FURNACE);
		helper.setBlock(furnace2, net.minecraft.world.level.block.Blocks.FURNACE);
		Container chest = container(helper, chestPos);
		ItemPipeBlockEntity p1 = pipe(helper, pipe1);
		ItemPipeBlockEntity p2 = pipe(helper, pipe2);
		Container f1 = container(helper, furnace1);
		Container f2 = container(helper, furnace2);
		if (chest == null || p1 == null || p2 == null || f1 == null || f2 == null) {
			helper.fail("MOD-115 rebuild-rig block entity missing");
			return;
		}
		chest.setItem(0, new ItemStack(Items.RAW_IRON, 64));
		p1.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		p1.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);
		p2.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);
		p1.serverTick(helper.getLevel(), p1.getBlockPos(), p1.getBlockState());
		p2.serverTick(helper.getLevel(), p2.getBlockPos(), p2.getBlockState());
		for (int i = 0; i < 400; i++) {
			// Simulate a furnace lit-flicker: a neighbour update that rebuilds the network mid-run.
			ItemNetworkManager.onNeighbourChanged(helper.getLevel(), helper.absolutePos(pipe1));
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		int in1 = f1.getItem(0).getCount();
		int in2 = f2.getItem(0).getCount();
		if (in1 <= 0 || in2 <= 0) {
			helper.fail("MOD-115 distribution across rebuilds: furnace1 input=" + in1 + ", furnace2 input="
					+ in2 + " — second furnace starved when the network rebuilds between transfers");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-115 repro (exact player case): a chest of smeltable + coal feeds TWO IRON furnaces, each via
	 * two endpoints — top (input, ore) and a side (fuel, coal). The report: the first furnace fills, the
	 * second gets nothing. Asserts BOTH iron furnaces receive ore in the input slot AND coal in the fuel
	 * slot. Furnaces are not ticked, so what the pipe delivers just accumulates.
	 */
	public static void distributesOreAndCoalToTwoIronFurnaces(GameTestHelper helper) {
		BlockPos chestPos = new BlockPos(0, 3, 1);
		// spine
		BlockPos spineA = new BlockPos(1, 3, 1); // above furnace1, WEST=extract chest, DOWN=insert f1 input
		BlockPos spineMid = new BlockPos(2, 3, 1);
		BlockPos spineB = new BlockPos(3, 3, 1); // above furnace2, DOWN=insert f2 input
		// fuel branches (drop to a side of each furnace)
		BlockPos branchA1 = new BlockPos(1, 3, 2);
		BlockPos branchA2 = new BlockPos(1, 2, 2); // NORTH=insert f1 fuel (its south side)
		BlockPos branchB1 = new BlockPos(3, 3, 2);
		BlockPos branchB2 = new BlockPos(3, 2, 2); // NORTH=insert f2 fuel
		BlockPos furnace1 = new BlockPos(1, 2, 1);
		BlockPos furnace2 = new BlockPos(3, 2, 1);
		Block ironFurnace = ModContent.IRON_FURNACE.get();
		for (BlockPos p : new BlockPos[] { spineA, spineMid, spineB, branchA1, branchA2, branchB1, branchB2 }) {
			helper.setBlock(p, ModContent.ITEM_PIPE.get());
		}
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		helper.setBlock(furnace1, ironFurnace);
		helper.setBlock(furnace2, ironFurnace);
		Container chest = container(helper, chestPos);
		Container f1 = container(helper, furnace1);
		Container f2 = container(helper, furnace2);
		ItemPipeBlockEntity sa = pipe(helper, spineA);
		ItemPipeBlockEntity sb = pipe(helper, spineB);
		ItemPipeBlockEntity ba2 = pipe(helper, branchA2);
		ItemPipeBlockEntity bb2 = pipe(helper, branchB2);
		if (chest == null || f1 == null || f2 == null || sa == null || sb == null || ba2 == null || bb2 == null) {
			helper.fail("MOD-115 ore+coal rig block entity missing");
			return;
		}
		chest.setItem(0, new ItemStack(Items.RAW_IRON, 32));
		chest.setItem(1, new ItemStack(Items.COAL, 32));
		sa.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);   // pull from chest
		sa.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);    // f1 input (top)
		sb.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);    // f2 input (top)
		ba2.setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);  // f1 fuel (south side)
		bb2.setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);  // f2 fuel (south side)
		for (BlockPos p : new BlockPos[] { spineA, spineMid, spineB, branchA1, branchA2, branchB1, branchB2 }) {
			ItemPipeBlockEntity pe = pipe(helper, p);
			if (pe != null) pe.serverTick(helper.getLevel(), pe.getBlockPos(), pe.getBlockState());
		}
		for (int i = 0; i < 800; i++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		// Iron furnace slots: 0 = input, 1 = fuel, 2 = result.
		int f1in = f1.getItem(0).getCount();
		int f1fuel = f1.getItem(1).getCount();
		int f2in = f2.getItem(0).getCount();
		int f2fuel = f2.getItem(1).getCount();
		if (f1in <= 0 || f1fuel <= 0 || f2in <= 0 || f2fuel <= 0) {
			helper.fail("MOD-115 ore+coal to two iron furnaces — f1(input=" + f1in + ",fuel=" + f1fuel
					+ ") f2(input=" + f2in + ",fuel=" + f2fuel + "); every value must be > 0 (second furnace starved = bug)");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-115 parallel-distribution guard: within a SINGLE transfer interval, one chest must feed BOTH
	 * furnaces — not one now and the other an interval later. With the old serve-one-endpoint-per-interval
	 * behaviour only one furnace would have items after {@code itemPipeTransferIntervalTicks} ticks; the
	 * parallel rewrite fills both. This is the discriminating test for the fix the player asked for.
	 */
	public static void feedsBothFurnacesWithinOneInterval(GameTestHelper helper) {
		BlockPos chestPos = new BlockPos(1, 3, 1);
		BlockPos p1 = new BlockPos(2, 3, 1);
		BlockPos p2 = new BlockPos(3, 3, 1);
		BlockPos furnace1 = new BlockPos(2, 2, 1);
		BlockPos furnace2 = new BlockPos(3, 2, 1);
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		helper.setBlock(p1, ModContent.ITEM_PIPE.get());
		helper.setBlock(p2, ModContent.ITEM_PIPE.get());
		helper.setBlock(furnace1, net.minecraft.world.level.block.Blocks.FURNACE);
		helper.setBlock(furnace2, net.minecraft.world.level.block.Blocks.FURNACE);
		Container chest = container(helper, chestPos);
		ItemPipeBlockEntity pipe1 = pipe(helper, p1);
		ItemPipeBlockEntity pipe2 = pipe(helper, p2);
		Container f1 = container(helper, furnace1);
		Container f2 = container(helper, furnace2);
		if (chest == null || pipe1 == null || pipe2 == null || f1 == null || f2 == null) {
			helper.fail("MOD-115 parallel rig block entity missing");
			return;
		}
		chest.setItem(0, new ItemStack(Items.RAW_IRON, 64));
		pipe1.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		pipe1.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);
		pipe2.setFaceMode(Direction.DOWN, PipeFaceMode.INSERT);
		pipe1.serverTick(helper.getLevel(), pipe1.getBlockPos(), pipe1.getBlockState());
		pipe2.serverTick(helper.getLevel(), pipe2.getBlockPos(), pipe2.getBlockState());
		// Exactly one interval's worth of ticks: the first tick does the whole parallel pass, the rest sit
		// on cooldown. Both furnaces must already have items — the old serial pipe would leave one empty.
		int interval = Math.max(1, Config.itemPipeTransferIntervalTicks);
		for (int i = 0; i < interval; i++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		int in1 = f1.getItem(0).getCount();
		int in2 = f2.getItem(0).getCount();
		if (in1 <= 0 || in2 <= 0) {
			helper.fail("MOD-115 parallel distribution: after one interval furnace1=" + in1 + " furnace2="
					+ in2 + " — BOTH must be fed in the same interval (parallel), not serialised");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-178 repro (player report): one EXTRACT source feeds THREE INSERT chests, where the first two (in
	 * the network's round-robin order) are already FULL of the item being pushed and only the third is free.
	 * The player saw the pipe "stop" on the full chests instead of skipping to the free one.
	 *
	 * <p>Discriminating layout: the two FULL chests are placed so they sort BEFORE the empty chest in the
	 * stable {@code (BlockPos, side)} endpoint order (both at x=2, the free one at x=3). The tick therefore
	 * visits {@code full → full → free}; a bug that let a full target abort the distribution pass would
	 * leave the free chest starving. Asserts the free chest receives exactly one batch within a single
	 * interval, and neither full chest gains or loses items.
	 */
	public static void skipsFullTargetsAndFillsFreeOne(GameTestHelper helper) {
		BlockPos pipePos = new BlockPos(2, 2, 2);
		BlockPos sourcePos = new BlockPos(1, 2, 2); // WEST  — extract
		BlockPos fullNorth = new BlockPos(2, 2, 1); // NORTH — insert, pre-filled (sorts first)
		BlockPos fullSouth = new BlockPos(2, 2, 3); // SOUTH — insert, pre-filled (sorts second)
		BlockPos freeEast = new BlockPos(3, 2, 2);  // EAST  — insert, empty (x=3 sorts last)
		helper.setBlock(sourcePos, ModContent.IRON_CHEST.get());
		helper.setBlock(pipePos, ModContent.ITEM_PIPE.get());
		helper.setBlock(fullNorth, ModContent.IRON_CHEST.get());
		helper.setBlock(fullSouth, ModContent.IRON_CHEST.get());
		helper.setBlock(freeEast, ModContent.IRON_CHEST.get());
		Container source = container(helper, sourcePos);
		ItemPipeBlockEntity pipe = pipe(helper, pipePos);
		Container full1 = container(helper, fullNorth);
		Container full2 = container(helper, fullSouth);
		Container free = container(helper, freeEast);
		if (source == null || pipe == null || full1 == null || full2 == null || free == null) {
			helper.fail("MOD-178 skip-full rig block entity missing");
			return;
		}
		source.setItem(0, new ItemStack(Items.RAW_IRON, 64));
		fillWith(full1, Items.RAW_IRON);
		fillWith(full2, Items.RAW_IRON);
		pipe.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		pipe.setFaceMode(Direction.NORTH, PipeFaceMode.INSERT);
		pipe.setFaceMode(Direction.SOUTH, PipeFaceMode.INSERT);
		pipe.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());
		// One interval: the first tick runs the whole distribution pass, the rest sit on cooldown.
		int interval = Math.max(1, Config.itemPipeTransferIntervalTicks);
		for (int i = 0; i < interval; i++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		int expected = Math.min(64, Config.itemPipeItemsPerTransfer);
		int delivered = free.getItem(0).getCount();
		int fullCapacity = full1.getContainerSize() * 64;
		int full1Count = countOf(full1, Items.RAW_IRON);
		int full2Count = countOf(full2, Items.RAW_IRON);
		if (delivered != expected) {
			helper.fail("MOD-178 free chest got " + delivered + " raw iron (expected " + expected
					+ ") — the pipe must skip the two full chests and fill the free one, not stop");
			return;
		}
		if (full1Count != fullCapacity || full2Count != fullCapacity) {
			helper.fail("MOD-178 full chest changed: full1=" + full1Count + " full2=" + full2Count
					+ " (expected " + fullCapacity + " each; a full target must neither gain nor lose items)");
			return;
		}
		helper.succeed();
	}

	private static void fillWith(Container container, net.minecraft.world.item.Item item) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			container.setItem(slot, new ItemStack(item, 64));
		}
	}

	private static int countOf(Container container, net.minecraft.world.item.Item item) {
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.is(item)) total += stack.getCount();
		}
		return total;
	}

	/** MOD-104: a disabled side disconnects the endpoint and moves nothing. */
	public static void disabledFaceBlocksTransfer(GameTestHelper helper) {
		if (!build(helper)) return;
		ItemPipeBlockEntity a = pipe(helper, PIPE_A);
		if (a == null) {
			helper.fail("MOD-104 source pipe vanished before disabled-face check");
			return;
		}
		a.setFaceMode(Direction.WEST, PipeFaceMode.DISABLED);
		ItemNetworkManager.tickAll(helper.getLevel());
		Container source = container(helper, SOURCE);
		Container target = container(helper, TARGET);
		int sourceCount = source == null ? -1 : source.getItem(0).getCount();
		int targetCount = target == null ? -1 : target.getItem(0).getCount();
		if (sourceCount != 16 || targetCount != 0) {
			helper.fail("MOD-104 disabled face leaked items; source=" + sourceCount + " target=" + targetCount);
			return;
		}
		helper.succeed();
	}

	/** MOD-104: an internal pipe link is only a join/disconnect gate and must stop the graph cleanly. */
	public static void disabledPipeLinkBlocksTransfer(GameTestHelper helper) {
		if (!build(helper)) return;
		ItemPipeBlockEntity a = pipe(helper, PIPE_A);
		if (a == null) {
			helper.fail("MOD-104 source pipe vanished before internal-link check");
			return;
		}
		a.setFaceMode(Direction.EAST, PipeFaceMode.DISABLED);
		ItemNetworkManager.tickAll(helper.getLevel());
		Container source = container(helper, SOURCE);
		Container target = container(helper, TARGET);
		int sourceCount = source == null ? -1 : source.getItem(0).getCount();
		int targetCount = target == null ? -1 : target.getItem(0).getCount();
		if (sourceCount != 16 || targetCount != 0) {
			helper.fail("MOD-104 disabled internal link leaked items; source=" + sourceCount + " target=" + targetCount);
			return;
		}
		helper.succeed();
	}

	// ── MOD-108: pipe ↔ MACHINE, not just chest ───────────────────────────────────────────────────
	//
	// The three scenarios above move items between two iron chests, which accept anything in any slot.
	// A machine does not: MachineBlockEntity gates automation through canPlaceItemThroughFace /
	// canTakeItemThroughFace, so a pipe can legitimately be refused. That path had no coverage — and it
	// is the one players actually build (chest → pipe → machine).

	/**
	 * MOD-108: a pipe INSERT face feeds a real machine's input slot, and the machine's own automation
	 * rules decide what lands there. Uses the macerator: it accepts ore in its input slot, so a
	 * successful transfer proves the pipe speaks the same item capability the machine exposes.
	 */
	public static void insertsIntoMachine(GameTestHelper helper) {
		helper.setBlock(SOURCE, ModContent.IRON_CHEST.get());
		helper.setBlock(PIPE_A, ModContent.ITEM_PIPE.get());
		helper.setBlock(PIPE_B, ModContent.ITEM_PIPE.get());
		helper.setBlock(TARGET, ModContent.MACERATOR.get());
		Container source = container(helper, SOURCE);
		ItemPipeBlockEntity a = pipe(helper, PIPE_A);
		ItemPipeBlockEntity b = pipe(helper, PIPE_B);
		Container machine = container(helper, TARGET);
		if (source == null || a == null || b == null || machine == null) {
			helper.fail("MOD-108 machine rig block entity missing");
			return;
		}
		// Raw iron is a macerator input, so the machine's canPlaceItemThroughFace admits it.
		source.setItem(0, new ItemStack(ModContent.RAW_TIN.get(), 16));
		a.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		b.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		a.serverTick(helper.getLevel(), a.getBlockPos(), a.getBlockState());
		b.serverTick(helper.getLevel(), b.getBlockPos(), b.getBlockState());
		ItemNetworkManager.tickAll(helper.getLevel());

		int expected = Math.min(16, Config.itemPipeItemsPerTransfer);
		int moved = 0;
		for (int slot = 0; slot < machine.getContainerSize(); slot++) {
			if (machine.getItem(slot).is(ModContent.RAW_TIN.get())) {
				moved += machine.getItem(slot).getCount();
			}
		}
		if (moved != expected || source.getItem(0).getCount() != 16 - expected) {
			helper.fail("MOD-108 pipe→machine moved " + moved + " (expected " + expected + "); source left "
					+ source.getItem(0).getCount());
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-108: shift + right-click with the wrench unbolts one of our blocks — it drops as an item and
	 * the space is cleared.
	 *
	 * <p>Also guards the scope: a vanilla block next to it must be left alone. A wrench that dismantled
	 * anything would be a griefing tool, so "ours only" is a rule worth a test rather than a comment.
	 */
	public static void wrenchDismantlesOwnBlocks(GameTestHelper helper) {
		helper.setBlock(PIPE_A, ModContent.ITEM_PIPE.get());
		helper.setBlock(PIPE_B, net.minecraft.world.level.block.Blocks.STONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack wrench = new ItemStack(ModContent.WRENCH.get());

		boolean ourBlock = dev.alaindustrial.item.WrenchDismantle.dismantle(
				helper.getLevel(), helper.absolutePos(PIPE_A), player, wrench);
		boolean vanillaBlock = dev.alaindustrial.item.WrenchDismantle.dismantle(
				helper.getLevel(), helper.absolutePos(PIPE_B), player, wrench);

		boolean pipeGone = helper.getLevel().getBlockState(helper.absolutePos(PIPE_A)).isAir();
		boolean stoneKept = helper.getLevel().getBlockState(helper.absolutePos(PIPE_B))
				.is(net.minecraft.world.level.block.Blocks.STONE);
		if (!ourBlock || !pipeGone) {
			helper.fail("MOD-108 wrench did not dismantle the pipe: handled=" + ourBlock + " gone=" + pipeGone);
			return;
		}
		if (vanillaBlock || !stoneKept) {
			helper.fail("MOD-108 wrench touched a vanilla block: handled=" + vanillaBlock + " stoneKept=" + stoneKept);
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-108: the same chest→pipe→chest run, but with VANILLA chests.
	 *
	 * <p>Every other scenario uses the mod's own iron chest. A player builds with vanilla chests, whose
	 * item capability is registered by the loader (not by us) — if that seam were broken, the pipe would
	 * do nothing in a real world while every test stayed green.
	 */
	public static void transfersBetweenVanillaChests(GameTestHelper helper) {
		helper.setBlock(SOURCE, net.minecraft.world.level.block.Blocks.CHEST);
		helper.setBlock(PIPE_A, ModContent.ITEM_PIPE.get());
		helper.setBlock(PIPE_B, ModContent.ITEM_PIPE.get());
		helper.setBlock(TARGET, net.minecraft.world.level.block.Blocks.CHEST);
		Container source = container(helper, SOURCE);
		ItemPipeBlockEntity a = pipe(helper, PIPE_A);
		ItemPipeBlockEntity b = pipe(helper, PIPE_B);
		Container target = container(helper, TARGET);
		if (source == null || a == null || b == null || target == null) {
			helper.fail("MOD-108 vanilla-chest rig block entity missing");
			return;
		}
		source.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
		a.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		b.setFaceMode(Direction.EAST, PipeFaceMode.INSERT);
		a.serverTick(helper.getLevel(), a.getBlockPos(), a.getBlockState());
		b.serverTick(helper.getLevel(), b.getBlockPos(), b.getBlockState());
		ItemNetworkManager.tickAll(helper.getLevel());

		int expected = Math.min(16, Config.itemPipeItemsPerTransfer);
		int delivered = target.getItem(0).getCount();
		if (delivered != expected || source.getItem(0).getCount() != 16 - expected) {
			helper.fail("MOD-108 vanilla chest→pipe→chest moved " + delivered + " (expected " + expected
					+ "); source left " + source.getItem(0).getCount());
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-108 balance guard: the network transfers only once per {@code itemPipeTransferIntervalTicks},
	 * not every tick.
	 *
	 * <p>This is the regression that shipped in MOD-104: a batch moved on EVERY tick, so the passive
	 * starter pipe ran at 20 items/s — 8× a vanilla hopper, and faster than the starter tier of any
	 * comparable mod. Ticking the network several times in a row must still yield exactly one batch;
	 * without the cooldown this test sees N batches.
	 */
	public static void respectsTransferInterval(GameTestHelper helper) {
		if (!build(helper)) return;
		// Several ticks back-to-back, fewer than one full interval.
		int ticks = Math.max(2, Math.min(5, Config.itemPipeTransferIntervalTicks - 1));
		for (int i = 0; i < ticks; i++) {
			ItemNetworkManager.tickAll(helper.getLevel());
		}
		Container target = container(helper, TARGET);
		int delivered = target == null ? -1 : target.getItem(0).getCount();
		int oneBatch = Math.min(16, Config.itemPipeItemsPerTransfer);
		if (delivered != oneBatch) {
			helper.fail("MOD-108 interval ignored: " + ticks + " ticks delivered " + delivered
					+ " items, expected exactly one batch of " + oneBatch
					+ " (a per-tick pipe would deliver ~" + ticks * oneBatch + ")");
			return;
		}
		helper.succeed();
	}
}

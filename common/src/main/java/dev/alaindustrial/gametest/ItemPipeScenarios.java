package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.ItemNetworkManager;
import dev.alaindustrial.core.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

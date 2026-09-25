package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * MOD-661: flowing fluid washes away the mod's transport lines — every cable, pipe and the monitoring
 * wire — on both lines of Minecraft, whatever shape the block has taken.
 *
 * <p>The two lines decide this differently. 26.3 asks the {@code #minecraft:washed_away_by_fluids} tag,
 * so the blocks are listed there. 26.2 washes away whatever does not block motion, and "blocks motion"
 * there is read from the collision box: a straight pipe is thin and washed, but a pipe with arms up and
 * down is a full block tall and counted as solid, so the same pipe survived or not depending on its
 * neighbours. That line forces the blocks non-solid instead. The column below is that case: its middle
 * pipe is a full block tall, and it must go like the rest.
 */
public final class FluidWashScenarios {

	private static final List<Supplier<Block>> LINES = List.of(
			() -> ModContent.COPPER_CABLE.get(), () -> ModContent.TIN_CABLE.get(),
			() -> ModContent.GOLD_CABLE.get(), () -> ModContent.ELECTRUM_CABLE.get(),
			() -> ModContent.INSULATED_COPPER_CABLE.get(), () -> ModContent.INSULATED_TIN_CABLE.get(),
			() -> ModContent.INSULATED_GOLD_CABLE.get(), () -> ModContent.INSULATED_ELECTRUM_CABLE.get(),
			() -> ModContent.ITEM_PIPE.get(), () -> ModContent.ITEM_PIPE_ADVANCED.get(),
			() -> ModContent.FLUID_PIPE.get(), () -> ModContent.REINFORCED_FLUID_PIPE.get(),
			() -> ModContent.STEAM_PIPE.get(), () -> ModContent.REINFORCED_STEAM_PIPE.get(),
			() -> ModContent.SMART_WIRE.get());

	/** Middle of the pipe column: arms up and down make it a full block tall. */
	private static final BlockPos COLUMN_MIDDLE = new BlockPos(6, 3, 7);

	private FluidWashScenarios() {
	}

	/** Whether this block is one of the transport lines flowing fluid washes away (MOD-661). */
	static boolean isTransportLine(Block block) {
		for (Supplier<Block> line : LINES) {
			if (line.get() == block) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Three rows on a stone floor, seven blocks at most per row (flowing water reaches seven cells), each
	 * row fed by a water source at its start; and a column of three item pipes on a stone post, fed at
	 * its middle. Every block must be washed away.
	 */
	public static void waterWashesAwayEveryTransportLine(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		List<BlockPos> placed = new ArrayList<>();
		List<Block> blocks = new ArrayList<>();
		int[] rows = {1, 3, 5};
		int next = 0;
		for (int row : rows) {
			for (int x = 1; x <= 7 && next < LINES.size(); x++, next++) {
				BlockPos pos = new BlockPos(x, 1, row);
				Block block = LINES.get(next).get();
				helper.setBlock(pos, block);
				placed.add(pos);
				blocks.add(block);
			}
		}

		// The column: a stone post keeps the floor water off its bottom pipe, so the middle pipe keeps
		// its arm down until the water that reaches it is its own.
		helper.setBlock(new BlockPos(6, 1, 7), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 1, 7), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 2, 7), Blocks.STONE);
		Block pipe = ModContent.ITEM_PIPE.get();
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(6, y, 7), pipe);
		}
		double height = helper.getBlockState(COLUMN_MIDDLE)
				.getCollisionShape(helper.getLevel(), helper.absolutePos(COLUMN_MIDDLE), CollisionContext.empty())
				.bounds().getYsize();
		if (height < 1.0) {
			helper.fail("MOD-661 precondition: the middle pipe of the column must be a full block tall (arms up and"
					+ " down), is " + height + " — the case this test exists for is not being built");
			return;
		}
		placed.add(COLUMN_MIDDLE);
		blocks.add(pipe);

		for (int row : rows) {
			helper.setBlock(new BlockPos(0, 1, row), Blocks.WATER);
		}
		helper.setBlock(new BlockPos(5, 3, 7), Blocks.WATER);

		helper.succeedWhen(() -> {
			for (int i = 0; i < placed.size(); i++) {
				if (helper.getBlockState(placed.get(i)).is(blocks.get(i))) {
					helper.fail("MOD-661: flowing water left " + blocks.get(i) + " standing at " + placed.get(i));
				}
			}
		});
	}
}

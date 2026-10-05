package dev.alaindustrial.command.demo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The coordinates and materials that more than one zone of the demo stand relies on (MOD-709). What
 * only one zone needs stays in that zone's file; the stand's size and its public constants stay in
 * {@link DemoStand}.
 */
final class StandLayout {
	private StandLayout() {
	}

	/** Floor material — also the datum marker {@link DemoStand#findOrigin} recognises for idempotent rebuilds. */
	static final Block FLOOR = Blocks.SMOOTH_STONE;

	/** Row of the generator line; the battery boxes sit one row behind it, the water mill's channel in front. */
	static final int GEN_Z = 8;

	/** Row z of the item-less blocks: on the floor in front of the farm row, clear of every zone. */
	static final int ITEMLESS_Z = 44;
	/** First column of the item-less row, and the pitch between two of its blocks (two free cells between). */
	static final int ITEMLESS_X0 = 2;
	static final int ITEMLESS_STEP = 3;
}

package dev.alaindustrial.command.demo;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.level.block.Block;

/**
 * Zone <b>itemless</b>: every block that has no item — fluids, fire, soot, the upper column
 * sections, the dome, the kok-sagyz plant and root, the capsule cells. They cannot hang in a
 * frame, so the item wall cannot show them; here each stands on its own, two free cells apart.
 * Fluids sit in sunken one-block basins (the pattern the oil and diesel pools use), the rest on
 * the floor. Derived from the registry, so a block added later joins the row by itself.
 *
 * <p>Domain (coding standard, section 1): cross-cutting view (the blocks that have no item); the
 * exhibits belong to the domains of their own blocks.
 */
final class ItemlessRowZone implements DemoZone {
	private static final Block FLOOR = StandLayout.FLOOR;
	private static final int ITEMLESS_Z = StandLayout.ITEMLESS_Z;

	/** Camera of {@code /ala demo tp itemless}. */
	static final DemoStand.TpPoint ITEMLESS_CAMERA =
			new DemoStand.TpPoint("itemless", 30.0, 10.0, 36.0, 0.0f, 42.0f, false);

	@Override
	public void build(StandWriter w) {
		List<Block> blocks = DemoStand.showcaseBlocks();
		for (int i = 0; i < blocks.size() && DemoStand.itemlessX(i) < DemoStand.WIDTH; i++) {
			int x = DemoStand.itemlessX(i);
			Block block = blocks.get(i);
			if (block instanceof net.minecraft.world.level.block.LiquidBlock) {
				w.set(x, -1, ITEMLESS_Z, FLOOR);
				w.set(x, 0, ITEMLESS_Z, block);
			} else if (block == ModContent.KOK_SAGYZ.get()) {
				// A plant needs its root under it.
				w.set(x, 0, ITEMLESS_Z, ModContent.KOK_SAGYZ_ROOT.get());
				w.set(x, 1, ITEMLESS_Z, block);
			} else {
				w.set(x, 1, ITEMLESS_Z, block);
			}
		}
	}
}

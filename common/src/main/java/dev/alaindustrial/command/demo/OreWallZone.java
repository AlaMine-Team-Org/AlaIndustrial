package dev.alaindustrial.command.demo;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.level.block.Block;

/**
 * Zone <b>ores</b>: a 6×2 wall at x=50..55, z=14 — stone variants on top, deepslate variants below —
 * with the Nether ore as its last column.
 *
 * <p>Palladium (MOD-423) breaks the pairing the wall was built around: it is the only ore of the
 * mod without a deepslate twin, because its host rock is netherrack/basalt/blackstone. Rather
 * than pad the grid with a filler block, it gets its own column with the ore on both rows, so the
 * wall stays rectangular and {@code DemoStandGameTest} still sees every registered block.
 *
 * <p>Domain (coding standard, section 1): Materials.
 */
final class OreWallZone implements DemoZone {
	/** Camera of {@code /ala demo tp ores}. */
	static final DemoStand.TpPoint ORES_CAMERA =
			new DemoStand.TpPoint("ores", 52.0, 5.0, 10.0, 0.0f, 30.0f, false);

	@Override
	public void build(StandWriter w) {
		Block[][] wall = {
				{ModContent.TIN_ORE.get(), ModContent.SILVER_ORE.get(),
						ModContent.NICKEL_ORE.get(), ModContent.URANIUM_ORE.get(),
						ModContent.SULFUR_ORE.get(), ModContent.PALLADIUM_ORE.get()},
				{ModContent.DEEPSLATE_TIN_ORE.get(), ModContent.DEEPSLATE_SILVER_ORE.get(),
						ModContent.DEEPSLATE_NICKEL_ORE.get(), ModContent.DEEPSLATE_URANIUM_ORE.get(),
						ModContent.DEEPSLATE_SULFUR_ORE.get(), ModContent.PALLADIUM_ORE.get()}};
		for (int i = 0; i < wall[0].length; i++) {
			w.set(50 + i, 2, 14, wall[0][i]);
			w.set(50 + i, 1, 14, wall[1][i]);
		}
	}
}

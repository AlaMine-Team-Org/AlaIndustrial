package dev.alaindustrial.command.demo;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;

/**
 * Zone <b>lab plaque</b> (MOD-513, row z=34, x 74..83): the seventeen engraved plates the plaque of
 * an abandoned lab is built from — the ten digits in one row on the floor, the seven broken prefix
 * letters one level up. Purely decorative. They keep their default FACING=NORTH, so the engraving
 * faces the cameras like every machine front on the stand.
 *
 * <p>Row z=34 sits east of the cable rows, and east of the monitoring wall too: the wall's front (x 66..70)
 * is left free, because the plaque used to stand right across it and hide the panels a visitor is
 * meant to read. A plate conducts nothing, so it cannot join two runs into one network the way an
 * energy block would.
 *
 * <p>Domain (coding standard, section 1): Decor.
 */
final class LabPlaqueZone implements DemoZone {
	@Override
	public void build(StandWriter w) {
		List<Supplier<Block>> digits = List.of(
				ModContent.ENGRAVED_PLATE_0, ModContent.ENGRAVED_PLATE_1, ModContent.ENGRAVED_PLATE_2, ModContent.ENGRAVED_PLATE_3,
				ModContent.ENGRAVED_PLATE_4, ModContent.ENGRAVED_PLATE_5, ModContent.ENGRAVED_PLATE_6,
				ModContent.ENGRAVED_PLATE_7, ModContent.ENGRAVED_PLATE_8, ModContent.ENGRAVED_PLATE_9);
		for (int i = 0; i < digits.size(); i++) {
			w.set(74 + i, 1, 34, digits.get(i).get());
		}
		List<Supplier<Block>> letters = List.of(
				ModContent.BROKEN_ENGRAVED_PLATE_W, ModContent.BROKEN_ENGRAVED_PLATE_K, ModContent.BROKEN_ENGRAVED_PLATE_P,
				ModContent.BROKEN_ENGRAVED_PLATE_B, ModContent.BROKEN_ENGRAVED_PLATE_D,
				ModContent.BROKEN_ENGRAVED_PLATE_R, ModContent.BROKEN_ENGRAVED_PLATE_M);
		for (int i = 0; i < letters.size(); i++) {
			w.set(76 + i, 2, 34, letters.get(i).get());
		}
	}
}

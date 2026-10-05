package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Zone <b>windmills</b>: the three wind mills on pillars, a battery box sitting directly beneath
 * each head as a decorative plinth. The box does <b>not</b> receive the mill's EU: a wind mill
 * emits only from its back <i>horizontal</i> face (opposite FACING, see
 * {@code WindMillBlockEntity#energyRoleForFace}), never downward, and the box's top face is inert
 * anyway (single-axis IO, MOD-006). A pitch of six keeps the mills well out of each other's
 * interference radius. Their EU/t depends on build height vs sea level, so on a low superflat they
 * are intentionally decorative (see MOD-058 task log) — the plinth box is purely scenic (MOD-103).
 *
 * <p>The row continues the generator row's pitch (x=62, 68, 74, then the lightning rod at 80), so the
 * whole power band reads as one line from coal to lightning.
 *
 * <p>Domain (coding standard, section 1): EnergyGeneration.
 */
final class WindMillsZone implements DemoZone {
	private static final Block FLOOR = StandLayout.FLOOR;
	private static final int GEN_Z = StandLayout.GEN_Z;

	/** Camera of {@code /ala demo tp windmills}. */
	static final DemoStand.TpPoint WINDMILLS_CAMERA =
			new DemoStand.TpPoint("windmills", 71.0, 9.0, 0.0, 0.0f, 12.0f, false);

	@Override
	public void build(StandWriter w) {
		Block[] mills = {ModContent.WIND_MILL.get(),
				ModContent.HIGH_ALTITUDE_WIND_MILL.get(), ModContent.STORM_WIND_MILL.get()};
		// Each head carries a rotor of its own grade: a wind mill with an empty rotor slot produces nothing
		// at any height, so without one the three stood there as scenery whatever the world's altitude.
		Item[] rotors = {ModContent.WINDMILL_ROTOR.get(), ModContent.WINDMILL_ROTOR_REINFORCED.get(),
				ModContent.WINDMILL_ROTOR_ADVANCED.get()};
		int x = 62;
		for (int i = 0; i < mills.length; i++) {
			for (int y = 1; y <= 4; y++) {
				w.set(x, y, GEN_Z, FLOOR);
			}
			w.set(x, 5, GEN_Z, ModContent.BATTERY_BOX.get());
			w.set(x, 6, GEN_Z, mills[i]);
			w.fillSlot(x, 6, GEN_Z, WindMillBlockEntity.ROTOR_SLOT, new ItemStack(rotors[i]));
			x += 6;
		}
		// MOD-386: the lightning rod shares this weather row — same mast-on-a-pillar shape, and a
		// conductor tip pre-installed so the stand shows the configured block rather than an inert one.
		x = 80;
		for (int y = 1; y <= 4; y++) {
			w.set(x, y, GEN_Z, FLOOR);
		}
		w.set(x, 5, GEN_Z, ModContent.BATTERY_BOX.get());
		w.set(x, 6, GEN_Z, ModContent.LIGHTNING_ROD_GENERATOR.get());
		w.fillSlot(x, 6, GEN_Z, LightningRodGeneratorBlockEntity.TIP_SLOT,
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get()));
	}
}

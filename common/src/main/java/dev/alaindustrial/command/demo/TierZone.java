package dev.alaindustrial.command.demo;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Zone <b>tiers</b> (MOD-294, row z=2, rows z=0..1 kept clear): one live network per voltage tier so
 * the ladder reads left to right along the stand's north edge, twenty cells between the three. Each storage block faces WEST — the
 * single-axis IO rule (front IN, back OUT, MOD-006) then puts its output face east, straight
 * into the tier's own cable grade, exactly like the cable-run boxes below.
 *
 * <p>HV is a stub by design: the electrum cable and its consumer exist, but real HV content is
 * roadmap; the row shows what is built rather than pretending otherwise.
 *
 * <p>Domain (coding standard, section 1): EnergyGrid.
 */
final class TierZone implements DemoZone {
	/** Camera of {@code /ala demo tp tiers}. */
	static final DemoStand.TpPoint TIERS_CAMERA =
			new DemoStand.TpPoint("tiers", 25.0, 9.0, -4.0, 0.0f, 28.0f, false);

	@Override
	public void build(StandWriter w) {
		// LV: battery box, buffer charged full → tin cable → a macerator actually grinding.
		w.placeFacing(4, 1, 2, ModContent.BATTERY_BOX.get(), Direction.WEST);
		w.chargeBuffer(4, 1, 2);
		w.set(5, 1, 2, ModContent.TIN_CABLE.get());
		w.placeWorkingMachine(6, 2, ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 64));
		// MV: CESU, buffer charged full → gold cable → the assembler, charged and idle (first MV machine).
		w.placeFacing(24, 1, 2, ModContent.CESU.get(), Direction.WEST);
		w.chargeBuffer(24, 1, 2);
		w.set(25, 1, 2, ModContent.GOLD_CABLE.get());
		w.set(26, 1, 2, ModContent.ASSEMBLER.get());
		w.chargeBuffer(26, 1, 2);
		// HV stub: an LV battery feeding a teleporter over HV wiring is legal (packet ceiling, not
		// floor) and keeps the row honest — no fake HV source stands in for content that is not built.
		w.placeFacing(44, 1, 2, ModContent.BATTERY_BOX.get(), Direction.WEST);
		w.chargeBuffer(44, 1, 2);
		w.set(45, 1, 2, ModContent.ELECTRUM_CABLE.get());
		w.set(46, 1, 2, ModContent.TELEPORTER.get());
		// MOD-112: the same capsule as the misc zone's station, from two glass blocks.
		w.assembleCapsule(46, 1, 2);
	}
}

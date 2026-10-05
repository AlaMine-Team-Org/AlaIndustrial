package dev.alaindustrial.command.demo;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Zone <b>cables</b> (rows z=28, 32, 36, 40, in <b>two columns</b>: bare grades at x=32..39, their
 * insulated counterparts at x=50..57): a fully charged battery box feeds a 6-cable run into an electric
 * furnace with input — a live network per run, so the energy visibly flows (and the resistive loss of
 * each material is observable in the GUI).
 *
 * <p>Rows must stay well apart or adjacent runs would connect into a single network; they are four
 * cells apart (MOD-659), with the lab plaque of {@link LabPlaqueZone} in the aisle between two of
 * them. Pairing each conductor with its insulated version side by side reads better than a list: the
 * loss difference is one glance away instead of four rows.
 *
 * <p>Domain (coding standard, section 1): EnergyGrid.
 */
final class CableRunsZone implements DemoZone {
	/** Camera of {@code /ala demo tp cables}. */
	static final DemoStand.TpPoint CABLES_CAMERA =
			new DemoStand.TpPoint("cables", 45.0, 14.0, 21.0, 0.0f, 45.0f, false);

	@Override
	public void build(StandWriter w) {
		Block[][] cables = {
			{ModContent.COPPER_CABLE.get(), ModContent.INSULATED_COPPER_CABLE.get()},
			{ModContent.TIN_CABLE.get(), ModContent.INSULATED_TIN_CABLE.get()},
			{ModContent.GOLD_CABLE.get(), ModContent.INSULATED_GOLD_CABLE.get()},
			{ModContent.ELECTRUM_CABLE.get(), ModContent.INSULATED_ELECTRUM_CABLE.get()},
		};
		int z = 28;
		for (Block[] row : cables) {
			for (int column = 0; column < row.length; column++) {
				int x0 = 32 + column * 18;
				// The box's rotation is load-bearing: single-axis IO (MOD-006) emits ONLY from the face
				// opposite FACING. The cable run sits to the box's east, so the box must face WEST for its
				// output face to meet the cables. Placed with the default state (FACING=NORTH) it would emit
				// southward into thin air, the cables would not connect, and the whole row would sit dead
				// (MOD-103) — the same fix pattern as the misc zone's teleporter box.
				w.placeFacing(x0, 1, z, ModContent.BATTERY_BOX.get(), Direction.WEST);
				w.chargeBuffer(x0, 1, z);
				for (int x = x0 + 1; x <= x0 + 6; x++) {
					w.set(x, 1, z, row[column]);
				}
				w.set(x0 + 7, 1, z, ModContent.ELECTRIC_FURNACE.get());
				w.fillSlot(x0 + 7, 1, z, 0, new ItemStack(Items.RAW_COPPER, 64));
			}
			z += 4;
		}
	}
}

package dev.alaindustrial.command.demo;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Zone <b>loss lane</b> (MOD-294, row z=14): a single 36-block copper run from a
 * charged battery box into an electric furnace. The bare-vs-insulated comparison at 6 blocks
 * already lives in the cable zone; this lane answers the other question — what a LONG haul
 * costs. Read it by walking the line and comparing the furnace GUI's received-EU against the
 * distance from the box.
 *
 * <p>Domain (coding standard, section 1): EnergyGrid.
 */
final class LossLaneZone implements DemoZone {
	/** Camera of {@code /ala demo tp loss}. */
	static final DemoStand.TpPoint LOSS_CAMERA =
			new DemoStand.TpPoint("loss", 22.0, 8.0, 10.0, 0.0f, 40.0f, false);

	@Override
	public void build(StandWriter w) {
		w.placeFacing(4, 1, 14, ModContent.BATTERY_BOX.get(), Direction.WEST);
		w.chargeBuffer(4, 1, 14);
		for (int x = 5; x <= 40; x++) {
			w.set(x, 1, 14, ModContent.COPPER_CABLE.get());
		}
		w.set(41, 1, 14, ModContent.ELECTRIC_FURNACE.get());
		w.fillSlot(41, 1, 14, 0, new ItemStack(Items.RAW_COPPER, 64));
	}
}

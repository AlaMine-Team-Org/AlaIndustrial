package dev.alaindustrial.block;

import java.util.List;

/**
 * How a cable arm meeting the Electric Mob Trap continues into it (MOD-855). Kept apart from
 * {@link ElectricMobTrapBlock} and free of game types, like {@link TeleporterCableArm}.
 *
 * <p>The trap's sides are open between its corner insulators: only the base plate (0..3 px high) reaches
 * the cell edge, so a cable at line height (5..11) ran straight into the cavity and read as a plug in a
 * hole. Met low instead, the cable drops its sleeve (2..8) the way it does at a solar panel, and this one
 * band carries the sleeve on over the base plate to the plate under the spark gap: that plate is 5..11 px
 * wide, the sleeve's own width, and starts 5 px in from every edge. The band's sides meet the plate's sides
 * only along an edge, never in one plane; its end lies flat on the plate's near face, the two facing each
 * other.
 */
public final class ElectricMobTrapCableArm {

	/** The one band, measured from {@code models/block/electric_mob_trap.json}. */
	public static final List<CableArmReach.Band> BANDS = List.of(new CableArmReach.Band(2f, 8f, 5f));

	private ElectricMobTrapCableArm() {
	}
}

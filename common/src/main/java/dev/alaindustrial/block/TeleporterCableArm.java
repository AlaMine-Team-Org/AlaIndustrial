package dev.alaindustrial.block;

import java.util.List;

/**
 * How a cable arm meeting the assembled teleporter station continues into it (MOD-672). Kept apart from
 * {@link TeleporterBlock} and free of game types so a plain JUnit test can re-measure the model and pin
 * this number ({@code TeleporterCableArmTest}).
 *
 * <p>The station's octagonal base is 10 px tall, so a cable at line height (5..11) stood a pixel above
 * its deck and read as a wire passing over the machine. Met low instead, the cable drops its sleeve
 * (2..8) the way it does at a solar panel. Every flat side of the octagon stands 0.4 px back from the
 * cell edge over the whole sleeve height, so one band closes that gap; its depth is the deepest of the
 * nearest surfaces, so no side is left with a slit. The front plate (6..10 wide, flush with the edge)
 * lies inside the band's cross-section and shares no plane with its sides.
 */
public final class TeleporterCableArm {

	/** The one band, measured from {@code models/block/teleporter_formed.json}. */
	public static final List<CableArmReach.Band> BANDS = List.of(new CableArmReach.Band(2f, 8f, 0.41f));

	private TeleporterCableArm() {
	}
}
